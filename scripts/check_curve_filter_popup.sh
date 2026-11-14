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
adb shell am instrument -w -e disposableCiEmulator true io.openhoyi.mobile.mock.test/io.openhoyi.mobile.CurveFilterPopupInstrumentation | tee build/curve-filter-popup/result.txt
grep -q CURVE_FILTER_POPUP_PASSED build/curve-filter-popup/result.txt
