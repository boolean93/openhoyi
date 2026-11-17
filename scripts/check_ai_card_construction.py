#!/usr/bin/env python3
"""Execute the existing detached card fixture only on a fresh disposable CI emulator."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess

from shared_mock_artifact import inspect_apk

APP = 'io.openhoyi.mobile.mock'
TEST = APP + '.test'
RUNNER = TEST + '/io.openhoyi.mobile.BrewAudioInstrumentation'


def require(value, message):
    if not value:
        raise ValueError(message)


def main():
    # This entry point intentionally cannot target a developer's physical device.
    require(os.environ.get('GITHUB_ACTIONS') == 'true', 'Disposable GitHub runner required')
    avd = os.environ.get('CI_CARD_AVD', '')
    require(re.fullmatch(r'openhoyi-card-[0-9]+-[0-9]+', avd), 'Dedicated AVD identity required')
    serial = 'emulator-5554'
    output = Path('build/ai-card-construction')
    output.mkdir(parents=True, exist_ok=False)

    def adb(*args, timeout=60):
        return subprocess.run(['adb', '-s', serial, *args], check=True, capture_output=True,
                              text=True, timeout=timeout).stdout.strip()

    require(adb('get-state') == 'device', 'Emulator unavailable')
    require(adb('shell', 'getprop', 'ro.kernel.qemu') == '1', 'Emulator required')
    require(adb('shell', 'getprop', 'ro.hardware') in ('ranchu', 'goldfish'), 'Unexpected hardware')
    require(adb('emu', 'avd', 'name').splitlines()[0] == avd, 'AVD identity mismatch')
    require(not adb('shell', 'pm', 'path', APP) and not adb('shell', 'pm', 'path', TEST), 'Fresh emulator required')
    sha = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()
    require(sha == os.environ.get('GITHUB_SHA'), 'Checkout differs from dispatched commit')
    paths = [Path('mobile/build/outputs/apk/mock/mobile-mock.apk'),
             Path('mobile/build/outputs/apk/androidTest/mock/mobile-mock-androidTest.apk')]
    pair = []
    for path, kind in zip(paths, ('app.apk', 'test.apk')):
        pair.append(dict(inspect_apk(path, kind), sha256=hashlib.sha256(path.read_bytes()).hexdigest()))
    require(pair[0]['certificateSha256'] == pair[1]['certificateSha256'], 'Pair signer mismatch')
    receipt = dict(sourceCommit=sha, runId=os.environ['GITHUB_RUN_ID'], avd=avd, serial=serial,
                   apks=pair, passed=False, detachedViewsOnly=True, visualAcceptance=False,
                   noModelRequest=True, noPhysicalDevice=True)
    try:
        for path, identity in zip(paths, pair):
            require(hashlib.sha256(path.read_bytes()).hexdigest() == identity['sha256'], 'APK changed')
            require('Success' in adb('install', str(path), timeout=120), 'Fixture install failed')
        command = ['shell', 'am', 'instrument', '-w', '-r', '-e', 'aiPreviewOnly', 'true',
                   '-e', 'aiCardConstructionOnly', 'true', RUNNER]
        result = adb(*command, timeout=180)
        (output / 'instrumentation.txt').write_text(result + '\n')
        require(re.search(r'^INSTRUMENTATION_CODE: -1$', result, re.M) and
                'AI_CHAT_PREVIEW_BASIC_CHECK_PASSED mockOnly=true noChatRequest=true' in result,
                'Card construction fixture failed')
        receipt['passed'] = True
    finally:
        (output / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
    print('AI_CARD_CONSTRUCTION_PASSED detachedViewsOnly=true visualAcceptance=false')


if __name__ == '__main__':
    main()
