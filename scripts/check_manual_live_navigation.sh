#!/usr/bin/env bash
set -Eeuo pipefail
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || {
  echo 'Disposable emulator required' >&2; exit 1;
}
results=build/task-frame-ui/manual-live
mkdir -p "$results"
trap 'adb logcat -d -s AndroidRuntime:E > "$results/logcat.txt" || true' EXIT
adb shell wm size 2560x1600
adb shell wm density 360
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 0
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb install -r mobile/build/outputs/apk/mock/mobile-mock.apk
adb install -r mobile/build/outputs/apk/androidTest/mock/mobile-mock-androidTest.apk
python3 - "$results/result.txt" <<'PY'
from pathlib import Path
import subprocess, sys
output = Path(sys.argv[1])
with output.open('w') as capture:
    result = subprocess.run(['adb', 'shell', 'am', 'instrument', '-w',
        '-e', 'mainNavigationChecks', 'manual-live', '-e', 'disposableCiEmulator', 'true',
        'io.openhoyi.mobile.mock.test/io.openhoyi.mobile.BrewAudioInstrumentation'],
        stdout=capture, stderr=subprocess.STDOUT, timeout=180)
text = output.read_text()
print(text)
if result.returncode or 'MANUAL_LIVE_NAVIGATION_PASSED' not in text or 'INSTRUMENTATION_CODE: -1' not in text:
    raise SystemExit('Manual live navigation failed; evidence retained')
PY
