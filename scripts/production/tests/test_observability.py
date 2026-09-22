"""Offline telemetry contract; use cached cfn-lint tooling (PyYAML), never AWS."""
import json
from pathlib import Path
import re
import sys
import yaml

root = Path(__file__).resolve().parents[3]
agent = json.loads((root / 'ops/cloudwatch/amazon-cloudwatch-agent.json').read_text())
assert set(agent) == {'agent', 'metrics', 'logs'}
assert agent['agent'] == {'metrics_collection_interval': 60, 'region': 'eu-west-1',
                          'omit_hostname': True, 'usage_data': False, 'logfile': ''}
metrics = agent['metrics']
assert metrics['namespace'] == 'TaskFlow/prod'
assert metrics['append_dimensions'] == {'InstanceId': '${aws:InstanceId}'}
assert metrics['aggregation_dimensions'] == [['InstanceId']]
assert set(metrics['metrics_collected']) == {'mem', 'disk'}
for plugin in ['mem', 'disk']:
    selected = metrics['metrics_collected'][plugin]
    assert selected['measurement'] == [plugin + '_used_percent']
    assert selected['drop_original_metrics'] == selected['measurement']
    assert set(selected) == ({'measurement', 'drop_original_metrics'} if plugin == 'mem'
                             else {'measurement', 'drop_original_metrics', 'resources', 'drop_device'})
assert metrics['metrics_collected']['disk']['resources'] == ['/']
assert metrics['metrics_collected']['disk']['drop_device'] is True
logs = agent['logs']['logs_collected']
assert set(logs) == {'files', 'journald'}
journal = logs['journald']['collect_list']
assert [x['units'] for x in journal] == [[x + '.service'] for x in
    ['docker', 'amazon-ssm-agent', 'amazon-cloudwatch-agent']]
assert all(x['priority'] == 'info' for x in journal)
files = logs['files']['collect_list']
assert len(files) == 1 and files[0]['file_path'] == '/var/log/cloud-init-output.log'
sources = journal + files
assert {x['log_group_name'] for x in sources} == {'/taskflow/prod/host'}
assert {x['log_stream_name'] for x in sources} == {
    '{instance_id}/' + suffix for suffix in ['docker', 'ssm', 'cloudwatch-agent', 'cloud-init']}
assert 'retention_in_days' not in json.dumps(agent)
assert 'credentials' not in json.dumps(agent)

compose = yaml.safe_load((root / 'compose.production.yaml').read_text())
def check_compose(model):
    services = model['services']
    assert set(services) == {'frontend', 'backend'}
    for service, group in [('frontend', 'nginx'), ('backend', 'backend')]:
        spec = services[service]
        assert spec['logging'] == {'driver': 'awslogs', 'options': {
            'awslogs-region': 'eu-west-1', 'awslogs-group': '/taskflow/prod/' + group,
            'awslogs-create-group': 'false', 'mode': 'non-blocking', 'max-buffer-size': '4m'}}
        assert spec['read_only'] and spec['restart'] == 'unless-stopped'
        assert spec['tmpfs'] == ['/tmp']
        assert spec['security_opt'] == ['no-new-privileges:true']
        assert not any(key.startswith('AWS_') for key in spec.get('environment', {}))
    backend = services['backend']
    assert 'ports' not in backend and 'secrets' not in services['frontend']
    assert [s['source'] for s in backend['secrets']] == ['database_username', 'database_password', 'jwt_signing']
    assert backend['environment']['TASKFLOW_COOKIE_SECURE'] == 'true'
    assert backend['environment']['TASKFLOW_DATABASE_PRODUCTION'] == 'true'
    assert backend['environment']['SPRING_FLYWAY_ENABLED'] == 'false'
    assert backend['environment']['SERVER_FORWARD_HEADERS_STRATEGY'] == 'native'
    assert backend['environment']['SERVER_TOMCAT_REMOTEIP_HOST_HEADER'] == 'X-Forwarded-Host'
    assert backend['environment']['SERVER_TOMCAT_REMOTEIP_PORT_HEADER'] == 'X-Forwarded-Port'
    assert backend['volumes'][0]['read_only'] and not backend['volumes'][0]['bind']['create_host_path']
    assert services['frontend']['depends_on']['backend']['condition'] == 'service_healthy'
check_compose(compose)
if len(sys.argv) > 1:
    check_compose(json.loads(Path(sys.argv[1]).read_text()))

