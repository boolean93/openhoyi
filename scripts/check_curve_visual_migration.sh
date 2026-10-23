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
sys.path.insert(0,str(Path('scripts').resolve()))
from window_mapping_contract import validate, DEFAULT_ACTIVITY, SHORT_ACTIVITY, TRANSLATED_WINDOW
root = Path(sys.argv[1])
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
manifests = sorted(root.glob('*/*/manifest.json'))
assert len(manifests)==12, f'Expected 12 real Activity configurations, got {len(manifests)}'
seen=set()
def whole(observation):
    if not observation['complete']: return False
    for line in observation['textLineBoxes']:
        if not (line['fullViewVisible'] and line['layoutPresent'] and line['lineCount']>0 and
                line['ellipsisCount']==0 and line['layoutHeightPx']<=line['contentHeightPx']): return False
        for bounds in line['horizontalLayoutLines']:
            if not (bounds['leftPx']>=0 and bounds['rightPx']<=line['contentWidthPx'] and
                    bounds['rightPx']-bounds['leftPx']<=line['contentWidthPx']): return False
    return True
def require_capture(scope, reference, expected_mode):
    required={'image','imageSha256','geometry','geometrySha256','proof','proofSha256'}
    assert set(reference)==required
    paths={name:Path(reference[name]) for name in ('image','geometry','proof')}
    for name,path in paths.items():
        assert not path.is_absolute() and path.parts and all(part not in ('','.','..') for part in path.parts)
        resolved=scope/path
        assert resolved.is_file() and resolved.resolve().is_relative_to(scope.resolve())
        assert sha(resolved)==reference[name+'Sha256']
    image=scope/paths['image']; geometry=json.loads((scope/paths['geometry']).read_text())
    proof=json.loads((scope/paths['proof']).read_text())
    assert proof['protocolVersion']==2 and proof['accepted'] and proof['timeoutMs']==5000
    assert proof['pixelComparison']=='each_equivalent_domain_all_app_pixels_no_tolerance_no_mask'
    assert proof['crossDomainComparison']=='DIAGNOSTIC_ONLY_PIPELINES_NOT_EQUIVALENT'
    assert proof['displayId']==0 and proof['mappingMode']==expected_mode
    assert proof['captureWindowKind']==('EXPLICIT_WINDOW' if expected_mode==TRANSLATED_WINDOW else 'ACTIVITY')
    attempt=proof['attempts'][proof['acceptedAttempt']-1]
    assert attempt['accepted'] and attempt['withinMatchingBudget'] and attempt['geometryUnchanged']
    assert attempt['geometryBefore']==attempt['geometryAfter'] and attempt['windowReadyAtBothEnds']
    assert attempt['windowDomainColorSpacesEqual'] and attempt['displayDomainColorSpacesEqual']
    assert attempt['windowLayerMatch'] and attempt['displayStable']
    for key in ('S1_W1','S1_W2','S1_S2','D1_D2_app'): assert attempt[key]['count']==0,(image,key)
    validate(proof,attempt,expected_mode)
    assert geometry==attempt['geometryBefore']['target'],(image,'geometry not accepted same-sample target')
    artifacts=proof['captureArtifacts']; expected_roles={'display','display-after'} | (
        {'layer-before','layer-after'} if expected_mode==TRANSLATED_WINDOW else set())
    assert {artifact['role'] for artifact in artifacts}==expected_roles
    assert len(artifacts)==len(expected_roles)
    for artifact in artifacts:
        artifact_path=image.parent/artifact['file']
        assert artifact_path.is_file() and artifact_path.parent==image.parent and sha(artifact_path)==artifact['fileSha256']
        dimensions=struct.unpack('>II',artifact_path.read_bytes()[16:24])
        assert list(dimensions)==artifact['bitmap']['sizePx']
        if artifact['role'] in ('layer-before','layer-after'):
            assert list(dimensions)==attempt[artifact['role']]['sizePx']
    assert proof['image']==image.name and proof['imageSha256']==reference['imageSha256']
    return geometry
