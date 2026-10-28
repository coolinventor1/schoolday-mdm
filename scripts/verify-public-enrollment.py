"""Verify HTTPS APK bytes and the deployed enrollment bundle, without tokens."""
import hashlib, json, pathlib, re, urllib.request
base = 'https://mdm.schoolday.one'
metadata = json.loads((pathlib.Path(__file__).resolve().parents[1] / 'enrollment/agent-metadata.json').read_text())
def fetch(path):
    request = urllib.request.Request(base + path, headers={'User-Agent': 'SchoolDay-MDM-Verification/1.0'})
    with urllib.request.urlopen(request, timeout=60) as response:
        assert response.status == 200
        return response.read()
assert hashlib.sha256(fetch('/enrollment/schoolday-agent.apk')).hexdigest() == metadata['apk_sha256']
print('Public HTTPS APK matches the verified signed release byte for byte.')
html = fetch('/login').decode()
paths = re.findall(r'src="(/assets/[^" ]+\.js)"', html)
assert paths, 'No application bundle found'
bundle = '\n'.join(fetch(path).decode() for path in paths)
for expected in [metadata['signing_certificate_sha256_base64url'], 'com.mdmesh.agent', base + '/enrollment/schoolday-agent.apk']:
    assert expected in bundle, 'Enrollment build value missing'
assert 'com.mdmesh.agent.debug' not in bundle, 'Debug enrollment package still present'
print('Public enrollment bundle contains the correct release package, certificate checksum and download URL.')
for path in ['/healthz', '/healthz/supervisor']:
    assert fetch(path).strip() == b'ok'
    print(path + ': healthy')
