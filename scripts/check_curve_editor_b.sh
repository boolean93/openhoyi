#!/usr/bin/env bash
set -euo pipefail
serial="${ANDROID_SERIAL:-emulator-5554}"
adb_cmd() { adb -s "$serial" "$@"; }
[[ "$serial" == emulator-* ]] || { echo 'Disposable emulator required' >&2; exit 2; }
[[ "$(adb_cmd shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || exit 2
out=build/task-first-evidence/editor-b
mkdir -p "$out"
adb_cmd install -r mobile/build/outputs/apk/mock/mobile-mock.apk
adb_cmd install -r mobile/build/outputs/apk/androidTest/mock/mobile-mock-androidTest.apk
for fixture in landscape-medium-light square-large-dark portrait-small-light portrait-large-dark; do
  case "$fixture" in
    landscape*) size=1600x1000; density=200; font=medium; dark=false ;;
    square*) size=1100x1100; density=200; font=large; dark=true ;;
    portrait-small*) size=720x1440; density=320; font=small; dark=false ;;
    *) size=720x1440; density=320; font=large; dark=true ;;
  esac
  adb_cmd shell wm size "$size"
  adb_cmd shell wm density "$density"
  printf 'size=%s density=%s font=%s dark=%s\n' "$size" "$density" "$font" "$dark" > "$out/$fixture-config.txt"
  adb_cmd shell settings put system accelerometer_rotation 0
  adb_cmd shell settings put system user_rotation 0
  adb_cmd shell am instrument -w -e editorBChecks true -e disposableCiEmulator true \
    -e fontSize "$font" -e dark "$dark" \
    io.openhoyi.mobile.mock.test/io.openhoyi.mobile.BrewAudioInstrumentation | tee "$out/$fixture.txt"
  grep -q EDITOR_B_CHECKS_PASSED "$out/$fixture.txt"
  mkdir -p "$out/$fixture"
  adb_cmd pull /sdcard/Android/data/io.openhoyi.mobile.mock/files/editor-b/. "$out/$fixture/"
done
printf '%s\n' "source=${PRODUCT_PR_HEAD_SHA:-$(git rev-parse HEAD)}" 'mockOnly=true' 'hardwareAcceptance=false' > "$out/source.txt"
