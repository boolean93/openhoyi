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
if [[ -n "${SCALE_SNAPSHOT_SCOPE_FILE:-}" ]]; then
  python3 scripts/ci_font_scale_snapshot_scope.py verify --owner "$SCALE_SNAPSHOT_SCOPE_FILE"
fi
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
  if [[ -z "${SCALE_SNAPSHOT_SCOPE_FILE:-}" ]]; then
    adb shell am force-stop "$package" || true
  fi
  if [[ -n "${SCALE_SNAPSHOT_SCOPE_FILE:-}" ]]; then
    if ! python3 - "$output_dir/font-scale-before.json" "$output_dir/font-scale-restore.json" "$exit_status" <<'SNAPSHOT_PENDING_PY'
import json,sys
from pathlib import Path
before=json.loads(Path(sys.argv[1]).read_text())
Path(sys.argv[2]).write_text(json.dumps({'before':before,'originalExitStatus':int(sys.argv[3]),
    'status':'PENDING_SNAPSHOT_RESTORE','restoreOperation':'owned_full_avd_snapshot_in_host_wrapper'},indent=2)+'\n')
SNAPSHOT_PENDING_PY
    then
      echo 'Snapshot pending proof write failed' >&2
      if (( exit_status == 0 )); then exit_status=1; fi
    fi
    return "$exit_status"
  fi
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
    if 'short-height' in path.parts and 'special-states' in path.parts and proof['captureWindowKind']=='ACTIVITY': expected=SHORT_ACTIVITY
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

def referenced_file(page, ref, expected):
    assert set(ref)=={'path','sha256'} and isinstance(ref['path'],str)
    assert ref['path']==expected and not ref['path'].startswith('/') and all(part not in ('','.','..') for part in ref['path'].split('/'))
    target=page.joinpath(*ref['path'].split('/'))
    assert target.is_file() and not any(part.is_symlink() for part in (target,*target.parents) if part==page or page in part.parents)
    assert target.resolve().is_relative_to(page.resolve()) and sha(target)==ref['sha256']
    return target

SNAPSHOT_KEYS={'coffeeState','scaleState','coffee','coffeeAt','alarmBits','alarmAt','settings','settingsAt',
    'sleepFirst','sleepSecond','sleepFirstAt','sleepSecondAt','weight','weightAt','scaleObservation',
    'scaleCapabilities','candidates','scanning','message'}
CAPABILITY_KEYS={'weight','tare','deviceFlow','battery','deviceTimer','validatedWeightControl'}
MESSAGE_KEYS={
    'Settings':{'firmwareMajor','firmwareMinor','firmwarePatch','flags','brewTemperatureC',
        'brewCompensationTenthsC','steamTemperatureC','standbyMinutes','standbyTemperatureC',
        'cupCount','filterFlags'},
    'IdleTelemetry':{'brewTemperatureHundredthsC','steamTemperatureHundredthsC',
        'brewPressureTenthsBar','steamPressureTenthsBar','sleepStateRaw','alarmBits','cupCount','extraSensorRaw'},
    'ExtractionTelemetry':{'slotOrPhase','elapsedSeconds','pressureTenthsBar','totalWaterTenthsMl',
        'brewTemperatureHundredthsC','flowTenthsMlPerSecond','statusBits','manualStageRaw'},
    'SleepPart':{'firstDaySundayIndex','enabledBits','days'}}
def require_frame(value):
    assert set(value)=={'size','hex'} and isinstance(value['size'],int) and value['size']>=0
    assert isinstance(value['hex'],str) and len(value['hex'])==2*value['size']
    assert all(ch in '0123456789ABCDEF' for ch in value['hex'])
def require_message(value):
    assert isinstance(value,dict) and value.get('kind') in MESSAGE_KEYS
    assert set(value)==MESSAGE_KEYS[value['kind']]|{'kind','raw'}
    require_frame(value['raw'])
    if value['kind']=='SleepPart':
        assert isinstance(value['days'],list)
        assert all(set(day)=={'sleepHour','sleepMinute','wakeHour','wakeMinute'} for day in value['days'])
def require_resource(value):
    assert isinstance(value,dict) and value.get('kind') in {'ResourceMessage','ResourceSequence'}
    if value['kind']=='ResourceSequence':
        assert set(value)=={'kind','parts'} and isinstance(value['parts'],list)
        for part in value['parts']:require_resource(part)
    else:
        assert set(value)=={'kind','resourceId','values'} and isinstance(value['resourceId'],int)
        assert isinstance(value['values'],list)
        scalar_types={'null','String','Char','Boolean','Byte','Short','Int','Long','Float','Double',
            'BigInteger','BigDecimal','ResourceText'}
        for item in value['values']:
            assert set(item)=={'type','value'} and item['type'] in scalar_types
            if item['type']=='ResourceText':require_resource(item['value'])
            elif item['type']=='null':assert item['value'] is None
