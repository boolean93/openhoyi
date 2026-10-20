#!/usr/bin/env bash
set -Eeuo pipefail
# M8 real-Activity evidence only, in its independently guarded disposable CI emulator.
[[ "${GITHUB_ACTIONS:-}" == true ]] || { echo 'Legacy visual captures require GitHub CI' >&2; exit 1; }
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || { echo 'Disposable emulator required' >&2; exit 1; }
package=io.openhoyi.mobile.mock
source_sha=$(git rev-parse HEAD)
source_dirty=false
[[ -z "$(git status --porcelain)" ]] || source_dirty=true
profile="${1:-tablet-user-landscape}"
[[ "$profile" == tablet-user-landscape ]] || { echo 'Only the frozen tablet-user-landscape profile is supported' >&2; exit 1; }
output_dir="${2:-$PWD/build/task-first-evidence/settings-visual-migration}"
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
matrix_initialized=false
collect() {
  local exit_status=$1
  if (( exit_status != 0 )) && [[ "$matrix_initialized" == true ]]; then
    python3 scripts/settings_matrix_isolation.py abort --output "$output_dir" --exit-status "$exit_status" || true
    # A later set -e failure must not replace the first already-recorded business failure.
    local selected_exit
    if selected_exit=$(python3 scripts/settings_matrix_isolation.py select-exit --output "$output_dir" --fallback "$exit_status"); then
      if [[ "$selected_exit" =~ ^([1-9]|[1-9][0-9]|1[0-9][0-9]|2[0-4][0-9]|25[0-5])$ ]]; then
        exit_status=$selected_exit
      fi
    fi
  fi
  if (( exit_status != 0 )); then
    mkdir -p "$output_dir/raw-device" || true
    adb pull "/sdcard/Android/data/$package/files/settings-visual-migration/$source_sha" "$output_dir/raw-device/" || true
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
matrix_gate=scripts/settings_matrix_isolation.py
python3 "$matrix_gate" init --output "$output_dir" --source "$source_sha" --apk "$apk_sha"
matrix_initialized=true
for product_theme in light dark; do
  if [[ "$product_theme" == dark ]]; then adb shell cmd uimode night yes; else adb shell cmd uimode night no; fi
  # End with three real 1.0 pages; this removes delete's explicit 1.3 -> default-1.0 transition,
  # not a persistence-queue ACK. Strict key presence/value restoration below remains mandatory.
  for font_scale in 1.3 1.0; do
    adb shell settings put system font_scale "$font_scale"
    for settings_page in app machine scale-settings; do
      result_file="$output_dir/$product_theme-$font_scale-$settings_page-result.txt"
      if ! python3 "$matrix_gate" before --output "$output_dir" --page "$settings_page" --theme "$product_theme" --scale "$font_scale"; then
        break 3
      fi
      adb shell am force-stop "$package"
      if python3 - "$result_file" "$source_sha" "$source_dirty" "$apk_sha" "$product_theme" "$font_scale" "$settings_page" <<'RUN_PY'
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
 'app':('app-settings-v1.png','f2022d5eb60b798a722d30f01933028a63f14a342958400c74fcdc25a7c1a899'),
 'machine':('machine-settings-v1.png','ccb438958aae1cb9a6e88bec1c2fdd5ffafd4a18d385ff63a0e9024c68f6c60d'),
 'scale-settings':('scale-settings-v1.png','f87b9cf3d260a3b01bd9d88e83fa2a5cb40d8e1d456de37b8c579515a8eec36f')}
import hashlib
reference_name,reference_sha=references[page]
assert hashlib.sha256((Path('docs/research/task-first-ui-concepts')/reference_name).read_bytes()).hexdigest()==reference_sha
command = ['adb','shell','am','instrument','-w']
for key,value in {'settingsVisualMigration':'true','disposableCiEmulator':'true','freshProcessVerified':'true',
    'settingsPage':page,'referencePngSha256':reference_sha,'sourceSha':source,'headSha':os.environ.get('PRODUCT_PR_HEAD_SHA',source),
    'sourceDirty':dirty,'apkSha':apk,'sampleTheme':theme,'expectedFontScale':scale,
    'ciRun':os.environ['GITHUB_RUN_ID'],'ciJob':os.environ['GITHUB_JOB']}.items():
    command += ['-e',key,value]