def require_details(scope, case_folder, report, item_id):
    assert report['itemId']==item_id and report['status']=='PASS_LAYOUT_ONLY_NOT_VISUAL_ACCEPTANCE'
    assert report['visualAcceptance']=='ASTRA_PENDING' and report['expectedMessage']
    assert report['originalScroll']==report['restoredScroll']
    assert report['coveredLines']==list(range(report['lineCount'])) and report['lineCount']>0
    steps=report['steps']
    assert len(steps)>=2 and steps[0]['name']=='initial' and steps[-1]['name']=='restored'
    assert len({step['name'] for step in steps})==len(steps)
    covered=set(); initial_ranges=None
    for step in steps:
        name=step['name']; folder=case_folder/'details'/name
        assert step['directory']==f'details/{name}'
        ref={'image':str(folder.relative_to(scope)/'window.png'),'imageSha256':step['windowSha256'],
            'geometry':str(folder.relative_to(scope)/'geometry.json'),'geometrySha256':step['geometrySha256'],
            'proof':str(folder.relative_to(scope)/'window-frame-sync.json'),'proofSha256':step['proofSha256']}
        geometry=require_capture(scope,ref,TRANSLATED_WINDOW)
        assert geometry['messageText']==report['expectedMessage'] and geometry['lineCount']==report['lineCount']
        assert geometry['layoutWidthPx']==geometry['contentWidthPx']>0
        for key in ('windowIdentity','decorIdentity','windowTokenIdentity'):
            assert geometry[key]==report[key]
        assert geometry['title']['complete'] and geometry['positive']['complete']
        lines=geometry['lines']; assert len(lines)==report['lineCount']
        ranges=[(line['start'],line['end'],line['text']) for line in lines]
        if initial_ranges is None: initial_ranges=ranges
        else: assert ranges==initial_ranges
        assert all(line['ellipsisCount']==0 for line in lines)
        visible={line['index'] for line in lines if line['fullyVisible']}
        assert visible==set(step['coveredLines'])
        for line in lines:
            if line['fullyVisible']:
                x1,y1,x2,y2=line['rectScreenPx']; a,b,c,d=geometry['scrollVisibleRectScreenPx']
                assert (a<=x1<=x2<=c if line['text'].strip()=='' else a<=x1<x2<=c)
                assert b<=y1<y2<=d
        covered.update(visible)
    assert covered==set(range(report['lineCount']))
    assert initial_ranges[0][0]==0 and initial_ranges[-1][1]==len(report['expectedMessage'])
    assert all(initial_ranges[i][1]==initial_ranges[i+1][0] for i in range(len(initial_ranges)-1))
    assert ''.join(row[2] for row in initial_ranges)==report['expectedMessage']
