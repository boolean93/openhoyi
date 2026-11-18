#!/usr/bin/env python3
"""Run palette windows and switch evidence on this workflow's disposable AVD only."""
import json
import os
from pathlib import Path
import re
import subprocess

from mock_runner_contract import APP, TEST
from shared_mock_artifact import DESTINATIONS, digest, inspect_apk, require, run

RUNNER = TEST + '/io.openhoyi.mobile.BrewAudioInstrumentation'
SERIAL = 'emulator-5554'
MARKER = ('THEME_PALETTE_CHECKS_PASSED previewCancelApply=true twoModes=true fixedMetricColors=true '
          'switchInkStates=true screenshots=6 sameService=true retainedDrafts=true noModelRequest=true noBle=true')
SCREENSHOTS = tuple(f'{sample}-{state}.png'
                    for sample in ('light-sea-salt', 'dark-graphite')
                    for state in ('on', 'off', 'disabled'))


def main():
    require(os.environ.get('GITHUB_ACTIONS') == 'true' and
            os.environ.get('GITHUB_EVENT_NAME') == 'workflow_dispatch' and
            os.environ.get('THEME_PALETTE_CHECKS') == 'true', 'Manual disposable GitHub runner required')
    run_id, attempt = os.environ['GITHUB_RUN_ID'], os.environ['GITHUB_RUN_ATTEMPT']
    avd = os.environ.get('CI_THEME_AVD')
    require(run_id.isdecimal() and attempt.isdecimal() and avd == f'openhoyi-theme-{run_id}-{attempt}',
            'Dedicated AVD identity required')
    sha = run('git', 'rev-parse', 'HEAD')
    require(re.fullmatch(r'[0-9a-f]{40}', sha) and sha == os.environ['GITHUB_SHA'],
            'Checkout differs from dispatched commit')
    run('git', 'diff', '--quiet', 'HEAD', '--')

    exe = Path(os.environ['ANDROID_HOME']) / 'platform-tools' / 'adb'

    def adb(*args, timeout=60, binary=False):
        result = subprocess.run([str(exe), '-s', SERIAL, *args], check=True, capture_output=True,
                                timeout=timeout).stdout
        return result if binary else result.decode('utf-8', errors='replace').strip()

    require(adb('get-state') == 'device' and adb('shell', 'getprop', 'ro.kernel.qemu') == '1',
            'Fresh emulator required')
    require(adb('shell', 'getprop', 'ro.hardware') in ('ranchu', 'goldfish') and
            adb('emu', 'avd', 'name').splitlines()[:1] == [avd], 'AVD identity mismatch')
    packages = set(adb('shell', 'pm', 'list', 'packages').splitlines())
    require({f'package:{APP}', f'package:{TEST}'}.isdisjoint(packages), 'App pair is already installed')

    output = Path('build/theme-palette-ui')
    output.mkdir(parents=True, exist_ok=False)
    apks = {}
    for kind, source in DESTINATIONS.items():
        require(Path(source).is_file() and not Path(source).is_symlink(), 'Missing actual built APK')
        apks[kind] = dict(inspect_apk(source, kind), sha256=digest(source))
    require(apks['app.apk']['certificateSha256'] == apks['test.apk']['certificateSha256'], 'Pair signer mismatch')
    receipt = dict(sourceCommit=sha, workflowRef=os.environ['GITHUB_WORKFLOW_REF'], runId=run_id,
                   runAttempt=attempt, avd=avd, serial=SERIAL, apks=apks, screenshots={},
                   passed=False, mockOnly=True, noModelRequest=True, noPhysicalDevice=True)
    try:
        for kind, source in DESTINATIONS.items():
            require(digest(source) == apks[kind]['sha256'], 'APK changed before install')
            require(adb('install', source, timeout=120).splitlines()[-1] == 'Success', 'Fixture install failed')
        result = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'themePaletteChecks', 'true',
                     '-e', 'disposableCiEmulator', 'true', RUNNER, timeout=240)
        (output / 'instrumentation.txt').write_text(result + '\n')
        require(re.search(r'^INSTRUMENTATION_CODE: -1$', result, re.M) and MARKER in result, 'Palette fixture failed')
        for name in SCREENSHOTS:
            data = adb('exec-out', 'run-as', APP, 'cat', f'files/theme-palette-evidence/{name}', binary=True)
            require(data.startswith(b'\x89PNG\r\n\x1a\n'), 'Invalid palette screenshot output')
            destination = output / name
            destination.write_bytes(data)
            receipt['screenshots'][name] = digest(destination)
        receipt['passed'] = True
    finally:
        (output / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
    print(MARKER)


if __name__ == '__main__':
    main()