command += ['io.openhoyi.mobile.mock.test/io.openhoyi.mobile.BrewAudioInstrumentation']
with Path(result_path).open('w') as capture:
    result = subprocess.run(command, stdout=capture, stderr=subprocess.STDOUT, timeout=600)
text = Path(result_path).read_text()
marker = 'SETTINGS_VISUAL_MIGRATION_CAPTURED fixtures=1 realActivities=true visualAcceptance=ASTRA_PENDING noBle=true isolationVerified=true' in text
Path(result_path).with_suffix('.run.json').write_text(json.dumps({
    'adbReturnCode':result.returncode, 'successMarker':marker,
    'instrumentationStdoutStderr':str(Path(result_path))},indent=2)+'\n')
if result.returncode or not marker:
    print(text)
    raise SystemExit('Real product capture failed; partial evidence retained, no automatic reset/retry')
RUN_PY
      then cell_exit=0; else cell_exit=$?; fi
      if ! python3 "$matrix_gate" after --output "$output_dir" --page "$settings_page" --theme "$product_theme" --scale "$font_scale" --result "$result_file" --exit-status "$cell_exit"; then
        # Unknown isolation/ownership/evidence: preserve the original failure and do not launch another cell.
        break 3
      fi
    done
  done
done
if python3 - "$output_dir/$source_sha" "$output_dir/matrix-ledger.json" <<'VERIFY_PY'
from pathlib import Path
import hashlib,json,struct,sys
root=Path(sys.argv[1]); ledger=json.loads(Path(sys.argv[2]).read_text()); sha=lambda path:hashlib.sha256(path.read_bytes()).hexdigest()
manifests=sorted(root.glob('*/*/manifest.json'))
assert len(ledger['cells'])==12
assert len(manifests)==12 or ledger['unsafeStop'] is not None, f'Expected 12 real M8 configurations, got {len(manifests)}'
assert all(cell['status'] in ('PASS_CAPTURED_PENDING_FULL_VERIFY','FAILED_ISOLATED_CONTINUED',
    'FAILED_UNSAFE_STOP','NOT_RUN_UNSAFE_PREDECESSOR') for cell in ledger['cells'])
