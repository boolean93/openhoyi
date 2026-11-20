#!/usr/bin/env bash
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true ]] || exit 1
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || exit 1
[[ "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" -ge 34 ]] || exit 1
sha=$(git rev-parse HEAD)
package=io.openhoyi.mobile.mock
test_package="$package.test/io.openhoyi.mobile.BrewAudioInstrumentation"
base=build/task-first-evidence/shot-review-layout/"$sha"
dir="$base/$PROFILE"
mkdir -p "$dir"
remote="/sdcard/Android/data/$package/files/shot-review-layout/$sha"
collect() { adb shell logcat -d -s AndroidRuntime:E > "$dir/runtime-errors.txt" || true; }
trap collect EXIT
adb shell wm size "$SIZE"
adb shell wm density "$DENSITY"
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 0
adb shell settings put system font_scale 1.0
if [[ "$THEME" == dark ]]; then adb shell cmd uimode night yes; else adb shell cmd uimode night no; fi
adb install -r mobile/build/outputs/apk/mock/mobile-mock.apk
adb install -r mobile/build/outputs/apk/androidTest/mock/mobile-mock-androidTest.apk
sha256sum mobile/build/outputs/apk/mock/mobile-mock.apk > "$dir/mock-apk.sha256"
adb shell am force-stop "$package"
timeout 150 adb shell am instrument -w -r \
  -e shotReviewLayout true -e disposableCiEmulator true -e sourceSha "$sha" \
  -e sampleTheme "$THEME" "$test_package" | tee "$dir/instrumentation.log"
grep -F 'SHOT_REVIEW_LAYOUT_SUCCESS noProvider=true' "$dir/instrumentation.log"
for file in proof.json none.png pending.png ready.png details.png; do
  adb pull "$remote/$file" "$dir/$file"
done
python3 - "$dir" "$sha" "$PROFILE" <<'PY'
import json, sys
from pathlib import Path
directory, sha, profile = Path(sys.argv[1]), sys.argv[2], sys.argv[3]
proof=json.loads((directory/'proof.json').read_text())
assert proof['sourceSha']==sha and proof['sameFinishedShot'] and proof['stateVariants']==5, proof
assert proof['reviewExplicitlyOpened'] and proof['realProviderCalls']==0 and len(proof['identicalScreenBounds'])==7, proof
w,h = proof['widthDp'],proof['heightDp']
assert ((w==320 and h>w) if profile=='narrow-phone-light' else (w>=1000 and w>h)), proof
for name in ('none','pending','ready','details'):
    assert (directory/f'{name}.png').read_bytes().startswith(b'\x89PNG\r\n\x1a\n'), name
print(json.dumps(proof,ensure_ascii=False))
PY
