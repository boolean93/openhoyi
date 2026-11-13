#!/usr/bin/env bash
set -euo pipefail
output=build/studio-preheat
mkdir -p "$output"
# Only a disposable CI emulator may receive the isolated Mock test pair.
[[ "${CI:-}" == true ]]
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]]
git rev-parse HEAD > "$output/source-sha.txt"
adb install -r mobile/build/outputs/apk/mock/mobile-mock.apk
adb install -r mobile/build/outputs/apk/androidTest/mock/mobile-mock-androidTest.apk
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 0
adb shell am instrument -w -e autoPreheatChecks only io.openhoyi.mobile.mock.test/io.openhoyi.mobile.BrewAudioInstrumentation | tee "$output/instrumentation.txt"
adb pull /sdcard/Android/data/io.openhoyi.mobile.mock/files/auto-preheat-ui "$output/"
grep -q 'SERVICE_AUTO_PREHEAT_CHECKS_PASSED' "$output/instrumentation.txt"
grep -q 'AUTO_PREHEAT_UI_CHECKS_PASSED' "$output/instrumentation.txt"
! grep -E 'FAILURES|INSTRUMENTATION_FAILED|Exception' "$output/instrumentation.txt"
