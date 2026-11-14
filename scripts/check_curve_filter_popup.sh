#!/usr/bin/env bash
set -Eeuo pipefail
case "${1:-false}" in
  false) exec bash scripts/check_curve_visual_migration.sh ;;
  true) ;;
  *) echo 'Expected filter_only=true or false' >&2; exit 2 ;;
esac
[[ "${GITHUB_ACTIONS:-}" == true ]] || { echo 'CI-only check' >&2; exit 1; }
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || { echo 'Disposable emulator required' >&2; exit 1; }
adb install -r mobile/build/outputs/apk/mock/mobile-mock.apk
adb install -r mobile/build/outputs/apk/androidTest/mock/mobile-mock-androidTest.apk
mkdir -p build/curve-filter-popup
trap 'adb logcat -d -s AndroidRuntime:E > build/curve-filter-popup/runtime-errors.txt; adb shell dumpsys window > build/curve-filter-popup/windows.txt; adb shell dumpsys activity activities > build/curve-filter-popup/activities.txt; adb pull /sdcard/Android/data/io.openhoyi.mobile.mock/files/curve-filter-popup build/curve-filter-popup/screenshots || true' EXIT
adb shell wm density 360
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 0
for orientation in portrait landscape; do
  if [[ "$orientation" == portrait ]]; then adb shell wm size 1600x2560; else adb shell wm size 2560x1600; fi
  adb shell am force-stop io.openhoyi.mobile.mock
  adb shell am instrument -w -e disposableCiEmulator true -e orientation "$orientation" io.openhoyi.mobile.mock.test/io.openhoyi.mobile.CurveFilterPopupInstrumentation | tee "build/curve-filter-popup/result-$orientation.txt"
  grep -q CURVE_FILTER_POPUP_PASSED "build/curve-filter-popup/result-$orientation.txt"
done
