"""Set public URL and verified release certificate; no secret values are printed."""
import json, pathlib, re
root = pathlib.Path(__file__).resolve().parents[1]
metadata = json.loads((root / 'enrollment/agent-metadata.json').read_text())
checksum = metadata['signing_certificate_sha256_base64url']
version = re.search(r"versionCode='(\d+)'", metadata['package_badging']).group(1)
apk_url = f'https://mdm.schoolday.one/enrollment/schoolday-agent-v{version}.apk'
assert re.fullmatch(r'[A-Za-z0-9_-]{43}', checksum)
updates = {
    'BASE_URL': 'https://mdm.schoolday.one',
    'VITE_AGENT_PACKAGE': 'com.mdmesh.agent',
    'VITE_AGENT_CHECKSUM': checksum,
    'VITE_AGENT_APK_URL': apk_url,
}
path = root / '.env'
lines = path.read_text().splitlines()
lines = [line for line in lines if line.partition('=')[0] not in updates]
lines.extend(key + '=' + value for key, value in updates.items())
path.write_text('\n'.join(lines) + '\n')
path.chmod(0o600)
print('Public URL and enrollment release metadata configured.')
