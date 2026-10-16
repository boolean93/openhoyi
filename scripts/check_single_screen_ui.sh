#!/usr/bin/env bash
set -Eeuo pipefail
# Only a disposable CI emulator: never apply display overrides to the user's tablet.
profile=${1:?profile required}
density=200
case "$profile" in
  phone) size=450x1000; font=1.0 ;;
  tablet-landscape) size=1600x1000; font=1.0 ;;
  tablet-portrait) size=1000x1600; font=1.0 ;;
  tablet-large-font) size=1600x1000; font=1.3 ;;
  # Xiaomi M2105K81AC: actual physical pixels/density, not the older roomy fixture.
  tablet-user-landscape) size=2560x1600; density=360; font=1.0 ;;
  tablet-user-portrait) size=1600x2560; density=360; font=1.0 ;;
  *) exit 2 ;;
esac
package=io.openhoyi.mobile.mock
output_dir="$PWD/build/single-screen-ui/$profile"
mkdir -p "$output_dir"
collect() {
  adb logcat -d -s AndroidRuntime:E > "$output_dir/logcat.txt" || true
  adb shell dumpsys activity activities > "$output_dir/activities.txt" || true
  adb pull "/sdcard/Android/data/$package/files/single-screen-window-checks" "$output_dir/" || true
}
trap collect EXIT
adb shell wm density "$density"
adb shell wm size "$size"
adb shell settings put system font_scale "$font"
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 0
# Hosted emulators can advertise a hardware keyboard. Still expose the actual
# soft keyboard for focused-input screenshots; this script is CI-emulator-only.
adb shell settings put secure show_ime_with_hard_keyboard 1
adb install -r mobile/build/outputs/apk/mock/mobile-mock.apk
adb install -r mobile/build/outputs/apk/androidTest/mock/mobile-mock-androidTest.apk
python3 - "$output_dir/instrumentation.txt" <<'CHECK_PY'
from pathlib import Path
import subprocess,sys
output=Path(sys.argv[1])
with output.open('w') as capture:
    result=subprocess.run(['adb','shell','am','instrument','-w','-e','singleScreenChecks','true',
        'io.openhoyi.mobile.mock.test/io.openhoyi.mobile.BrewAudioInstrumentation'],stdout=capture,timeout=1200)
if result.returncode:
    raise SystemExit(result.returncode)
expected='SINGLE_SCREEN_WINDOW_GEOMETRY_PASSED pages=21 fixtures=336 legacyDetailFixture=true noBle=true'
if expected not in output.read_text():
    raise SystemExit('Single screen matrix did not confirm all 336 populated windows')
list_expected='SINGLE_SCREEN_LIST_STATE_PASSED pages=7 restoreFixtures=14 language=zh-Hans appearance=light noBle=true'
if list_expected not in output.read_text():
    raise SystemExit('Actual list scroll/recreation/return checks did not confirm all 14 fixtures')
CHECK_PY
collect
python3 - "$output_dir/single-screen-window-checks" <<'CHECK_PY'
from pathlib import Path
import struct,sys
root=Path(sys.argv[1])
list_result=root/'list-state-result.txt'
if not list_result.is_file() or 'pages=7 restoreFixtures=14 language=zh-Hans appearance=light noBle=true' not in list_result.read_text():
    raise SystemExit('List restoration evidence missing/incomplete')
restore_names={'inventory-selected-restored.png', 'legacy-curve-selected-restored.png'}
restored=[root/name for name in sorted(restore_names)]
if not all(path.is_file() for path in restored):
    raise SystemExit('Selected inventory/legacy-curve restoration screenshots missing')
paths=[path for path in root.glob('*.png') if path.name not in restore_names]
if len(paths)!=336:
    raise SystemExit(f'Incomplete screenshots: {len(paths)}/336')
states=list((root/'states').glob('*.png'))
if len(states)!=64:
    raise SystemExit(f'Incomplete initial/populated state screenshots: {len(states)}/64')
forms=list((root/'chinese-form-states').glob('*.png'))
if len(forms)<44:
    raise SystemExit(f'Incomplete Chinese form groups/long editor screenshots: {len(forms)}')
extra=list((root/'chinese-extra-states').glob('*.png'))
if len(extra)!=2:
    raise SystemExit(f'Incomplete Chinese scale dose captures: {len(extra)}/2')
safety=list((root/'chinese-safety-states').glob('*.png'))
if len(safety)!=6:
    raise SystemExit(f'Incomplete Chinese full warning captures: {len(safety)}/6')
for path in paths+states+forms+extra+safety+restored:
    data=path.read_bytes()
    if len(data)<10000 or data[:8]!=b'\x89PNG\r\n\x1a\n':
        raise SystemExit(f'Invalid screenshot: {path}')
    if not all(struct.unpack('>II',data[16:24])):
        raise SystemExit(f'Empty screenshot: {path}')
print(f'Validated 336 pages, 64 initial/populated captures, {len(forms)} Chinese form states, 2 scale dose captures and 6 full warning captures')
CHECK_PY