success={(cell['page'],cell['profile']) for cell in ledger['cells'] if cell['status']=='PASS_CAPTURED_PENDING_FULL_VERIFY'}
assert len({(cell['page'],cell['profile']) for cell in ledger['cells']})==12
seen=set(); pending=[]; protocol_count=0
references={
'app':'f2022d5eb60b798a722d30f01933028a63f14a342958400c74fcdc25a7c1a899',
'machine':'ccb438958aae1cb9a6e88bec1c2fdd5ffafd4a18d385ff63a0e9024c68f6c60d',
'scale-settings':'f87b9cf3d260a3b01bd9d88e83fa2a5cb40d8e1d456de37b8c579515a8eec36f'}
required={
'app':{'theme-write-rejected','language-write-rejected','feedback-write-rejected','feedback-preview-start-stop',
'feedback-preview-leave-stopped','saf-cancel','saf-failure','saf-owned-success','theme-success-recreated','hardware-audio-output'} |
{'language-'+tag for tag in ('zh-Hans','en','ru','th','ar','ja','ko','es')},
'machine':{'section-'+str(index) for index in range(5)} | {'section-recreated','invalid-brew','invalid-compensation',
'invalid-steam','legal-input-mock-preview','temperature-native-ime','schedule-draft-native-ime','schedule-cancel-no-write','device-confirmed-unknown-recovery','no-service-disabled'},
'scale-settings':{'precision-recreated','real-scale-no-data','ready-nonmock-hardware-capabilities'}}
def require_protocol(path, kind=None):
    assert path.is_file(), ('Missing expected protocol2 proof',str(path))
    proof=json.loads(path.read_text())
    assert proof['protocolVersion']==2 and proof['accepted'] and proof['timeoutMs']==5000 and proof['attempts'],path
    assert proof['pixelComparison']=='each_equivalent_domain_all_app_pixels_no_tolerance_no_mask'
    assert proof['crossDomainComparison']=='DIAGNOSTIC_ONLY_PIPELINES_NOT_EQUIVALENT'
    assert proof['displayId']==0 and proof['windowIdentity'] and proof['decorIdentity'] and proof['windowTokenIdentity']
    if kind is not None: assert proof['captureWindowKind']==kind,(path,proof['captureWindowKind'],kind)
    attempt=proof['attempts'][proof['acceptedAttempt']-1]
    finished=[event for event in attempt['budget'] if event['stage']=='comparison_complete']
    assert len(finished)==1 and 0<=finished[0]['elapsedMs']<=5000 and finished[0]['remainingMs']>=0,(path,finished)
    assert attempt['accepted'] and attempt['withinMatchingBudget'] and attempt['geometryUnchanged'] and attempt['windowReadyAtBothEnds']
    assert attempt['windowDomainColorSpacesEqual'] and attempt['displayDomainColorSpacesEqual']
    assert attempt['windowLayerMatch'] and attempt['displayStable']
    for key in ('S1_W1','S1_W2','S1_S2','D1_D2_app'):assert attempt[key]['count']==0,(path,key)
    source=attempt['windowSourceRectPx']; screen=attempt['displayAppRectPx']
    assert attempt['surface-beforeSourceRectAfterSdk']==source==attempt['surface-afterSourceRectAfterSdk'],path
    for geometry in (attempt['geometryBefore'],attempt['geometryAfter']):
        assert geometry['hasWindowFocus'] and not geometry['rootLayoutRequested'] and not geometry['decorLayoutRequested']
        decor=geometry['windowDecorSizePx']; ds=geometry['decorLocationOnScreenPx']
        assert geometry['decorLocationInWindowPx']==[0,0]
        assert screen[0]-source[0]==ds[0] and screen[1]-source[1]==ds[1]
        assert 0<=source[0]<source[2]<=decor[0] and 0<=source[1]<source[3]<=decor[1]
        assert attempt['layer-before']['sizePx']==decor==attempt['layer-after']['sizePx']
        if proof['captureWindowKind']=='EXPLICIT_WINDOW':
            assert proof['mappingMode']=='EXPLICIT_WINDOW_MEASURED_DECOR'
            assert geometry['windowIdentity']==proof['windowIdentity'] and geometry['decorIdentity']==proof['decorIdentity']
            assert geometry['rootIdentity']==proof['rootIdentity'] and geometry['rootViewIdentity']==proof['decorIdentity']
            assert geometry['rootTokenIdentity']==geometry['decorTokenIdentity']==proof['windowTokenIdentity']
            assert geometry['displayId']==proof['displayId']==0
            assert geometry['metricsRole']=='diagnostic_host_container_not_assumed_dialog_bounds'
            chain=geometry['targetAncestorChain'];assert chain and chain[0]['identity']==proof['rootIdentity'] and chain[-1]['identity']==proof['decorIdentity']
            for node in chain:
                assert node['attached'] and node['hardwareAccelerated'] and node['laidOut'] and node['hasWindowFocus'] and not node['layoutRequested']
                assert node['tokenIdentity']==proof['windowTokenIdentity'] and node['displayId']==0
                assert node['matrixIdentity'] and node['transform']==[1,1,0,0,0,0,0,0]
        else:
            assert proof['mappingMode']=='ACTIVITY_STRICT_METRICS_DECOR'
            assert geometry['windowMetricsBoundsPx']==[ds[0],ds[1],ds[0]+decor[0],ds[1]+decor[1]]
    artifacts=proof['captureArtifacts']
    assert {item['role'] for item in artifacts}=={'display','display-after'}
    for item in artifacts:
        image=path.parent/item['file'];assert image.is_file() and sha(image)==item['fileSha256']
    return proof

