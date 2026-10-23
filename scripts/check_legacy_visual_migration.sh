#!/usr/bin/env bash
set -Eeuo pipefail
# M7 real-Activity evidence only, in its independently guarded disposable CI emulator.
[[ "${GITHUB_ACTIONS:-}" == true ]] || { echo 'Legacy visual captures require GitHub CI' >&2; exit 1; }
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || { echo 'Disposable emulator required' >&2; exit 1; }
package=io.openhoyi.mobile.mock
source_sha=$(git rev-parse HEAD)
source_dirty=false
[[ -z "$(git status --porcelain)" ]] || source_dirty=true
profile="${1:-tablet-user-landscape}"
[[ "$profile" == tablet-user-landscape ]] || { echo 'Only the frozen tablet-user-landscape profile is supported' >&2; exit 1; }
output_dir="${2:-$PWD/build/task-first-evidence/legacy-visual-migration}"
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
    adb pull "/sdcard/Android/data/$package/files/legacy-visual-migration/$source_sha" "$output_dir/raw-device/" || true
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
    for legacy_page in curve history detail; do
      adb shell am force-stop "$package"
      python3 - "$output_dir/$product_theme-$font_scale-$legacy_page-result.txt" "$source_sha" "$source_dirty" "$apk_sha" "$product_theme" "$font_scale" "$legacy_page" <<'RUN_PY'
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
 'curve':('legacy-curve-v1.png','5dad2df70071007507e626fec6db35ac87b590a7f79f036c44af3b7ecfbf35bf'),
 'history':('legacy-history-v1.png','ca299170ee2a154015ca5d7894ff73e8e49c39a2d73ea48d56800ddd1899ff5e'),
 'detail':('legacy-history-detail-v1.png','35f3c2e39575558f3e3b229858d507edb016cc9e4212384557241909d968b7bb')}
import hashlib
reference_name,reference_sha=references[page]
assert hashlib.sha256((Path('docs/research/task-first-ui-concepts')/reference_name).read_bytes()).hexdigest()==reference_sha
command = ['adb','shell','am','instrument','-w']
for key,value in {'legacyVisualMigration':'true','disposableCiEmulator':'true','freshProcessVerified':'true',
    'legacyPage':page,'referencePngSha256':reference_sha,'sourceSha':source,'headSha':os.environ.get('PRODUCT_PR_HEAD_SHA',source),
    'sourceDirty':dirty,'apkSha':apk,'sampleTheme':theme,'expectedFontScale':scale,
    'ciRun':os.environ['GITHUB_RUN_ID'],'ciJob':os.environ['GITHUB_JOB']}.items():
    command += ['-e',key,value]
command += ['io.openhoyi.mobile.mock.test/io.openhoyi.mobile.BrewAudioInstrumentation']
with Path(result_path).open('w') as capture:
    result = subprocess.run(command, stdout=capture, stderr=subprocess.STDOUT, timeout=600)
text = Path(result_path).read_text()
if result.returncode or 'LEGACY_VISUAL_MIGRATION_CAPTURED fixtures=1 realActivities=true visualAcceptance=ASTRA_PENDING noBle=true isolationVerified=true' not in text:
    print(text)
    raise SystemExit('Real product capture failed; partial evidence retained, no automatic reset/retry')
RUN_PY
    done
  done
