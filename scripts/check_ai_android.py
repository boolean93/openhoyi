#!/usr/bin/env python3
"""Run fake-only AI Android checks on an explicitly disposable emulator, never a physical serial."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import uuid

from shared_mock_artifact import inspect_apk

APP = 'io.openhoyi.mobile.mock'
TEST = APP + '.test'
RUNNER = TEST + '/io.openhoyi.mobile.BrewAudioInstrumentation'
PROFILES = (
    ('phone-small-light', '320x640', 'zh-Hans', 'small', 'false', 'true'),
    ('landscape-medium-dark', '800x360', 'en', 'medium', 'true', 'false'),
    ('square-large-light', '480x480', 'zh-Hans', 'large', 'false', 'false'),
    ('phone-large-dark', '320x640', 'en', 'large', 'true', 'false'),
)
PRIVATE_MARKERS = ('PRIVATE_USER_SENTINEL', 'PRIVATE_TOOL_SENTINEL', 'PRIVATE_ERROR_SENTINEL',
                   'PRIVATE_KEY_SENTINEL', 'PRIVATE_THROWABLE_SENTINEL', 'fake-sentinel-')


def require(value, message):
    if not value:
        raise ValueError(message)


def target_contract(serial, avd, disposable):
    require(disposable, 'Explicit disposable-emulator flag required')
    require(re.fullmatch(r'emulator-[0-9]+', serial or ''), 'Physical/network serials are forbidden')
    require(re.fullmatch(r'openhoyi-ai-[a-zA-Z0-9_-]{1,80}', avd or ''), 'Dedicated AI fixture AVD name required')


def assert_no_private_logs(text):
    require(not any(marker in text for marker in PRIVATE_MARKERS), 'Private fixture value appeared in Android logcat')


def result_pid(output, phase):
    require(re.search(r'^INSTRUMENTATION_CODE: -1$', output, re.M) and
            re.search(r'AI_ANDROID_CHECKS_PASSED phase=' + re.escape(phase) + r'(?:\s|$)', output),
            'Android AI fixture did not pass')
    values = re.findall(r'^INSTRUMENTATION_RESULT: appPid=([0-9]+)$', output, re.M)
    require(len(values) == 1 and int(values[0]) > 0, 'Target process identity missing')
    return values[0]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--avd-name', required=True)
    parser.add_argument('--disposable-emulator', action='store_true')
    parser.add_argument('--sdk', type=Path, default=Path(os.environ.get('ANDROID_HOME', 'missing-android-sdk')))
    parser.add_argument('--apk', type=Path, default=Path('mobile/build/outputs/apk/mock/mobile-mock.apk'))
    parser.add_argument('--test-apk', type=Path, default=Path('mobile/build/outputs/apk/androidTest/mock/mobile-mock-androidTest.apk'))
    parser.add_argument('--evidence', type=Path, required=True)
    args = parser.parse_args()
    target_contract(args.serial, args.avd_name, args.disposable_emulator)  # Before any ADB invocation.
    require(not args.evidence.exists(), 'Evidence directory must be new')
    adb = str(args.sdk.resolve() / 'platform-tools/adb')
    os.environ['ANDROID_HOME'] = str(args.sdk.resolve())

    def call(*tail, timeout=120, binary=False):
        value = subprocess.run([adb, '-s', args.serial, *tail], check=True, capture_output=True,
                               text=not binary, timeout=timeout)
        return value.stdout if binary else value.stdout.strip()

    require(call('get-state') == 'device', 'Selected emulator not available')
    require(call('shell', 'getprop', 'ro.kernel.qemu') == '1', 'Target is not an emulator')
    require(call('shell', 'getprop', 'ro.hardware') in ('ranchu', 'goldfish'), 'Unexpected emulator hardware')
    require(call('emu', 'avd', 'name').splitlines()[0] == args.avd_name, 'AVD identity differs')
    # A fresh disposable target avoids replacing any existing user's Mock data or test package.
    require(not call('shell', 'pm', 'path', APP) and not call('shell', 'pm', 'path', TEST), 'Fresh disposable emulator required')
    app_hash = hashlib.sha256(args.apk.read_bytes()).hexdigest()
    test_hash = hashlib.sha256(args.test_apk.read_bytes()).hexdigest()
    app = inspect_apk(args.apk, 'app.apk')
    tests = inspect_apk(args.test_apk, 'test.apk')
    require(app_hash == hashlib.sha256(args.apk.read_bytes()).hexdigest() and
            test_hash == hashlib.sha256(args.test_apk.read_bytes()).hexdigest(), 'APK pair changed during inspection')
    app['apkSha256'] = app_hash
    tests['apkSha256'] = test_hash
    require(app['certificateSha256'] == tests['certificateSha256'], 'APK pair signer differs')
    args.evidence.mkdir(parents=True)
    identifier = str(uuid.uuid4())
    receipt = {'schemaVersion': 1, 'fixtureId': identifier, 'serial': args.serial, 'avdName': args.avd_name,
               'app': app, 'tests': tests, 'phases': [], 'passed': False, 'noModelNetwork': True,
               'physicalDevicesForbidden': True, 'layoutProfiles': [p[0] for p in PROFILES]}
    size = call('shell', 'wm', 'size')
    density = call('shell', 'wm', 'density')
    hardware_ime = call('shell', 'settings', 'get', 'secure', 'show_ime_with_hard_keyboard')
    require(hardware_ime in ('null', '0', '1'), 'Unexpected original keyboard setting')
    (args.evidence / 'original-display.txt').write_text(size + '\n' + density + '\n')

    def run_phase(phase, label=None, extra=()):
        name = label or phase
        output = call('shell', 'am', 'instrument', '-w', '-r', '-e', 'aiSafetyChecks', 'true',
                      '-e', 'disposableEmulator', 'true', '-e', 'aiFixtureId', identifier,
                      '-e', 'aiPhase', phase, *extra, RUNNER, timeout=180)
        (args.evidence / (name + '.instrumentation.txt')).write_text(output)
        observed = re.findall(r'^INSTRUMENTATION_RESULT: appPid=([0-9]+)$', output, re.M)
        if len(observed) == 1 and int(observed[0]) > 0:
            logs = call('logcat', '-d', '--pid=' + observed[0], '-v', 'threadtime')
            (args.evidence / (name + '.logcat.txt')).write_text(logs)
            assert_no_private_logs(logs)
        pid = result_pid(output, phase)
        receipt['phases'].append({'label': name, 'phase': phase, 'appPid': int(pid), 'passed': True,
                                  'logcatPrivateMarkersAbsent': True})

    try:
        call('shell', 'settings', 'put', 'secure', 'show_ime_with_hard_keyboard', '1')
        require(app_hash == hashlib.sha256(args.apk.read_bytes()).hexdigest() and
                test_hash == hashlib.sha256(args.test_apk.read_bytes()).hexdigest(), 'APK pair changed before installation')
        require('Success' in call('install', str(args.apk.resolve())), 'Mock install failed')
        require('Success' in call('install', str(args.test_apk.resolve())), 'Test install failed')
        run_phase('seed')
        call('shell', 'am', 'force-stop', APP)
        run_phase('verify')  # Fixture itself requires a different process PID and retains no plaintext.
        call('shell', 'am', 'force-stop', APP)
        run_phase('logs')
        for label, dimensions, language, font, dark, flow in PROFILES:
            call('shell', 'am', 'force-stop', APP)
            call('shell', 'wm', 'size', dimensions)
            call('shell', 'wm', 'density', '160')
            run_phase('ui', label, ('-e', 'aiLanguage', language, '-e', 'aiFont', font,
                                    '-e', 'aiDark', dark, '-e', 'aiFlow', flow))
            for filename in ('layout.png', 'layout.json'):
                value = call('exec-out', 'run-as', APP, 'cat', 'files/ai-fixture-' + identifier + '/' + filename, binary=True)
                (args.evidence / (label + '-' + filename)).write_bytes(value)
                if filename == 'layout.json':
                    capture = json.loads(value)['externalCapture']
                    require(capture in ('not_attempted', 'unavailable', 'blackened'), 'Unknown screenshot observation')
                    receipt['phases'][-1]['externalCapture'] = capture
        receipt['protectedScreenshotBlackenedObserved'] = any(p.get('externalCapture') == 'blackened' for p in receipt['phases'])
        receipt['screenCaptureUnavailable'] = any(p.get('externalCapture') == 'unavailable' for p in receipt['phases'])
        receipt['capturePassed'] = True
    finally:
        # Only this script's UUID fixture and emulator display settings are cleaned/restored.
        receipt['passed'] = False
        try:
            run_phase('cleanup')
            receipt['cleanupPassed'] = True
        except Exception:
            receipt['cleanupPassed'] = False
        try:
            override = re.search(r'Override size: ([0-9]+x[0-9]+)', size)
            call('shell', 'wm', 'size', override[1] if override else 'reset')
            override = re.search(r'Override density: ([0-9]+)', density)
            call('shell', 'wm', 'density', override[1] if override else 'reset')
            if hardware_ime == 'null':
                call('shell', 'settings', 'delete', 'secure', 'show_ime_with_hard_keyboard')
            else:
                call('shell', 'settings', 'put', 'secure', 'show_ime_with_hard_keyboard', hardware_ime)
            call('shell', 'am', 'force-stop', APP)
            receipt['keyboardSettingRestored'] = call('shell', 'settings', 'get', 'secure', 'show_ime_with_hard_keyboard') == hardware_ime
            receipt['displayRestored'] = call('shell', 'wm', 'size') == size and call('shell', 'wm', 'density') == density
        except Exception:
            receipt['restoreFailed'] = True
        receipt['passed'] = all(receipt.get(k) is True for k in ('capturePassed', 'cleanupPassed', 'keyboardSettingRestored', 'displayRestored'))
        (args.evidence / 'receipt.json').write_text(json.dumps(receipt, indent=2))
    require(receipt['passed'] and receipt['displayRestored'] and receipt['keyboardSettingRestored'], 'Android AI acceptance incomplete')
    print('AI_ANDROID_ACCEPTANCE_PASSED fakeOnly=true noModelNetwork=true noPhysicalDevice=true profiles=4')


if __name__ == '__main__':
    main()