# Every state declared as captured must have the expected concrete Window protocol and original files.
state_kinds={
'app':{name:'ACTIVITY' for name in required['app'] if not name.startswith('saf-') and name not in {'hardware-audio-output','language-write-rejected'}},
'machine':{name:'ACTIVITY' for name in required['machine'] if name not in {'legal-input-mock-preview','schedule-draft-native-ime','schedule-cancel-no-write','device-confirmed-unknown-recovery'}},
'scale-settings':{'precision-recreated':'ACTIVITY','real-scale-no-data':'ACTIVITY'}}
state_kinds['app']['language-write-rejected']='EXPLICIT_WINDOW'
state_kinds['machine'].update({name:'EXPLICIT_WINDOW' for name in ('legal-input-mock-preview','schedule-draft-native-ime','schedule-cancel-no-write')})
core_app={'product/settings/app/theme/toggle','product/settings/app/language/current','product/settings/app/feedback/toggle','product/settings/app/feedback/preview','product/settings/app/export','product/settings/app/machine'} | {
'product/settings/app/info/'+key+'/'+part for key in ('name','version','mode','package') for part in ('label','value')}
core_machine={'product/settings/machine/connection','product/settings/machine/write-status'} | {
'product/settings/machine/readback/'+key+'/'+part for key in ('brew','steam','run-mode','supply') for part in ('label','number')} | {
'product/settings/machine/readback/'+key+'/unit' for key in ('brew','steam')} | {
'product/settings/machine/'+key+'/'+part for key in ('brew','compensation','steam') for part in ('unit','action')} | {
'product/settings/machine/field/'+key for key in ('brew','compensation','steam')}
core_scale={'product/settings/scale/precision','product/settings/scale/capabilities'} | {
'product/settings/scale/help/'+str(index)+'/'+part for index in range(3) for part in ('title','body')}

def require_whole(actual):
    assert actual['complete'] and actual['textLineBoxes']
    for text in actual['textLineBoxes']:
        assert text['fullViewVisible'] and text['layoutPresent'] and text['lineCount']>0 and text['ellipsisCount']==0
        assert text['layoutHeightPx']<=text['contentHeightPx']
        assert all(line['leftPx']>=0 and line['rightPx']<=text['contentWidthPx'] for line in text['horizontalLayoutLines'])
        typography=text['actualTypography']
        assert {'textSizePx','weight','typeface','textScaleX','textSkewX','fakeBold','letterSpacingEm','baselineScreenPx','colors','actualLayout','fontMetricsPx'}<=set(typography)

def require_state(folder,kind):
    assert (folder/'window.png').is_file() and (folder/'geometry.json').is_file(),folder
    require_protocol(folder/'window-frame-sync.json',kind)
    if kind=='EXPLICIT_WINDOW':
        typography=json.loads((folder/'dialog-typography.json').read_text())
        assert typography['source']=='accepted_explicit_dialog_window_target_geometry_not_owner_Activity'
        geometry=json.loads((folder/'geometry.json').read_text())
        assert typography['nodes']==geometry['nodes'] and typography['nodes']
        texts=[text for node in typography['nodes'] for text in node['textLineBoxes']]
        assert texts
        for text in texts:
            assert {'textSizePx','weight','typeface','textScaleX','textSkewX','fakeBold','letterSpacingEm','baselineScreenPx','colors','actualLayout','fontMetricsPx'}<=set(text['actualTypography'])
        safe=json.loads((folder/'safe-controls.json').read_text());assert safe['controls']
        expected={'language-write-rejected':{'negative'},'legal-input-mock-preview':{'positive'},
                  'schedule-draft-native-ime':{'positive','negative'},'schedule-cancel-no-write':{'positive','negative'}}[folder.name]
        buttons=safe['buttonRoles'];assert {button['role'] for button in buttons}=={'positive','negative'} and len(buttons)==2
        for button in buttons:
            assert isinstance(button['present'],bool) and isinstance(button['isShown'],bool)
            assert button['isShown']==(button['role'] in expected) and (not button['isShown'] or button['present'])
        left,top,right,bottom=safe['safeRectScreenPx']
        shown_roles=set()
        for control in safe['controls']:
            assert control['isShown'] is True and control['roles']
            assert set(control['roles'])<={'positive','negative','focused'}
            shown_roles.update(set(control['roles'])&{'positive','negative'})
            require_whole(control)
            x,y,r,b=control['rectPx'];assert left<=x<r<=right and top<=y<b<=bottom
        assert shown_roles==expected

