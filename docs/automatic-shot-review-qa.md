# Automatic shot review: dedicated Mock route check

This **QA-only branch** sits on feature commit `01155d26cf5a216c52f7116ada7c22f7aa1bc2e1`. The opt-in fake seam is not part of that feature or release branch. Use a fresh/disposable Android 34+ emulator on an approved hosted runner. Do not install this fixture on someone's tablet, use a real key, or attach hardware.

```sh
./gradlew :mobile:assembleMock :mobile:assembleMockAndroidTest --no-daemon --console=plain
test "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" = 1
adb install mobile/build/outputs/apk/mock/mobile-mock.apk
adb install mobile/build/outputs/apk/androidTest/mock/mobile-mock-androidTest.apk
adb shell am force-stop io.openhoyi.mobile.mock
source_sha="$(git rev-parse HEAD)"
adb shell am instrument -w -r \
  -e automaticShotReviewRoute true \
  -e disposableCiEmulator true -e freshProcessVerified true \
  -e sourceSha "$source_sha" \
  io.openhoyi.mobile.mock.test/io.openhoyi.mobile.BrewAudioInstrumentation
adb pull "/sdcard/Android/data/io.openhoyi.mobile.mock/files/automatic-shot-review/$source_sha/" ./automatic-shot-review-evidence
```

For approved hosted runs, dispatch the existing extraction/history workflow with the isolated QA ref and `mode=automatic-shot-review`; this mode uses `scripts/check_automatic_shot_review_route.sh`, runs only the dedicated fixture, disables network on the disposable emulator, and publishes the machine-readable evidence folder. The branch must be present remotely before GitHub can run it; this is not a request to push or create a PR.

Require `AUTOMATIC_SHOT_REVIEW_ROUTE_SUCCESS` and match the proof JSON's exact test SHA. Report hosted Android execution separately from a local Kotlin compile. This launches the actual MainActivity + embedded ExtractionFragment, runs three real Mock Service completion flows with stored samples and asserts: first verdict shown on its cup only; next running cup hides it; a simulated missing-key/disabled authorization causes no dispatch and no label; pending/failed fake response leaves the completed-extraction page and normal actions usable. Two synthetic requests never touch the real transport or credentials. This is not a hardware, production provider, or broad visual acceptance claim.
