#!/usr/bin/env bash
set -Eeuo pipefail

# Disposable CI emulator, isolated Mock only. Never launch Alpha or a device transport.
package=io.openhoyi.mobile.mock
output_dir="$PWD/build/advanced-mock-pages"
mkdir -p "$output_dir"
apk=mobile/build/outputs/apk/mock/mobile-mock.apk
test_apk=mobile/build/outputs/apk/androidTest/mock/mobile-mock-androidTest.apk
test -s "$apk"
test -s "$test_apk"
collect() {
  adb logcat -d -s AndroidRuntime:E OpenHoyiLanguage:I > "$output_dir/logcat.txt" || true
  adb shell dumpsys activity activities > "$output_dir/activities.txt" || true
  adb pull "/sdcard/Android/data/$package/files/advanced-page-screenshots" "$output_dir/" || true
}
on_error() {
  local status=$?
  trap - ERR
  collect
  adb shell settings put system font_scale 1.0 || true
  exit "$status"
}
trap on_error ERR
adb shell wm density 200
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 0
adb install -r "$apk"
adb install -r "$test_apk"
for profile in compact wideFont; do
  adb shell am force-stop "$package"
  if [ "$profile" = compact ]; then
    adb shell wm size 450x1000
    adb shell settings put system font_scale 1.0
  else
    adb shell wm size 1600x1000
    adb shell settings put system font_scale 1.3
  fi
  python3 - "$profile" "$output_dir/$profile.txt" <<'PY'
from pathlib import Path
import subprocess,sys
profile,output=sys.argv[1:]
with Path(output).open('w') as capture:
    try:
        result=subprocess.run(['adb','shell','am','instrument','-w','-e','advancedPageChecks',profile,
            'io.openhoyi.mobile.mock.test/io.openhoyi.mobile.BrewAudioInstrumentation'], stdout=capture, timeout=900)
    except subprocess.TimeoutExpired:
        raise SystemExit('Advanced Mock page matrix timed out; partial output retained')
if result.returncode:
    raise SystemExit(result.returncode)
expected=f'ADVANCED_PAGE_LAYOUT_CHECKS_PASSED profile={profile} languages=8 themes=2 pages=15 fixtures=240 preservedState=true noBle=true'
if expected not in Path(output).read_text():
    raise SystemExit(f'Advanced Mock page matrix did not confirm completion: {output}')
PY
  adb pull "/sdcard/Android/data/$package/files/advanced-page-screenshots/$profile" "$output_dir/$profile-screenshots"
  python3 - "$output_dir/$profile-screenshots" <<'PY'
from pathlib import Path
import struct,sys
paths=list(Path(sys.argv[1]).glob('*.png'))
if len(paths)!=60:
    raise SystemExit(f'Incomplete advanced page screenshots: {len(paths)}/60')
for path in paths:
    data=path.read_bytes()
    if len(data)<10000 or data[:8]!=b'\x89PNG\r\n\x1a\n':
        raise SystemExit(f'Invalid screenshot: {path}')
    width,height=struct.unpack('>II',data[16:24])
    if not width or not height:
        raise SystemExit(f'Empty screenshot geometry: {path}')
print(f'Advanced page screenshots validated: {len(paths)}')
PY
done
adb shell am force-stop "$package"
adb shell wm size 450x1000
adb shell settings put system font_scale 1.0
python3 - "$output_dir/business-flows.txt" <<'PY'
from pathlib import Path
import subprocess,sys
output=Path(sys.argv[1])
with output.open('w') as capture:
    try:
        result=subprocess.run(['adb','shell','am','instrument','-w','-e','advancedFlows','true',
            'io.openhoyi.mobile.mock.test/io.openhoyi.mobile.BrewAudioInstrumentation'],stdout=capture,timeout=600)
    except subprocess.TimeoutExpired:
        raise SystemExit('Advanced Mock business paths timed out; partial output retained')
if result.returncode:
    raise SystemExit(result.returncode)
expected='ADVANCED_BUSINESS_UI_CHECKS_PASSED paths=17 inventoryEvents=3 newDrafts=3 language=zh-Hans theme=light sameService=true preservedMachinePrefs=true noBle=true'
if expected not in output.read_text():
    raise SystemExit(f'Advanced Mock business paths did not confirm completion: {output}')
PY
collect
adb shell am force-stop "$package"
python3 - "$output_dir/curve-document-contracts.txt" <<'PY'
from pathlib import Path
import subprocess,sys
output=Path(sys.argv[1])
with output.open('w') as capture:
    try:
        result=subprocess.run(['adb','shell','am','instrument','-w','-e','documentContracts','true',
            'io.openhoyi.mobile.mock.test/io.openhoyi.mobile.BrewAudioInstrumentation'],stdout=capture,timeout=600)
    except subprocess.TimeoutExpired:
        raise SystemExit('Curve document contracts timed out; partial output retained')
if result.returncode:
    raise SystemExit(result.returncode)
expected='CURVE_DOCUMENT_CONTRACT_CHECKS_PASSED paths=11 pickerContracts=7 shareContracts=2 fileFixtures=4 newDrafts=1 realResolver=true signaturePermission=true systemPicker=false externalSend=false noBle=true'
if expected not in output.read_text():
    raise SystemExit(f'Curve document contracts did not confirm completion: {output}')
PY
collect
adb shell am force-stop "$package"
adb shell settings put system font_scale 1.0
python3 - "$output_dir/curve-system-documents.txt" <<'PY'
from pathlib import Path
import subprocess,sys
output=Path(sys.argv[1])
with output.open('w') as capture:
    try:
        result=subprocess.run(['adb','shell','am','instrument','-w','-e','systemDocuments','true',
            '-e','disposableCiEmulator','true',
            'io.openhoyi.mobile.mock.test/io.openhoyi.mobile.BrewAudioInstrumentation'],stdout=capture,timeout=600)
    except subprocess.TimeoutExpired:
        raise SystemExit('Actual system document picker timed out; partial output retained')
if result.returncode:
    raise SystemExit(result.returncode)
expected='CURVE_SYSTEM_DOCUMENT_CHECKS_PASSED paths=7 newDrafts=1 systemPicker=true externalSend=false noBle=true'
if expected not in output.read_text():
    raise SystemExit(f'Actual system document picker did not confirm completion: {output}')
PY
collect
adb shell am force-stop "$package"
adb shell wm size 1600x1000

# Run after non-execution checks so completed CI-owned shot evidence is not their baseline.
python3 - "$output_dir/custom-curve-execution.txt" <<'CHECK_PY'
from pathlib import Path
import subprocess,sys
output=Path(sys.argv[1])
with output.open('w') as capture:
    result=subprocess.run(['adb','shell','am','instrument','-w','-e','customExecution','true',
        '-e','disposableCiEmulator','true',
        'io.openhoyi.mobile.mock.test/io.openhoyi.mobile.BrewAudioInstrumentation'],stdout=capture,timeout=180)
if result.returncode:
    raise SystemExit(result.returncode)
expected='CUSTOM_CURVE_EXECUTION_CHECKS_PASSED import=true select=true confirm=true running=true ended=true history=true usage=true journal=true noBle=true'
if expected not in output.read_text() or 'SERVICE_CUSTOM_PRESSURE_DISPATCH_CHECKS_PASSED fixtures=39 fakeWrites=21 fakeBarriers=39 blocked=33 reloadedRecords=39 exactWire=true durableBeforeWrite=true lateSuccessIgnored=true noBle=true detached=true' not in output.read_text():
    raise SystemExit(f'Custom curve execution did not confirm completion: {output}')
CHECK_PY
collect
adb shell am force-stop "$package"
