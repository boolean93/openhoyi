#!/usr/bin/env bash
set -Eeuo pipefail
# Disposable CI emulator only: never install on or mutate the user's tablet.
[[ "${CI:-}" == true ]] || { echo 'CI emulator required' >&2; exit 2; }
serial=$(adb devices | awk '$2 == "device" {print $1}')
[[ "$serial" == emulator-* && "$serial" != *$'\n'* ]] || exit 2
out="$PWD/build/download-routing"
mkdir -p "$out"
adb -s "$serial" install -r mobile/build/outputs/apk/mock/mobile-mock.apk
adb -s "$serial" install -r mobile/build/outputs/apk/androidTest/mock/mobile-mock-androidTest.apk
python3 - "$serial" "$out" <<'PY'
import pathlib,subprocess,sys
serial,out=sys.argv[1:]
r=subprocess.run(['adb','-s',serial,'shell','am','instrument','-w','-r','-e','downloadRoutingChecks','true','io.openhoyi.mobile.mock.test/io.openhoyi.mobile.BrewAudioInstrumentation'],capture_output=True,text=True,timeout=180)
text=r.stdout+r.stderr
pathlib.Path(out,'instrumentation.txt').write_text(text)
print(text)
assert r.returncode == 0 and 'DOWNLOAD_ROUTING_PASSED' in text and 'INSTRUMENTATION_CODE: -1' in text
PY
