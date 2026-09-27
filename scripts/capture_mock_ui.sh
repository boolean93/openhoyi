#!/usr/bin/env bash
set -euo pipefail

output_dir="$PWD/build/mock-ui-screenshots"
mkdir -p "$output_dir"
apk="mobile/build/outputs/apk/mock/mobile-mock.apk"
test -s "$apk"

on_error() {
  local status=$?
  adb exec-out screencap -p > "$output_dir/failure.png" 2>/dev/null || true
  adb logcat -d -s AndroidRuntime:E ActivityTaskManager:I ActivityManager:I OpenHoyiMobile:D > \
    "$output_dir/app-logcat.txt" 2>/dev/null || true
  adb shell dumpsys activity activities > "$output_dir/activity.txt" 2>/dev/null || true
  echo '=== UI dump ===' >&2
  tail -12 "$output_dir/uiautomator.txt" >&2 2>/dev/null || true
  echo '=== Foreground activity ===' >&2
  grep -E 'mResumedActivity|topResumedActivity|mFocusedApp|mCurrentFocus' "$output_dir/activity.txt" | tail -8 >&2 || true
  echo '=== App errors and launch ===' >&2
  grep -E -A 12 'FATAL EXCEPTION|Process: io.openhoyi|ANR in io.openhoyi|START u0|Displayed io.openhoyi' \
    "$output_dir/app-logcat.txt" | tail -45 >&2 || true
  echo '=== UI hierarchy preview ===' >&2
  head -c 800 "$output_dir/hierarchy.xml" >&2 2>/dev/null || true
  echo '=== Theme preference ===' >&2
  cat "$output_dir/theme-pref.xml" >&2 2>/dev/null || true
  echo >&2
  exit "$status"
}
trap on_error ERR

# Use a landscape logical display; emulator rotation settings alone did not rotate the AVD.
adb shell wm size 1600x1000
adb shell wm density 200
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 0
adb install -r "$apk"
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

tap_nav() {
  local index="$1" expected="$2"
  # Five equal native navigation targets occupy the bar above the AVD taskbar.
  adb shell input tap "$((160 * (2 * index + 1)))" 888
  assert_activity "$expected"
  sleep 2
}

capture() {
  local name="$1" path="$output_dir/$1.png"
  sleep 2
  adb exec-out screencap -p > "$path"
  python3 - "$path" <<'PY'
import struct
import sys
from pathlib import Path

image = Path(sys.argv[1]).read_bytes()
if len(image) < 10000 or image[:8] != b'\x89PNG\r\n\x1a\n':
    raise SystemExit('empty or invalid screenshot')
width, height = struct.unpack('>II', image[16:24])
print(f'{sys.argv[1]}: {width}x{height}')
if width <= height:
    raise SystemExit('emulator is not in landscape layout')
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
adb shell input tap 120 175
sleep 2
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
