#!/usr/bin/env bash
set -Eeuo pipefail

output_dir="$PWD/build/mock-ui-screenshots"
mkdir -p "$output_dir"
apk="mobile/build/outputs/apk/mock/mobile-mock.apk"
test -s "$apk"

on_error() {
  local status=$?
  trap - ERR
  adb exec-out screencap -p > "$output_dir/failure.png" 2>/dev/null || true
  adb logcat -d -s AndroidRuntime:E ActivityTaskManager:I ActivityManager:I OpenHoyiMobile:D > \
    "$output_dir/app-logcat.txt" 2>/dev/null || true
  adb shell dumpsys activity activities > "$output_dir/activity.txt" 2>/dev/null || true
  adb pull /sdcard/Android/data/io.openhoyi.mobile.mock/files/active-cup-visual \
    "$output_dir/active-cup-visual" >/dev/null 2>&1 || true
  echo '=== Foreground activity ===' >&2
  grep -E 'mResumedActivity|topResumedActivity|mFocusedApp|mCurrentFocus' "$output_dir/activity.txt" | tail -8 >&2 || true
  echo '=== App errors and launch ===' >&2
  grep -E -A 12 'FATAL EXCEPTION|Process: io.openhoyi|ANR in io.openhoyi|START u0|Displayed io.openhoyi' \
    "$output_dir/app-logcat.txt" | tail -45 >&2 || true
  echo '=== Theme preference ===' >&2
  cat "$output_dir/theme-pref.xml" >&2 2>/dev/null || true
  echo >&2
  exit "$status"
}
trap on_error ERR

# Use a landscape logical display; emulator rotation settings alone did not rotate the AVD.
adb shell wm size 2560x1600
adb shell wm density 360
adb shell settings put system font_scale 1.0
{
  adb shell wm size
  adb shell wm density
  adb shell settings get system font_scale
} > "$output_dir/display-profile.txt"
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 0
adb install -r "$apk"
test_apk=mobile/build/outputs/apk/androidTest/mock/mobile-mock-androidTest.apk
test -s "$test_apk"
adb install -r "$test_apk"
python3 - "$output_dir/idle-accessibility.txt" <<'PY'
from pathlib import Path
import subprocess,sys
output=Path(sys.argv[1])
with output.open('w') as capture:
    result=subprocess.run(['adb','shell','am','instrument','-w','-e','idleEvents','true',
        'io.openhoyi.mobile.mock.test/io.openhoyi.mobile.BrewAudioInstrumentation'],stdout=capture,timeout=90)
if result.returncode or 'IDLE_ACCESSIBILITY_DIAGNOSTIC_DONE' not in output.read_text():
    raise SystemExit('Idle accessibility diagnostic failed; output retained')
PY
# Formal capture gets normal continuous rendering, not the diagnostic pause.
adb shell am force-stop io.openhoyi.mobile.mock
adb shell am start -W -n io.openhoyi.mobile.mock/io.openhoyi.mobile.HomeActivity
sleep 15

assert_activity() {
  local expected="$1" attempt
  for attempt in 1 2 3 4 5; do
    if adb shell dumpsys activity activities | grep "topResumedActivity=.*io.openhoyi.mobile.$expected" >/dev/null; then
      return 0
    fi
    sleep 2
  done
  echo "Expected foreground activity: $expected" >&2
  return 1
}

tap_control() {
  python3 scripts/mock_ui_tap.py "$1" --scroll
  sleep 2
}

tap_nav() {
  local expected="$2"
  case "$expected" in
    HomeActivity) tap_control '冲煮' ;;
    CurveActivity) tap_control '曲线' ;;
    HistoryActivity) tap_control '历史' ;;
    BeanInventoryActivity) tap_control '豆子' ;;
    ExtractionActivity)
      tap_control '冲煮'
      tap_control '设备连接'
      tap_control '实时萃取'
      ;;
    AppSettingsActivity)
      tap_control '冲煮'
      tap_control '设备连接'
      tap_control '应用设置'
      ;;
    MachineSettingsActivity)
      tap_control '冲煮'
      tap_control '设备连接'
      tap_control '应用设置'
      tap_control '机器设置'
      ;;
    *) echo "Unsupported Mock route: $expected" >&2; return 1 ;;
  esac
  assert_activity "$expected"
}


toggle_theme() {
  tap_nav 0 AppSettingsActivity
  tap_control '深色主题'
  assert_activity AppSettingsActivity
  tap_nav 0 HomeActivity
}

capture() {
  local name="$1" path="$output_dir/$1.png" expected
  case "$name" in
    home-*|portrait-home-*) expected=HomeActivity ;;
    curves-*|curve-detail-*|portrait-curves-*|portrait-curve-detail-*) expected=CurveActivity ;;
    extraction-*|portrait-extraction-*) expected=ExtractionActivity ;;
    history-detail-*|portrait-history-detail-*) expected=HistoryDetailActivity ;;
    history-*|portrait-history-*) expected=HistoryActivity ;;
    settings-*|portrait-settings-*) expected=MachineSettingsActivity ;;
    *) echo "Unknown screenshot name: $name" >&2; return 1 ;;
  esac
  sleep 2
  assert_activity "$expected"
  adb exec-out screencap -p > "$path"
  assert_activity "$expected"
  python3 - "$path" "$name" <<'PY'
