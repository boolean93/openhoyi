#!/usr/bin/env python3
"""Run palette windows and switch evidence on this workflow's disposable AVD only."""
import argparse
from contextlib import contextmanager, nullcontext
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
          'switchInkStates=true screenshots=24 sameService=true retainedDrafts=true noModelRequest=true noBle=true')
SAMPLES = tuple(f'{mode}-{palette}'
                for mode in ('light', 'dark') for palette in ('classic', 'sea-salt', 'coffee', 'graphite'))
STATES = ('on', 'off', 'disabled')
SCREENSHOTS = tuple(f'{sample}-{state}.png' for sample in SAMPLES for state in STATES)
BACKGROUND_MARKER = ('PAGE_BACKGROUND_CHECKS_PASSED protectedInk=true noActivityRecreateOnApply=true '
                     'cancelFailureStale=true privateFiles=true aiHeaderFade=true aiComposer=true aiAttachmentMenu=true '
                     'dashboardCoverage=true screenshots=33 noModelRequest=true noBle=true')
BACKGROUNDS = tuple(f'{mode}-{page}.png' for mode in ('light', 'dark')
                    for page in ('photo', 'settings', 'home', 'curve', 'ai', 'home-mist', 'ai-fade', 'ai-composer',
                                 'ai-attachment-menu', 'scale', 'history', 'beans', 'extraction', 'ai-welcome')) + (
                        'landscape-home.png', 'landscape-scale.png', 'landscape-curve.png', 'landscape-settings.png',
                        'dark-home-disabled-modes.png', 'dark-curve-detail.png',
                    )
BACKGROUND_FAILURE_FRAMES = tuple(f'{name.removesuffix(".png")}.failed.png' for name in BACKGROUNDS)


def validate_distinct_screenshots(hashes):
    for sample in SAMPLES:
        require(len({hashes[f'{sample}-{state}.png'] for state in STATES}) == len(STATES),
                f'Duplicate switch screenshots for {sample}')


def run_fixture(adb, output, hashes, arg, directory, marker, names, log, *, retain_partial=False, failure_frames=()):
    success = False
    try:
        result = adb('shell', 'am', 'instrument', '-w', '-r', '-e', arg, 'true',
                     '-e', 'disposableCiEmulator', 'true', RUNNER, timeout=240)
        (output / log).write_text(result + '\n')
        require(re.search(r'^INSTRUMENTATION_CODE: -1$', result, re.M) and marker in result,
                f'{arg} fixture failed')
        success = True
    finally:
        if success or retain_partial:
            # Only these fixture-owned names are read; never enumerate app files for diagnostics.
            for name in names + (failure_frames if not success else ()):
                try:
                    data = adb('exec-out', 'run-as', APP, 'cat', f'files/{directory}/{name}',
                               binary=True, timeout=60 if success else 5)
                except (subprocess.CalledProcessError, subprocess.TimeoutExpired):
                    if success:
                        raise
                    continue
                valid = data.startswith(b'\x89PNG\r\n\x1a\n')
                if not success and not valid:
                    continue
                require(valid, 'Invalid appearance screenshot output')
                destination = output / name
                destination.write_bytes(data)
                hashes[name] = digest(destination)


@contextmanager
def with_software_keyboard(adb):
    # The caller has already verified the dedicated disposable AVD.
    key = 'show_ime_with_hard_keyboard'
    original = adb('shell', 'settings', 'get', 'secure', key)
    require(original in ('null', '0', '1'), 'Unexpected original keyboard setting')
    try:
        adb('shell', 'settings', 'put', 'secure', key, '1')
        yield
    finally:
        if original == 'null':
            adb('shell', 'settings', 'delete', 'secure', key)
        else:
            adb('shell', 'settings', 'put', 'secure', key, original)
        require(adb('shell', 'settings', 'get', 'secure', key) == original, 'Keyboard setting not restored')


def main(with_backgrounds=False):
    flag, avd_var, avd_kind = ('PAGE_BACKGROUND_CHECKS', 'CI_BACKGROUND_AVD', 'background') if with_backgrounds else (
        'THEME_PALETTE_CHECKS', 'CI_THEME_AVD', 'theme')
    require(os.environ.get('GITHUB_ACTIONS') == 'true' and
            os.environ.get('GITHUB_EVENT_NAME') == 'workflow_dispatch' and
            os.environ.get(flag) == 'true', 'Manual disposable GitHub runner required')
    run_id, attempt = os.environ['GITHUB_RUN_ID'], os.environ['GITHUB_RUN_ATTEMPT']
    avd = os.environ.get(avd_var)
    require(run_id.isdecimal() and attempt.isdecimal() and avd == f'openhoyi-{avd_kind}-{run_id}-{attempt}',
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

    output = Path('build/page-background-ui' if with_backgrounds else 'build/theme-palette-ui')
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
        fixtures = [('themePaletteChecks', 'theme-palette-evidence', MARKER, SCREENSHOTS, 'instrumentation.txt')]
        if with_backgrounds:
            fixtures.append(('pageBackgroundChecks', 'page-background-evidence', BACKGROUND_MARKER, BACKGROUNDS,
                             'background-instrumentation.txt'))
        for arg, directory, marker, names, log in fixtures:
            background = arg == 'pageBackgroundChecks'
            with with_software_keyboard(adb) if background else nullcontext():
                run_fixture(adb, output, receipt['screenshots'], arg, directory, marker, names, log,
                            retain_partial=background, failure_frames=BACKGROUND_FAILURE_FRAMES if background else ())
        validate_distinct_screenshots(receipt['screenshots'])
        receipt['appearanceCombined'] = with_backgrounds
        receipt['passed'] = True
    finally:
        (output / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
    print(MARKER)
    if with_backgrounds:
        print(BACKGROUND_MARKER)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--with-backgrounds', action='store_true')
    main(parser.parse_args().with_backgrounds)
