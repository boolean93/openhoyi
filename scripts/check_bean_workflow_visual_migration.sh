#!/usr/bin/env bash
set -Eeuo pipefail
# M4 real-Activity evidence only, in its independently guarded disposable CI emulator.
[[ "${GITHUB_ACTIONS:-}" == true ]] || { echo 'Bean workflow visual captures require GitHub CI' >&2; exit 1; }
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || { echo 'Disposable emulator required' >&2; exit 1; }
package=io.openhoyi.mobile.mock
source_sha=$(git rev-parse HEAD)
source_dirty=false
[[ -z "$(git status --porcelain)" ]] || source_dirty=true
output_dir="$PWD/build/task-first-evidence/bean-workflow-visual-migration"
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
    adb pull "/sdcard/Android/data/$package/files/bean-workflow-visual-migration/$source_sha" "$output_dir/raw-device/" || true
  fi
  adb logcat -d -s AndroidRuntime:E > "$output_dir/runtime-errors.txt" || true
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
    for bean_workflow_page in preparation batch ledger; do
      adb shell am force-stop "$package"
      python3 - "$output_dir/$product_theme-$font_scale-$bean_workflow_page-result.txt" "$source_sha" "$source_dirty" "$apk_sha" "$product_theme" "$font_scale" "$bean_workflow_page" <<'RUN_PY'
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
 'preparation':('bean-preparation-v1.png','ff154e547f7a8ff73be115e10560646aa4754d18b5807bd6b84d554735701cd7'),
 'batch':('bean-batch-v2.png','81ccf1510d6f780bd0e1e83b06130b9521820f2d51a89e045cfe4841158614ca'),
 'ledger':('bean-ledger-v3.png','114940f8efb35918334c5281d705e1a981394bb989110f617f9a0c7b4cf2f6ba')}
import hashlib
reference_name,reference_sha=references[page]
assert hashlib.sha256((Path('docs/research/task-first-ui-concepts')/reference_name).read_bytes()).hexdigest()==reference_sha
command = ['adb','shell','am','instrument','-w']
for key,value in {'beanWorkflowVisualMigration':'true','disposableCiEmulator':'true','freshProcessVerified':'true',
    'beanWorkflowPage':page,'referencePngSha256':reference_sha,'sourceSha':source,'headSha':os.environ.get('PRODUCT_PR_HEAD_SHA',source),
    'sourceDirty':dirty,'apkSha':apk,'sampleTheme':theme,'expectedFontScale':scale,
    'ciRun':os.environ['GITHUB_RUN_ID'],'ciJob':os.environ['GITHUB_JOB']}.items():
    command += ['-e',key,value]
command += ['io.openhoyi.mobile.mock.test/io.openhoyi.mobile.BrewAudioInstrumentation']
with Path(result_path).open('w') as capture:
    result = subprocess.run(command, stdout=capture, stderr=subprocess.STDOUT, timeout=600)
text = Path(result_path).read_text()
if result.returncode or 'BEAN_WORKFLOW_VISUAL_MIGRATION_CAPTURED fixtures=1 realActivities=true visualAcceptance=ASTRA_PENDING noBle=true isolationVerified=true' not in text:
    print(text)
    raise SystemExit('Real product capture failed; partial evidence retained, no automatic reset/retry')
RUN_PY
    done
  done
