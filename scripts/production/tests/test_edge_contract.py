"""Offline edge IaC parity/hostname assertions; PyYAML from cached cfn-lint tooling."""
from pathlib import Path
import re
import yaml

root = Path(__file__).resolve().parents[3]
cf = yaml.load((root / 'infra/cloudformation/taskflow.yaml').read_text(), Loader=yaml.BaseLoader)
resources = cf['Resources']
def props(name):
    return resources[name]['Properties']
tf = (root / 'infra/terraform/load_balancer.tf').read_text()
variables = (root / 'infra/terraform/variables.tf').read_text()
param = cf['Parameters']['PublicHostname']
assert 'Default' not in param
pattern = param['AllowedPattern']
assert pattern in variables
assert 'length(var.public_hostname) <= 128' in variables
for host in ['taskflow.example.com', 'a-b.example.org']:
    assert re.fullmatch(pattern, host)
for host in ['https://taskflow.example.com', 'example.com/path', 'example.com:443',
             '*.example.com', '-bad.example.com', 'bad-.example.com', 'example..com',
             'example.com.', 'localhost', 'EXAMPLE.com', '192.0.2.1', 'a' * 64 + '.com']:
    assert not re.fullmatch(pattern, host), host
assert int(param['MaxLength']) == 128
assert props('HttpsListener')['DefaultActions'][0]['Type'] == 'fixed-response'
assert props('HttpsListener')['DefaultActions'][0]['FixedResponseConfig']['StatusCode'] == '404'
assert 'type             = "forward"' not in tf.split('resource "aws_lb_listener_rule"')[0]
for name, priority, paths in [('InternalHealthBlock', '1', ['/internal/*', '/actuator', '/actuator/*']),
                              ('DocumentationBlock', '2', ['/swagger-ui*', '/v3/api-docs*'])]:
    rule = props(name)
    assert rule['Priority'] == priority
    assert rule['Conditions'][0]['PathPatternConfig']['Values'] == paths
    assert len(paths) <= 3
    assert rule['Actions'][0]['FixedResponseConfig']['StatusCode'] == '404'
    assert re.search(r'priority\s*=\s*' + priority + r'\b', tf)
    assert all('"' + path + '"' in tf for path in paths)
assert props('PublicHostRule')['Priority'] == '10'
assert props('PublicHostRule')['Conditions'][0]['HostHeaderConfig']['Values'] == ['PublicHostname']
assert props('PublicHostRule')['Actions'][0]['Type'] == 'forward'
assert 'values = [var.public_hostname]' in tf
assert props('HttpListener')['DefaultActions'][0]['RedirectConfig']['StatusCode'] == 'HTTP_301'
assert props('AppTargetGroup')['HealthCheckPath'] == '/internal/health'
assert 'path                = "/internal/health"' in tf
assert props('HttpsListener')['Certificates'] == [{'CertificateArn': 'AcmCertificateArn'}]
assert 'certificate_arn   = var.acm_certificate_arn' in tf
assert 'Default' not in cf['Parameters']['AcmCertificateArn']
attrs = {a['Key']: a['Value'] for a in props('AppLoadBalancer')['LoadBalancerAttributes']}
assert attrs['routing.http.xff_header_processing.mode'] == 'append'
assert re.search(r'xff_header_processing_mode\s*=\s*"append"', tf)
assert props('HttpsListener')['SslPolicy'] == 'ELBSecurityPolicy-TLS13-1-2-Res-PQ-2025-09'
assert props('HttpsListener')['SslPolicy'] in tf
assert not any(r['Type'].startswith(('AWS::Route53::', 'AWS::CertificateManager::')) for r in resources.values())
nginx = (root / 'frontend/nginx/nginx.conf').read_text()
for name in ['PublicSubnetACidr', 'PublicSubnetBCidr']:
    assert cf['Parameters'][name]['Default'] in nginx
compose = yaml.safe_load((root / 'compose.production.yaml').read_text())
assert set(compose['services']) == {'frontend', 'backend'}
backend = compose['services']['backend']
assert 'ports' not in backend
assert backend['environment']['SERVER_FORWARD_HEADERS_STRATEGY'] == 'native'
assert backend['environment']['TASKFLOW_COOKIE_SECURE'] == 'true'
assert len(backend['secrets']) == 3
assert 'secrets' not in compose['services']['frontend']
assert all(s['read_only'] for s in compose['services'].values())
print('Hostname syntax, listener order/defaults, XFF, TLS, subnet and Compose parity passed')
