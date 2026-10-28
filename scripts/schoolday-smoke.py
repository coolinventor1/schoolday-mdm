"""Verify local pilot login without printing credentials, cookies, or reset tokens."""
import hashlib
import http.cookiejar
import json
import os
from pathlib import Path
import urllib.request

base = os.environ.get('MDM_SMOKE_BASE', 'http://127.0.0.1:9095')
credentials = Path.home() / '.local/share/schoolday-mdm/admin-login.txt'
password = next(line.split(': ', 1)[1] for line in credentials.read_text().splitlines() if line.startswith('Temporary password: '))
client = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
payload = json.dumps({'login': 'admin', 'password': hashlib.md5(password.encode()).hexdigest().upper()}).encode()
request = urllib.request.Request(base + '/rest/public/auth/login', data=payload, headers={'Content-Type': 'application/json'})
with client.open(request, timeout=20) as response:
    result = json.load(response)
assert result.get('status') == 'OK', 'Login failed'
print('Login accepted; response secrets withheld.')
if os.environ.get('MDM_SMOKE_ENROLL') == '1':
    request = urllib.request.Request(base + '/rest/private/agent/v1/token', data=b'{}', headers={'Content-Type': 'application/json'})
    with client.open(request, timeout=20) as response:
        enrollment = json.load(response)
    assert enrollment.get('status') == 'OK' and enrollment.get('data', {}).get('token'), 'Enrollment token creation failed'
    print('Single-use enrollment token creation accepted; token withheld and not used.')
logout = urllib.request.Request(base + '/rest/public/auth/logout', data=b'{}', headers={'Content-Type': 'application/json'})
client.open(logout, timeout=20).close()
for route in ['/', '/healthz', '/healthz/supervisor']:
    with client.open(base + route, timeout=20) as response:
        assert response.status == 200
        if route == '/':
            assert b'SchoolDay Device Management' in response.read()
    print(route + ': HTTP 200')
