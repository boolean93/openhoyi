#!/usr/bin/env bash
set -Eeuo pipefail
# M6 real-Activity evidence only, in its independently guarded disposable CI emulator.
[[ "${GITHUB_ACTIONS:-}" == true ]] || { echo 'Scale visual captures require GitHub CI' >&2; exit 1; }
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || { echo 'Disposable emulator required' >&2; exit 1; }
package=io.openhoyi.mobile.mock
source_sha=$(git rev-parse HEAD)
source_dirty=false
[[ -z "$(git status --porcelain)" ]] || source_dirty=true
output_dir="$PWD/build/task-first-evidence/scale-visual-migration"
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
    adb pull "/sdcard/Android/data/$package/files/scale-visual-migration/$source_sha" "$output_dir/raw-device/" || true
  fi
  adb logcat -d -s AndroidRuntime:E > "$output_dir/runtime-errors.txt" || true
  adb logcat -d -v threadtime -s ImeTracker:I InputMethodManager:D InsetsController:D > "$output_dir/ime-diagnostics.txt" || true
  adb shell dumpsys input_method > "$output_dir/input-method.txt" || true
  adb shell dumpsys activity activities > "$output_dir/activities.txt" || true
  adb shell am get-config > "$output_dir/font-scale-global-config-before-restore.txt" || true
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
  adb shell am get-config > "$output_dir/font-scale-global-config-after-restore.txt" || true
  return "$exit_status"
}
trap 'original_exit=$?; trap - EXIT; if collect "$original_exit"; then exit 0; else exit "$?"; fi' EXIT
adb shell wm size 2560x1600
adb shell wm density 360
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 0
# APKs were built by the workflow. Installation is confined to this guarded emulator.
adb install -r mobile/build/outputs/apk/mock/mobile-mock.apk
adb install -r mobile/build/outputs/apk/androidTest/mock/mobile-mock-androidTest.apk
for product_theme in light dark; do
  if [[ "$product_theme" == dark ]]; then adb shell cmd uimode night yes; else adb shell cmd uimode night no; fi
  # End with two real 1.0 pages; this removes delete's explicit 1.3 -> default-1.0 transition,
  # not a persistence-queue ACK. Strict key presence/value restoration below remains mandatory.
  for font_scale in 1.3 1.0; do
    adb shell settings put system font_scale "$font_scale"
    for scale_page in scale connection; do
      adb shell am force-stop "$package"
      python3 - "$output_dir/$product_theme-$font_scale-$scale_page-result.txt" "$source_sha" "$source_dirty" "$apk_sha" "$product_theme" "$font_scale" "$scale_page" <<'RUN_PY'
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
references={
 'scale':('scale-v2.png','d33ae2f0348cd0464b992196a5e1b1c855b3bdce7232f211755e19d4b693bfaf'),
 'connection':('scale-connection-v1.png','21cba381b9c7025aa138458d37d0c0f0f3e0a9fb277bff943ea3cbcec1edc931')}
import hashlib
reference_name,reference_sha=references[page]
assert hashlib.sha256((Path('docs/research/task-first-ui-concepts')/reference_name).read_bytes()).hexdigest()==reference_sha
command = ['adb','shell','am','instrument','-w']
for key,value in {'scaleVisualMigration':'true','disposableCiEmulator':'true','freshProcessVerified':'true',
    'scalePage':page,'referencePngSha256':reference_sha,'sourceSha':source,'headSha':os.environ.get('PRODUCT_PR_HEAD_SHA',source),
    'sourceDirty':dirty,'apkSha':apk,'sampleTheme':theme,'expectedFontScale':scale,
    'ciRun':os.environ['GITHUB_RUN_ID'],'ciJob':os.environ['GITHUB_JOB']}.items():
    command += ['-e',key,value]
command += ['io.openhoyi.mobile.mock.test/io.openhoyi.mobile.BrewAudioInstrumentation']
with Path(result_path).open('w') as capture:
    result = subprocess.run(command, stdout=capture, stderr=subprocess.STDOUT, timeout=600)
