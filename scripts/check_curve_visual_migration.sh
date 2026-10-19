#!/usr/bin/env bash
set -Eeuo pipefail
# M3 real-Activity evidence only, in its independently guarded disposable CI emulator.
[[ "${GITHUB_ACTIONS:-}" == true ]] || { echo 'Curve visual captures require GitHub CI' >&2; exit 1; }
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || { echo 'Disposable emulator required' >&2; exit 1; }
package=io.openhoyi.mobile.mock
source_sha=$(git rev-parse HEAD)
source_dirty=false
[[ -z "$(git status --porcelain)" ]] || source_dirty=true
output_dir="$PWD/build/task-first-evidence/curve-visual-migration"
mkdir -p "$output_dir"
apk_sha=$(python3 - <<'PY'
import hashlib
from pathlib import Path
print(hashlib.sha256(Path('mobile/build/outputs/apk/mock/mobile-mock.apk').read_bytes()).hexdigest())
PY
)
# Snapshot key presence AND value. `settings get` prints null for absence and cannot prove presence.
python3 - "$output_dir/font-scale-before.json" <<'FONT_BEFORE_PY'
import json, subprocess, sys
from pathlib import Path
result=subprocess.run(['adb','shell','settings','list','system'],capture_output=True,text=True,timeout=10,check=True)
entries=[line[len('font_scale='):] for line in result.stdout.replace('\r\n','\n').splitlines() if line.startswith('font_scale=')]
assert len(entries)<=1, 'Ambiguous font_scale snapshot'
Path(sys.argv[1]).write_text(json.dumps({'present':bool(entries),'value':entries[0] if entries else None},indent=2)+'\n')
FONT_BEFORE_PY
collect() {
  local exit_status=$1
  if (( exit_status != 0 )); then
    mkdir -p "$output_dir/raw-device" || true
    adb pull "/sdcard/Android/data/$package/files/curve-visual-migration/$source_sha" "$output_dir/raw-device/" || true
  fi
  # Only a persisted, acknowledged owner may receive one crash fallback stop.
  # Collector failure can fail success, never replace the original business failure.
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
  # Restore only this key, preserving absence; never put the literal "null" for an absent setting.
  if ! python3 - "$output_dir/font-scale-before.json" "$output_dir/font-scale-restore.json" "$exit_status" <<'FONT_RESTORE_PY'
import json, subprocess, sys, traceback
from pathlib import Path
before=json.loads(Path(sys.argv[1]).read_text())
proof={'before':before,'originalExitStatus':int(sys.argv[3])}
try:
    command=['adb','shell','settings']+(['put','system','font_scale',before['value']] if before['present'] else ['delete','system','font_scale'])
    proof['restoreOperation']='put_original_value' if before['present'] else 'delete_originally_absent_key'
    restored=subprocess.run(command,capture_output=True,text=True,timeout=10,check=True)
    proof['restoreReturnCode']=restored.returncode
    result=subprocess.run(['adb','shell','settings','list','system'],capture_output=True,text=True,timeout=10,check=True)
    entries=[line[len('font_scale='):] for line in result.stdout.replace('\r\n','\n').splitlines() if line.startswith('font_scale=')]
    assert len(entries)<=1, 'Ambiguous restored font_scale'
    after={'present':bool(entries),'value':entries[0] if entries else None}
    proof['after']=after
    assert after==before, 'font_scale presence/value not restored'
    proof['status']='PASS_SETTING_RESTORE_ONLY'
except Exception:
    proof['status']='FAIL'; proof['failure']=traceback.format_exc()
    raise
finally:
    Path(sys.argv[2]).write_text(json.dumps(proof,indent=2)+'\n')
FONT_RESTORE_PY
  then
    # Cleanup failure can fail a previously successful run, but cannot replace its original nonzero failure.
    if (( exit_status == 0 )); then exit_status=1; fi
  fi
  return "$exit_status"
}
trap 'original_exit=$?; trap - EXIT; if collect "$original_exit"; then exit 0; else exit "$?"; fi' EXIT
# Actual capability probe only on this guarded CI emulator. No support is assumed from an SDK/source lookup.
python3 scripts/collect_ime_recreation_trace.py probe --directory "$output_dir/ime-native-capability"
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
    for curve_page in library import preview; do
      adb shell am force-stop "$package"
      python3 - "$output_dir/$product_theme-$font_scale-$curve_page-result.txt" "$source_sha" "$source_dirty" "$apk_sha" "$product_theme" "$font_scale" "$curve_page" <<'RUN_PY'
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
for key,value in {'curveVisualMigration':'true','disposableCiEmulator':'true','freshProcessVerified':'true',
    'curvePage':page,'sourceSha':source,'headSha':os.environ.get('PRODUCT_PR_HEAD_SHA',source),
    'sourceDirty':dirty,'apkSha':apk,'sampleTheme':theme,'expectedFontScale':scale,
    'ciRun':os.environ['GITHUB_RUN_ID'],'ciJob':os.environ['GITHUB_JOB']}.items():
    command += ['-e',key,value]