done
adb pull "/sdcard/Android/data/$package/files/bean-workflow-visual-migration/$source_sha" "$output_dir/"
python3 - "$output_dir/$source_sha" <<'VERIFY_PY'
from pathlib import Path
import hashlib, json, struct, sys
sys.path.insert(0,str(Path('scripts').resolve()))
from window_mapping_contract import validate, TRANSLATED_WINDOW
root = Path(sys.argv[1])
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
manifests = sorted(root.glob('*/*/manifest.json'))
assert len(manifests)==12, f'Expected 12 real Activity configurations, got {len(manifests)}'
seen=set()
def require_details(folder, case_name, expected_event=None):
    base=folder/'states'/case_name
    report=json.loads((base/'details-disclosure.json').read_text())
    assert report['case']==case_name and report['status']=='PASS_LAYOUT_ONLY_NOT_VISUAL_ACCEPTANCE'
    assert report['originalWindowCallbackAndOwnerVerified'] is True
    assert report['eventId'] and f"eventId={report['eventId']}" in report['originalEvent']
    assert report['eventOrder'].count(report['eventId'])==1
    if expected_event is not None: assert report['eventId']==expected_event
    assert report['expectedMessage'] and report['title'] and report['lineCount']>0
    assert report['initialScroll']==report['restoredScroll'] and report['initialPageScroll']==report['restoredPageScroll']
    assert report['restoredImage']==f'states/{case_name}/restored/window.png'
    assert report['coveredLines']==list(range(report['lineCount']))
    steps=report['steps']
    assert len(steps)>=2 and steps[0]['name']=='initial' and steps[-1]['name']=='restored'
    assert len({step['name'] for step in steps})==len(steps)
    covered=set(); initial_ranges=None
    for step in steps:
        name=step['name']; state=base/name
        assert step['image']==f'states/{case_name}/{name}/window.png'
        assert step['geometry']==f'states/{case_name}/{name}/geometry.json'
        assert step['proof']==f'states/{case_name}/{name}/window-frame-sync.json'
        assert (state/'window.png').is_file() and (state/'geometry.json').is_file()
        assert sha(state/'window.png')==step['imageSha256'] and sha(state/'geometry.json')==step['geometrySha256']
        assert sha(state/'window-frame-sync.json')==step['proofSha256']
        proof=json.loads((state/'window-frame-sync.json').read_text())
        assert proof['protocolVersion']==2 and proof['accepted'] and proof['timeoutMs']==5000
        assert proof['pixelComparison']=='each_equivalent_domain_all_app_pixels_no_tolerance_no_mask'
        assert proof['crossDomainComparison']=='DIAGNOSTIC_ONLY_PIPELINES_NOT_EQUIVALENT'
        assert proof['displayId']==0 and proof['windowIdentity'] and proof['decorIdentity'] and proof['windowTokenIdentity']
        assert proof['captureWindowKind']=='EXPLICIT_WINDOW' and proof['mappingMode']==TRANSLATED_WINDOW
        assert proof['windowIdentity']==report['windowIdentity'] and proof['decorIdentity']==report['decorIdentity']
        assert proof['windowTokenIdentity']==report['windowTokenIdentity']
        accepted=proof['attempts'][proof['acceptedAttempt']-1]
        assert accepted['accepted'] and accepted['withinMatchingBudget'] and accepted['geometryUnchanged']
        assert accepted['geometryBefore']==accepted['geometryAfter'] and accepted['windowReadyAtBothEnds']
        assert accepted['windowDomainColorSpacesEqual'] and accepted['displayDomainColorSpacesEqual']
        assert accepted['windowLayerMatch'] and accepted['displayStable']
        for key in ('S1_W1','S1_W2','S1_S2','D1_D2_app'): assert accepted[key]['count']==0,(state,key)
        validate(proof,accepted,TRANSLATED_WINDOW)
        artifacts=proof['captureArtifacts']
        names={'display':'window.png','display-after':'window-display-after.png',
            'layer-before':'window-layer-before.png','layer-after':'window-layer-after.png'}
        assert {item['role'] for item in artifacts}==set(names)
        for item in artifacts:
            image=state/names[item['role']]
            assert item['file']==names[item['role']] and image.is_file() and sha(image)==item['fileSha256']
            size=struct.unpack('>II',image.read_bytes()[16:24])
            assert list(size)==item['bitmap']['sizePx']
            if item['role'] in ('layer-before','layer-after'):
                assert list(size)==accepted[item['role']]['sizePx']
        geometry=json.loads((state/'geometry.json').read_text())
        assert geometry==accepted['geometryBefore']['target'],(state,'geometry not from accepted same-sample target')
        for key in ('windowIdentity','decorIdentity','windowTokenIdentity','ownerActivityIdentity','messageIdentity'):
            assert geometry[key]==report[key],(state,key)
        assert geometry['message']['textLineBoxes'] and geometry['message']['textLineBoxes'][0]['text']==report['expectedMessage']
        assert geometry['lineGeometrySemantics']=='actual_Layout_line_boxes_screen_origin_from_TextView_baseline_includes_vertical_gravity_and_self_scroll_not_glyph_ink'
        assert len(geometry['layoutOriginScreenPx'])==2 and len(geometry['messageSelfScroll'])==2
        assert geometry['layoutWidthPx']==geometry['contentWidthPx']>0
        assert geometry['title']['complete'] and geometry['positive']['complete']
        for role in ('title','positive'):
            assert geometry[role]['textLineBoxes'] and all(text['fullViewVisible'] and text['layoutPresent'] and
                text['lineCount']>0 and text['ellipsisCount']==0 for text in geometry[role]['textLineBoxes'])
        message_line=geometry['message']['textLineBoxes'][0]
        assert message_line['layoutPresent'] and message_line['lineCount']==report['lineCount'] and message_line['ellipsisCount']==0
        if geometry['messageFitsViewport']:
            assert geometry['message']['complete'] and message_line['fullViewVisible']
        assert geometry['scrollY']==step['scrollY'] and geometry['lineCount']==report['lineCount']
        safe=geometry['safeRectScreenPx']; positive=geometry['positive']['rectPx']
        assert safe[0]<=positive[0]<positive[2]<=safe[2] and safe[1]<=positive[1]<positive[3]<=safe[3]
        lines=geometry['lines']; assert len(lines)==report['lineCount']
        assert [line['index'] for line in lines]==list(range(report['lineCount']))
        ranges=[(line['start'],line['end'],line['text']) for line in lines]
        if initial_ranges is None: initial_ranges=ranges
        else: assert ranges==initial_ranges,(state,'Layout line boundaries changed')
        actual={line['index'] for line in lines if line['fullyVisible']}
        assert actual==set(step['coveredLines']) and all(line['ellipsisCount']==0 for line in lines)
        for line in lines:
            if line['fullyVisible']:
                line_rect=line['screenRectPx']; viewport=geometry['scrollVisibleRectScreenPx']
                assert viewport[0]<=line_rect[0]<line_rect[2]<=viewport[2]
                assert viewport[1]<=line_rect[1]<line_rect[3]<=viewport[3]
        covered.update(actual)
    assert covered==set(range(report['lineCount']))
    assert initial_ranges[0][0]==0 and initial_ranges[-1][1]==len(report['expectedMessage'])
    assert all(initial_ranges[index][1]==initial_ranges[index+1][0] for index in range(len(initial_ranges)-1))
    assert ''.join(item[2] for item in initial_ranges)==report['expectedMessage']
    return report
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
    business=json.loads((folder/'bean-workflow-business.json').read_text())
    assert business['completed'] and business['noMachineOrServiceCommands'], path
    assert (folder/'geometry.json').is_file()
    if m['page']=='preparation':
        assert isolation['noServiceBeforeOriginalDelegateRestore'] and isolation['noHomeRouteCovered']
        owners=isolation['preparationScaleOwnerCleanup']; assert owners
        for owner in owners:
            assert owner['destroyed'] and owner['ownerNull'] and not owner['bound'] and not owner['visible'] and not owner['refresh250msScheduled']
        for phase in ('confirmed-ledger-matched-receipt','post-consume-intent-write-failed'):
            state=folder/'states'/phase
            assert (state/'window.png').is_file() and (state/'geometry.json').is_file(), state
            original=json.loads((state/'window-frame-sync.json').read_text())
            assert original['protocolVersion']==2 and original['accepted'], state
            reach=json.loads((state/'support-description-reachability.json').read_text())
            assert reach['phase']==phase and reach['status']=='PASS_LAYOUT_ONLY_NOT_VISUAL_ACCEPTANCE', state
            assert reach['initialStateImage']==f'states/{phase}/window.png', state
            assert reach['wholeAfter'] and reach['scrollRestoredExactly'] and reach['targetObservationRestoredExactly'], state
            assert reach['fixedActionsUnmovedAfter'] and reach['fixedActionsUnmovedRestored'], state
            assert reach['businessUnchangedAfter'] and reach['businessUnchangedRestored'], state
            assert reach['after']['textLineBoxes'] and all(line['text']==reach['targetText'] and line['fullViewVisible']
                and line['layoutPresent'] and line['lineCount']>0 and line['ellipsisCount']==0
                for line in reach['after']['textLineBoxes']), state
            for step,image_key in (('description-visible','visibleImage'),('description-restored','restoredImage')):
                evidence=state/step
                assert reach[image_key]==f'states/{phase}/{step}/window.png', evidence
                assert (evidence/'window.png').is_file() and (evidence/'geometry.json').is_file(), evidence
                proof=json.loads((evidence/'window-frame-sync.json').read_text())
                assert proof['protocolVersion']==2 and proof['accepted'] and proof['timeoutMs']==5000, evidence
    elif m['page']=='batch':
        report=require_details(folder,'batch-long-reason-details','m4-long-reason-adjust')
        assert report['expectedMessage'].count('盘点修正：保留完整的库存核对说明。')==30
        cases={case['case']:case for case in business['cases']}
        assert 'long-reason-original-detail-disclosure-no-data-truncation' in cases
        assert any(f"eventId={report['eventId']}" in event for event in cases['long-reason-original-detail-disclosure-no-data-truncation']['events'])
    elif m['page']=='ledger':
        require_details(folder,'ledger-selected-event-details')

assert seen=={(page,f'tablet-user-landscape-{theme}-font-{scale}') for page in ('preparation','batch','ledger') for theme in ('light','dark') for scale in ('1.0','1.3')}
(root/'capture-summary.json').write_text(json.dumps({'realConfigurations':12,'sourceSha':root.name,
    'visualAcceptance':'ASTRA_PENDING','specimenImagesUsedAsProductEvidence':False},indent=2)+'\n')
print('BEAN_WORKFLOW_VISUAL_MIGRATION_RETAINED configurations=12 actualActivities=true visualAcceptance=ASTRA_PENDING')
VERIFY_PY
