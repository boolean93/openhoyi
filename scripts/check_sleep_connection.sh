#!/usr/bin/env bash
set -Eeuo pipefail
# Disposable CI emulator only; never changes a user's tablet or production APK.
[[ "${CI:-}" == true ]] || { echo 'CI emulator required' >&2; exit 2; }
serial=$(adb devices | awk '$2 == "device" {print $1}')
[[ "$serial" == emulator-* && "$serial" != *$'\n'* ]] || exit 2
out="$PWD/build/sleep-connection"
mkdir -p "$out"
trap 'adb -s "$serial" pull /sdcard/Android/data/io.openhoyi.mobile.mock/files/connection-sleep-presentation "$out/" || true' EXIT
adb -s "$serial" install -r mobile/build/outputs/apk/mock/mobile-mock.apk
adb -s "$serial" install -r mobile/build/outputs/apk/androidTest/mock/mobile-mock-androidTest.apk
adb -s "$serial" shell wm size 1600x1000
adb -s "$serial" shell wm density 200
python3 - "$serial" "$out" <<'PY'
import pathlib,subprocess,sys
serial,out=sys.argv[1:]
r=subprocess.run(['adb','-s',serial,'shell','am','instrument','-w','-r','-e','connectionSleepChecks','true','io.openhoyi.mobile.mock.test/io.openhoyi.mobile.BrewAudioInstrumentation'],capture_output=True,text=True,timeout=180)
text=r.stdout+r.stderr
pathlib.Path(out,'instrumentation.txt').write_text(text)
print(text)
assert r.returncode == 0 and 'CONNECTION_SLEEP_PRESENTATION_PASSED' in text and 'INSTRUMENTATION_CODE: -1' in text
PY
adb -s "$serial" pull /sdcard/Android/data/io.openhoyi.mobile.mock/files/connection-sleep-presentation "$out/"