def require_snapshot(state):
    assert state['serviceSnapshotSchemaVersion']==1
    value=state['serviceSnapshot'];assert isinstance(value,dict) and set(value)==SNAPSHOT_KEYS
    for key in ('coffee','settings','sleepFirst','sleepSecond'):
        if value[key] is not None:require_message(value[key])
    if value['weight'] is not None:
        weight=value['weight'];assert set(weight)=={'weightHundredthsGram','deviceFlowHundredths',
            'weightSignRaw','flowSignRaw','raw'};require_frame(weight['raw'])
    if value['scaleCapabilities'] is not None:assert set(value['scaleCapabilities'])==CAPABILITY_KEYS
    if value['scaleObservation'] is not None:
        observation=value['scaleObservation']
        assert set(observation)=={'hundredthsGram','receivedAtMs','capabilities','evidence',
            'deviceFlowHundredths','batteryPercent','raw','eligibleForControl'}
        assert set(observation['capabilities'])==CAPABILITY_KEYS;require_frame(observation['raw'])
    assert isinstance(value['candidates'],list)
    assert all(set(candidate)=={'address','advertisedName','rssi','candidateRole','scaleProtocolId'}
        for candidate in value['candidates'])
    if value['message'] is not None:
        message=value['message'];assert set(message)=={'initialText','rendered','isResource','resource'}
        assert isinstance(message['initialText'],str) and isinstance(message['rendered'],str)
        assert message['isResource']==(message['resource'] is not None)
        if message['resource'] is not None:require_resource(message['resource'])

