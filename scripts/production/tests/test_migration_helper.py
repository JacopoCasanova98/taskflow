"""Reference helper checks using a fake Java process, never a database."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / 'run-migrations.sh'


class MigrationHelperTest(unittest.TestCase):
    def test_requires_inputs_propagates_failure_and_passes_no_password_argument(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            fake = root / 'java'
            fake.write_text('''#!/usr/bin/env python3
import pathlib, sys
assert sys.argv[1:] == ['-Dloader.main=com.taskflow.operations.DatabaseMigration',
                        '-cp', '/fixture/backend.jar', 'org.springframework.boot.loader.launch.PropertiesLauncher']
sys.exit(23)
''')
            fake.chmod(0o700)
            env = {'PATH': str(root) + ':' + os.environ['PATH'],
                   'TASKFLOW_DB_URL': 'jdbc:postgresql://database.invalid:5432/taskflow',
                   'TASKFLOW_MIGRATION_PASSWORD_FILE': str(root / 'password'),
                   'TASKFLOW_BACKEND_JAR': '/fixture/backend.jar'}
            for key in ['TASKFLOW_DB_URL', 'TASKFLOW_MIGRATION_PASSWORD_FILE', 'TASKFLOW_BACKEND_JAR']:
                missing = dict(env)
                del missing[key]
                result = subprocess.run([str(SCRIPT)], env=missing, capture_output=True)
                self.assertNotEqual(result.returncode, 0)
            result = subprocess.run([str(SCRIPT)], env=env, capture_output=True)
            self.assertEqual(result.returncode, 23)
            self.assertEqual(result.stdout, b'')
            self.assertEqual(result.stderr, b'')


if __name__ == '__main__':
    unittest.main()
