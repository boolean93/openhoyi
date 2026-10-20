#!/usr/bin/env bash
set -Eeuo pipefail
# Additional M2 real-form evidence only, in the existing disposable CI emulator.
[[ "${GITHUB_ACTIONS:-}" == true ]] || { echo 'Form visual captures require GitHub CI' >&2; exit 1; }
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || { echo 'Disposable emulator required' >&2; exit 1; }
package=io.openhoyi.mobile.mock
source_sha=$(git rev-parse HEAD)
source_dirty=false
[[ -z "$(git status --porcelain)" ]] || source_dirty=true
output_dir="$PWD/build/task-first-evidence/form-visual-migration"
mkdir -p "$output_dir"
apk_sha=$(python3 - <<'PY'
import hashlib
from pathlib import Path
print(hashlib.sha256(Path('mobile/build/outputs/apk/mock/mobile-mock.apk').read_bytes()).hexdigest())
PY
)
original_font_scale=$(adb shell settings get system font_scale | tr -d '\r')
collect() {
  local exit_status=$1
  if (( exit_status != 0 )); then
    mkdir -p "$output_dir/raw-device" || true
    adb pull "/sdcard/Android/data/$package/files/form-visual-migration/$source_sha" "$output_dir/raw-device/" || true
  fi
  if [[ -f "$output_dir/ime-active-profile.json" ]]; then
    if ! python3 - "$output_dir" <<'IME_EXIT_PY'
import json,subprocess,sys
from pathlib import Path
root=Path(sys.argv[1]);active=json.loads((root/'ime-active-profile.json').read_text())
result=subprocess.run(['python3','scripts/collect_ime_recreation_trace.py','after','--directory',active['directory'],
    '--device-directory',active['deviceDirectory'],'--probe-directory',str(root/'ime-native-capability')],timeout=120)
raise SystemExit(result.returncode)
IME_EXIT_PY
    then if (( exit_status == 0 )); then exit_status=1; fi; fi
  fi
  adb logcat -d -s AndroidRuntime:E > "$output_dir/runtime-errors.txt" || true
  adb logcat -d -v threadtime -s ImeTracker:I InputMethodManager:D InsetsController:D > "$output_dir/ime-diagnostics.txt" || true
  adb shell dumpsys input_method > "$output_dir/input-method.txt" || true
  adb shell dumpsys activity activities > "$output_dir/activities.txt" || true
  adb shell am force-stop "$package" || true
  adb shell settings put system font_scale "$original_font_scale" || true
  return "$exit_status"
}
trap 'collect "$?"' EXIT
adb shell wm size 2560x1600
adb shell wm density 360
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 0
# APKs were built by the workflow. Installation is confined to this guarded emulator.
adb install -r mobile/build/outputs/apk/mock/mobile-mock.apk
adb install -r mobile/build/outputs/apk/androidTest/mock/mobile-mock-androidTest.apk
for product_theme in light dark; do
  if [[ "$product_theme" == dark ]]; then adb shell cmd uimode night yes; else adb shell cmd uimode night no; fi
  for font_scale in 1.0 1.3; do
    adb shell settings put system font_scale "$font_scale"
    for form_page in curve-editor bean-entry review; do
      adb shell am force-stop "$package"
      python3 - "$output_dir/$product_theme-$font_scale-$form_page-result.txt" "$source_sha" "$source_dirty" "$apk_sha" "$product_theme" "$font_scale" "$form_page" <<'RUN_PY'
from pathlib import Path
import json, os, subprocess, sys
result_path, source, dirty, apk, theme, scale, page = sys.argv[1:]
def fresh_process_absent(probe):
    # Exact remote marker proves pidof actually ran. Unknown status, output, stderr or transport failure is not absence.
    return probe.returncode == 0 and probe.stdout.replace('\r\n','\n') == 'FRESH_PIDOF_STATUS=1\nFRESH_PIDOF_OUTPUT=\n' and probe.stderr == ''
probe = subprocess.run(['adb','shell','sh'], input=r"""
command -v pidof >/dev/null 2>&1 || exit 70
fresh_process_pid_output=$(pidof io.openhoyi.mobile.mock 2>&1)
fresh_process_pid_status=$?
printf 'FRESH_PIDOF_STATUS=%s\n' "$fresh_process_pid_status"
printf 'FRESH_PIDOF_OUTPUT=%s\n' "$fresh_process_pid_output"
""", stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=10)
verified = fresh_process_absent(probe)
Path(result_path).with_suffix('.fresh-process.json').write_text(json.dumps({
    'remoteCommand':'pidof io.openhoyi.mobile.mock', 'adbReturnCode':probe.returncode,
    'stdout':probe.stdout, 'stderr':probe.stderr, 'freshProcessVerified':verified,
    'policy':'exact_completed_remote_pidof_exit1_empty_output_only; all_unknowns_fail_closed'},indent=2)+'\n')
if not verified:
    raise SystemExit('Fresh-process guard failed or unknown; no fixture delegate installed')
command = ['adb','shell','am','instrument','-w']
for key,value in {'formVisualMigration':'true','disposableCiEmulator':'true','freshProcessVerified':'true',
    'formPage':page,'sourceSha':source,'headSha':os.environ.get('PRODUCT_PR_HEAD_SHA',source),
    'sourceDirty':dirty,'apkSha':apk,'sampleTheme':theme,'expectedFontScale':scale,
    'ciRun':os.environ['GITHUB_RUN_ID'],'ciJob':os.environ['GITHUB_JOB']}.items():
    command += ['-e',key,value]