cf = yaml.load((root / 'infra/cloudformation/taskflow.yaml').read_text(), Loader=yaml.BaseLoader)
resources = cf['Resources']
tf = (root / 'infra/terraform/observability.tf').read_text()
variables = (root / 'infra/terraform/variables.tf').read_text()
alarms = {k: v['Properties'] for k, v in resources.items() if v['Type'] == 'AWS::CloudWatch::Alarm'}
tf_alarms = dict(re.findall(r'resource "aws_cloudwatch_metric_alarm" "(\w+)" \{\n(.*?)\n\}', tf, re.S))
assert len(alarms) == len(tf_alarms) == 8
expected = [
    ('Ec2StatusAlarm', 'ec2_status', 'AWS/EC2', 'StatusCheckFailed', 'Maximum', 'GreaterThanThreshold', '0', '60', '2', 'missing'),
    ('Ec2CpuAlarm', 'ec2_cpu', 'AWS/EC2', 'CPUUtilization', 'Average', 'GreaterThanThreshold', '80', '300', '3', 'missing'),
    ('AlbHealthyAlarm', 'alb_healthy', 'AWS/ApplicationELB', 'HealthyHostCount', 'Minimum', 'LessThanThreshold', '1', '60', '2', 'breaching'),
    ('Alb5xxAlarm', 'alb_5xx', 'AWS/ApplicationELB', 'HTTPCode_ELB_5XX_Count', 'Sum', 'GreaterThanOrEqualToThreshold', '5', '300', '2', 'notBreaching'),
    ('RdsCpuAlarm', 'rds_cpu', 'AWS/RDS', 'CPUUtilization', 'Average', 'GreaterThanThreshold', '80', '300', '3', 'missing'),
    ('RdsStorageAlarm', 'rds_storage', 'AWS/RDS', 'FreeStorageSpace', 'Minimum', 'LessThanThreshold', '5368709120', '300', '3', 'missing'),
    ('HostMemoryAlarm', 'host_memory', 'TaskFlow/${Environment}', 'mem_used_percent', 'Average', 'GreaterThanOrEqualToThreshold', '85', '300', '3', 'missing'),
    ('HostRootDiskAlarm', 'host_root_disk', 'TaskFlow/${Environment}', 'disk_used_percent', 'Average', 'GreaterThanOrEqualToThreshold', '85', '300', '3', 'missing'),
]
fields = [('Namespace', 'namespace'), ('MetricName', 'metric_name'), ('Statistic', 'statistic'),
          ('ComparisonOperator', 'comparison_operator'), ('Threshold', 'threshold'), ('Period', 'period'),
          ('EvaluationPeriods', 'evaluation_periods'), ('TreatMissingData', 'treat_missing_data')]
for cf_name, tf_name, *values in expected:
    alarm, block = alarms[cf_name], tf_alarms[tf_name]
    for (cf_field, tf_field), value in zip(fields, values):
        assert alarm[cf_field] == value, (cf_name, cf_field)
        match = re.search(r'^\s*' + tf_field + r'\s*=\s*(.+)$', block, re.M)
        actual = match[1].strip('"').replace('${var.environment}', '${Environment}')
        if tf_field == 'threshold' and tf_name == 'rds_storage':
            assert actual == '5 * 1024 * 1024 * 1024'
        else:
            assert actual == value, (tf_name, tf_field, actual)
    for action in ['AlarmActions', 'OKActions']:
        assert alarm[action] == ['HasAlarmTopic', ['AlarmTopicArn'], []]
    assert alarm['InsufficientDataActions'] == []
    for action in ['alarm_actions', 'ok_actions']:
        assert re.search(r'^\s*' + action + r'\s*= local.alarm_notification_actions$', block, re.M)
    assert re.search(r'insufficient_data_actions\s*= \[\]', block)
    if tf_name.startswith('host_'):
        assert alarm['Dimensions'] == [{'Name': 'InstanceId', 'Value': 'AppInstance'}]
        assert 'dimensions = { InstanceId = aws_instance.app.id }' in block
assert 'var.alarm_topic_arn == "" ? [] : [var.alarm_topic_arn]' in tf
assert cf['Conditions']['HasAlarmTopic'] == [['AlarmTopicArn', '']]
param = cf['Parameters']['AlarmTopicArn']
assert param['Default'] == '' and param['AllowedPattern'] in variables
assert re.search(r'variable "alarm_topic_arn".*?default\s*= ""', variables, re.S)
pattern = param['AllowedPattern']
for value in ['', 'arn:aws:sns:eu-west-1:000000000000:fixture-topic']:
    assert re.fullmatch(pattern, value)
for value in ['*', 'https://example.invalid/topic', 'arn:aws:sqs:eu-west-1:000000000000:queue',
              'arn:aws:sns:eu-west-1:000000000000:topic.fifo', 'arn:aws:sns:eu-west-1:123:topic']:
    assert not re.fullmatch(pattern, value)
assert not any(r['Type'].startswith(('AWS::SNS::', 'AWS::CloudWatch::Dashboard')) for r in resources.values())
groups = [r['Properties'] for r in resources.values() if r['Type'] == 'AWS::Logs::LogGroup']
assert len(groups) == 4 and all(x['RetentionInDays'] == '14' for x in groups)
assert len(re.findall(r'retention_in_days\s*= 14', tf)) == 2
iam = (root / 'infra/terraform/iam.tf').read_text()
assert 'Action   = ["logs:CreateLogStream", "logs:PutLogEvents"]' in iam
assert '[for group in aws_cloudwatch_log_group.app : "${group.arn}:*"]' in iam
assert '"cloudwatch:namespace" = "TaskFlow/${var.environment}"' in iam
assert not any(action in iam for action in ['logs:CreateLogGroup', 'logs:*', 'CloudWatchAgentServerPolicy'])
policies = resources['AppRole']['Properties']['Policies']
telemetry = next(p['PolicyDocument']['Statement'] for p in policies
                 if any(s.get('Action') == 'cloudwatch:PutMetricData' for s in p['PolicyDocument']['Statement']))
assert telemetry[0]['Action'] == ['logs:CreateLogStream', 'logs:PutLogEvents']
assert telemetry[0]['Resource'] == ['BackendLogGroup.Arn', 'NginxLogGroup.Arn', 'HostLogGroup.Arn']
assert telemetry[1]['Condition']['StringEquals']['cloudwatch:namespace'] == 'TaskFlow/${Environment}'
assert resources['PostgresDatabase']['Properties']['EnableCloudwatchLogsExports'] == ['postgresql']
print('PASS: host-only config, metric dimensions, Compose/security, eight alarms, optional SNS, IAM and retention parity')
