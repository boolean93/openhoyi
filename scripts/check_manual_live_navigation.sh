#!/usr/bin/env bash
set -Eeuo pipefail
[[ "${GITHUB_ACTIONS:-}" == true ]] || { echo 'Disposable GitHub CI required' >&2; exit 1; }
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || {
  echo 'Disposable emulator required' >&2; exit 1;
}
mode="${1:-}"
[[ "$mode" == "" || "$mode" == "--completion-only" ]] || exit 2
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
if [[ "$mode" == "--completion-only" ]]; then
  adb shell wm size 1080x1920
  adb shell wm density 480
else
  adb shell wm size 2560x1600
  adb shell wm density 360
fi
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 0
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb install "$app_apk"
adb install "$test_apk"
if [[ "$mode" != "--completion-only" ]]; then
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
  adb exec-out run-as "$app" cat files/porcelain-manual-result.png > "$results/porcelain-manual-result.png"
fi

# Check flow feedback, conditional AI review and dismissal in the native dialog.
python3 - "$results/completion-dialog.txt" <<'PY'
from pathlib import Path
import subprocess, sys
output = Path(sys.argv[1])
with output.open('w') as capture:
    result = subprocess.run(['adb', 'shell', 'am', 'instrument', '-w', '-r',
        '-e', 'disposableEmulator', 'true',
        'io.openhoyi.mobile.mock.test/io.openhoyi.mobile.BrewCompletionDialogInstrumentation'],
        stdout=capture, stderr=subprocess.STDOUT, timeout=90)
text = output.read_text()
print(text)
if (result.returncode or 'BREW_COMPLETION_DIALOG_CHECKS_PASSED' not in text
        or 'INSTRUMENTATION_CODE: -1' not in text.splitlines()):
    raise SystemExit('Completion Dialog failed; evidence retained')
PY

adb exec-out run-as "$app" cat files/porcelain-result-card.png > "$results/porcelain-result-card.png"
