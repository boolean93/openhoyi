#!/usr/bin/env python3
"""Generate tiny signed APK fixtures locally. Ephemeral test keys never leave a temporary directory."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import zipfile


def run(*args):
    return subprocess.run(list(map(str, args)), check=True, capture_output=True, text=True).stdout


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--sdk', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    tools = args.sdk / 'build-tools/35.0.0'
    with tempfile.TemporaryDirectory(prefix='hoyi-update-fixture-') as directory:
        temp = Path(directory)
        manifest = temp / 'AndroidManifest.xml'
        manifest.write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android"
            package="io.openhoyi.mobile" android:versionCode="94" android:versionName="0.2.0-fixture">
            <uses-sdk android:minSdkVersion="26" android:targetSdkVersion="35" />
            <application android:hasCode="false" android:debuggable="false" android:label="Update test fixture" />
            </manifest>''')
        unsigned = args.output / 'unsigned.apk'
        run(tools / 'aapt2', 'link', '-I', args.sdk / 'platforms/android-35/android.jar',
            '--manifest', manifest, '-o', unsigned)
        key = temp / 'ephemeral-test.p12'
        run('keytool', '-genkeypair', '-noprompt', '-keystore', key, '-storetype', 'PKCS12',
            '-storepass', 'fixture-only', '-keypass', 'fixture-only', '-alias', 'fixture',
            '-keyalg', 'RSA', '-keysize', '2048', '-validity', '2', '-dname', 'CN=Ephemeral Update Fixture')
        valid = args.output / 'valid.apk'
        run(tools / 'apksigner', 'sign', '--ks', key, '--ks-pass', 'pass:fixture-only',
            '--key-pass', 'pass:fixture-only', '--out', valid, unsigned)
        signature = run(tools / 'apksigner', 'verify', '--print-certs', valid)
        import re
        certificate = re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-f]{64})', signature)[1]
        tampered = args.output / 'tampered.apk'
        shutil.copyfile(valid, tampered)
        with zipfile.ZipFile(tampered, 'a') as archive:
            archive.writestr('assets/tampered', b'changed after signing')
        isolated_manifest = temp / 'isolated-manifest.xml'
        isolated_manifest.write_text(manifest.read_text().replace('package="io.openhoyi.mobile"',
            'package="io.openhoyi.updatefixture"'))
        isolated_unsigned = args.output / 'isolated-unsigned.apk'
        run(tools / 'aapt2', 'link', '-I', args.sdk / 'platforms/android-35/android.jar',
            '--manifest', isolated_manifest, '-o', isolated_unsigned)
        isolated = args.output / 'isolated.apk'
        run(tools / 'apksigner', 'sign', '--ks', key, '--ks-pass', 'pass:fixture-only',
            '--key-pass', 'pass:fixture-only', '--out', isolated, isolated_unsigned)
        (args.output / 'isolated-identity.json').write_text(json.dumps({
            'packageName': 'io.openhoyi.updatefixture', 'versionCode': 94,
            'signerSha256': certificate, 'apkSha256': hashlib.sha256(isolated.read_bytes()).hexdigest(),
            'apkSize': isolated.stat().st_size,
        }, indent=2) + '\n')
        (args.output / 'identity.json').write_text(json.dumps({
            'packageName': 'io.openhoyi.mobile', 'versionCode': 94, 'debuggable': False,
            'signerSha256': certificate, 'apkSha256': hashlib.sha256(valid.read_bytes()).hexdigest(),
            'apkSize': valid.stat().st_size, 'scope': 'Synthetic signed APK; no install or device operation',
        }, indent=2) + '\n')
    print('UPDATE_APK_FIXTURES_READY signed=true unsigned=true tampered=true ephemeralKeysRemoved=true')


if __name__ == '__main__':
    main()
