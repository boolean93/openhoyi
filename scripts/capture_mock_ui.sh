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
  echo >&2
  exit "$status"
}
trap on_error ERR

# A phone AVD with a tablet-sized landscape display exercises the two-column layout.
adb shell wm size 1000x1600
adb shell wm density 200
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
adb install -r "$apk"
adb shell am start -W -n io.openhoyi.mobile.mock/io.openhoyi.mobile.HomeActivity
sleep 15

tap_node() {
  local attribute="$1" target="$2" point="" attempt
  for attempt in 1 2 3; do
    adb shell uiautomator dump /sdcard/openhoyi-ui.xml > "$output_dir/uiautomator.txt" 2>&1 || true
    adb exec-out cat /sdcard/openhoyi-ui.xml > "$output_dir/hierarchy.xml" || true
    point="$(python3 - "$output_dir/hierarchy.xml" "$attribute" "$target" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

try:
    root = ET.parse(sys.argv[1]).getroot()
    for node in root.iter('node'):
        if node.get(sys.argv[2]) != sys.argv[3]:
            continue
        bounds = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.get('bounds', ''))
        if bounds:
            left, top, right, bottom = map(int, bounds.groups())
            print(f'{(left + right) // 2} {(top + bottom) // 2}')
            break
except (ET.ParseError, OSError):
    pass
PY
)"
    if [[ -n "$point" ]]; then
      adb shell input tap $point
      sleep 2
      return 0
    fi
    sleep 2
  done
  echo "UI node missing: $attribute=$target" >&2
  return 1
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
PY
}

capture home-light
tap_node content-desc 曲线
capture curves-light
tap_node content-desc 萃取
capture extraction-light
tap_node content-desc 历史
capture history-empty-light
tap_node content-desc 设置
capture settings-light

tap_node content-desc 首页
tap_node content-desc 切换深色模式
capture home-dark
tap_node content-desc 曲线
capture curves-dark
tap_node text '采集曲线 2'
capture curve-detail-dark
tap_node text 使用此曲线
tap_node content-desc 萃取
capture extraction-ready-dark
tap_node text 开始萃取
tap_node text 开始模拟
capture extraction-running-dark
sleep 38
capture extraction-ended-dark
tap_node content-desc 历史
capture history-list-dark
tap_node text '采集曲线 2'
capture history-detail-dark
