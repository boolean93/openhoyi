#!/usr/bin/env bash
set -Eeuo pipefail

# Isolated Mock only. Never install or launch Alpha/Lab or send device commands.
output_dir="$PWD/build/mock-lifecycle"
mkdir -p "$output_dir"
package=io.openhoyi.mobile.mock
apk=mobile/build/outputs/apk/mock/mobile-mock.apk
test -s "$apk"
collect() { adb logcat -d -v raw -s OpenHoyiLifecycle:I '*:S' | tr -d '\r' > "$output_dir/lifecycle.txt"; }
on_error() {
  local status=$?
  trap - ERR
  collect || true
  adb logcat -d -s AndroidRuntime:E OpenHoyiMobile:I > "$output_dir/errors.txt" || true
  adb shell dumpsys activity activities > "$output_dir/activities.txt" || true
  exit "$status"
}
trap on_error ERR
assert_activity() {
  local page="$1"
  for attempt in 1 2 3 4 5; do
    if adb shell dumpsys activity activities | grep "topResumedActivity=.*io.openhoyi.mobile.$page" >/dev/null; then return; fi
    sleep 2
  done
  echo "Missing foreground page $page" >&2
  return 1
}
assert_edges() {
  collect
  python3 - "$output_dir/lifecycle.txt" "$1" "$2" <<'PYCODE'
from pathlib import Path
import sys
lines=Path(sys.argv[1]).read_text().splitlines()
actual=(lines.count("visible"), lines.count("hidden"))
expected=(int(sys.argv[2]), int(sys.argv[3]))
if actual!=expected:
    raise SystemExit(f"Lifecycle edges {actual}, expected {expected}: {lines}")
print(f"lifecycle edges: visible={actual[0]}, hidden={actual[1]}")
PYCODE
}

adb shell wm size 1600x1000
adb shell wm density 200
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 0
adb install -r "$apk"
adb shell am force-stop "$package"
adb shell run-as "$package" rm -f shared_prefs/appearance.xml
adb logcat -c
adb shell am start -W -n "$package/io.openhoyi.mobile.HomeActivity"
assert_activity HomeActivity
sleep 3
assert_edges 1 1

# Framework navigation order must not hide the app on readers that have no service binding.
for item in '1 CurveActivity' '2 ExtractionActivity' '3 HistoryActivity' '4 MachineSettingsActivity' '0 HomeActivity'; do
  read -r index page <<< "$item"
  adb shell input tap "$((160 * (2 * index + 1)))" 888
  assert_activity "$page"
  sleep 2
  assert_edges 1 1
done

# The existing theme Switch calls Activity.recreate(). Require a NEW config-stop callback.
cp "$output_dir/lifecycle.txt" "$output_dir/before-theme-switch.txt"
adb shell input tap 1530 103
sleep 3
assert_activity HomeActivity
adb shell run-as "$package" cat shared_prefs/appearance.xml > "$output_dir/appearance.xml"
grep 'name="dark" value="true"' "$output_dir/appearance.xml" >/dev/null
assert_edges 1 1
python3 - "$output_dir" <<'PYCODE'
from pathlib import Path
import json,sys
root=Path(sys.argv[1])
marker="recreating:HomeActivity"
before=(root/"before-theme-switch.txt").read_text().splitlines().count(marker)
after=(root/"lifecycle.txt").read_text().splitlines().count(marker)
if after<=before:
    raise SystemExit("Theme Switch did not produce a new Home configuration-stop callback")
(root/"theme-recreation.json").write_text(json.dumps({"homeConfigurationStopsBefore":before,"homeConfigurationStopsAfter":after})+"\n")
PYCODE

# True background closes visibility once. Rebuilding a stopped page must not reopen it.
adb shell input keyevent KEYCODE_HOME
sleep 3
assert_edges 1 2
cp "$output_dir/lifecycle.txt" "$output_dir/before-background-resize.txt"
adb shell wm size 1000x1600
sleep 3
assert_edges 1 2
adb shell wm size 1600x1000
sleep 3
assert_edges 1 2
cp "$output_dir/lifecycle.txt" "$output_dir/after-background-resize.txt"
python3 - "$output_dir" <<'PYCODE'
from pathlib import Path
import json,sys
root=Path(sys.argv[1])
marker="destroyed:HomeActivity:configuration"
before=(root/"before-background-resize.txt").read_text().splitlines().count(marker)
after=(root/"after-background-resize.txt").read_text().splitlines().count(marker)
# Android may defer stopped-activity relaunch. Record this limit instead of claiming that path ran.
(root/"background-resize.json").write_text(json.dumps({"homeConfigurationDestroyObservedWhileBackground":after>before})+"\n")
PYCODE
adb shell am start -W -n "$package/io.openhoyi.mobile.HomeActivity"
assert_activity HomeActivity
sleep 3
assert_edges 2 2
adb shell input keyevent KEYCODE_HOME
sleep 3
assert_edges 2 3
printf 'Mock lifecycle checks passed\n' > "$output_dir/result.txt"
