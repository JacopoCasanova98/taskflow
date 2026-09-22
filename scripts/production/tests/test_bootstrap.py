"""Static bootstrap parity and fake-firewall tests; no host firewall commands."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[3]


class BootstrapTest(unittest.TestCase):
    def test_parity_and_guard(self):
        terraform = (ROOT / 'infra/terraform/templates/user-data.sh.tftpl').read_text()
        cf = (ROOT / 'infra/cloudformation/taskflow.yaml').read_text()
        body = cf.split('      UserData:\n        Fn::Base64: |\n')[1].split('      Tags:\n')[0]
        self.assertEqual(terraform, '\n'.join(line[10:] if line else '' for line in body.splitlines()) + '\n')
        self.assertNotIn('get-secret-value', terraform)
        self.assertIn('ExecStartPre=/usr/local/sbin/taskflow-imds-guard --prepare', terraform)
        self.assertIn('ExecStartPost=/usr/local/sbin/taskflow-imds-guard', terraform)
        subprocess.run(['bash', '-n'], input=terraform, text=True, check=True)
        guard = terraform.split("<<'GUARD'\n")[1].split('\nGUARD')[0]
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            script = root / 'guard'
            script.write_text(guard)
            state = root / 'state'
            state.write_text('[]')
            fake = root / 'iptables'
            fake.write_text('''#!/usr/bin/env python3
import json, os, pathlib, sys
p=pathlib.Path(os.environ['STATE']); state=json.loads(p.read_text()); args=sys.argv[1:]
assert args[0]=='-w'; args=args[1:]
assert 'OUTPUT' not in args
if args[:2]==['-n','-L']: sys.exit(0 if 'chain' in state else 1)
if args[0]=='-N': state.append('chain')
elif args[0]=='-C': sys.exit(0 if args[1:] in state else 1)
elif args[0]=='-I':
    assert args[2]=='1'; state.append([args[1]]+args[3:])
else: sys.exit(2)
p.write_text(json.dumps(state))
''')
            fake.chmod(0o700)
            nft = root / 'nft'
            nft.write_text('#!/bin/sh\nprintf "%s\\n" "$NFT_TABLES"\n')
            nft.chmod(0o700)
            env = {'PATH': str(root) + ':' + os.environ['PATH'], 'STATE': str(state), 'NFT_TABLES': ''}
            def run(*args):
                return subprocess.run(['bash', str(script), *args], env=env, capture_output=True).returncode
            self.assertNotEqual(run(), 0)  # Absent Docker chain fails visibly.
            self.assertEqual(run('--prepare'), 0)
            self.assertEqual(run(), 0)
            before = state.read_text()
            self.assertEqual(run('--prepare'), 0)
            self.assertEqual(run(), 0)
            self.assertEqual(state.read_text(), before)  # No duplicates.
            self.assertIn(['DOCKER-USER', '-d', '169.254.169.254/32', '-j', 'REJECT'], json.loads(before))
            env['NFT_TABLES'] = 'table ip docker-bridges'
            self.assertNotEqual(run(), 0)  # Stale iptables chain cannot hide native nftables.


if __name__ == '__main__':
    unittest.main()