command += ['io.openhoyi.mobile.mock.test/io.openhoyi.mobile.BrewAudioInstrumentation']
native_args=None
if page=='curve-editor' and theme=='light' and scale=='1.0':
    root=Path(result_path).parent
    native_dir=root/'ime-native-traces'/'light-1.0-curve-editor'
    device=f'/sdcard/Android/data/io.openhoyi.mobile.mock/files/form-visual-migration/{source}/tablet-user-landscape-light-font-1.0/curve-editor/ime-recreation-native'
    subprocess.run(['python3','scripts/collect_ime_recreation_trace.py','probe','--directory',str(root/'ime-native-capability')],timeout=90)
    native_args=['python3','scripts/collect_ime_recreation_trace.py','after','--directory',str(native_dir),
        '--device-directory',device,'--probe-directory',str(root/'ime-native-capability')]
    before=subprocess.run(['python3','scripts/collect_ime_recreation_trace.py','before','--directory',str(native_dir)],timeout=90)
    if before.returncode: raise SystemExit('M2 native trace before-file observation failed; no trace started')
    (native_dir/'fixture-context.json').write_text(json.dumps({'sourceSha':source,'headSha':os.environ.get('PRODUCT_PR_HEAD_SHA',source),
        'sourceDirty':dirty,'apkSha256':apk,'profile':'tablet-user-landscape-light-font-1.0',
        'fixture':'CurveEditor.editor-stage-hidden-values.recreate','ciRun':os.environ['GITHUB_RUN_ID'],'ciJob':os.environ['GITHUB_JOB']},indent=2)+'\n')
    (root/'ime-active-profile.json').write_text(json.dumps({'directory':str(native_dir),'deviceDirectory':device},indent=2)+'\n')
trace_return_code=0
try:
    with Path(result_path).open('w') as capture:
        result = subprocess.run(command, stdout=capture, stderr=subprocess.STDOUT, timeout=600)
    text = Path(result_path).read_text()
    if result.returncode or 'FORM_VISUAL_MIGRATION_CAPTURED fixtures=1 realActivities=true visualAcceptance=ASTRA_PENDING noBle=true isolationVerified=true' not in text:
        print(text)
        raise SystemExit('Real product capture failed; partial evidence retained, no automatic reset/retry')
finally:
    if native_args is not None:
        try:
            traced=subprocess.run(native_args,timeout=120)
            trace_return_code=traced.returncode
        except Exception as diagnostic_error:
            trace_return_code=1
            try:
                Path(result_path).with_suffix('.ime-collector-error.json').write_text(json.dumps({'diagnosticError':repr(diagnostic_error),
                    'originalBusinessFailureMustKeepPrecedence':True},indent=2)+'\n')
            except Exception as evidence_error:
                print(f'M2 IME collector evidence write failed: {evidence_error!r}',file=sys.stderr)
if trace_return_code:
    raise SystemExit('Original M2 business fixture completed but native trace diagnostic is partial/unavailable; original assertions unchanged')
RUN_PY
    done
  done
done
adb pull "/sdcard/Android/data/$package/files/form-visual-migration/$source_sha" "$output_dir/"
python3 - "$output_dir/$source_sha" <<'VERIFY_PY'
from pathlib import Path
import hashlib, json, struct, sys
root = Path(sys.argv[1])
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
manifests = sorted(root.glob('*/*/manifest.json'))
assert len(manifests)==12, f'Expected 12 real Activity configurations, got {len(manifests)}'
seen=set()
for path in manifests:
    m=json.loads(path.read_text()); folder=path.parent
    assert m['sourceSha']==root.name and m['realProductActivity'] and not m['specimenOnly']
    assert m['visualAcceptance']=='ASTRA_PENDING' and m['layoutStatus']!='FAIL'
    key=(m['page'],m['profile']); assert key not in seen; seen.add(key)
    assert m['fontSha256']=='a3041811a78c361b1de50f953c805e0244951c21c5bd412f7232ef0d899af0da'
    assert tuple(m['wholeWindowPngSizePx'])==struct.unpack('>II',(folder/'window.png').read_bytes()[16:24])==(2560,1600)
    assert m['window']['densityDpi']==360 and m['window']['locale'].startswith('zh')
    assert m['window']['uiModeNightMeaning']==m['profile'].split('-font-')[0].rsplit('-',1)[-1]
    assert struct.pack('>f',m['window']['fontScale'])==struct.pack('>f',float(m['profile'].rsplit('-font-',1)[-1]))  # exact Float32, no tolerance
    assert m['window']['imeVisible'] is False
    assert m['initialFrameSync']['protocolVersion']==2 and m['initialFrameSync']['accepted']
    assert (folder/'window-frame-sync.json').is_file()
    for name,value in m['fileSha256'].items(): assert sha(folder/name)==value, (path,name)
    isolation=json.loads((folder/'fixture-isolation.json').read_text())
    assert isolation['status']=='PASS_ISOLATION_ONLY' and isolation['businessDiskByteUnchanged'] and isolation['typedPreferencesRestored']
    for proof_file in folder.rglob('*-frame-sync.json'):
        proof=json.loads(proof_file.read_text())
        assert proof['protocolVersion']==2 and proof['accepted'], proof_file
    business=json.loads((folder/'form-business.json').read_text())
    assert business['completed'] and business['noMachineOrServiceCommands'], path
    assert (folder/'geometry.json').is_file()

assert seen=={(page,f'tablet-user-landscape-{theme}-font-{scale}') for page in ('bean-entry','review','curve-editor') for theme in ('light','dark') for scale in ('1.0','1.3')}
(root/'capture-summary.json').write_text(json.dumps({'realConfigurations':12,'sourceSha':root.name,
    'visualAcceptance':'ASTRA_PENDING','specimenImagesUsedAsProductEvidence':False},indent=2)+'\n')
print('FORM_VISUAL_MIGRATION_RETAINED configurations=12 actualActivities=true visualAcceptance=ASTRA_PENDING')
VERIFY_PY