def require_library_special(folder):
    scope=folder/'library-special-scope'
    fixture=json.loads((scope/'fixture.json').read_text())
    assert fixture['specialLibraryScope'] and fixture['actualLibraryCount']==105 and len(fixture['baseLibraryIds'])==103
    owned=json.loads((scope/'special-scope-business.json').read_text())
    assert owned['expectedOwnedCustomWrites']==owned['actualOwnedCustomWrites']==fixture['ownedCustomWriteAttemptsAtPrepare']==2
    assert owned['ownedDocumentIds']==[fixture['document']['id'],fixture['readonlyOwnedDocument']['id']]
    assert owned['specialLibraryCount']==105 and owned['originalMainScopeAlreadyCleaned'] and owned['noService']
    isolation=json.loads((scope/'fixture-isolation.json').read_text())
    assert isolation['status']=='PASS_ISOLATION_ONLY' and isolation['typedPreferencesRestored'] and isolation['businessDiskByteUnchanged']
    assert isolation['allOwnedLegacyReadersExitedAndMainCallbackBarrier'] and isolation['ownedLegacyArchiveSha256']
    names=['long-factory','captured','no-pressure','readonly-flow','four-stage-pressure','short-pressure']
    assert {p.name for p in (scope/'special-states/library').iterdir() if p.is_dir()}==set(names)
    common={f'product/curve-library/summary/{i}/{suffix}' for i in range(3) for suffix in ('label','number')}
    common|={f'product/curve-library/summary/{i}/unit' for i in range(2)}
    common|={'product/curve-library/full-parameters','product/curve-library/use','title','source','availability'}
    for name in names:
        case=scope/'special-states/library'/name
        report=json.loads((case/'case.json').read_text()); model=json.loads((case/'fixture.json').read_text())
        assert report['case']==name and report['itemId']==model['id'] and report['status']=='PASS_LAYOUT_ONLY_NOT_VISUAL_ACCEPTANCE'
        assert report['visualAcceptance']=='ASTRA_PENDING' and report['sourceSelection']['selectedId']==model['id']
        startup=require_capture(scope,report['startupCapture'],DEFAULT_ACTIVITY)['libraryCaseState']
        prepared=require_capture(scope,report['preparedCapture'],DEFAULT_ACTIVITY)['libraryCaseState']
        assert report['startupState']==startup and report['preparedState']==prepared
        assert report['baselineSemantics']=='real_list_click_after_startup; startup_false_preserved'
        assert startup['detailRequested'] is False and startup['list']['selectedId']==report['originalSelection']['selectedId']
        assert prepared['detailRequested'] is True and prepared['list']['selectedId']==report['originalSelection']['selectedId']
        assert report['originalSelection']['adapterItemId']==startup['list']['selectedId']
        assert prepared['list']['query']==startup['list']['selectedId']
        assert report['initialList']==prepared['list'] and report['initialDetailRequested'] is True
        assert report['initialScroll']==[prepared['detailScrollY'],prepared['fallbackScrollY']]
        restored_case=require_capture(scope,report['restoredCapture'],DEFAULT_ACTIVITY)['libraryCaseState']
        assert report['restoredState']==restored_case and report['restorationMismatches']==[]
        assert restored_case==prepared and report['restoredList']==prepared['list']
        assert startup['activityIdentity']==prepared['activityIdentity']==restored_case['activityIdentity']
        assert startup['windowIdentity']==prepared['windowIdentity']==restored_case['windowIdentity']
        assert startup['decorIdentity']==prepared['decorIdentity']==restored_case['decorIdentity']
        assert startup['windowTokenIdentity']==prepared['windowTokenIdentity']==restored_case['windowTokenIdentity']
        assert prepared['hasWindowFocus'] and restored_case['hasWindowFocus'] and prepared['services']==[]
        initial=require_capture(scope,report['initialCapture'],SHORT_ACTIVITY if name=='short-pressure' else DEFAULT_ACTIVITY)
        disclosure=initial['libraryDisclosure']
        assert disclosure['itemId']==model['id'] and disclosure['actualChartTargets']==model['targets']
        assert disclosure['metricValues']==model['metricValues'] and disclosure['metricUnits']==model['metricUnits']
        assert disclosure['selectEnabled']==model['canStart'] and disclosure['assignEnabled']==model['slotEligible']
        assert set(disclosure['common'])==common-{'title','source','availability'}
        assert disclosure['title']['textLineBoxes'][0]['text']==model['name']
        canvas=json.loads((case/'canvas.json').read_text())
        baselines=json.loads((case/'baseline-alignment.json').read_text())
        assert len(baselines)==2
        for index,value in enumerate(baselines):
            assert value['index']==index and value['numberId']==f'product/curve-library/summary/{index}/number'
            assert value['unitId']==f'product/curve-library/summary/{index}/unit'
            assert value['modelUnit']==model['metricUnits'][index] and value['status']!='FAIL'
            if value['status']=='PASS_LAYOUT_ONLY_NOT_VISUAL_ACCEPTANCE':
                assert value['deltaPx']==0 and value['numberShown'] and value['unitShown']
            elif value['status']=='NOT_APPLICABLE_MODEL_NULL_UNIT':
                assert model['metricUnits'][index] is None and value['unitVisibility']==8 and value['numberShown']
            else:
                assert value['status']=='NOT_APPLICABLE_SHORT_FALLBACK' and name=='short-pressure'
                assert disclosure['fallbackText'] and not value['numberShown'] and not value['unitShown']
        assert (case/'CurveActivity-typography.json').is_file(),case
        assert canvas['actualTargets']==model['targets'] and canvas['chartShown']==disclosure['chartShown']
        assert canvas['drawOperations']==disclosure['actualDrawOperations']
        if not model['targets']: assert not disclosure['chartShown'] and canvas['noPressureMeansNoFakePath']
        else: assert disclosure['chartShown'] or disclosure['fallbackText']
        if name=='readonly-flow': assert model['source']=='owned-draft' and not model['canStart'] and not disclosure['editEnabled']
        if name=='long-factory': assert model['source']=='factory'
        if name=='long-factory':
            assert model['longSelectionRule']=='max_CurveDetailsText_render_length_then_id'
            assert model['formatterFullTextLength']>0 and len(model['formatterFullTextSha256'])==64
            assert all(ch in '0123456789abcdef' for ch in model['formatterFullTextSha256'])
        if name=='captured': assert model['source']=='captured'
        if name=='short-pressure':
            assert report['originalWindowLayout']==report['windowLayoutAfterRestore']
            assert report['preRestoreActivityIdentity']==report['postRestoreActivityIdentity']
            assert model['targets']==[20,90,80,60]
            if not report['actualCompact']:
                assert report['originalBackAfterRestore']=='finished_original_activity'
                assert report['destroyedActivityIdentity']==report['preRestoreActivityIdentity']
        reach=json.loads((case/'reachability.json').read_text())
        assert set(reach)==common|({'fallback-line-coverage'} if not disclosure['chartShown'] else set())
        for key,item in ((key,value) for key,value in reach.items() if key!='fallback-line-coverage'):
            assert item['mode'] in ('initial','reachable','short-fallback','model-null-unit')
            if item['mode']=='initial':
                assert item['geometry']=='geometry.json'
                observation=disclosure['common'].get(key,disclosure.get(key))
                assert observation and whole(observation)
            elif item['mode']=='reachable':
                target=require_capture(scope,item['capture'],SHORT_ACTIVITY if name=='short-pressure' else DEFAULT_ACTIVITY)
                actual=target['libraryDisclosure']; observation=actual['common'].get(key,actual.get(key))
                assert observation and whole(observation)
            elif item['mode']=='model-null-unit':
                assert key=='product/curve-library/summary/1/unit' and model['metricUnits'][1] is None and item['expected'] is None
            else: assert name=='short-pressure' and item['actualFallbackText']==disclosure['fallbackText']
        if not disclosure['chartShown']:
            coverage=reach['fallback-line-coverage']; lines=disclosure['fallbackLines']
            assert coverage['lineCount']==len(lines)>0 and coverage['allCovered']==list(range(len(lines)))
            assert all(line['ellipsisCount']==0 for line in lines)
            assert lines[0]['start']==0 and lines[-1]['end']==len(disclosure['fallbackText'])
            assert all(lines[index]['end']==lines[index+1]['start'] for index in range(len(lines)-1))
            assert ''.join(line['text'] for line in lines)==disclosure['fallbackText']
            actual={line['index'] for line in lines if line['fullyVisible']}
            assert sorted(actual)==coverage['initialCovered']
            for segment in coverage['segments']:
                state=require_capture(scope,segment['capture'],SHORT_ACTIVITY if name=='short-pressure' else DEFAULT_ACTIVITY)
                observed=state['libraryDisclosure']['fallbackLines']; assert len(observed)==len(lines)
                assert [(line['start'],line['end'],line['text']) for line in observed]==[(line['start'],line['end'],line['text']) for line in lines]
                assert observed[segment['line']]['fullyVisible'] and all(line['ellipsisCount']==0 for line in observed)
                actual.update(line['index'] for line in observed if line['fullyVisible'])
            assert actual==set(range(len(lines)))
        details=json.loads((case/'details-report.json').read_text())
        require_details(scope,case,details,model['id'])
        restored=require_capture(scope,report['restoredCapture'],DEFAULT_ACTIVITY)
        assert restored['actualRestoreState']['hasWindowFocus']
        assert report['initialList']==report['restoredList']
    preset=json.loads((scope/'preset-eligibility-and-slots/business.json').read_text())
    assert preset['status']=='PASS_MECHANICAL_ONLY_NOT_VISUAL_ACCEPTANCE' and preset['visualAcceptance']=='ASTRA_PENDING'
    chooser=require_capture(scope,preset['chooserCapture'],TRANSLATED_WINDOW)
    assert len(chooser['actualAdapterChoices'])==5 and chooser['choices']==chooser['actualAdapterChoices']
    assert whole(chooser['titleView']) and chooser['titleView']['textLineBoxes'][0]['text']==chooser['title']
    assert len(chooser['visibleRows'])==5
    for index,row in enumerate(chooser['visibleRows']):
        assert row['adapterPosition']==index and row['adapterText']==chooser['choices'][index]
        assert whole(row['child']) and whole(row['label'])
        assert row['label']['textLineBoxes'][0]['text']==chooser['choices'][index]
    assert [step['slot'] for step in preset['steps']]==[1,2,3,4,5]
    for step in preset['steps']:
        require_capture(scope,step['capture'],DEFAULT_ACTIVITY)
        key=f'slot_{step["slot"]}'; before=step['before']; after=step['after']
        assert step['menu']==step['menuNodes']['localizedAdapterChoices']
        assert step['menuNodes']['visibleNodes'] and step['menuNodes']['noIndependentWindowProtocolClaim']
        assert after[key]=={'type':'String','value':preset['qualifiedId']}
        assert {k:v for k,v in before.items() if k!=key}=={k:v for k,v in after.items() if k!=key}
        assert step['actualStoredId']==preset['qualifiedId']
    assert [item['case'] for item in preset['negative']]==['captured','readonly-flow']
    assert all(item['assignAbsent'] and not item['assignEnabled'] and item['slotMap']==preset['steps'][-1]['after']
        and item['actualMenu']==item['actualMenuNodes']['localizedAdapterChoices']
        and item['localizedAssignText'] not in item['actualMenu']
        and item['actualMenuNodes']['visibleNodes'] and item['actualMenuNodes']['noIndependentWindowProtocolClaim']
        for item in preset['negative'])
    assert preset['before']==preset['afterRestore'] and preset['typedPresenceValueRestored'] and preset['businessModelUnchanged']
    require_capture(scope,preset['restoredCapture'],DEFAULT_ACTIVITY)
    route_root=scope/'library-source-routes'
    routes=json.loads((route_root/'business.json').read_text())
    names=['edit-draft','edit-copy-factory','share-page','import-page','legacy-page']
    assert routes['routes']==names and routes['completed']
    assert {p.name for p in (route_root/'routes').iterdir() if p.is_dir()}==set(names)
    classes=['CurveEditorActivity','CurveEditorActivity','CurveShareActivity','CurveImportActivity','LegacyCurveActivity']
    for name,cls in zip(names,classes):
        route=json.loads((route_root/'routes'/name/'business.json').read_text())
        assert route['route']==name and route['status']=='PASS_MECHANICAL_ONLY_NOT_VISUAL_ACCEPTANCE' and route['visualAcceptance']=='ASTRA_PENDING'
        assert route['targetFocused'] and route['targetClass'].endswith('.'+cls)
        assert route['sourceId']==route['intentExtraId']
        content=route['targetContent']
        if name=='edit-draft': assert content['draftId']==fixture['document']['id'] and content['sourceDocument']==fixture['document']
        elif name=='edit-copy-factory': assert content['newDraftId'].startswith('draft-') and content['newDraftId']!=content['factorySourceId'] and content['copyWarningVisible']
        elif name=='share-page': assert content['encodedDocument']==fixture['document'] and not content['externalShareCopyExportInvoked']
        elif name=='import-page': assert content['rawInputEmpty'] and not content['clipboardOrSafReadInvoked']
        else: assert content['ownedArchiveCount']==1 and not content['importOrOriginalFileReadInvoked']
        require_capture(scope,route['targetCapture'],DEFAULT_ACTIVITY)
        require_capture(scope,route['restoredCapture'],DEFAULT_ACTIVITY)
        assert route['before']==route['returned']
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
    if m['page']=='library':
        assert m['libraryVisualCaseCount']==6 and m['libraryBusinessCaseCount']==2 and m['mainLibraryOriginalCount']==104
        assert m['librarySpecialActualCount']==105 and m['isolatedOwnedScopeCount']==2
        require_library_special(folder)

assert seen=={(page,f'tablet-user-landscape-{theme}-font-{scale}') for page in ('library','import','preview') for theme in ('light','dark') for scale in ('1.0','1.3')}
(root/'capture-summary.json').write_text(json.dumps({'realConfigurations':12,'sourceSha':root.name,
    'libraryVisualCasesPerProfile':6,'libraryBusinessCasesPerProfile':2,
    'visualAcceptance':'ASTRA_PENDING','specimenImagesUsedAsProductEvidence':False},indent=2)+'\n')
print('CURVE_VISUAL_MIGRATION_RETAINED configurations=12 actualActivities=true visualAcceptance=ASTRA_PENDING libraryVisualCasesPerProfile=6 libraryBusinessCasesPerProfile=2')
VERIFY_PY
