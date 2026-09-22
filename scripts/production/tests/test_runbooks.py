"""Static repository contracts only: never execute runbook commands or contact AWS."""
from pathlib import Path
import json
import re
import unittest

ROOT = Path(__file__).resolve().parents[3]
DOCS = ('DEPLOYMENT_RUNBOOK.md', 'DISASTER_RECOVERY.md', 'PRODUCTION_RUNTIME.md',
        'RDS_DATABASE_OPERATIONS.md', 'EC2_BOOTSTRAP_SECRETS.md', 'OBSERVABILITY.md',
        'ARCHITECTURE.md', 'ROADMAP.md')


def read(path):
    return (ROOT / path).read_text()


def normalized(text):
    return re.sub(r'\s+', ' ', text.replace('`', '').replace('**', '')).lower()


class RunbookContractTest(unittest.TestCase):
    def setUp(self):
        self.deployment = read('docs/DEPLOYMENT_RUNBOOK.md')
        self.recovery = read('docs/DISASTER_RECOVERY.md')

    def contains(self, text, *patterns):
        for pattern in patterns:
            with self.subTest(contract=pattern):
                self.assertRegex(normalized(text), pattern)

    def test_release_gates_and_runtime_identity(self):
        # Structured gate rows anchor order without coupling to complete paragraphs.
        rows = re.findall(r'^\| D(\d) \| (.*?) \| (.*?) \|$', self.deployment, re.M)
        self.assertEqual([number for number, _, _ in rows], list('1234567'))
        for (_, action, outcome), pattern in zip(rows, (
                'release.*preflight', 'pull both.*digests', 'ca/config.*materialize secrets',
                'observability', 'flyway migration.*successful.*taskflow_migrator',
                'recreate backend and frontend.*d5 succeeded', 'acceptance')):
            self.contains(action + ' ' + outcome, pattern)
        self.contains(self.deployment, 'immutable.*digests', 'same full reviewed git commit',
                      'frontend_image.*backend_image', '@sha256:', 'taskflow_app',
                      'runtime never uses rds master', 'sslmode=verify-full', 'official.*ca bundle',
                      'no normal docker compose down', 'earlier migrations may have committed',
                      'old runtime.*only.*schema-compatible', 'not one transaction',
                      'current database schema', 'never retag', 'no automatic flyway down migrations')
        blocks = '\n'.join(re.findall(r'```[^\n]*\n(.*?)```', self.deployment, re.S))
        self.assertNotRegex(blocks, r'docker\s+compose[^\n]*\bdown\b')

    def test_secret_and_ca_lifecycle(self):
        rotation = self.deployment.split('## Credential and CA rotation')[1].split('\n## ')[0]
        self.contains(rotation, 'password.*awscurrent.*materialize.*recreate backend',
                      'do not use docker restart', 'jwt.*refresh tokens.*not revoked',
                      'secret rollback.*must agree', 'never restore a known-compromised',
                      'rds ca rotation.*official.*bundle.*recreate backend.*verify-full')
        self.contains(self.deployment, 'reacquire after reboot/replacement before backend creation')

    def test_recovery_limits_and_cutover(self):
        self.contains(self.recovery, 'ec2.*no authoritative application data',
                      'replaceable host', 'retention.*7 days',
                      'pitr creates a new db instance.*does not overwrite',
                      'snapshot restore also creates a new db instance',
                      'latestrestorabletime.*earliest/latest window',
                      'not an exact five-minute rpo or rpo=0 guarantee',
                      'rto is not guaranteed or measured',
                      'regional disaster recovery is not provided', 'single-az rds',
                      'before switching.*quiesce/stop backend',
                      'do not run old and new databases with concurrent taskflow writes',
                      'restored postgresql passwords.*reconcile.*credentials.*before cutover',
                      'materializing awscurrent alone cannot change a restored db password',
                      'resurrect.*revoked refresh sessions',
                      'new instance identifier.*dimensions.*log-group path.*iac owner.*reconcile')
        for text in (self.deployment, self.recovery):
            self.contains(text, 'ms9.8.*deferred')
        ending = self.recovery.split('## Validation boundary')[1]
        self.contains(ending, 'static.*not evidence.*restore', 'ms9.8.*deferred')

    def test_implementation_contracts(self):
        compose = read('compose.production.yaml')
        for service in ('FRONTEND', 'BACKEND'):
            self.assertIn('image: ${TASKFLOW_' + service + '_IMAGE:?', compose)
        self.assertNotRegex(compose, r'(?m)^\s*build:')
        self.assertIn('SPRING_FLYWAY_ENABLED: "false"', compose)
        self.assertIn('create_host_path: false', compose)
        materializer = read('scripts/production/materialize-secrets.sh')
        self.contains(materializer, 'awscurrent', 'rename_exchange',
                      'backend recreation is required', 'taskflow_app')
        self.assertIn('DatabaseMigration', read('scripts/production/run-migrations.sh'))
        bootstrap = read('scripts/production/bootstrap-database.sql')
        self.assertIn('GRANT USAGE, CREATE ON SCHEMA public TO taskflow_migrator', bootstrap)
        self.assertIn('GRANT USAGE ON SCHEMA public TO taskflow_app', bootstrap)
        database = read('infra/terraform/database.tf')
        for field, value in (('backup_retention_period', '7'), ('delete_automated_backups', 'false'),
                             ('skip_final_snapshot', 'false'), ('multi_az', 'false')):
            self.assertRegex(database, rf'{field}\s*=\s*{value}\b')
        self.assertRegex(read('infra/terraform/ecr.tf'), r'image_tag_mutability\s*=\s*"IMMUTABLE"')
        outputs = read('infra/terraform/outputs.tf')
        for output in ('database_endpoint', 'app_instance_id', 'ecr_repository_urls'):
            self.assertIn('output "' + output + '"', outputs)
        terraform = '\n'.join(p.read_text() for p in (ROOT / 'infra/terraform').glob('*.tf'))
        self.assertNotRegex(terraform, r'resource\s+"aws_backup_')
        self.assertEqual(len(re.findall(r'resource\s+"aws_instance"', terraform)), 1)
        cf = read('infra/cloudformation/taskflow.yaml')
        self.assertNotIn('AWS::Backup::', cf)
        agent = json.loads(read('ops/cloudwatch/amazon-cloudwatch-agent.json'))
        self.assertEqual(agent['metrics']['append_dimensions'], {'InstanceId': '${aws:InstanceId}'})
        self.assertNotIn('/aws/rds/', json.dumps(agent))

    def test_repository_links_and_no_broad_scripts(self):
        for name in DOCS:
            path = ROOT / 'docs' / name
            for target in re.findall(r'\]\(([^)]+)\)', path.read_text()):
                if re.match(r'[a-z]+://|#', target):
                    continue
                relative, _, anchor = target.partition('#')
                destination = (path.parent / relative).resolve()
                self.assertTrue(destination.exists(), f'{name}: {target}')
                if anchor and destination.suffix == '.md':
                    headings = re.findall(r'^#{1,6} (.+)$', destination.read_text(), re.M)
                    slugs = {re.sub(r'[^\w\- ]', '', heading.lower()).replace(' ', '-')
                             for heading in headings}
                    self.assertIn(anchor, slugs, f'{name}: {target}')
        # Check filenames without executing Git or any operational program.
        ignored = {'.git', 'node_modules', 'target', '.angular', '.terraform'}
        def files(directory):
            for path in directory.iterdir():
                if path.is_symlink() or path.name in ignored:
                    continue
                if path.is_dir():
                    yield from files(path)
                else:
                    yield path
        for path in files(ROOT):
            self.assertNotRegex(path.name.lower(), r'^(deploy|rollback|restore[-_]rds)\.(sh|py|bash|ps1)$')
        # Narrow pre-existing helpers remain the only operational scripts here.
        self.assertEqual({p.name for p in (ROOT / 'scripts/production').glob('*.sh')},
                         {'materialize-secrets.sh', 'run-migrations.sh'})


if __name__ == '__main__':
    result = unittest.main(exit=False)
    if result.result.wasSuccessful():
        print('PASS: static runbook, repository implementation and link contracts')
    raise SystemExit(not result.result.wasSuccessful())
