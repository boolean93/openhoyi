# Automatic shot review: dedicated Mock route check

This **QA-only branch** sits on feature commit `7dadffcf83c48cb469a7a555d96408b7c87fd12c`. The opt-in fake seam is not part of that feature or release branch. Use a fresh/disposable Android 34+ emulator on an approved hosted runner. Do not install this fixture on someone's tablet, use a real key, or attach hardware.

```sh
./gradlew :mobile:assembleMock :mobile:assembleMockAndroidTest --no-daemon --console=plain
test "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" = 1
adb install mobile/build/outputs/apk/mock/mobile-mock.apk
adb install mobile/build/outputs/apk/androidTest/mock/mobile-mock-androidTest.apk
adb shell am force-stop io.openhoyi.mobile.mock
adb shell wm size 640x1120
adb shell wm density 320
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 0
source_sha="$(git rev-parse HEAD)"
adb shell am instrument -w -r \
  -e automaticShotReviewRoute true \
  -e disposableCiEmulator true -e freshProcessVerified true \
  -e sourceSha "$source_sha" \
  io.openhoyi.mobile.mock.test/io.openhoyi.mobile.BrewAudioInstrumentation
adb pull "/sdcard/Android/data/io.openhoyi.mobile.mock/files/automatic-shot-review/$source_sha/" ./automatic-shot-review-evidence
```

For approved hosted runs, dispatch the existing extraction/history workflow with the isolated QA ref and `mode=automatic-shot-review`; this mode uses `scripts/check_automatic_shot_review_route.sh`, runs only the dedicated fixture, disables network on the disposable emulator, and publishes the machine-readable evidence folder. The branch must be present remotely before GitHub can run it; this is not a request to push or create a PR.

Require `AUTOMATIC_SHOT_REVIEW_ROUTE_SUCCESS` and match the proof JSON's exact test SHA. Report hosted Android execution separately from a local Kotlin compile. This launches the actual MainActivity + embedded ExtractionFragment, runs three real Mock Service completion flows with stored samples and asserts: first verdict opens only on tap and all three structured parts are readable without truncation on a 320dp viewport with the app's Large font; a real Live D selection starts the next cup without stale verdict, and native STOP touch reaches the active Mock Service; a simulated missing-key/disabled authorization causes no dispatch and no label; pending/failed fake response leaves the completed-extraction page and normal actions usable. Two synthetic requests never touch the real transport or credentials. This is not a hardware, production provider, or broad visual acceptance claim.