def capture_ref_pair(page, case, directory, kind):
    assert set(case)>= {'geometryRef','frameSyncRef'} and not any(k in case for k in ('geometry','observed','actualWindowGeometry','frameSync'))
    geometry=referenced_file(page,case['geometryRef'],f'{directory}/geometry.json')
    proof=referenced_file(page,case['frameSyncRef'],f'{directory}/window-frame-sync.json')
    observed=json.loads(geometry.read_text());assert isinstance(observed,dict) and observed
    if 'supportState' in observed:require_snapshot(observed['supportState'])
    accepted=require_protocol(proof,kind)
    assert observed==accepted['attempts'][accepted['acceptedAttempt']-1]['geometryBefore']['target']
    return observed

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
        contract=json.loads((folder/'snapshot-contract.json').read_text())
        assert contract['schemaVersion']==1 and contract['completed'] and contract['sameValueNewInstancesEqual']
        assert set(contract['topLevelKeys'])==SNAPSHOT_KEYS
        assert set(contract['changedValueCases'])=={'sleepDay','sleepEnabledBits','sleepRaw','sleepTimestamp',
            'capability','candidateOrder','messageResourceId','messageArgument','nullVersusZero',
            'candidateEmpty','scaleRaw','coffeeField'}
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
    assert special['evidenceSummaryVersion']==2 and special['referenceBase']=='page_root'
    assert special['completed'] and special['mainConfigurationCountContribution']==0 and special['visualAcceptance']=='ASTRA_PENDING'
    fixture=json.loads((folder/'fixture.json').read_text())
    assert fixture['defaultUninstalledRuntimeObserved'] and fixture['synthetic'] and not fixture['hardwareMeasurement']
    if m['page']=='scale':
        assert fixture['explicitMainSamples']==[-125,200,800,1200,1600,1750,1800,1801,1799,1800,1800]
        assert fixture['mainSampleTimes']==list(range(114600,124601,1000))
        assert fixture['actualPausedTimerMs']==24600 and fixture['actualNotifyDelta']==11 and fixture['tickNotifyDelta']==0
        required={'one-decimal','paused-recreated','stale-1501','expired-10001','ready-null','disconnected','negative-offline',
            'gap-and-evidence-change','battery-capability-82','battery-unavailable','weight-capability-false','mock-capture-disabled','short-height','short-height-trend-detail','no-service'}
        assert len(special['cases'])==15 and {case['case'] for case in special['cases']}==required
        for case in special['cases']:
            state=case['case'];capture_ref_pair(folder,case,f'special-states/{state}',
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
        reach=json.loads((folder/'scroll-reachability.json').read_text())
        assert reach['evidenceSummaryVersion']==2 and reach['referenceBase']=='page_root' and reach['completed']
        tags=['product/scale/timer/toggle','product/scale/timer/reset','product/scale/battery',
            'product/scale/device-status','product/scale/connect','product/scale/settings']
        assert len(reach['targets'])==6 and [case['tag'] for case in reach['targets']]==tags
        for i,case in enumerate(reach['targets'],1):
            assert case['case']==f'after-scroll-{i}'
            capture_ref_pair(folder,case,f'after-scroll-{i}','ACTIVITY')
        assert isinstance(reach['restoredY'],int) and reach['restoredY']>=0
        assert len(reach['originalScrollPx'])==2 and all(isinstance(v,int) and v>=0 for v in reach['originalScrollPx'])
        assert reach['originalScrollPx'][1]==reach['restoredY'] and reach['restoredStateEqual'] is True
        before=capture_ref_pair(folder,reach['beforeRefs'],'support-before','ACTIVITY')
        restored=capture_ref_pair(folder,reach['restoredRefs'],'scroll-restored','ACTIVITY')
        assert before['supportState']==restored['supportState']
        assert before['supportState']['scrollPx']==reach['originalScrollPx']
        assert before['supportState']['activityIdentity']==restored['supportState']['activityIdentity']
        assert before['supportState']['windowIdentity']==restored['supportState']['windowIdentity']
        assert before['supportState']['scrollIdentity']==restored['supportState']['scrollIdentity']
        disclosure_cases={'negative-offline','mock-capture-disabled','short-height'}
        fixed=['product/scale/timer/help','product/scale/device-status','product/scale/connect',
            'product/scale/settings','scale-trend-fallback']
        for case in special['cases']:
            state=case['case']
            if state not in disclosure_cases:
                assert 'supportDisclosureRef' not in case
                continue
            path=referenced_file(folder,case['supportDisclosureRef'],f'special-states/{state}/support-disclosure.json')
            disclosure=json.loads(path.read_text())
            assert disclosure['case']==state and disclosure['evidenceSummaryVersion']==2
            assert disclosure['referenceBase']=='page_root' and disclosure['completed'] is True
            assert disclosure['restoredStateEqual'] is True
            assert disclosure['initialRefs']=={'geometryRef':case['geometryRef'],'frameSyncRef':case['frameSyncRef']}
            initial=capture_ref_pair(folder,case,f'special-states/{state}','ACTIVITY')
            restored=capture_ref_pair(folder,disclosure['restoredRefs'],f'special-states/{state}/disclosure-restored','ACTIVITY')
            original=initial['supportState']
            assert restored['supportState']==original
            assert original['scrollPx']==disclosure['originalScrollPx']
            assert original['trendInline']==initial['productLayoutEvidence']['trendInline']
            if original['trendInline']:
                assert original['fallbackVisibility']==8 and original['fallbackShown'] is False
            else:assert original['fallbackVisibility']==0
            required=fixed+([] if state=='negative-offline' else ['product/scale/dose-help'])
            assert len(disclosure['targets'])==len(required)
            assert [t['semanticId'] for t in disclosure['targets']]==required
            for index,target in enumerate(disclosure['targets']):
                semantic=target['semanticId'];count=target['lineCount']
                if semantic=='scale-trend-fallback' and original['trendInline']:
                    assert target=={'semanticId':semantic,'actualTag':semantic,
                        'viewIdentity':original['fallbackIdentity'],
                        'model':'LAYOUT_NOT_APPLICABLE_INLINE_TREND',
                        'lineCount':0,'coveredLines':[],'stages':[]}
                    continue
                assert target['model']=='REAL_VISIBLE_SUPPORT_TARGET'
                assert isinstance(target['text'],str) and target['text'] and count>0
                assert target['actualTag']==(None if semantic in ('product/scale/timer/help','product/scale/dose-help') else semantic)
                assert target['coveredLines']==list(range(count)) and target['stages']
                offsets=target['lineOffsets'];assert len(offsets)==count and offsets[0][0]==0
                assert offsets[-1][1]==len(target['text'].encode('utf-16-le','surrogatepass'))//2
                assert all(len(pair)==2 and 0<=pair[0]<=pair[1] for pair in offsets)
                assert all(offsets[i-1][1]==offsets[i][0] for i in range(1,count))
                assert target['button']==(semantic in ('product/scale/connect','product/scale/settings','scale-trend-fallback'))
                covered=set()
                for step,stage in enumerate(target['stages']):
                    observed=capture_ref_pair(folder,stage,f'special-states/{state}/disclosure/{index}-stage-{step}','ACTIVITY')
                    exact=observed['disclosureTarget'];actual=observed['supportState']
                    assert exact['semanticId']==semantic and exact['text']==target['text']
                    assert exact['actualTag']==target['actualTag']
                    assert exact['viewIdentity']==target['viewIdentity'] and exact['scrollIdentity']==original['scrollIdentity']
                    assert exact['lineCount']==count and exact['button']==target['button']
                    assert target['fitsViewport']==(exact['heightPx']<=exact['scrollHeightPx'])
                    assert stage['whole']==exact['whole'] and stage['visibleLines']==exact['visibleLines']
                    for key in ('activityIdentity','windowIdentity','decorIdentity','rootIdentity','windowTokenIdentity',
                        'scrollIdentity','trendInline','fallbackIdentity','fallbackVisibility','fallbackShown',
                        'serviceSnapshotSchemaVersion','serviceSnapshot','serviceObservationSerial','weight','freshness','tareStatus',
                        'deviceStatus','tareEnabled','captureEnabled','fixedPrimary','fixedTare','fixedCaptureFooter'):
                        assert actual[key]==original[key],(state,semantic,key)
                    for key in ('buffer','bufferNow','timer'):
                        assert actual['trendBufferTimerAndAllScrolls'][key]==original['trendBufferTimerAndAllScrolls'][key]
                    visible=[]
                    assert len(exact['lineScreenRects'])==count
                    for row,line in enumerate(exact['lineScreenRects']):
                        assert line['line']==row and line['ellipsisCount']==0
                        assert [line['start'],line['end']]==offsets[row]
                        utf16_length=len(exact['text'].encode('utf-16-le','surrogatepass'))//2
                        assert 0<=line['start']<=line['end']<=utf16_length
                        rect=line['rectPx'];viewport=exact['viewportScreenRectPx'];clip=exact['targetVisibleRectPx']
                        horizontal=0<=line['layoutLeftPx']<=line['layoutRightPx']<=exact['contentWidthPx']
                        shown=exact['screenMappingVerified'] and horizontal and viewport[0]<=rect[0] and rect[2]<=viewport[2] and \
                            viewport[1]<=rect[1] and rect[3]<=viewport[3] and \
                            clip[0]<=rect[0] and rect[2]<=clip[2] and clip[1]<=rect[1] and rect[3]<=clip[3]
                        assert line['fullyVisible']==shown
                        if shown:visible.append(row)
                    assert visible==exact['visibleLines'] and visible
                    assert stage['requestedLine'] is None or stage['requestedLine'] in visible
                    assert set(visible)-covered
                    covered.update(visible)
                    if target['button']:
                        assert exact['whole'] and exact['enabled'] and exact['clickable']
                        assert exact['heightPx']>=exact['minimumTouchHeightPx']
                    elif target['fitsViewport']:assert exact['whole']
                assert covered==set(range(count))
                if target['button'] or target['fitsViewport']:assert len(target['stages'])==1
        for case in special['cases']:
            if case['case'] in ('short-height','short-height-trend-detail'):
                assert case['acceptanceStatus']=='PASS_PROTOCOL2_ONLY_NOT_VISUAL'
        assert special['pendingStrictEvidence']==[]
        pending.extend(case['acceptanceStatus'] for case in special['cases'] if case.get('acceptanceStatus','').startswith('PENDING'))
    else:
        assert fixture['candidateCount']==3 and special['fortyCandidateGestureScrollY']>0 and special['exactRecreateAndReturnScroll'] and special['noScanNoConnect']
        required={'main-three-rows-reachable','empty','long-name','forty-recreated','forty-navigation-return'}
        assert len(special['cases'])==5 and {case['case'] for case in special['cases']}==required
        for case in special['cases']:
            capture_ref_pair(folder,case,f"special-states/{case['case']}",'ACTIVITY')
        capture_ref_pair(folder,special['preRecreateRefs'],'special-states/forty-gesture','ACTIVITY')
    assert (folder/'baseline-alignment.json').is_file() and (folder/'geometry.json').is_file()
assert seen=={(page,f'tablet-user-landscape-{theme}-font-{scale}') for page in ('scale','connection') for theme in ('light','dark') for scale in ('1.0','1.3')}
(root/'capture-summary.json').write_text(json.dumps({'realConfigurations':8,'sourceSha':root.name,'visualAcceptance':'ASTRA_PENDING',
    'specialStatesCountedAsMainConfigurations':False,'pendingStrictSpecialEvidence':pending,'allVisualItemsClosed':False},indent=2)+'\n')
print('SCALE_VISUAL_MIGRATION_RETAINED configurations=8 actualActivities=true visualAcceptance=ASTRA_PENDING')
VERIFY_PY
