#!/usr/bin/env bash
set -Eeuo pipefail
[[ "${GITHUB_ACTIONS:-}" == true ]] || { echo 'Disposable GitHub CI required' >&2; exit 1; }
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || {
  echo 'Disposable emulator required' >&2; exit 1;
}
app=io.openhoyi.mobile.mock
test_app="$app.test"
installed="$(adb shell pm list packages -u | tr -d '\r')"
for package in "$app" "$test_app"; do
  if grep -Fxq "package:$package" <<< "$installed"; then
    echo "Existing package or retained data: $package" >&2
    exit 1
  fi
done

sha="$(git rev-parse HEAD)"
[[ "$sha" == "${GITHUB_SHA:-}" ]] || { echo 'Checkout differs from dispatched SHA' >&2; exit 1; }
app_apk=mobile/build/outputs/apk/mock/mobile-mock.apk
test_apk=mobile/build/outputs/apk/androidTest/mock/mobile-mock-androidTest.apk
aapt="${ANDROID_HOME:?}/build-tools/35.0.0/aapt"
package_of() {
  "$aapt" dump badging "$1" | sed -n "s/^package: name='\([^']*\)'.*/\1/p"
}
[[ "$(package_of "$app_apk")" == "$app" && "$(package_of "$test_apk")" == "$test_app" ]] || {
  echo 'Only the exact Mock app and its test APK may be installed' >&2
  exit 1
}
results=build/task-frame-ui/manual-live
mkdir -p "$results"
{
  printf 'sourceCommit=%s\nappPackage=%s\ntestPackage=%s\n' "$sha" "$app" "$test_app"
  sha256sum "$app_apk" "$test_apk"
} | tee "$results/apk-identity.txt"
trap 'adb logcat -d -s AndroidRuntime:E > "$results/logcat.txt" || true' EXIT
adb shell wm size 2560x1600
adb shell wm density 360
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 0
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb install "$app_apk"
adb install "$test_apk"
python3 - "$results/result.txt" <<'PY'
from pathlib import Path
import subprocess, sys
output = Path(sys.argv[1])
with output.open('w') as capture:
    result = subprocess.run(['adb', 'shell', 'am', 'instrument', '-w', '-r',
        '-e', 'mainNavigationChecks', 'manual-live', '-e', 'disposableCiEmulator', 'true',
        'io.openhoyi.mobile.mock.test/io.openhoyi.mobile.BrewAudioInstrumentation'],
        stdout=capture, stderr=subprocess.STDOUT, timeout=180)
text = output.read_text()
print(text)
# With -r, the special stream is not substituted for the result bundle and exit code.
lines = text.splitlines()
if (result.returncode or 'INSTRUMENTATION_RESULT: manualLive=MANUAL_LIVE_NAVIGATION_PASSED' not in lines
        or 'INSTRUMENTATION_CODE: -1' not in lines):
    raise SystemExit('Manual live navigation failed; evidence retained')
PY