done
adb pull "/sdcard/Android/data/$package/files/legacy-visual-migration/$source_sha" "$output_dir/"
python3 - "$output_dir/$source_sha" <<'VERIFY_PY'
from pathlib import Path
import hashlib,json,struct,sys
sys.path.insert(0,str(Path('scripts').resolve()))
from window_mapping_contract import validate, DEFAULT_ACTIVITY, DEFAULT_WINDOW, TRANSLATED_WINDOW, SHORT_ACTIVITY
root=Path(sys.argv[1]); sha=lambda path:hashlib.sha256(path.read_bytes()).hexdigest()
manifests=sorted(root.glob('*/*/manifest.json'))
assert len(manifests)==12, f'Expected 12 real M7 configurations, got {len(manifests)}'
seen=set(); pending=[]; protocol_count=0
references={'curve':'5dad2df70071007507e626fec6db35ac87b590a7f79f036c44af3b7ecfbf35bf',
'history':'ca299170ee2a154015ca5d7894ff73e8e49c39a2d73ea48d56800ddd1899ff5e',
'detail':'35f3c2e39575558f3e3b229858d507edb016cc9e4212384557241909d968b7bb'}
required={
'curve':{'missing-parameters','long-name-category','short-window','not-imported','bad-file-read-failure','saf-cancel',
'duplicate-no-change','malformed-utf8','wrong-format','over-16MiB','curve-explicit-replacement'},
'history':{'three-sources','saf-cancel','duplicate-no-change','malformed-utf8','wrong-format','over-16MiB',
'history-merge-new-id','history-conflicting-id','history-duplicate-id-in-file','long-name-source','short-window',
'empty-or-unavailable','bad-file-read-failure','missing-legacy-id'},
'detail':{'long-name-source','short-window','empty-or-unavailable','bad-file-read-failure','missing-legacy-id'}}
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
    relative=path.relative_to(root).parts
    expected=SHORT_ACTIVITY if relative[2:4]==('special-states','short-window') else DEFAULT_ACTIVITY
    validate(proof,attempt,expected)
    artifacts=proof['captureArtifacts']
    assert {item['role'] for item in artifacts}=={'display','display-after'}
    for item in artifacts:
        image=path.parent/item['file'];assert image.is_file() and sha(image)==item['fileSha256']
    return proof

def referenced_file(base, ref, expected):
    assert set(ref)=={'path','sha256'} and ref['path']==expected
    assert isinstance(ref['path'],str) and not ref['path'].startswith('/')
    assert all(part not in ('','.','..') for part in ref['path'].split('/'))
    target=base.joinpath(*ref['path'].split('/'))
    assert target.is_file() and target.resolve().is_relative_to(base.resolve())
    assert not any(part.is_symlink() for part in (target,*target.parents) if part==base or base in part.parents)
    assert sha(target)==ref['sha256']
    return target

def whole(observation):
    if not observation['complete']:return False
    for line in observation['textLineBoxes']:
        if not (line['fullViewVisible'] and line['layoutPresent'] and line['lineCount']>0 and
                line['ellipsisCount']==0 and line['layoutHeightPx']<=line['contentHeightPx']):return False
        for bounds in line['horizontalLayoutLines']:
            if not (bounds['leftPx']>=0 and bounds['rightPx']<=line['contentWidthPx'] and
                    bounds['rightPx']-bounds['leftPx']<=line['contentWidthPx']):return False
    return True

def short_detail_capture(special_root, refs, prefix, expected_mode):
    assert set(refs)=={'windowRef','geometryRef','frameSyncRef'}
    image=referenced_file(special_root,refs['windowRef'],f'{prefix}/window.png')
    geometry_path=referenced_file(special_root,refs['geometryRef'],f'{prefix}/geometry.json')
    proof_path=referenced_file(special_root,refs['frameSyncRef'],f'{prefix}/window-frame-sync.json')
    proof=require_protocol(proof_path,'ACTIVITY')
    assert proof['mappingMode']==expected_mode
    geometry=json.loads(geometry_path.read_text())
    accepted=proof['attempts'][proof['acceptedAttempt']-1]['geometryBefore']['target']
    assert geometry==accepted and proof['imageSha256']==sha(image)
    assert tuple(geometry['appSizePx']) and struct.unpack('>II',image.read_bytes()[16:24])==(2560,1600)
    return geometry['shortDetail']