import struct
import sys
from pathlib import Path

image = Path(sys.argv[1]).read_bytes()
if len(image) < 10000 or image[:8] != b'\x89PNG\r\n\x1a\n':
    raise SystemExit('empty or invalid screenshot')
width, height = struct.unpack('>II', image[16:24])
print(f'{sys.argv[1]}: {width}x{height}')
if sys.argv[2].startswith('portrait-'):
    if width >= height:
        raise SystemExit('emulator is not in portrait layout')
elif (width, height) != (2560, 1600):
    raise SystemExit('screenshot does not match user tablet 2560x1600')
PY
}

assert_activity HomeActivity
capture home-light
tap_nav 1 CurveActivity
capture curves-light
tap_nav 2 ExtractionActivity
capture extraction-light
tap_nav 3 HistoryActivity
capture history-empty-light
tap_nav 4 MachineSettingsActivity
capture settings-light

tap_nav 0 HomeActivity
toggle_theme
sleep 5
capture home-dark
adb shell run-as io.openhoyi.mobile.mock cat shared_prefs/appearance.xml > "$output_dir/theme-pref.xml"
grep 'name="dark" value="true"' "$output_dir/theme-pref.xml" >/dev/null
tap_nav 1 CurveActivity
capture curves-dark
tap_nav 2 ExtractionActivity
capture extraction-dark
tap_nav 3 HistoryActivity
capture history-empty-dark
tap_nav 4 MachineSettingsActivity
capture settings-dark

tap_nav 1 CurveActivity
tap_control '采集曲线 2'
sleep 2
capture curve-detail-dark
tap_control '使用此曲线'
sleep 2
adb shell run-as io.openhoyi.mobile.mock cat shared_prefs/curves.xml > "$output_dir/selected-curve.xml"
grep 'name="selected">capture-2<' "$output_dir/selected-curve.xml" >/dev/null
tap_nav 0 HomeActivity
capture home-selected-dark
tap_nav 2 ExtractionActivity
capture extraction-ready-dark
tap_control '开始萃取'
sleep 2
capture extraction-confirm-dark
adb logcat -c
tap_control '开始模拟'
sleep 3
adb logcat -d -s OpenHoyiMobile:I | grep 'Mock 萃取已开始' > "$output_dir/shot-start-log.txt"
capture extraction-running-dark
sleep 34
capture extraction-ended-dark
tap_nav 3 HistoryActivity
capture history-list-dark
tap_control '采集曲线 2'
assert_activity HistoryActivity
capture history-selected-dark
tap_control '查看详情'
assert_activity HistoryDetailActivity
capture history-detail-dark


# Capture the approved light-theme running composition at the user's tablet geometry.
# This is a second real Mock shot, not relabeling the waiting-state screenshot.
toggle_theme
adb shell run-as io.openhoyi.mobile.mock cat shared_prefs/appearance.xml > "$output_dir/light-theme-pref.xml"
grep 'name="dark" value="false"' "$output_dir/light-theme-pref.xml" >/dev/null
tap_nav 2 ExtractionActivity
capture extraction-ready-light
tap_control '开始萃取'
capture extraction-confirm-light
adb logcat -c
tap_control '开始模拟'
sleep 3
adb logcat -d -s OpenHoyiMobile:I | grep 'Mock 萃取已开始' > "$output_dir/shot-start-light-log.txt"
capture extraction-running-light
sleep 34
capture extraction-ended-light

# Portrait capture retired by the user on 2026-10-16; landscape flows above remain.

# Reuse actual start/recreation/stop checks at this same user-tablet geometry.
# Separate raw-source scenarios; do not hide weight values to fake no-scale data.
adb shell am force-stop io.openhoyi.mobile.mock
# Instrumentation starts Home inside its own process before binding Extraction.
python3 - "$output_dir" <<'PYCAPTURE'
import subprocess
import sys
from pathlib import Path
out = Path(sys.argv[1])
with (out / 'active-cup-visual.txt').open('w') as log:
    subprocess.run(['adb', 'shell', 'am', 'instrument', '-w', '-e', 'activeCupVisual', 'true',
        '-e', 'disposableCiEmulator', 'true',
        'io.openhoyi.mobile.mock.test/io.openhoyi.mobile.BrewAudioInstrumentation'],
        stdout=log, stderr=subprocess.STDOUT, timeout=120, check=True)
if 'ACTIVE_CUP_VISUAL_PASSED states=2 actualMockStartStop=true noBle=true' not in (out / 'active-cup-visual.txt').read_text():
    raise SystemExit('Active cup visual checks failed; output retained')
PYCAPTURE
adb pull /sdcard/Android/data/io.openhoyi.mobile.mock/files/active-cup-visual "$output_dir/active-cup-visual"
