"""Install isolated Google SDK packages with repository checksum validation."""
import hashlib, io, os, pathlib, subprocess, urllib.request, xml.etree.ElementTree as ET, zipfile
root = pathlib.Path.home() / '.schoolday-mdm-sdk'
base = 'https://dl.google.com/android/repository/'
doc = ET.fromstring(urllib.request.urlopen(base + 'repository2-1.xml', timeout=60).read())
for wanted in ['cmdline-tools;latest']:
    package = next(p for p in doc if p.tag.endswith('remotePackage') and p.get('path') == wanted)
    archive = next(a for a in package.find('archives') if a.findtext('host-os') in ('windows', None))
    complete = archive.find('complete')
    data = urllib.request.urlopen(base + complete.findtext('url'), timeout=120).read()
    assert hashlib.sha1(data).hexdigest() == complete.findtext('checksum')
    destination = root / wanted.replace(';', '/')
    with zipfile.ZipFile(io.BytesIO(data)) as bundle:
        prefix = bundle.namelist()[0].split('/')[0] + '/'
        for member in bundle.infolist():
            if member.is_dir(): continue
            target = (destination / member.filename.removeprefix(prefix)).resolve()
            assert target.is_relative_to(destination.resolve())
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(bundle.read(member))
    license_id = package.find('uses-license').get('ref')
    license_text = next(p.text for p in doc if p.tag.endswith('license') and p.get('id') == license_id)
    licenses = root / 'licenses'
    licenses.mkdir(exist_ok=True)
    (licenses / license_id).write_text(hashlib.sha1(license_text.strip().encode()).hexdigest() + '\n')
    print('Installed and checksum verified:', wanted, flush=True)
env = os.environ.copy()
env['JAVA_HOME'] = str(pathlib.Path.home() / '.jdks/ms-21.0.11')
manager = root / 'cmdline-tools/latest/bin/sdkmanager.bat'
subprocess.run([str(manager), '--sdk_root=' + str(root), '--licenses'], input='y\n' * 100, text=True, env=env, check=True)
subprocess.run([str(manager), '--sdk_root=' + str(root), 'platforms/android-35', 'build-tools/34.0.0'], env=env, check=True)