command += ['io.openhoyi.mobile.mock.test/io.openhoyi.mobile.BrewAudioInstrumentation']
native_args=None
if page=='import':
    root=Path(result_path).parent
    native_dir=root/'ime-native-traces'/f'{theme}-{scale}-import'
    device=f'/sdcard/Android/data/io.openhoyi.mobile.mock/files/curve-visual-migration/{source}/tablet-user-landscape-{theme}-font-{scale}/import/ime-recreation-native'
    native_args=['python3','scripts/collect_ime_recreation_trace.py','after','--directory',str(native_dir),
        '--device-directory',device,'--probe-directory',str(root/'ime-native-capability')]
    before=subprocess.run(['python3','scripts/collect_ime_recreation_trace.py','before','--directory',str(native_dir)],timeout=90)
    if before.returncode: raise SystemExit('Native trace before-file observation failed; no trace started')
    (native_dir/'fixture-context.json').write_text(json.dumps({'sourceSha':source,'headSha':os.environ.get('PRODUCT_PR_HEAD_SHA',source),
        'sourceDirty':dirty,'apkSha256':apk,'profile':f'tablet-user-landscape-{theme}-font-{scale}','fixture':'Import.raw.recreate',
        'ciRun':os.environ['GITHUB_RUN_ID'],'ciJob':os.environ['GITHUB_JOB']},indent=2)+'\n')
    (root/'ime-active-profile.json').write_text(json.dumps({'directory':str(native_dir),'deviceDirectory':device},indent=2)+'\n')
trace_return_code=0
try:
    with Path(result_path).open('w') as capture:
        result = subprocess.run(command, stdout=capture, stderr=subprocess.STDOUT, timeout=600)
    text = Path(result_path).read_text()
    if result.returncode or 'CURVE_VISUAL_MIGRATION_CAPTURED fixtures=1 realActivities=true visualAcceptance=ASTRA_PENDING noBle=true isolationVerified=true' not in text:
        print(text)
        raise SystemExit('Real product capture failed; partial evidence retained, no automatic reset/retry')
finally:
    if native_args is not None:
        try:
            traced=subprocess.run(native_args,timeout=120)
            trace_return_code=traced.returncode
        except Exception as diagnostic_error:
            trace_return_code=1
            Path(result_path).with_suffix('.ime-collector-error.json').write_text(json.dumps({'diagnosticError':repr(diagnostic_error),
                'originalBusinessFailureMustKeepPrecedence':True},indent=2)+'\n')
if trace_return_code:
    raise SystemExit('Original business fixture completed but native trace diagnostic is partial/unavailable; original assertions unchanged')
RUN_PY
    done
  done
done
adb pull "/sdcard/Android/data/$package/files/curve-visual-migration/$source_sha" "$output_dir/"
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
    assert m['overallStatus']=='PASS_MECHANICAL_ONLY_NOT_VISUAL_ACCEPTANCE' and m['isolationStatus']=='PASS_ISOLATION_ONLY'
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
    proof_files=list(folder.rglob('*-frame-sync.json'))
    assert proof_files, path
    for proof_file in proof_files:
        proof=json.loads(proof_file.read_text())
        assert proof['protocolVersion']==2 and proof['accepted'], proof_file
    business=json.loads((folder/'curve-business.json').read_text())
    assert business['completed'] and business['noMachineOrServiceCommands'], path
    assert (folder/'geometry.json').is_file()

assert seen=={(page,f'tablet-user-landscape-{theme}-font-{scale}') for page in ('library','import','preview') for theme in ('light','dark') for scale in ('1.0','1.3')}
(root/'capture-summary.json').write_text(json.dumps({'realConfigurations':12,'sourceSha':root.name,
    'visualAcceptance':'ASTRA_PENDING','specimenImagesUsedAsProductEvidence':False},indent=2)+'\n')
print('CURVE_VISUAL_MIGRATION_RETAINED configurations=12 actualActivities=true visualAcceptance=ASTRA_PENDING')
VERIFY_PY
