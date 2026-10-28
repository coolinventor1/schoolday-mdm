"""Build signed SchoolDay agent; keep its permanent signing key outside the repo."""
import os, pathlib, secrets, subprocess
root = pathlib.Path(__file__).resolve().parents[1]
private = pathlib.Path.home() / 'SchoolDay-private-backups' / 'mdm-signing'
private.mkdir(parents=True, exist_ok=True)
key = private / 'schoolday-mdm.jks'
password_file = private / 'keystore-password.txt'
if not password_file.exists():
    password_file.write_text(secrets.token_hex(32))
env = os.environ.copy()
env['JAVA_HOME'] = r'C:\Users\Owner\.jdks\ms-21.0.11'
env['ANDROID_HOME'] = r'C:\Users\Owner\OneDrive\Documents\ChatGPT\SchoolDay\passpoint-android\tools\sdk'
env['MDM_RELEASE_STORE_FILE'] = str(key)
env['MDM_RELEASE_STORE_PASSWORD'] = password_file.read_text().strip()
env['MDM_RELEASE_KEY_PASSWORD'] = env['MDM_RELEASE_STORE_PASSWORD']
env['MDM_RELEASE_KEY_ALIAS'] = 'schoolday-mdm'
if not key.exists():
    subprocess.run([str(pathlib.Path(env['JAVA_HOME']) / 'bin/keytool.exe'), '-genkeypair', '-keystore', str(key), '-storepass:env', 'MDM_RELEASE_STORE_PASSWORD', '-keypass:env', 'MDM_RELEASE_KEY_PASSWORD', '-alias', 'schoolday-mdm', '-keyalg', 'RSA', '-keysize', '3072', '-validity', '10000', '-dname', 'CN=SchoolDay Device Management,O=SchoolDay'], env=env, check=True)
subprocess.run([str(root / 'agent-android/gradlew.bat'), '--no-daemon', ':app:assembleRelease'], cwd=root / 'agent-android', env=env, check=True)
