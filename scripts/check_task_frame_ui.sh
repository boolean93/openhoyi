#!/usr/bin/env bash
set -Eeuo pipefail
profile=${1:?Expected tablet-user-landscape, tablet-user-portrait or phone}
case "$profile" in
  tablet-user-landscape) size=2560x1600; density=360 ;;
  tablet-user-portrait) size=1600x2560; density=360 ;;
  phone) size=450x1000; density=200 ;;
  *) exit 2 ;;
esac
# Fail before display changes or installation on any physical device.
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || { echo 'Disposable emulator required' >&2; exit 1; }
package=io.openhoyi.mobile.mock
output_dir="$PWD/build/task-frame-ui/$profile"
mkdir -p "$output_dir"
collect() {
  adb logcat -d -s AndroidRuntime:E > "$output_dir/logcat.txt" || true
  adb logcat -d -v threadtime -s ImeTracker:I InputMethodManager:D InsetsController:D > "$output_dir/ime-diagnostics.txt" || true
  adb shell dumpsys input_method > "$output_dir/input-method.txt" || true
  adb shell dumpsys activity activities > "$output_dir/activities.txt" || true
  adb pull "/sdcard/Android/data/$package/files/task-frame-checks" "$output_dir/screenshots" || true
  adb pull "/sdcard/Android/data/$package/files/curve-library-checks" "$output_dir/curve-library" || true
  adb pull "/sdcard/Android/data/$package/files/two-cup-chart-checks" "$output_dir/two-cup-components" || true
  adb pull "/sdcard/Android/data/$package/files/home-workbench-checks" "$output_dir/home-workbench" || true
  adb pull "/sdcard/Android/data/$package/files/four-metric-chart-checks" "$output_dir/chart-components" || true
}
trap collect EXIT
adb shell wm size "$size"
adb shell wm density "$density"
adb shell settings put system font_scale 1.0
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 0
adb install -r mobile/build/outputs/apk/mock/mobile-mock.apk
adb install -r mobile/build/outputs/apk/androidTest/mock/mobile-mock-androidTest.apk
python3 - "$output_dir/result.txt" <<'CHECK_PY'
from pathlib import Path
import subprocess,sys
output=Path(sys.argv[1])
with output.open('w') as capture:
    result=subprocess.run(['adb','shell','am','instrument','-w','-e','taskFrameChecks','true',
        'io.openhoyi.mobile.mock.test/io.openhoyi.mobile.BrewAudioInstrumentation'],
        stdout=capture,stderr=subprocess.STDOUT,timeout=600)
if result.returncode or 'TASK_FRAME_CHECKS_PASSED pages=24 noBle=true' not in output.read_text():
    print(output.read_text())
    raise SystemExit('Task frame checks failed; evidence retained')
CHECK_PY

python3 - "$output_dir/chart-result.txt" <<'CHART_PY'
from pathlib import Path
import subprocess,sys
output=Path(sys.argv[1])
with output.open('w') as capture:
    result=subprocess.run(['adb','shell','am','instrument','-w','-e','fourMetricChartChecks','true',
        'io.openhoyi.mobile.mock.test/io.openhoyi.mobile.BrewAudioInstrumentation'],
        stdout=capture,stderr=subprocess.STDOUT,timeout=180)
if result.returncode or 'FOUR_METRIC_CHART_CHECKS_PASSED fixtures=4 nativeComponent=true shortHeight=true noBle=true' not in output.read_text():
    print(output.read_text())
    raise SystemExit('Four-metric native component checks failed; evidence retained')
CHART_PY

# Real Home views at normal and accessibility font scales. Emulator guard above is mandatory.
for font_scale in 1.0 1.3; do
  adb shell settings put system font_scale "$font_scale"
  adb shell am force-stop "$package"
  python3 - "$output_dir/home-$font_scale.txt" "$font_scale" <<'HOME_PY'
from pathlib import Path
import subprocess,sys
output=Path(sys.argv[1])
with output.open('w') as capture:
    result=subprocess.run(['adb','shell','am','instrument','-w','-e','homeWorkbenchChecks','true','-e','expectedFontScale',sys.argv[2],
        'io.openhoyi.mobile.mock.test/io.openhoyi.mobile.BrewAudioInstrumentation'],
        stdout=capture,stderr=subprocess.STDOUT,timeout=180)
if result.returncode or 'HOME_WORKBENCH_CHECKS_PASSED fixtures=4 shortHeight=true recovery=true recentSelection=true readOnly=true noBle=true' not in output.read_text() or 'SCALE_TREND_PANEL_CHECKS_PASSED fixtures=4 shortEntry=true recovery=true empty=true dialogStatus=true dialogDismiss=true componentOnly=true' not in output.read_text():
    print(output.read_text())
    raise SystemExit('Home workbench checks failed; evidence retained')
HOME_PY
  adb pull "/sdcard/Android/data/$package/files/home-workbench-checks" "$output_dir/home-font-$font_scale"
done
adb shell settings put system font_scale 1.0

for font_scale in 1.0 1.3; do
  adb shell settings put system font_scale "$font_scale"
  adb shell am force-stop "$package"
  python3 - "$output_dir/two-cup-$font_scale.txt" "$font_scale" <<'TWO_CUP_PY'
from pathlib import Path
import subprocess,sys
output=Path(sys.argv[1])
with output.open('w') as capture:
    result=subprocess.run(['adb','shell','am','instrument','-w','-e','twoCupChartChecks','true',
        '-e','expectedFontScale',sys.argv[2],
        'io.openhoyi.mobile.mock.test/io.openhoyi.mobile.BrewAudioInstrumentation'],
        stdout=capture,stderr=subprocess.STDOUT,timeout=180)
if result.returncode or 'TWO_CUP_CHART_CHECKS_PASSED fixtures=10 sharedScale=true recovery=true noBle=true' not in output.read_text():
    print(output.read_text())
    raise SystemExit('Two-cup native checks failed; evidence retained')
TWO_CUP_PY
  adb pull "/sdcard/Android/data/$package/files/two-cup-chart-checks" "$output_dir/two-cup-font-$font_scale"
done
adb shell settings put system font_scale 1.0

for font_scale in 1.0 1.3; do
  adb shell settings put system font_scale "$font_scale"
  adb shell am force-stop "$package"
  python3 - "$output_dir/curve-library-$font_scale.txt" "$font_scale" <<'CURVE_PY'
from pathlib import Path
import subprocess,sys
output=Path(sys.argv[1])
with output.open('w') as capture:
    result=subprocess.run(['adb','shell','am','instrument','-w','-e','curveLibraryChecks','true',
        '-e','expectedFontScale',sys.argv[2],
        'io.openhoyi.mobile.mock.test/io.openhoyi.mobile.BrewAudioInstrumentation'],
        stdout=capture,stderr=subprocess.STDOUT,timeout=180)
if result.returncode or 'CURVE_LIBRARY_VIEWPORT_CHECKS_PASSED fixtures=5 shortHeight=true recovery=true readOnly=true noBle=true' not in output.read_text():
    print(output.read_text())
    raise SystemExit('Curve library viewport checks failed; evidence retained')
CURVE_PY
  adb pull "/sdcard/Android/data/$package/files/curve-library-checks" "$output_dir/curve-library-font-$font_scale"
done
adb shell settings put system font_scale 1.0