def require_reach(folder,page,domain):
    report=json.loads((folder/'reachability.json').read_text())
    assert report['page']==('scale' if page=='scale-settings' else page)
    assert report['domain']==domain and report['completed'] and report['exactScrollRestored']
    assert report['beforeScroll']==report['restoredScroll']
    body=report['allActualBodyTargets'];targets=report['targets']
    ids=[item['id'] for item in body]; actual_ids=[item['id'] for item in targets]
    assert ids and len(set(ids))==len(ids) and ids==actual_ids,(folder,ids,actual_ids)
    if domain=='language-current': expected={'product/settings/app/language/current'}
    elif domain.startswith('section-'):
        index=int(domain.split('-')[1])
        keys={0:('brew','compensation','steam'),2:('standby',)}.get(index,())
        expected={'product/settings/machine/'+key+'/'+part for key in keys for part in ('unit','action')} | {'product/settings/machine/field/'+key for key in keys}
    else: expected={'app':core_app,'machine':core_machine,'scale-settings':core_scale}[page]
    assert len(report['coreTargets'])==len(expected) and set(report['coreTargets'])==expected,(folder,report['coreTargets'],expected)
    assert {key for key in ids if key.startswith('product/settings/')}==expected
    assert [item['viewIdentity'] for item in body]==[item['viewIdentity'] for item in targets]
    for item in targets:
        proof_path=folder/item['proof']
        assert proof_path.name=='window-frame-sync.json'
        require_state(proof_path.parent,'ACTIVITY')
        assert folder/item['window']==proof_path.parent/'window.png' and folder/item['geometry']==proof_path.parent/'geometry.json'
        geometry=json.loads((folder/item['geometry']).read_text())
        matches=[node for node in geometry['nodes'] if node['viewIdentity']==item['viewIdentity']]
        assert len(matches)==1 and matches[0]==item['acceptedActualTarget']
        require_whole(matches[0])
    assert report['restoreProof']=='restored/window-frame-sync.json'
    require_state(folder/'restored','ACTIVITY')

