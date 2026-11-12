#!/usr/bin/env python3
"""One fixed API34 CI emulator acceptance: isolated host, real runtime crypto, product-UI fixture crops.

Never modifies install permission, appops, settings, device policy, user data, or a production package.
"""
import argparse
import json
from pathlib import Path
import re
import subprocess

HOST = 'io.openhoyi.mobile.updatefixture'
TEST = HOST + '.test'
INSTALLER_TARGET = 'io.openhoyi.updatefixture'
RUNNER = TEST + '/io.openhoyi.mobile.UpdateAcceptanceInstrumentation'


def run(*args, timeout=90):
    return subprocess.run(list(map(str, args)), check=True, capture_output=True, text=True, timeout=timeout).stdout


def verify_apks(sdk, root):
    aapt = sdk / 'build-tools/35.0.0/aapt'
    signer = sdk / 'build-tools/35.0.0/apksigner'
    host = root / 'mobile/build/outputs/apk/updateFixture/mobile-updateFixture.apk'
    test = root / 'mobile/build/outputs/apk/androidTest/updateFixture/mobile-updateFixture-androidTest.apk'
    certificates = []
    for apk, package in [(host, HOST), (test, TEST)]:
        badging = run(aapt, 'dump', 'badging', apk)
        if not badging.startswith("package: name='" + package + "' "):
            raise ValueError('Unexpected isolated APK identity')
        permissions = run(aapt, 'dump', 'permissions', apk)
        if 'BLUETOOTH' in permissions or 'LOCATION' in permissions:
            raise ValueError('An isolated test requested device permissions')
        tree = run(aapt, 'dump', 'xmltree', apk, 'AndroidManifest.xml')
        if 'io.openhoyi.mobile.mock.test' in tree or 'CURVE_FIXTURE' in tree:
            raise ValueError('Ordinary Mock test provider or permission leaked into isolation')
        if apk == test and (HOST not in tree or 'UpdateAcceptanceInstrumentation' not in tree):
            raise ValueError('Unexpected isolated instrumentation target')
        cert = re.findall(r'^Signer #1 certificate SHA-256 digest: ([0-9a-f]{64})$',
                          run(signer, 'verify', '--print-certs', apk), re.MULTILINE)
        if len(cert) != 1:
            raise ValueError('Test signing identity is unavailable')
        certificates.append(cert[0])
    if len(set(certificates)) != 1:
        raise ValueError('Host and instrumentation certificate differ')
    return host, test


def require_fresh_emulator(serial, qemu, installed):
    if not re.fullmatch(r'emulator-[0-9]+', serial) or qemu.strip() != '1':
        raise ValueError('This fixed acceptance command is for a disposable CI emulator only')
    if set(installed) & {'package:' + p for p in (HOST, TEST, INSTALLER_TARGET)}:
        raise ValueError('Isolated namespace already exists; no overwrite, uninstall, or clear-data fallback')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--sdk', type=Path, required=True)
    parser.add_argument('--verify-only', action='store_true')
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    host_apk, test_apk = verify_apks(args.sdk, root)
    if args.verify_only:
        print('UPDATE_ACCEPTANCE_MANIFESTS_PASSED isolated=true noBle=true certificatesMatch=true')
        return
    adb = args.sdk / 'platform-tools/adb'
    serial = run(adb, 'get-serialno').strip()
    def device(*cmd, timeout=90):
        return run(adb, '-s', serial, *cmd, timeout=timeout)
    # Only read-only device calls occur before the emulator and fresh-namespace checks.
    installed = set(device('shell', 'pm', 'list', 'packages').splitlines())
    require_fresh_emulator(serial, device('shell', 'getprop', 'ro.kernel.qemu'), installed)
    out = root / 'build/update-acceptance'
    out.mkdir(parents=True, exist_ok=True)
    (out / 'install-host.txt').write_text(device('install', host_apk))
    (out / 'install-test.txt').write_text(device('install', test_apk))
    result = device('shell', 'am', 'instrument', '-w', RUNNER, timeout=480)
    (out / 'instrumentation.txt').write_text(result)
    passed = 'UPDATE_ACCEPTANCE_SCOPED_PASSED' in result and 'UPDATE_ACCEPTANCE_FAILED' not in result
    try:
        device('pull', '/sdcard/Android/data/' + HOST + '/files/update-acceptance', out)
    except subprocess.CalledProcessError:
        if passed:
            raise
        # Keep the original runner failure; partial evidence is best-effort and never retries an install.
    if not passed:
        raise RuntimeError('Update acceptance runner failed; see instrumentation.txt and any partial evidence')
    report = json.loads((out / 'update-acceptance/runtime-install-safety.json').read_text())
    if report.get('actualArchivePackage') != INSTALLER_TARGET or report.get('permissionPolicyChanged') is not False:
        raise ValueError('Runtime safety evidence identity mismatch')
    after = set(device('shell', 'pm', 'list', 'packages').splitlines())
    if 'package:' + INSTALLER_TARGET in after:
        raise ValueError('The runner must never confirm or perform installation')
    print('UPDATE_ACCEPTANCE_SCOPED_PASSED api=34 runtimeCrypto=true fixtureScreenshots=true '
          'installerConfirmationVerified=' + str(report.get('installerConfirmationVerified')).lower()
          + ' noPermissionChanges=true noInstall=true')


if __name__ == '__main__':
    main()
