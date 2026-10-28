"""Verify release signing and create public, non-secret enrollment metadata."""
import base64, hashlib, json, os, pathlib, shutil, subprocess, re
root = pathlib.Path(__file__).resolve().parents[1]
private = pathlib.Path.home() / 'SchoolDay-private-backups/mdm-signing'
java = pathlib.Path.home() / '.jdks/ms-21.0.11/bin'
sdk = pathlib.Path.home() / '.schoolday-mdm-sdk/build-tools/34.0.0'
apk = root / 'agent-android/app/build/outputs/apk/release/app-release.apk'
env = os.environ.copy()
env['MDM_KEY_PASSWORD'] = (private / 'keystore-password.txt').read_text().strip()
cert = subprocess.check_output([str(java / 'keytool.exe'), '-exportcert', '-keystore', str(private / 'schoolday-mdm.jks'), '-alias', 'schoolday-mdm', '-storepass:env', 'MDM_KEY_PASSWORD'], env=env)
digest = hashlib.sha256(cert).digest()
verification = subprocess.check_output([str(java / 'java.exe'), '-jar', str(sdk / 'lib/apksigner.jar'), 'verify', '--print-certs', str(apk)], text=True)
assert digest.hex() in verification, 'APK is not signed with the permanent SchoolDay key'
badging = subprocess.check_output([str(sdk / 'aapt2.exe'), 'dump', 'badging', str(apk)], text=True)
assert "package: name='com.mdmesh.agent'" in badging
assert 'application-debuggable' not in badging
manifest = subprocess.check_output([str(sdk / 'aapt2.exe'), 'dump', 'xmltree', str(apk), '--file', 'AndroidManifest.xml'], text=True)
assert 'android:testOnly' not in manifest or 'android:testOnly(0x01010272)=(type 0x12)0x0' in manifest
assert 'com.mdmesh.agent.admin.AdminReceiver' in manifest
destination = root / 'enrollment'
destination.mkdir(exist_ok=True)
shutil.copyfile(apk, destination / 'schoolday-agent.apk')
version = re.search(r"versionCode='(\d+)'", badging).group(1)
shutil.copyfile(apk, destination / f'schoolday-agent-v{version}.apk')
metadata = {'package': 'com.mdmesh.agent', 'apk_sha256': hashlib.sha256(apk.read_bytes()).hexdigest(), 'signing_certificate_sha256_base64url': base64.urlsafe_b64encode(digest).decode().rstrip('='), 'package_badging': badging.splitlines()[0]}
(destination / 'agent-metadata.json').write_text(json.dumps(metadata, indent=2) + '\n')
print('Release signature, package, manifest and non-debuggable status verified.')
print(metadata['package_badging'])
