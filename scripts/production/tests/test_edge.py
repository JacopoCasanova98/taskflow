"""Offline Nginx fixture. Run via test_edge.sh; no AWS/ALB emulation claims."""
import http.server
import json
import sys
import time
import urllib.request
import urllib.error

class Backend(http.server.BaseHTTPRequestHandler):
    healthy = True
    def do_GET(self):
        if self.path == '/api/fixture/down':
            Backend.healthy = False
        if self.path == '/api/fixture/up':
            Backend.healthy = True
        health = self.path == '/actuator/health'
        self.send_response(200 if not health or Backend.healthy else 503)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Cache-Control', 'no-store')
        self.send_header('X-Frame-Options', 'SAMEORIGIN')
        self.send_header('Strict-Transport-Security', 'max-age=1')
        self.send_header('Set-Cookie', 'fixture=NOT-A-SECRET; Secure; HttpOnly; SameSite=Strict')
        self.end_headers()
        self.wfile.write(json.dumps({'status': 'UP' if Backend.healthy else 'DOWN'} if health
                                   else dict(self.headers)).encode())
    do_POST = do_GET
    def log_message(self, *args):
        pass

if sys.argv[1] == 'backend':
    http.server.ThreadingHTTPServer(('0.0.0.0', 8080), Backend).serve_forever()
    sys.exit()

trusted = sys.argv[1] == 'trusted'
base = 'http://frontend:8080'
headers = {'Host': 'taskflow.example.com', 'X-Forwarded-Proto': 'https',
           'X-Forwarded-Port': '443', 'X-Forwarded-For': '198.51.100.99, 198.51.100.8',
           'X-Forwarded-Host': 'spoof.invalid', 'Forwarded': 'proto=https;host=spoof.invalid',
           'X-XSRF-TOKEN': 'NOT-A-SECRET-fixture', 'Cookie': 'XSRF-TOKEN=NOT-A-SECRET-fixture'}
def request(path, override=None, data=None):
    req = urllib.request.Request(base + path, headers=headers | (override or {}), data=data)
    try:
        response = urllib.request.urlopen(req, timeout=5)
    except urllib.error.HTTPError as error:
        response = error
    return response.status, response.headers, response.read()

for _ in range(30):
    try:
        assert request('/')[0] == 200
        break
    except (OSError, AssertionError):
        time.sleep(.2)
else:
    raise AssertionError('Nginx not ready')
status, response_headers, body = request('/api/echo')
assert status == 200
upstream = {k.lower(): v for k, v in json.loads(body).items()}
assert upstream['x-forwarded-proto'] == ('https' if trusted else 'http'), upstream
assert upstream['x-forwarded-port'] == ('443' if trusted else '8080')
assert upstream['x-forwarded-for'] == ('198.51.100.8' if trusted else '10.42.2.10')
assert upstream['x-forwarded-host'] == 'taskflow.example.com'
assert upstream['host'] == 'taskflow.example.com'
assert 'forwarded' not in upstream
assert upstream['x-xsrf-token'] == 'NOT-A-SECRET-fixture'
assert upstream['cookie'] == headers['Cookie']
assert response_headers.get_all('X-Frame-Options') == ['DENY']
assert response_headers.get_all('Cache-Control') == ['no-store']
assert response_headers.get('Set-Cookie').endswith('Secure; HttpOnly; SameSite=Strict')
assert response_headers.get_all('Strict-Transport-Security') == (['max-age=31536000'] if trusted else None)
for invalid in ['https, http', 'bogus']:
    assert json.loads(request('/api/echo', {'X-Forwarded-Proto': invalid})[2])['X-Forwarded-Proto'] == 'http'
for invalid in ['80', '443, 80', 'bogus']:
    _, normalized_headers, normalized_body = request('/api/echo', {'X-Forwarded-Port': invalid})
    normalized = json.loads(normalized_body)
    assert normalized['X-Forwarded-Proto'] == 'http'
    assert normalized['X-Forwarded-Port'] == '8080'
    assert normalized_headers.get('Strict-Transport-Security') is None
for path in ['/actuator', '/actuator/health', '/swagger-ui.html', '/swagger-ui/index.html',
             '/v3/api-docs', '/v3/api-docs.yaml', '/internal/other']:
    assert request(path)[0] == 404, path
assert request('/boards/fixture')[0] == 200  # Angular deep link
assert request('/missing.js')[0] == 404
assert request('/api/echo', data=b'x' * (1024 * 1024 + 1))[0] == 413
if trusted:
    assert json.loads(request('/internal/health')[2]) == {'status': 'UP'}
    request('/api/fixture/down')
    assert request('/internal/health')[0] == 503
    request('/api/fixture/up')
    assert request('/internal/health')[0] == 200
else:
    assert request('/internal/health')[0] == 404
codes = [request('/api/auth/login', data=b'{}')[0] for _ in range(30)]
assert (429 in codes) == trusted, codes
codes = [request('/api/auth/register', data=b'{}')[0] for _ in range(30)]
assert (429 in codes) == trusted, codes
# Rate limit does not affect refresh, logout, CRUD or static assets.
for path in ['/api/auth/refresh', '/api/auth/logout', '/api/tasks', '/']:
    assert request(path)[0] == 200
print(sys.argv[1] + ': proxy, spoofing, security, health and rate-limit checks passed')