verification_errors=[]
verification_results=[]
for path in manifests:
    m=json.loads(path.read_text()); folder=path.parent
    if (m['page'],m['profile']) not in success:
        continue # Preserve failed-cell original manifest/partial evidence; no false mechanical PASS.
    cell_key={'page':m['page'],'profile':m['profile']}
    try:
        assert m['sourceSha']==root.name and m['realProductActivity'] and not m['specimenOnly']
        assert m['visualAcceptance']=='ASTRA_PENDING'
        assert m['layoutStatus']==m['overallStatus']=='PASS_MECHANICAL_ONLY_NOT_VISUAL_ACCEPTANCE'
        assert m['isolationStatus']=='PASS_ISOLATION_ONLY'
        assert m['referenceDesignPngSha256']==references[m['page']]
        key=(m['page'],m['profile']);assert key not in seen;seen.add(key)
        assert m['fontSha256']=='a3041811a78c361b1de50f953c805e0244951c21c5bd412f7232ef0d899af0da'
        assert tuple(m['wholeWindowPngSizePx'])==struct.unpack('>II',(folder/'window.png').read_bytes()[16:24])==(2560,1600)
        assert m['window']['densityDpi']==360 and m['window']['locale'].startswith('zh') and not m['window']['imeVisible']
        assert m['window']['uiModeNightMeaning']==m['profile'].split('-font-')[0].rsplit('-',1)[-1]
        assert struct.pack('>f',m['window']['fontScale'])==struct.pack('>f',float(m['profile'].rsplit('-font-',1)[-1]))
        for name,value in m['fileSha256'].items():assert sha(folder/name)==value,(path,name)
        proofs=list(folder.rglob('*-frame-sync.json'));assert proofs
        for proof in proofs:
            require_protocol(proof); protocol_count+=1
        require_protocol(folder/'window-frame-sync.json','ACTIVITY')
        isolation=json.loads((folder/'fixture-isolation.json').read_text())
        for name in ('businessDiskByteUnchanged','typedPreferencesRestored','originalLazyIdentityAndInitializedRestored',
                     'allActivitiesDestroyed','noServiceBeforeOriginalDelegateRestore','scopedWritersTerminated',
                     'allOwnedCallbacksDrained','feedbackPreviewStoppedAndUnobserved','providerSessionRemoved',
                     'mockClockOwnerAbsentBeforeAndAfter'):
            assert isolation[name] is True,(path,name)
        assert isolation['mainQueueBarrierAfterWriters'] and isolation['providerLifecycle'] is not None
        trace_status=isolation['scopedTraceStatusAfterTermination'].split()
        assert trace_status[0]=='closed' and 'dropped=0' in trace_status
        assert 'errors='+str(isolation['expectedActualExportFailures']) in trace_status
        assert isolation['expectedActualExportFailures']==(1 if m['page']=='app' else 0)
        assert isolation['status']=='PASS_ISOLATION_ONLY' and isolation['clipboard']=='NOT_READ_OR_WRITTEN'
        fixture=json.loads((folder/'fixture.json').read_text())
        assert fixture['synthetic'] and not fixture['hardwareMeasurement'] and fixture['clock']=='DEFAULT_SYSTEM_MONOTONIC'
        assert fixture['noDeviceControl'] and fixture['noMockReadOnlyScaleInput']
        if m['page']=='machine':
            assert fixture['realMockService'] and fixture['realSettingsReadback']
            for name in ('actualServiceDestroyed','serviceRecoveryLazyIdentityRestored','serviceHandlersDrained','serviceHubClosed',
                         'serviceVisibilityObserverRemoved','serviceFeedbackObserverRemoved'):
                assert isolation[name] is True,(path,name)
        else: assert not fixture['realMockService']
        assert (folder/'baseline-alignment.json').is_file() and (folder/'geometry.json').is_file()
        special=json.loads((folder/'special-states/manifest.json').read_text())
        assert special['completed'] and special['mainConfigurationCountContribution']==0 and special['visualAcceptance']=='ASTRA_PENDING'
        assert len(special['cases'])==len(required[m['page']]) and {case['case'] for case in special['cases']}==required[m['page']],path
        for name,kind in state_kinds[m['page']].items():require_state(folder/'special-states'/name,kind)
        require_reach(folder/'main-reachability',m['page'],'main')
        if m['page']=='machine':
            for index in range(5):require_reach(folder/'special-states'/('section-'+str(index))/'reachable','machine','section-'+str(index))
        if m['page']=='scale-settings':require_reach(folder/'special-states/all-instructions','scale-settings','all-instructions')
        if m['page']=='app':
            for tag in ('zh-Hans','en','ru','th','ar','ja','ko','es'):
                require_reach(folder/'special-states'/('language-'+tag)/'current-reachable','app','language-current')
            saf=json.loads((folder/'special-states/saf-business.json').read_text())
            assert saf['providerSessionRemoved'] and saf['providerSession']
            entries={item['case']:item for item in saf['cases']}
            assert len(saf['cases'])==3 and set(entries)=={'saf-cancel','saf-failure','saf-owned-success'}
            assert entries['saf-cancel']['writes']==0 and entries['saf-cancel']['originalCallback']
            assert entries['saf-failure']['actualFailedToast'] and entries['saf-failure']['outputWrites']==0
            assert entries['saf-owned-success']['actualSuccessToast'] and entries['saf-owned-success']['outputWrites']==1 and 'metadata.json' in entries['saf-owned-success']['zipEntries']
            owned=[item for item in isolation['providerLifecycle'] if item['session']==saf['providerSession']]
            assert [item['operation'] for item in owned]==['created_owned_provider','owned_provider_cleanup_returned']
        pending.extend(case['acceptanceStatus'] for case in special['cases'] if case.get('acceptanceStatus','').startswith('PENDING'))
    except Exception as exc:
        verification_errors.append({"cell":str(path),"error":str(exc)})
        verification_results.append({**cell_key,'status':'FAIL_ORIGINAL_FULL_VERIFY','error':str(exc)})
    else:
        verification_results.append({**cell_key,'status':'PASS_ORIGINAL_FULL_VERIFY'})
(root.parent/'matrix-verification.json').write_text(json.dumps({'results':verification_results,
    'errors':verification_errors},indent=2)+'\n')
assert seen==success and not verification_errors, verification_errors
if len(success)!=12 or len(manifests)!=12 or ledger['unsafeStop'] is not None:
    print(f'SETTINGS_VISUAL_MIGRATION_PARTIAL successes={len(success)} failed={12-len(success)}; original failures retained')
    raise SystemExit(1)
(root/'capture-summary.json').write_text(json.dumps({'realConfigurations':12,'sourceSha':root.name,'visualAcceptance':'ASTRA_PENDING',
'protocol2AcceptedProofCount':protocol_count,'specialStatesCountedAsMainConfigurations':False,'pendingStrictSpecialEvidence':pending,
'allVisualItemsClosed':False},indent=2)+'\n')
print('SETTINGS_VISUAL_MIGRATION_RETAINED configurations=12 actualActivities=true visualAcceptance=ASTRA_PENDING')
VERIFY_PY
then verify_exit=0; else verify_exit=$?; fi
python3 "$matrix_gate" finalize --output "$output_dir" --verify-exit "$verify_exit"