text = Path(result_path).read_text()
if result.returncode or 'SCALE_VISUAL_MIGRATION_CAPTURED fixtures=1 realActivities=true visualAcceptance=ASTRA_PENDING noBle=true isolationVerified=true' not in text:
    print(text)
    raise SystemExit('Real product capture failed; partial evidence retained, no automatic reset/retry')
RUN_PY
    done
  done
done
adb pull "/sdcard/Android/data/$package/files/scale-visual-migration/$source_sha" "$output_dir/"
python3 - "$output_dir/$source_sha" <<'VERIFY_PY'
from pathlib import Path
import hashlib,json,struct,sys
sys.path.insert(0,str(Path('scripts').resolve()))
from window_mapping_contract import validate, DEFAULT_ACTIVITY, DEFAULT_WINDOW, TRANSLATED_WINDOW, SHORT_ACTIVITY
root=Path(sys.argv[1]); sha=lambda path:hashlib.sha256(path.read_bytes()).hexdigest()
manifests=sorted(root.glob('*/*/manifest.json'))
assert len(manifests)==8, f'Expected 8 real M6 configurations, got {len(manifests)}'
seen=set(); pending=[]
def require_protocol(path, kind=None):
    assert path.is_file(), ('Missing expected protocol2 proof',str(path))
    proof=json.loads(path.read_text())
    assert proof['protocolVersion']==2 and proof['accepted'] and proof['timeoutMs']==5000 and proof['attempts'],path
    assert proof['pixelComparison']=='each_equivalent_domain_all_app_pixels_no_tolerance_no_mask'
    assert proof['crossDomainComparison']=='DIAGNOSTIC_ONLY_PIPELINES_NOT_EQUIVALENT'
    assert proof['displayId']==0 and proof['windowIdentity'] and proof['decorIdentity'] and proof['windowTokenIdentity']
    if kind is not None: assert proof['captureWindowKind']==kind,(path,proof['captureWindowKind'],kind)
    attempt=proof['attempts'][proof['acceptedAttempt']-1]
    assert attempt['accepted'] and attempt['withinMatchingBudget'] and attempt['geometryUnchanged'] and attempt['windowReadyAtBothEnds']
    assert attempt['windowDomainColorSpacesEqual'] and attempt['displayDomainColorSpacesEqual']
    assert attempt['windowLayerMatch'] and attempt['displayStable']
    for key in ('S1_W1','S1_W2','S1_S2','D1_D2_app'):assert attempt[key]['count']==0,(path,key)
    if path.parent.name=='short-height': expected=SHORT_ACTIVITY
    elif path.parent.name in ('trend-main-data','short-height-trend-detail') and proof['captureWindowKind']=='EXPLICIT_WINDOW': expected=TRANSLATED_WINDOW
    else: expected=DEFAULT_WINDOW if proof['captureWindowKind']=='EXPLICIT_WINDOW' else DEFAULT_ACTIVITY
    validate(proof,attempt,expected)
    artifacts=proof['captureArtifacts']
    expected_artifacts={'display','display-after','layer-before','layer-after'} if expected==TRANSLATED_WINDOW else {'display','display-after'}
    assert {item['role'] for item in artifacts}==expected_artifacts
    for item in artifacts:
        image=path.parent/item['file'];assert image.is_file() and sha(image)==item['fileSha256']
        if item['role'] in ('layer-before','layer-after'):
            assert item['bitmap']['sizePx']==attempt[item['role']]['sizePx']
    return proof

def require_rejected_protocol(path, reason):
    assert path.is_file(),('Missing expected real Window negative proof',str(path))
    proof=json.loads(path.read_text())
    assert proof['protocolVersion']==2 and not proof['accepted'] and proof['timeoutMs']==5000
    assert proof['captureWindowKind']=='EXPLICIT_WINDOW' and proof['mappingMode']=='EXPLICIT_WINDOW_MEASURED_DECOR'
    assert reason in proof['failure'] and proof['attempts']
    assert proof['windowIdentity'] and proof['decorIdentity'] and proof['windowTokenIdentity']
    # These two cases are rejected before any pixel acquisition. They cannot stand in for a positive capture.
    assert proof['captureArtifacts']==[] and all('surface-beforeCode' not in a for a in proof['attempts'])
    return proof

