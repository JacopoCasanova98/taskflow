"""Offline Linux/root tests: fake aws on PATH, real jq, no credentials/network."""
import base64
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / 'materialize-secrets.sh'
NAMES = ['spring.datasource.username', 'spring.datasource.password', 'taskflow.security.jwt.secret-base64']


class MaterializeTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.bin = self.root / 'bin'
        self.bin.mkdir()
        fake = self.bin / 'aws'
        fake.write_text('''#!/usr/bin/env python3
import os, pathlib, sys
if sys.argv[1:] == ['--version']:
    print('aws-cli/2.0.0 offline-fixture'); sys.exit(0)
args = sys.argv[1:]
assert args[:3] == ['secretsmanager', 'get-secret-value', '--secret-id']
assert args[4:] == ['--version-stage','AWSCURRENT','--query','SecretString','--output','json']
assert args[3] in ('fixture-db', 'fixture-jwt')
assert os.environ['AWS_PAGER'] == '' and os.environ['AWS_CLI_AUTO_PROMPT'] == 'off'
if os.environ.get('FAIL_AWS') == args[3]:
    print('NOT-A-SECRET-error-fixture', file=sys.stderr); sys.exit(1)
sys.stdout.write((pathlib.Path(os.environ['FIXTURES']) / args[3]).read_text())
''')
        fake.chmod(0o700)
        self.runtime = self.root / 'runtime'
        self.env = {'PATH': str(self.bin) + ':' + os.environ['PATH'],
                    'TASKFLOW_RUNTIME_DIR': str(self.runtime), 'FIXTURES': str(self.root)}
        self.db = {'username': 'fixture-user', 'password': 'NOT-A-SECRET fixture $() \\"'}
        self.jwt = base64.b64encode(bytes(32)).decode()
        self.payloads(self.db, self.jwt)

    def payloads(self, db, jwt):
        (self.root / 'fixture-db').write_text(json.dumps(json.dumps(db)))
        (self.root / 'fixture-jwt').write_text(json.dumps(jwt))

    def run_script(self, success):
        result = subprocess.run([str(SCRIPT), 'fixture-db', 'fixture-jwt'], env=self.env,
                                capture_output=True, text=True)
        self.assertEqual(result.returncode == 0, success, 'Unexpected materializer exit status')
        self.assertNotIn(self.jwt, result.stdout + result.stderr)
        self.assertNotIn('NOT-A-SECRET', result.stdout + result.stderr)
        self.assertEqual(list(self.runtime.glob('.secrets.*')), [])
        return result

    def snapshot(self):
        return {p.name: p.read_bytes() for p in (self.runtime / 'secrets').iterdir()}

    def test_success_replacement_and_permissions(self):
        self.run_script(True)
        self.assertEqual(self.snapshot(), dict(zip(NAMES, [self.db['username'].encode(),
                                                         self.db['password'].encode(), self.jwt.encode()])))
        for p in [self.runtime, self.runtime / 'secrets']:
            self.assertEqual(p.stat().st_mode & 0o777, 0o700)
            self.assertEqual((p.stat().st_uid, p.stat().st_gid), (0, 0))
        for p in (self.runtime / 'secrets').iterdir():
            self.assertEqual(p.stat().st_mode & 0o777, 0o400)
            self.assertEqual((p.stat().st_uid, p.stat().st_gid), (10001, 10001))
        self.db['username'] = 'replacement-fixture-user'
        self.payloads(self.db, self.jwt)
        self.run_script(True)
        self.assertEqual(self.snapshot()[NAMES[0]], b'replacement-fixture-user')

    def test_invalid_inputs_never_replace_existing_generation(self):
        self.run_script(True)
        before = self.snapshot()
        cases = [({}, self.jwt), ({'password': 'NOT-A-SECRET'}, self.jwt),
                 ({'username': 'fixture'}, self.jwt),
                 ({'username': '', 'password': 'NOT-A-SECRET'}, self.jwt),
                 ({'username': 'fixture', 'password': ''}, self.jwt),
                 ({'username': 'fixture', 'password': 1}, self.jwt),
                 (self.db, ''), (self.db, 'not-base64!'),
                 (self.db, base64.b64encode(bytes(31)).decode()),
                 (self.db, base64.b64encode(bytes(33)).decode())]
        for db, jwt in cases:
            with self.subTest(case=cases.index((db, jwt))):
                self.payloads(db, jwt)
                self.run_script(False)
                self.assertEqual(self.snapshot(), before)
        (self.root / 'fixture-db').write_text(json.dumps('{invalid-json'))
        self.run_script(False)
        self.assertEqual(self.snapshot(), before)
        self.payloads(self.db, self.jwt)
        for secret in ['fixture-db', 'fixture-jwt']:
            self.env['FAIL_AWS'] = secret
            self.run_script(False)
            self.assertEqual(self.snapshot(), before)

    def test_permission_failure_does_not_publish_valid_staging(self):
        self.run_script(True)
        before = self.snapshot()
        fake = self.bin / 'chown'
        fake.write_text('#!/bin/sh\nexit 1\n')
        fake.chmod(0o700)
        self.db['username'] = 'replacement-fixture-user'
        self.payloads(self.db, self.jwt)
        self.run_script(False)
        self.assertEqual(self.snapshot(), before)

    def test_first_failure_publishes_nothing(self):
        self.payloads(self.db, '')
        self.run_script(False)
        self.assertFalse((self.runtime / 'secrets').exists())


if __name__ == '__main__':
    unittest.main()
