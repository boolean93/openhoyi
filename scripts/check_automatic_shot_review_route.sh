#!/usr/bin/env bash
set -euo pipefail
test "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" = 1
test "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" -ge 34
source_sha="$(git rev-parse HEAD)"
proof="build/task-first-evidence/automatic-shot-review/$source_sha"
mkdir -p "$proof"
# Fresh hosted simulator only; no real app / credentials / hardware.
adb install mobile/build/outputs/apk/mock/mobile-mock.apk
adb install mobile/build/outputs/apk/androidTest/mock/mobile-mock-androidTest.apk
adb shell am force-stop io.openhoyi.mobile.mock
# 640 pixels at density 320 = a 320dp portrait viewport.
adb shell wm size 640x1120
adb shell wm density 320
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 0
# Defense in depth: the fixture itself injects no outbound transport.
adb shell cmd connectivity airplane-mode enable
adb shell svc wifi disable
adb shell svc data disable
timeout 120 adb shell am instrument -w -r \
  -e automaticShotReviewRoute true -e disposableCiEmulator true -e freshProcessVerified true \
  -e sourceSha "$source_sha" \
  io.openhoyi.mobile.mock.test/io.openhoyi.mobile.BrewAudioInstrumentation | tee "$proof/instrumentation.log"
grep -F 'AUTOMATIC_SHOT_REVIEW_ROUTE_SUCCESS' "$proof/instrumentation.log"
adb pull "/sdcard/Android/data/io.openhoyi.mobile.mock/files/automatic-shot-review/$source_sha/proof.json" "$proof/proof.json"
python3 - "$source_sha" "$proof/proof.json" <<'PY'
import json, pathlib, sys
proof = json.loads(pathlib.Path(sys.argv[2]).read_text())
assert proof["sourceSha"] == sys.argv[1]
assert proof["actualMockServiceCompletions"] == 3
assert proof["viewportWidthDp"] == 320 and proof["appFont"] == "large"
assert proof["fakeRequests"] == 2 and proof["realOutboundRequests"] == 0
assert all(proof[key] is True for key in ("actualMainActivityExtractionFragment",
    "distinctFirstVerdict", "noReuseOnNextCup", "missingKeySilent", "pendingAndFailureNonblocking",
    "onlyOpenedOnTap", "allThreeSlotsReadableWithoutTruncation",
    "actualLiveSlotDStart", "nativeStopTouchUnobstructed"))
PY
