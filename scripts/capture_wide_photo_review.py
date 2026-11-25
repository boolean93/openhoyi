#!/usr/bin/env python3
"""One-time synthetic visual review on the lifecycle workflow's own emulator."""

import json
import os
from pathlib import Path
import struct
import subprocess

from check_theme_palette_ui import APP, SERIAL, TEST, run_fixture
from shared_mock_artifact import DESTINATIONS, digest, inspect_apk, require, run


def main():
    require(os.environ.get('GITHUB_ACTIONS') == 'true'
            and os.environ.get('GITHUB_EVENT_NAME') == 'workflow_dispatch'
            and os.environ.get('PAGE_BACKGROUND_CHECKS') == 'true', 'Manual CI only')
    sha = run('git', 'rev-parse', 'HEAD')
    require(sha == os.environ['GITHUB_SHA'], 'Dispatched source mismatch')
    run('git', 'diff', '--quiet', 'HEAD', '--')
    exe = Path(os.environ['ANDROID_HOME']) / 'platform-tools' / 'adb'

    def adb(*args, timeout=60, binary=False):
        data = subprocess.run([str(exe), '-s', SERIAL, *args], stdout=subprocess.PIPE,
                              check=True, timeout=timeout).stdout
        return data if binary else data.decode().strip()

    avd = f"openhoyi-background-{os.environ['GITHUB_RUN_ID']}-{os.environ['GITHUB_RUN_ATTEMPT']}"
    require(adb('shell', 'getprop', 'ro.kernel.qemu') == '1'
            and adb('shell', 'getprop', 'ro.hardware') in ('ranchu', 'goldfish')
            and adb('emu', 'avd', 'name').splitlines()[0] == avd, 'Dedicated runner required')
    packages = set(adb('shell', 'pm', 'list', 'packages').splitlines())
    require({f'package:{APP}', f'package:{TEST}'}.isdisjoint(packages), 'Expected fresh Mock')
    apks = {name: dict(inspect_apk(path, name), sha256=digest(path))
            for name, path in DESTINATIONS.items()}
    require(apks['app.apk']['certificateSha256'] == apks['test.apk']['certificateSha256'],
            'APK pair signer mismatch')
    out = Path('build/page-background-ui')
    out.mkdir(parents=True, exist_ok=False)
    receipt = {'sourceCommit': sha, 'apks': apks, 'avd': avd,
               'size': '2560x1600', 'densityDpi': 360, 'mockOnly': True,
               'syntheticFixtureSha256': digest(Path('mobile/src/androidTest/assets/photo-surface-review.jpg')),
               'screenshots': {}, 'passed': False}
    names = tuple(f'{mode}-{background}-{page}.png'
                  for mode in ('dark', 'light')
                  for background in ('photo', 'solid')
                  for page in ('curves',))
    try:
        adb('shell', 'wm', 'size', '2560x1600')
        adb('shell', 'wm', 'density', '360')
        for key, value in (('accelerometer_rotation', '0'), ('user_rotation', '0'), ('font_scale', '1.0')):
            adb('shell', 'settings', 'put', 'system', key, value)
        for name, path in DESTINATIONS.items():
            require(apks[name]['sha256'] == digest(path), 'APK changed before install')
            require(adb('install', path, timeout=120).splitlines()[-1] == 'Success', 'Install failed')
        run_fixture(adb, out, receipt['screenshots'], 'widePhotoReview', 'page-background-evidence',
                    'WIDE_PHOTO_REVIEW_CAPTURED screenshots=4 mockOnly=true', names,
                    'wide-photo-instrumentation.txt', retain_partial=True)
        for name in names:
            data = (out / name).read_bytes()
            require(struct.unpack('>II', data[16:24]) == (2560, 1600), f'Invalid PNG dimensions {name}')
        require(len(set(receipt['screenshots'].values())) == len(names), 'Duplicate PNGs')
        receipt['passed'] = True
    finally:
        (out / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
    print('WIDE_PHOTO_REVIEW_CAPTURED screenshots=4 mockOnly=true')


if __name__ == '__main__':
    main()
