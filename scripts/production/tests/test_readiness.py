"""Static final audit. Requires cached PyYAML; no AWS, subprocess or runtime startup.

Optionally set TASKFLOW_READINESS_COMPOSE_JSON to an already-rendered fixture model.
Run adjacent edge, observability and runbook contracts for their detailed assertions.
"""
import json
import os
from pathlib import Path
import re
import unittest
import yaml

ROOT = Path(__file__).resolve().parents[3]


def read(path):
    return (ROOT / path).read_text()


def tf_blocks(text, kind):
    return dict(re.findall(r'resource "' + kind + r'" "(\w+)" \{\n(.*?)\n\}', text, re.S))


class ReadinessTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.cf = yaml.load(read('infra/cloudformation/taskflow.yaml'), Loader=yaml.BaseLoader)
        cls.resources = cls.cf['Resources']
        cls.tf = '\n'.join(p.read_text() for p in (ROOT / 'infra/terraform').glob('*.tf'))

    def props(self, name):
        return self.resources[name]['Properties']

    def test_runtime(self):
        models = [yaml.safe_load(read('compose.production.yaml'))]
        if os.environ.get('TASKFLOW_READINESS_COMPOSE_JSON'):
            models.append(json.loads(Path(os.environ['TASKFLOW_READINESS_COMPOSE_JSON']).read_text()))
        for model in models:
            services = model['services']
            self.assertEqual(set(services), {'frontend', 'backend'})
            for name, service in services.items():
                self.assertNotIn('build', service)
                self.assertTrue(service['image'].startswith('${TASKFLOW_') or '@sha256:' in service['image'])
                self.assertTrue(service['read_only'])
                self.assertIn('/tmp', service['tmpfs'])
                self.assertIn('no-new-privileges:true', service['security_opt'])
                self.assertEqual(service['restart'], 'unless-stopped')
                self.assertIn('test', service['healthcheck'])
                self.assertEqual(service['logging']['driver'], 'awslogs')
                self.assertEqual(service['logging']['options']['awslogs-create-group'], 'false')
                self.assertNotIn('privileged', service)
                self.assertRegex(read(name + '/Dockerfile'), r'(?m)^USER (nginx|10001:10001)$')
            backend = services['backend']
            self.assertNotIn('ports', backend)
            self.assertNotIn('secrets', services['frontend'])
            ports = services['frontend']['ports']
            self.assertEqual(len(ports), 1)
            if isinstance(ports[0], str):
                self.assertEqual(ports[0], '0.0.0.0:8080:8080')
            else:
                self.assertEqual((ports[0]['host_ip'], str(ports[0]['published']), ports[0]['target']),
                                 ('0.0.0.0', '8080', 8080))
            env = backend['environment']
            for key, value in {'TASKFLOW_COOKIE_SECURE': 'true', 'SPRING_FLYWAY_ENABLED': 'false',
                               'TASKFLOW_DATABASE_PRODUCTION': 'true',
                               'SERVER_FORWARD_HEADERS_STRATEGY': 'native'}.items():
                self.assertEqual(env[key], value)
            self.assertFalse(any(k.startswith('AWS_') or 'PASSWORD' in k or 'JWT' in k for k in env))
            if not env['TASKFLOW_DB_URL'].startswith('${'):
                self.assertIn('sslmode=verify-full', env['TASKFLOW_DB_URL'])
                self.assertIn('sslrootcert=/opt/taskflow/trust/rds-ca-bundle.pem', env['TASKFLOW_DB_URL'])
                self.assertNotIn('@', env['TASKFLOW_DB_URL'])
            self.assertEqual({s['source'] for s in backend['secrets']},
                             {'database_username', 'database_password', 'jwt_signing'})
            self.assertTrue(all('file' in s for s in model['secrets'].values()))
            ca = backend['volumes'][0]
            self.assertTrue(ca['read_only'])
            self.assertFalse(ca['bind']['create_host_path'])
            self.assertEqual(ca['target'], '/opt/taskflow/trust/rds-ca-bundle.pem')
        app = yaml.safe_load(read('backend/src/main/resources/application.yml'))
        self.assertEqual(app['spring']['jpa']['hibernate']['ddl-auto'], 'validate')
        self.assertEqual(app['spring']['config']['import'], 'optional:configtree:/run/secrets/')

    def test_network_and_imds_parity(self):
        # Compare complete permitted rule sets; CF's documented localhost sentinel
        # suppresses default egress and is not a routable all-traffic exception.
        expected = {
            'ingress': {('alb', '80', '0.0.0.0/0'), ('alb', '443', '0.0.0.0/0'),
                        ('app', '8080', 'alb'), ('db', '5432', 'app')},
            'egress': {('alb', '8080', 'app'), ('app', '5432', 'db'), ('app', '443', '0.0.0.0/0')}}
        names = {'AlbSecurityGroup.GroupId': 'alb', 'AppSecurityGroup.GroupId': 'app',
                 'DatabaseSecurityGroup.GroupId': 'db'}
        for block in tf_blocks(self.tf, 'aws_security_group').values():
            self.assertNotRegex(block, r'\b(ingress|egress)\s*\{')
        self.assertNotIn('resource "aws_security_group_rule"', self.tf)
        for direction in expected:
            actual = set()
            for block in tf_blocks(self.tf, 'aws_vpc_security_group_' + direction + '_rule').values():
                self.assertRegex(block, r'ip_protocol\s*=\s*"tcp"')
                port = re.search(r'from_port\s*=\s*(\d+)', block)[1]
                self.assertRegex(block, r'to_port\s*=\s*' + port + r'\b')
                group = re.search(r'(?m)^\s*security_group_id\s*=\s*aws_security_group\.(\w+)\.id', block)[1]
                peer = re.search(r'referenced_security_group_id\s*=\s*aws_security_group\.(\w+)\.id', block)
                peer = peer[1] if peer else re.search(r'cidr_ipv4\s*=\s*"([^"]+)"', block)[1]
                actual.add((group, port, peer))
            self.assertEqual(actual, expected[direction])
            cf_rules = [r['Properties'] for r in self.resources.values()
                        if r['Type'] == 'AWS::EC2::SecurityGroup' + direction.title()]
            actual = set()
            for rule in cf_rules:
                self.assertEqual(rule['IpProtocol'], 'tcp')
                self.assertEqual(rule['FromPort'], rule['ToPort'])
                peer = rule.get('CidrIp') or names[rule.get('SourceSecurityGroupId') or rule['DestinationSecurityGroupId']]
                actual.add((names[rule['GroupId']], rule['FromPort'], peer))
            self.assertEqual(actual, expected[direction])
        for group in names:
            props = self.props(group.split('.')[0])
            self.assertNotIn('SecurityGroupIngress', props)
            sentinel = props['SecurityGroupEgress']
            self.assertEqual(len(sentinel), 1)
            self.assertEqual((sentinel[0]['IpProtocol'], sentinel[0]['CidrIp']), ('-1', '127.0.0.1/32'))
        self.assertNotRegex(self.tf, r'resource "aws_(nat_gateway|egress_only_internet_gateway)"')
        self.assertFalse(any(r['Type'] == 'AWS::EC2::NatGateway' for r in self.resources.values()))
        self.assertNotIn('cidr_ipv6', self.tf)
        self.assertNotIn('CidrIpv6', read('infra/cloudformation/taskflow.yaml'))
        self.assertEqual(len(tf_blocks(self.tf, 'aws_route')), 1)
        self.assertIn('route_table_id         = aws_route_table.public.id', self.tf)
        routes = [r['Properties'] for r in self.resources.values() if r['Type'] == 'AWS::EC2::Route']
        self.assertEqual(len(routes), 1)
        self.assertEqual(routes[0]['RouteTableId'], 'PublicRouteTable')
        self.assertEqual(self.props('AppInstance')['MetadataOptions'], {
            'HttpEndpoint': 'enabled', 'HttpTokens': 'required',
            'HttpPutResponseHopLimit': '2', 'InstanceMetadataTags': 'disabled'})
        for key, value in [('http_endpoint', '"enabled"'), ('http_tokens', '"required"'),
                           ('http_put_response_hop_limit', '2')]:
            self.assertRegex(self.tf, key + r'\s*=\s*' + value)
        bootstrap = read('infra/terraform/templates/user-data.sh.tftpl')
        self.assertIn('169.254.169.254/32', bootstrap)
        self.assertIn('DOCKER-USER', bootstrap)
        self.assertNotIn('get-secret-value', bootstrap)

    def test_database_release_and_secrets(self):
        db = self.props('PostgresDatabase')
        for key, value in {'Engine': 'postgres', 'PubliclyAccessible': 'false', 'MultiAZ': 'false',
                           'AllocatedStorage': '20', 'StorageType': 'gp3', 'StorageEncrypted': 'true',
                           'BackupRetentionPeriod': '7', 'DeleteAutomatedBackups': 'false',
                           'DeletionProtection': 'true', 'ManageMasterUserPassword': 'true'}.items():
            self.assertEqual(db[key], value)
        self.assertEqual(self.cf['Parameters']['DbEngineVersion']['Default'], '17')
        for key in ('DeletionPolicy', 'UpdateReplacePolicy'):
            self.assertEqual(self.resources['PostgresDatabase'][key], 'Snapshot')
        for key, value in [('backup_retention_period', '7'), ('multi_az', 'false'),
                           ('publicly_accessible', 'false'), ('storage_encrypted', 'true'),
                           ('allocated_storage', '20'), ('deletion_protection', 'true'),
                           ('delete_automated_backups', 'false'), ('skip_final_snapshot', 'false')]:
            self.assertRegex(self.tf, key + r'\s*=\s*' + value + r'\b')
        self.assertRegex(self.tf, r'(?s)variable "db_engine_version".*?default\s*=\s*"17"')
        contract = read('backend/src/main/java/com/taskflow/shared/config/ProductionDatabaseContract.java')
        self.assertIn('Map.of("sslmode", "verify-full", "sslrootcert", CA_PATH)', contract)
        self.assertIn('taskflow_app', read('backend/src/main/java/com/taskflow/shared/config/DatabaseCredentialsConfiguration.java'))
        self.assertIn('taskflow_migrator', read('backend/src/main/java/com/taskflow/operations/DatabaseMigration.java'))
        repos = [r['Properties'] for r in self.resources.values() if r['Type'] == 'AWS::ECR::Repository']
        self.assertEqual(len(repos), 2)
        for repo in repos:
            self.assertEqual(repo['ImageTagMutability'], 'IMMUTABLE')
            selection = json.loads(repo['LifecyclePolicy']['LifecyclePolicyText'])['rules'][0]['selection']
            self.assertEqual(selection['tagStatus'], 'untagged')
            self.assertEqual(selection['countNumber'], 7)
        self.assertEqual(self.props('RegistryScanningConfiguration')['ScanType'], 'BASIC')
        self.assertEqual(self.props('RegistryScanningConfiguration')['Rules'][0]['ScanFrequency'], 'SCAN_ON_PUSH')
        self.assertEqual(self.cf['Parameters']['ManageEcrRegistryScanning']['Default'], 'false')
        for pattern in (r'image_tag_mutability\s*=\s*"IMMUTABLE"', r'scan_type\s*=\s*"BASIC"',
                        r'scan_frequency\s*=\s*"SCAN_ON_PUSH"', r'tagStatus\s*=\s*"untagged"',
                        r'countNumber\s*=\s*7'):
            self.assertRegex(read('infra/terraform/ecr.tf'), pattern)
        release = read('docs/CONTAINER_RELEASE.md')
        for term in ('same', 'git-<full-git-sha>', 'semantic version', '@sha256:', 'linux/amd64', 'scan-on-push'):
            self.assertIn(term, release)
        materializer = read('scripts/production/materialize-secrets.sh')
        for term in ('taskflow_app', 'AWSCURRENT', 'len(decoded) != 32', 'RENAME_EXCHANGE', 'backend recreation'):
            self.assertIn(term, materializer)
        policies = self.props('AppRole')['Policies']
        secret_statements = [s for p in policies for s in p['PolicyDocument']['Statement']
                             if 'secretsmanager:GetSecretValue' in s.get('Action', [])]
        self.assertEqual(len(secret_statements), 1)
        self.assertEqual(secret_statements[0]['Resource'], ['ApplicationDatabaseSecret', 'JwtSigningSecret'])

    def test_edge_and_observability_inventory(self):
        self.assertNotIn('Default', self.cf['Parameters']['PublicHostname'])
        self.assertEqual(self.props('HttpsListener')['DefaultActions'][0]['FixedResponseConfig']['StatusCode'], '404')
        self.assertEqual(self.props('PublicHostRule')['Conditions'][0]['HostHeaderConfig']['Values'], ['PublicHostname'])
        attrs = self.props('AppLoadBalancer')['LoadBalancerAttributes']
        self.assertIn({'Key': 'routing.http.xff_header_processing.mode', 'Value': 'append'}, attrs)
        for term in ('/internal/*', '/actuator', '/swagger-ui*', '/v3/api-docs*'):
            self.assertIn(term, read('infra/terraform/load_balancer.tf'))
        security = read('backend/src/main/java/com/taskflow/shared/security/SecurityConfiguration.java')
        self.assertNotRegex(security, r'(?i)(allowedOrigins|allowedOriginPatterns|CrossOrigin)')
        groups = [r['Properties'] for r in self.resources.values() if r['Type'] == 'AWS::Logs::LogGroup']
        self.assertEqual(len(groups), 4)
        self.assertTrue(all(g['RetentionInDays'] == '14' for g in groups))
        self.assertEqual(sum(r['Type'] == 'AWS::CloudWatch::Alarm' for r in self.resources.values()), 8)
        self.assertEqual(len(tf_blocks(self.tf, 'aws_cloudwatch_metric_alarm')), 8)
        self.assertEqual(self.cf['Parameters']['AlarmTopicArn']['Default'], '')
        forbidden = ('AWS::SNS::', 'AWS::CloudWatch::Dashboard', 'AWS::XRay::', 'AWS::WAF', 'AWS::Backup::')
        self.assertFalse(any(r['Type'].startswith(forbidden) for r in self.resources.values()))
        self.assertNotRegex(self.tf, r'resource "aws_(sns_|cloudwatch_dashboard|xray_|wafv2_|backup_)')
        agent = json.loads(read('ops/cloudwatch/amazon-cloudwatch-agent.json'))
        self.assertEqual(agent['metrics']['namespace'], 'TaskFlow/prod')

    def test_documents_and_execution_artifact_hygiene(self):
        for name in ('PRODUCTION_READINESS.md', 'PRODUCTION_SMOKE_TEST.md', 'DEPLOYMENT_RUNBOOK.md', 'DISASTER_RECOVERY.md'):
            document = ROOT / 'docs' / name
            self.assertTrue(document.is_file())
            for target in re.findall(r'\]\(([^)]+)\)', document.read_text()):
                if not re.match(r'[a-z]+://|#', target):
                    relative, _, anchor = target.partition('#')
                    destination = document.parent / relative
                    self.assertTrue(destination.exists(), target)
                    if anchor and destination.suffix == '.md':
                        headings = re.findall(r'^#{1,6} (.+)$', destination.read_text(), re.M)
                        slugs = {re.sub(r'[^\w\- ]', '', h.lower()).replace(' ', '-') for h in headings}
                        self.assertIn(anchor, slugs, target)
        readiness = read('docs/PRODUCTION_READINESS.md')
        for category in ('PROVEN LOCALLY / STATICALLY', 'REQUIRES REAL DEPLOYMENT', 'KNOWN ACCEPTED LIMITATION', 'BLOCKER'):
            self.assertIn(category, readiness)
        self.assertNotRegex(readiness, r'(?m)^- \[x\]')  # Live checklist stays unexecuted.
        smoke = read('docs/PRODUCTION_SMOKE_TEST.md').lower()
        for term in ('register', 'login', 'board', 'column', 'task', 'logout', 'csrf', 'cleanup', 'stop', 'utc', 'digest'):
            self.assertIn(term, smoke)
        deployment = re.sub(r'\s+', ' ', read('docs/DEPLOYMENT_RUNBOOK.md').lower())
        self.assertRegex(deployment, r'only after migration success.*recreate both services')
        self.assertRegex(deployment, r'current database.*schema.*recreate backend/frontend')
        self.assertIn('no automatic flyway down migrations', deployment)
        recovery = re.sub(r'\s+', ' ', read('docs/DISASTER_RECOVERY.md').lower().replace('**', ''))
        for term in ('new db instance', 'regional disaster recovery is not provided',
                     'rto is not guaranteed or measured', 'quiesce/stop backend'):
            self.assertIn(term, recovery)
        # Filesystem-only inventory includes ignored execution artifacts, but skips
        # dependency/build caches. This is not a replacement for Gitleaks.
        ignored = {'.git', '.terraform', 'node_modules', 'target', 'dist', '.angular', '.venv'}
        for directory, dirs, files in os.walk(ROOT):
            dirs[:] = [d for d in dirs if d not in ignored]
            for name in files:
                path = Path(directory) / name
                relative = path.relative_to(ROOT)
                self.assertFalse('.tfstate' in name or name.endswith('.tfplan'), str(relative))
                self.assertNotIn(name, {'.env.production', 'credentials', 'terraform.tfvars', 'terraform.tfvars.json',
                                        'deployment-record.json', 'release-manifest.json'})
                self.assertFalse(name.endswith(('.auto.tfvars', '.auto.tfvars.json')), str(relative))
                self.assertFalse('.aws' in relative.parts or ('.docker' in relative.parts and name == 'config.json'), str(relative))
                self.assertNotRegex(name, r'^(deploy|rollback|restore[-_]rds)\.(sh|py|bash|ps1)$')


if __name__ == '__main__':
    result = unittest.main(exit=False)
    if result.result.wasSuccessful():
        print('PASS: static production readiness (not live production validation)')
    raise SystemExit(not result.result.wasSuccessful())