for path in manifests:
    m=json.loads(path.read_text()); folder=path.parent
    assert m['sourceSha']==root.name and m['realProductActivity'] and not m['specimenOnly']
    assert m['visualAcceptance']=='ASTRA_PENDING' and m['layoutStatus']=='PASS_MECHANICAL_ONLY_NOT_VISUAL_ACCEPTANCE'
    assert m['overallStatus']=='PASS_MECHANICAL_ONLY_NOT_VISUAL_ACCEPTANCE' and m['isolationStatus']=='PASS_ISOLATION_ONLY'
    key=(m['page'],m['profile']); assert key not in seen;seen.add(key)
    assert m['fontSha256']=='a3041811a78c361b1de50f953c805e0244951c21c5bd412f7232ef0d899af0da'
    assert tuple(m['wholeWindowPngSizePx'])==struct.unpack('>II',(folder/'window.png').read_bytes()[16:24])==(2560,1600)
    assert m['window']['densityDpi']==360 and m['window']['locale'].startswith('zh') and not m['window']['imeVisible']
    assert m['window']['uiModeNightMeaning']==m['profile'].split('-font-')[0].rsplit('-',1)[-1]
    assert struct.pack('>f',m['window']['fontScale'])==struct.pack('>f',float(m['profile'].rsplit('-font-',1)[-1]))
    assert m['initialFrameSync']['protocolVersion']==2 and m['initialFrameSync']['accepted']
    for name,value in m['fileSha256'].items(): assert sha(folder/name)==value,(path,name)
    require_protocol(folder/'window-frame-sync.json','ACTIVITY')
    negative_folders=[]
    if m['page']=='scale':
        negative_folders.append(folder/'special-states/short-height-trend-detail/window-negative-cases')
        if not m['window']['productLayoutEvidence']['trendInline']:
            negative_folders.append(folder/'trend-main-data/window-negative-cases')
    rejected={}
    for negative in negative_folders:
        negative_manifest=json.loads((negative/'manifest.json').read_text())
        assert negative_manifest['mainConfigurationCountContribution']==0 and negative_manifest['positiveWindowCaptureStillRequired']
        assert not negative_manifest['bitmapOrSurfaceInsetsInjection']
        assert {case['case'] for case in negative_manifest['cases']}=={'wrong-window-root','unfocused-owner'}
        for name,reason in {'wrong-window-root':'Wrong Window/root/token/display owner',
            'unfocused-owner':'not a ready focused attached surface'}.items():
            rejected[negative/name/'window-frame-sync.json']=reason
            require_rejected_protocol(negative/name/'window-frame-sync.json',reason)
    assert set(folder.rglob('window-negative-cases/*/window-frame-sync.json'))==set(rejected)
    for proof in folder.rglob('*-frame-sync.json'):
        if proof in rejected:require_rejected_protocol(proof,rejected[proof])
        else:require_protocol(proof)
    isolation=json.loads((folder/'fixture-isolation.json').read_text())
    for key in ('businessDiskByteUnchanged','typedPreferencesRestored','originalLazyIdentityAndInitializedRestored','legacyUnchanged',
        'allActivitiesDestroyed','noServiceBeforeOriginalDelegateRestore','scopedWritersTerminated','serviceOwnerCallbacksRemoved',
        'serviceHubClosed','serviceHandlersDrained','scaleOwnersUnboundAndRefreshRemoved','originalVisibilityObserversPreserved',
        'inputClearedAfterServiceAndWritersBeforeClock','mockClockRemovedAfterAllOwnersDrained','serviceOriginalLazyIdentityAndInitializedRestored'):
        assert isolation[key] is True,(path,key)
    assert isolation['status']=='PASS_ISOLATION_ONLY' and isolation['ownedAsyncReaderCount']==0
    guards=json.loads((folder/'service-guards.json').read_text())
    assert guards['positiveMockMainClockOwnerHubNull'] and guards['duplicateNotificationDelta']==2 and guards['duplicateTrendPointDelta']==0
    assert len(guards['cases'])==16 and all(case['notificationDelta']==0 for case in guards['cases'])
    special=json.loads((folder/'special-states/manifest.json').read_text())
    assert special['completed'] and special['mainConfigurationCountContribution']==0 and special['visualAcceptance']=='ASTRA_PENDING'
    fixture=json.loads((folder/'fixture.json').read_text())
    assert fixture['defaultUninstalledRuntimeObserved'] and fixture['synthetic'] and not fixture['hardwareMeasurement']
    if m['page']=='scale':
        assert fixture['explicitMainSamples']==[-125,200,800,1200,1600,1750,1800,1801,1799,1800,1800]
        assert fixture['mainSampleTimes']==list(range(114600,124601,1000))
        assert fixture['actualPausedTimerMs']==24600 and fixture['actualNotifyDelta']==11 and fixture['tickNotifyDelta']==0
        required={'one-decimal','paused-recreated','stale-1501','expired-10001','ready-null','disconnected','negative-offline',
            'gap-and-evidence-change','battery-capability-82','battery-unavailable','weight-capability-false','mock-capture-disabled','short-height','short-height-trend-detail','no-service'}
        assert {case['case'] for case in special['cases']}==required
        for state in required:
            require_protocol(folder/'special-states'/state/'window-frame-sync.json',
                'EXPLICIT_WINDOW' if state=='short-height-trend-detail' else 'ACTIVITY')
        inline=m['window']['productLayoutEvidence']['trendInline']
        trend=require_protocol(folder/'trend-main-data/window-frame-sync.json','ACTIVITY' if inline else 'EXPLICIT_WINDOW')
        if float(m['profile'].rsplit('-font-',1)[-1])==1.0:assert inline
        if not inline:
            target=json.loads((folder/'trend-main-data/geometry.json').read_text())
            assert target['observationScope']=='only_explicit_target_window_decor_subtree' and len(target['charts'])==1
            returned=json.loads((folder/'trend-main-data/dialog-return-state.json').read_text())
            assert returned['exactBufferTimerAndScrollRestored'] and returned['activityFocusRestored'] and returned['before']==returned['after']
        target=json.loads((folder/'special-states/short-height-trend-detail/geometry.json').read_text())
        assert target['observationScope']=='only_explicit_target_window_decor_subtree' and len(target['charts'])==1
        returned=json.loads((folder/'special-states/short-height-trend-detail/dialog-return-state.json').read_text())
        assert returned['exactBufferTimerAndScrollRestored'] and returned['activityFocusRestored'] and returned['before']==returned['after']
        for i in range(1,7):require_protocol(folder/f'after-scroll-{i}/window-frame-sync.json','ACTIVITY')
        for case in special['cases']:
            if case['case'] in ('short-height','short-height-trend-detail'):
                assert case['acceptanceStatus']=='PASS_PROTOCOL2_ONLY_NOT_VISUAL' and case['frameSync']['accepted']
        assert special['pendingStrictEvidence']==[]
        pending.extend(case['acceptanceStatus'] for case in special['cases'] if case.get('acceptanceStatus','').startswith('PENDING'))
    else:
        assert fixture['candidateCount']==3 and special['fortyCandidateGestureScrollY']>0 and special['exactRecreateAndReturnScroll'] and special['noScanNoConnect']
        assert {case['case'] for case in special['cases']}=={'main-three-rows-reachable','empty','long-name','forty-recreated','forty-navigation-return'}
        for state in ('main-three-rows-reachable','empty','long-name','forty-gesture','forty-recreated','forty-navigation-return'):
            require_protocol(folder/'special-states'/state/'window-frame-sync.json','ACTIVITY')
    assert (folder/'baseline-alignment.json').is_file() and (folder/'geometry.json').is_file()
assert seen=={(page,f'tablet-user-landscape-{theme}-font-{scale}') for page in ('scale','connection') for theme in ('light','dark') for scale in ('1.0','1.3')}
(root/'capture-summary.json').write_text(json.dumps({'realConfigurations':8,'sourceSha':root.name,'visualAcceptance':'ASTRA_PENDING',
    'specialStatesCountedAsMainConfigurations':False,'pendingStrictSpecialEvidence':pending,'allVisualItemsClosed':False},indent=2)+'\n')
print('SCALE_VISUAL_MIGRATION_RETAINED configurations=8 actualActivities=true visualAcceptance=ASTRA_PENDING')
VERIFY_PY