def short_badge_whole(state, initial, fixed_identity):
    assert all(state[key]==initial[key] for key in fixed_identity)
    assert [(item['identity'],item['tag']) for item in state['scrolls']]==[(item['identity'],item['tag']) for item in initial['scrolls']]
    assert state['windowLayout']==initial['windowLayout'] and state['workspaceHeightPx']==initial['workspaceHeightPx']
    badge=state['headingReadonly'];original=initial['headingReadonly']
    assert state['headingReadonlyIdentity']==initial['headingReadonlyIdentity']
    assert badge['semanticId']==original['semanticId']=='legacy/readonly'
    assert len(badge['textLineBoxes'])==len(original['textLineBoxes'])>0
    for before,after in zip(original['textLineBoxes'],badge['textLineBoxes']):
        assert before['semanticId']==after['semanticId']=='legacy/readonly' and before['text']==after['text']
    rect=badge['rectPx'];visible=badge['visibleRectPx']
    assert badge['screenMappingVerified'] and badge['globalVisible']
    assert len(rect)==len(visible)==4 and rect==visible and rect[2]>rect[0] and rect[3]>rect[1]
    assert whole(badge)

for path in manifests:
    m=json.loads(path.read_text()); folder=path.parent
    assert m['sourceSha']==root.name and m['realProductActivity'] and not m['specimenOnly']
    assert m['visualAcceptance']=='ASTRA_PENDING' and m['layoutStatus']=='PASS_MECHANICAL_ONLY_NOT_VISUAL_ACCEPTANCE'
    assert m['overallStatus']=='PASS_MECHANICAL_ONLY_NOT_VISUAL_ACCEPTANCE' and m['isolationStatus']=='PASS_ISOLATION_ONLY'
    assert m['referenceDesignPngSha256']==references[m['page']]
    key=(m['page'],m['profile']);assert key not in seen;seen.add(key)
    assert m['fontSha256']=='a3041811a78c361b1de50f953c805e0244951c21c5bd412f7232ef0d899af0da'
    assert tuple(m['wholeWindowPngSizePx'])==struct.unpack('>II',(folder/'window.png').read_bytes()[16:24])==(2560,1600)
    assert m['window']['densityDpi']==360 and m['window']['locale'].startswith('zh') and not m['window']['imeVisible']
    assert m['window']['uiModeNightMeaning']==m['profile'].split('-font-')[0].rsplit('-',1)[-1]
    assert struct.pack('>f',m['window']['fontScale'])==struct.pack('>f',float(m['profile'].rsplit('-font-',1)[-1]))
    initial=m['initialFrameSync']
    assert initial['protocolVersion']==2 and initial['accepted'] and initial['timeoutMs']==5000 and initial['attempts']
    for name,value in m['fileSha256'].items():assert sha(folder/name)==value,(path,name)
    proofs=list(folder.rglob('*-frame-sync.json'));assert proofs
    for proof in proofs:
        require_protocol(proof)
        protocol_count+=1
    isolation=json.loads((folder/'fixture-isolation.json').read_text())
    for name in ('businessDiskByteUnchanged','typedPreferencesRestored','originalLazyIdentityAndInitializedRestored','legacyUnchanged',
    'allActivitiesDestroyed','noServiceBeforeOriginalDelegateRestore','scopedWritersTerminated','allOwnedReadersExitedAndMainBarrier','providerSessionRemoved','mockClockOwnerAbsentBeforeAndAfter'):
        assert isolation[name] is True,(path,name)
    assert isolation['status']=='PASS_ISOLATION_ONLY' and isolation['clipboard']=='NOT_READ_OR_WRITTEN'
    status=isolation['scopedTraceStatusAfterTermination'].split()
    assert status[0]=='closed' and 'dropped=0' in status and 'errors=0' in status and not any(t.startswith('lastError=') for t in status)
    assert all(not thread['alive'] for thread in isolation['ownedReaderThreads'])
    drains=json.loads((folder/'reader-drains.json').read_text());assert drains
    for drain in drains:
        assert drain['status']=='PASS_DRAIN_ONLY' and drain['timeoutMs']==10000 and drain['completionElapsedMs']<=10000
        assert drain['finalNoLiveReaderGuard'] and drain['rounds']
        last=drain['rounds'][-1]
        assert last['afterConfirmationBarrier']==[] and last['confirmedUiSettled']
        for round in drain['rounds']:
            assert {'beforeJoin','afterMainBarrier','newThreadIds','uiSettled','remainingMs'}<=set(round)
            assert all({'id','name','state','alive'}<=set(thread) for thread in round['beforeJoin']+round['afterMainBarrier'])
    fixture=json.loads((folder/'fixture.json').read_text())
    assert fixture['synthetic'] and not fixture['hardwareMeasurement'] and fixture['noServiceNoMockClock']
    assert len(fixture['historyArchive']['items'])==41 and len(fixture['curveArchive']['items'])==41
    assert fixture['mainLegacyId']=='m7-main-legacy-shot'
    assert (folder/'baseline-alignment.json').is_file() and (folder/'geometry.json').is_file()
    if m['page']!='curve':
        canvas=json.loads((folder/'canvas.json').read_text()); assert canvas['pointCount']==31 and canvas['plotHeightDp']>=160
        texts=[op for op in canvas['operations'] if op['operation']=='text'];assert texts
        assert all(op['weight']==400 and not op['fakeBold'] for op in texts)
        paths=[op for op in canvas['operations'] if op['operation']=='path'];assert len(paths)==2 and all(len(op['verticesPx'])==31 for op in paths)
    special=json.loads((folder/'special-states/manifest.json').read_text())
    assert special['completed'] and special['mainConfigurationCountContribution']==0 and special['visualAcceptance']=='ASTRA_PENDING'
    assert {case['case'] for case in special['cases']}==required[m['page']],path
    require_protocol(folder/'window-frame-sync.json','ACTIVITY')
    require_protocol(folder/'special-states/short-window/window-frame-sync.json','ACTIVITY')
    short=next(case for case in special['cases'] if case['case']=='short-window')
    assert short['acceptanceStatus']=='PASS_PROTOCOL2_ONLY_NOT_VISUAL' and short['frameSync']['accepted']
    if m['page']=='detail':
        base=folder/'special-states'
        original=short_detail_capture(base,short['originalCapture'],'states/short-window-detail-original',DEFAULT_ACTIVITY)
        initial=short_detail_capture(base,short['initialCapture'],'short-window',SHORT_ACTIVITY)
        assert original==short['originalDetail']
        assert initial==short['initialDetail'] and initial['actualPlotHeightDp']>=96.0
        assert not original['contentBudget']['shortFallback'] and initial['contentBudget']['shortFallback']
        assert initial['contentScrollViewportPx']>0 and initial['contentScrollChildHeightPx']>=initial['contentScrollViewportPx']
        assert initial['workspaceHeightPx']>=initial['contentBudget']['workspaceMinimumPx']>0
        assert initial['supportingViewportHeightPx']>0 and initial['chartViewportHeightPx']>0
        assert initial['contentBudget']['contentHeightPx']==initial['contentScrollChildHeightPx']
        fixed_identity=('activityIdentity','windowIdentity','decorIdentity','windowTokenIdentity',
            'workspaceIdentity','supportingScrollIdentity','contentScrollIdentity','chartViewportIdentity',
            'buttonIdentity','chartIdentity','listIdentities','headingReadonlyIdentity')
        badge_reach=short['badgeReachability']
        assert set(badge_reach)=={'semanticKey','initialScrolls','requestRectangleReturned','reached','restored','completed'}
        assert badge_reach['semanticKey']=='legacy/readonly' and badge_reach['completed'] is True
        assert badge_reach['initialScrolls']==initial['scrolls']
        assert isinstance(badge_reach['requestRectangleReturned'],bool)
        assert set(badge_reach['reached'])==set(badge_reach['restored'])=={'capture','state'}
        assert badge_reach['reached']['capture']!=badge_reach['restored']['capture']
        badge_state=short_detail_capture(base,badge_reach['reached']['capture'],'short-window/badge-reachable',SHORT_ACTIVITY)
        assert badge_state==badge_reach['reached']['state']
        short_badge_whole(badge_state,initial,fixed_identity)
        badge_restored=short_detail_capture(base,badge_reach['restored']['capture'],'short-window/badge-restored',SHORT_ACTIVITY)
        assert badge_restored==badge_reach['restored']['state']
        assert badge_restored['scrolls']==badge_reach['initialScrolls']==initial['scrolls']
        assert all(badge_restored[key]==initial[key] for key in fixed_identity)
        assert badge_restored['windowLayout']==initial['windowLayout'] and badge_restored['headingReadonly']['semanticId']=='legacy/readonly'
        assert badge_restored['headingReadonly']['textLineBoxes']==initial['headingReadonly']['textLineBoxes']
        chart_reach=short['chartReachability']
        assert chart_reach['completed'] and chart_reach['initialScrolls']==initial['scrolls']
        expected_text=initial['canvasTextOperations']
        assert expected_text and [x['index'] for x in expected_text]==sorted(set(x['index'] for x in expected_text))
        assert [x['semanticKey'] for x in expected_text]==[f"text-{x['index']}" for x in expected_text]
        assert [x['semanticKey'] for x in chart_reach['reached']]==['plot']+[x['semanticKey'] for x in expected_text]
        assert {'legend','pressure-unit','flow-unit','pressure-tick','flow-tick','time-tick'} <= {x['role'] for x in expected_text}
        for position,target_reach in enumerate(chart_reach['reached']):
            phase='short-window/chart-plot-reachable' if target_reach['semanticKey']=='plot' else f"short-window/chart-{target_reach['semanticKey']}-reachable"
            state=short_detail_capture(base,target_reach['capture'],phase,SHORT_ACTIVITY)
            assert all(state[key]==initial[key] for key in fixed_identity)
            assert state['workspaceHeightPx']>0 and state['chartViewportHeightPx']>0
            assert state['chartMappingVerified'] and len(state['chartOriginScreenPx'])==2 and len(state['chartVisibleRectPx'])==4
            assert len(state['canvasTextOperations'])==len(expected_text)
            for before,after in zip(expected_text,state['canvasTextOperations']):
                assert all(before[key]==after[key] for key in ('index','semanticKey','role','text','glyphBoundsPx'))
            if target_reach['semanticKey']=='plot':
                box=state['plotBoundsPx'];origin=state['chartOriginScreenPx'];visible=state['chartVisibleRectPx']
                actual_width=max(0,min(origin[0]+box[2],visible[2])-max(origin[0]+box[0],visible[0]))
                actual_height=max(0,min(origin[1]+box[3],visible[3])-max(origin[1]+box[1],visible[1]))
                assert actual_width>0 and actual_height/state['density']>=96
                assert abs(actual_width-state['visiblePlotWidthPx'])<0.001
                assert abs(actual_height/state['density']-state['visiblePlotHeightDp'])<0.001
                assert target_reach['visiblePlotHeightDp']==state['visiblePlotHeightDp']
            else:
                expected=expected_text[position-1];observed=state['canvasTextOperations'][position-1]
                assert target_reach['operation']==observed and observed['semanticKey']==expected['semanticKey']
                box=observed['glyphBoundsPx'];origin=state['chartOriginScreenPx'];visible=state['chartVisibleRectPx']
                assert len(box)==4 and observed['wholeGlyph']
                assert origin[0]+box[0]>=visible[0] and origin[1]+box[1]>=visible[1]
                assert origin[0]+box[2]<=visible[2] and origin[1]+box[3]<=visible[3]
        chart_restored=short_detail_capture(base,chart_reach['restored']['capture'],'short-window/chart-restored',SHORT_ACTIVITY)
        assert chart_restored==chart_reach['restored']['state'] and chart_restored['scrolls']==initial['scrolls']
        assert initial['actualPaneMode'] in ('split','stacked')
        assert initial['button']['semanticId']=='legacy/detail/full-notice'
        if initial['actualPaneMode']=='split':
            assert short['reachabilityMode']=='SPLIT_FIXED_INITIAL' and initial['secondaryViewportBounded']
            assert whole(initial['button']) and initial['buttonHeightPx']>=108
            assert 'reachableCapture' not in short
        else:
            assert short['reachabilityMode']=='STACKED_REAL_SUPPORT_SCROLL' and not initial['secondaryViewportBounded']
            reached=short_detail_capture(base,short['reachableCapture'],'short-window/reachable',SHORT_ACTIVITY)
            assert reached==short['reachableDetail'] and reached['actualPaneMode']=='stacked'
            for key in ('activityIdentity','windowIdentity','decorIdentity','windowTokenIdentity',
                        'workspaceIdentity','supportingScrollIdentity','contentScrollIdentity','chartViewportIdentity',
                        'buttonIdentity','chartIdentity','listIdentities'):
                assert reached[key]==initial[key]
            assert whole(reached['button']) and reached['buttonHeightPx']>=108
            assert reached['button']['semanticId']=='legacy/detail/full-notice'
        restored=short_detail_capture(base,short['restoredCapture'],'states/short-window-detail-restored',DEFAULT_ACTIVITY)
        assert restored==short['restoredDetail'] and restored['scrolls']==original['scrolls']
        assert restored['actualPaneMode']==original['actualPaneMode']
        assert restored['windowLayout']==short['originalWindowLayout']
        for key in ('activityIdentity','windowIdentity','decorIdentity','windowTokenIdentity',
                    'workspaceIdentity','supportingScrollIdentity','contentScrollIdentity','chartViewportIdentity',
                    'buttonIdentity','chartIdentity','listIdentities'):
            assert restored[key]==original[key]==initial[key]
    if m['page']=='curve':
        expected_targets=['title','metadata']
        for index in range(4):
            expected_targets += [f'metric-{index}-label',f'metric-{index}-number']
            if index<3:expected_targets.append(f'metric-{index}-unit')
        expected_targets += ['notice','full-details']
        long=next(case for case in special['cases'] if case['case']=='long-name-category')
        for label,case,base in (
            ('long-name-category',long,folder/'special-states/long-name-category'),
            ('short-window',short,folder/'special-states/short-window')):
            ref=case['curveDisclosureRef']
            source=referenced_file(folder/'special-states',ref,f'{label}/curve-disclosure.json')
            disclosure=json.loads(source.read_text())
            assert disclosure['evidenceSummaryVersion']==2 and disclosure['referenceBase']=='disclosure_root'
            assert disclosure['case']==label and disclosure['archiveSha256'] and disclosure['selectedFingerprint']
            assert disclosure['selectedIndex']>=0 and disclosure['restored'] and disclosure['nativeInvariantUnchanged']
            assert disclosure['originalWindow']['width'] and disclosure['originalWindow']['height']
            assert disclosure['fixedAtDetail']['pageBack']['complete'] and disclosure['fixedAfterRestore']['pageBack']['complete']
            assert disclosure['fixedAfterRestore']['import']['complete']
            assert disclosure['allLayoutLinesCovered'] and disclosure['fullTargetSet']==expected_targets
            assert [target['semanticKey'] for target in disclosure['targets']]==expected_targets
            restore='curve-short-detail-restored' if label=='short-window' else 'curve-long-detail-restored'
            restored=disclosure['restoreRefs']
            assert set(restored)=={'geometryRef','frameSyncRef'}
            restored_geometry=json.loads(referenced_file(base,restored['geometryRef'],f'states/{restore}/geometry.json').read_text())
            restored_proof=require_protocol(referenced_file(base,restored['frameSyncRef'],f'states/{restore}/window-frame-sync.json'),'ACTIVITY')
            assert restored_geometry==restored_proof['attempts'][restored_proof['acceptedAttempt']-1]['geometryBefore']['target']
            for target in disclosure['targets']:
                count=target['lineCount']
                assert count>0 and target['coveredLines']==list(range(count)) and target['stages']
                assert target['text'] and (not target['fitsViewport'] or target['wholeWhenFits'])
                seen_lines=set()
                for stage in target['stages']:
                    assert stage['visibleLines'] and set(stage['visibleLines'])=={line['line'] for line in stage['lineScreenRects']}
                    assert stage['phase'].startswith(f"curve-{'short' if label=='short-window' else 'long'}-{target['semanticKey']}-lines-")
                    seen_lines.update(stage['visibleLines'])
                    clip=stage['viewportScreenRectPx']
                    assert len(clip)==4 and clip[2]>clip[0] and clip[3]>clip[1]
                    for line in stage['lineScreenRects']:
                        rect=line['rectPx'];assert len(rect)==4 and clip[0]<=rect[0]<=rect[2]<=clip[2] and clip[1]<=rect[1]<rect[3]<=clip[3]
                    if target['fitsViewport']:assert stage['whole']
                    geometry=json.loads(referenced_file(base,stage['geometryRef'],f"states/{stage['phase']}/geometry.json").read_text())
                    proof=require_protocol(referenced_file(base,stage['frameSyncRef'],f"states/{stage['phase']}/window-frame-sync.json"),'ACTIVITY')
                    accepted=proof['attempts'][proof['acceptedAttempt']-1]['geometryBefore']['target']
                    assert geometry==accepted and geometry['disclosureTarget']['visibleLines']==stage['visibleLines']
                    observed=geometry['disclosureTarget']
                    for key in ('lineScreenRects','viewportScreenRectPx','scrollPx','whole'):
                        assert observed[key]==stage[key]
                assert seen_lines==set(range(count))
        require_protocol(folder/'special-states/states/short-window-restored/window-frame-sync.json','ACTIVITY')
    pending.extend(case['acceptanceStatus'] for case in special['cases'] if case.get('acceptanceStatus','').startswith('PENDING'))
    if m['page'] in ('curve','history'):
        business=json.loads((folder/'continuous-list-business.json').read_text())
        assert business['count']==41 and business['realTouchGesture'] and business['exactOffsetAndSelectionRestored'] and business['nativeModelsUnchanged']
    if m['page']=='history':
        row=json.loads((folder/'selected-history-row-visibility.json').read_text())
        assert row['selectedChildIndex']==1 and row['adapterIndex']==row['firstVisiblePosition']+1
        assert row['sameDay']==(row['expectedDay']==row['previousDay'])
        assert row['actualDayVisibility']==('GONE' if row['sameDay'] else 'VISIBLE')
        assert row['onlyExpectedGoneDayExcludedFromTextWhole'] and row['card']['complete']
        for key in ('title','metadata'):
            observed=row[key];assert observed['complete']
            assert all(line['fullViewVisible'] and line['layoutPresent'] and line['lineCount']>0 and
                line['ellipsisCount']==0 and line['layoutHeightPx']<=line['contentHeightPx'] for line in observed['textLineBoxes'])
        if row['sameDay']:
            assert not row['day']['complete'] and row['day']['textLineBoxes'][0]['layoutPresent'] is False
        else:assert row['day']['complete'] and all(line['fullViewVisible'] for line in row['day']['textLineBoxes'])
        routes=json.loads((folder/'special-states/sources-business.json').read_text())
        assert len(routes)==2 and {route['journal'] for route in routes}=={True,False}
        assert all(route['realRouteAndRecreate'] and route['exactReturnState'] and route['nativeModelsUnchanged'] for route in routes)
    if m['page']=='detail':
        detail=json.loads((folder/'detail-reachability.json').read_text());assert len(detail['prose'])==6 and detail['fullOriginalDisclaimerReachable']
assert seen=={(page,f'tablet-user-landscape-{theme}-font-{scale}') for page in ('curve','history','detail') for theme in ('light','dark') for scale in ('1.0','1.3')}
(root/'capture-summary.json').write_text(json.dumps({'realConfigurations':12,'sourceSha':root.name,'visualAcceptance':'ASTRA_PENDING',
'protocol2AcceptedProofCount':protocol_count,'specialStatesCountedAsMainConfigurations':False,'pendingStrictSpecialEvidence':pending,
'allVisualItemsClosed':False},indent=2)+'\n')
print('LEGACY_VISUAL_MIGRATION_RETAINED configurations=12 actualActivities=true visualAcceptance=ASTRA_PENDING')
VERIFY_PY
