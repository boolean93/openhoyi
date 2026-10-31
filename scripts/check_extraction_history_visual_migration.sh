#!/usr/bin/env bash
set -Eeuo pipefail
# M5 real-Activity evidence only, in its independently guarded disposable CI emulator.
[[ "${GITHUB_ACTIONS:-}" == true ]] || { echo 'Extraction/history visual captures require GitHub CI' >&2; exit 1; }
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || { echo 'Disposable emulator required' >&2; exit 1; }
package=io.openhoyi.mobile.mock
source_sha=$(git rev-parse HEAD)
source_dirty=false
[[ -z "$(git status --porcelain)" ]] || source_dirty=true
output_dir="$PWD/build/task-first-evidence/extraction-history-visual-migration"
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
    adb pull "/sdcard/Android/data/$package/files/extraction-history-visual-migration/$source_sha" "$output_dir/raw-device/" || true
  fi
  adb logcat -d -s AndroidRuntime:E > "$output_dir/runtime-errors.txt" || true
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
  # End with four real 1.0 pages; this removes delete's explicit 1.3 -> default-1.0 transition,
  # not a persistence-queue ACK. Strict key presence/value restoration below remains mandatory.
  for font_scale in 1.3 1.0; do
    adb shell settings put system font_scale "$font_scale"
    for extraction_history_page in extraction history history-detail comparison; do
      adb shell am force-stop "$package"
      python3 - "$output_dir/$product_theme-$font_scale-$extraction_history_page-result.txt" "$source_sha" "$source_dirty" "$apk_sha" "$product_theme" "$font_scale" "$extraction_history_page" <<'RUN_PY'
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
 'extraction':('extraction-four-series-v3.png','dab980016ff32a5134e1d6ac767c0afad7b2edc1aed741dbac8e5c7e5f9741f2'),
 'history':('history-list-v3.png','f3b9d375ed73a445f9a00752c7a213ca79b1f8c69b7ac327af03062881384283'),
 'history-detail':('history-detail-v2.png','7eaaee545d1cf6b13402d210e49b71453d26f6aef47c6a4a876e9875574f5a6d'),
 'comparison':('brew-comparison-v1.png','7c7f43ebf4ce7508ab302c2aef75753e930dbde196c33b799e2d95c1d6832269')}
import hashlib
reference_name,reference_sha=references[page]
assert hashlib.sha256((Path('docs/research/task-first-ui-concepts')/reference_name).read_bytes()).hexdigest()==reference_sha
command = ['adb','shell','am','instrument','-w']
for key,value in {'extractionHistoryVisualMigration':'true','disposableCiEmulator':'true','freshProcessVerified':'true',
    'extractionHistoryPage':page,'referencePngSha256':reference_sha,'sourceSha':source,'headSha':os.environ.get('PRODUCT_PR_HEAD_SHA',source),
    'sourceDirty':dirty,'apkSha':apk,'sampleTheme':theme,'expectedFontScale':scale,
    'ciRun':os.environ['GITHUB_RUN_ID'],'ciJob':os.environ['GITHUB_JOB']}.items():
    command += ['-e',key,value]
command += ['io.openhoyi.mobile.mock.test/io.openhoyi.mobile.BrewAudioInstrumentation']
with Path(result_path).open('w') as capture:
    result = subprocess.run(command, stdout=capture, stderr=subprocess.STDOUT, timeout=600)
text = Path(result_path).read_text()
if result.returncode or 'EXTRACTION_HISTORY_VISUAL_MIGRATION_CAPTURED fixtures=1 realActivities=true visualAcceptance=ASTRA_PENDING noBle=true isolationVerified=true' not in text:
    print(text)
    raise SystemExit('Real product capture failed; partial evidence retained, no automatic reset/retry')
RUN_PY
    done
  done
done
adb pull "/sdcard/Android/data/$package/files/extraction-history-visual-migration/$source_sha" "$output_dir/"
python3 - "$output_dir/$source_sha" <<'VERIFY_PY'
from pathlib import Path
import hashlib, json, re, struct, sys
root = Path(sys.argv[1])
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
manifests = sorted(root.glob('*/*/manifest.json'))
assert len(manifests)==16, f'Expected 16 real Activity configurations, got {len(manifests)}'
seen=set()
special_captures=0
extraction_special_captures=0
extraction_special_names=['STARTING','STOP_REQUESTED','UNKNOWN_live','no_scale','stale_scale',
    'no_service_ready','manual','historical_reference_toggle','missing_samples',
    'water_reset','sample_gap','journal_unavailable']
extraction_scenario_ids={
    'STARTING':'m5-extraction-starting','STOP_REQUESTED':'m5-extraction-stop-requested',
    'UNKNOWN_live':'m5-extraction-unknown-live','no_scale':'m5-extraction-no-scale',
    'stale_scale':'m5-extraction-stale-scale','no_service_ready':'m5-extraction-no-service-ready',
    'manual':'m5-extraction-manual','historical_reference_toggle':'m5-extraction-historical-reference-toggle',
    'missing_samples':'m5-extraction-missing-samples','water_reset':'m5-extraction-water-reset',
    'sample_gap':'m5-extraction-sample-gap','journal_unavailable':'m5-extraction-journal-unavailable'}
assert list(extraction_scenario_ids)==extraction_special_names
assert len(set(extraction_scenario_ids.values()))==12
assert all(re.fullmatch(r'[A-Za-z0-9-]{1,64}',value) for value in extraction_scenario_ids.values())
expected_specials={'missing-samples':'m5-history-extra-38','no-notes':'m5-history-extra-6',
    'unknown-observation':'m5-history-extra-7','sample-gap':'m5-history-extra-8','water-reset':'m5-history-extra-9'}
def require_proof(path):
    assert path.is_file(),('Missing expected protocol2 proof',str(path))
    proof=json.loads(path.read_text())
    assert proof['protocolVersion']==2 and proof['accepted'] and proof['timeoutMs']==5000,path
    assert proof['windowLayerMatch'] and proof['displayStable'],path
    attempt=proof['attempts'][proof['acceptedAttempt']-1]
    assert attempt['accepted'] and attempt['withinMatchingBudget'] and attempt['geometryUnchanged'] and attempt['windowReadyAtBothEnds'],path
    assert attempt['windowDomainColorSpacesEqual'] and attempt['displayDomainColorSpacesEqual'],path
    completed=[x for x in attempt['budget'] if x['stage']=='comparison_complete']
    assert len(completed)==1 and completed[0]['elapsedMs']<=5000 and completed[0]['remainingMs']>=0,path
    for key in ('S1_W1','S1_W2','S1_S2','D1_D2_app'):
        assert attempt[key]['count']==0,(path,key)
    return proof

def validate_same_frame_values(geometry, typography, pairs, before, after):
    assert geometry==before==after, 'Accepted before/after target differs from saved geometry'
    assert geometry['typographyEvidence']==typography, 'Typography is not accepted-target typography'
    assert geometry['baselinePairs']==pairs, 'Baseline file is not accepted-target baseline data'
    assert geometry['baselinePairCount']==len(pairs)
    ids=geometry['baselinePairIds']
    assert ids==[pair['semanticId'] for pair in pairs] and len(ids)==len(set(ids))
    views={}
    for view in typography['views']:
        tag=view.get('tag')
        if tag and (tag.endswith('/number') or tag.endswith('/unit')):
            assert tag not in views, ('Duplicate typography tag',tag)
            views[tag]=view
    expected=[tag[:-len('/number')] for tag in views if tag.endswith('/number') and tag[:-len('/number')]+'/unit' in views]
    assert set(ids)==set(expected), ('Visible number/unit pair missing',ids,expected)
    for pair in pairs:
        prefix=pair['semanticId']
        for role in ('number','unit'):
            item=pair[role]; view=views[prefix+'/'+role]
            assert type(item['identity']) is int and item['identity']>0
            assert item['present'] is True and item['text']==view['text']
            assert item['attached'] is True and item['isShown'] is True
            assert item['layoutPresent'] is True and item['viewLayoutRequested'] is False
            assert item['valid'] is True and item['invalidReason'] is None
            assert type(item['localBaselinePx']) is int and item['localBaselinePx']>=0
            assert len(item['originOnScreenPx'])==2 and all(type(x) is int for x in item['originOnScreenPx'])
            assert type(item['screenBaselinePx']) is int
            assert item['originOnScreenPx'][1]+item['localBaselinePx']==item['screenBaselinePx']
            layout=view['actualLayout']
            assert layout['viewOriginOnScreenPx']==item['originOnScreenPx']
            assert layout['viewAttachedToWindow'] is True and layout['viewLayoutRequested'] is False
            assert layout['displayedLayoutConfirmed'] is True and layout['status']=='actual_layout_api'
            assert view['baselinePx']==item['localBaselinePx']
            assert pair[role+'BaselineOnScreenPx']==item['screenBaselinePx']
        assert pair['numberBaselineOnScreenPx']==pair['unitBaselineOnScreenPx'], ('Exact baseline mismatch',prefix,pair)
        assert pair['status']=='PASS_EXACT_BASELINE_ONLY', ('Missing exact baseline result',prefix)

def require_same_frame_capture(folder, proof):
    assert proof['captureWindowKind']=='ACTIVITY',folder
    geometry=json.loads((folder/'geometry.json').read_text())
    typography_files=list(folder.glob('*Activity-typography.json'))
    assert len(typography_files)==1, ('Missing or duplicate Activity typography',folder)
    typography=json.loads(typography_files[0].read_text())
    pairs=json.loads((folder/'baseline-alignment.json').read_text())
    attempt=proof['attempts'][proof['acceptedAttempt']-1]
    validate_same_frame_values(geometry,typography,pairs,
        attempt['geometryBefore']['target'],attempt['geometryAfter']['target'])

def require_extraction_plot(folder):
    frame=require_proof(folder/'window-frame-sync.json')
    evidence=json.loads((folder/'extraction-plot-budget.json').read_text())
    assert evidence['frameSync']==frame
    assert evidence['plotView']['screenMappingVerified']
    assert evidence['actualPlotHeightDp']>=96 and evidence['visiblePlotHeightDp']>=96,(folder,evidence)
    assert evidence['axisInk'] and all(x['completeInVisiblePlotView'] for x in evidence['axisInk'])
    geometry=json.loads((folder/'geometry.json').read_text())
    chart=next(x for x in geometry['charts'] if x['class']=='ExtractionOverlayView')
    plots=[x['boundsPx'] for x in chart['canvasEvidence'] if x['operation']=='plot']
    assert len(plots)==1 and abs((plots[0][3]-plots[0][1])/(geometry['densityDpi']/160)-evidence['actualPlotHeightDp'])<0.01
    return evidence

def require_chart_header(folder,metrics,caption=False,action='stop'):
    expected=[f'legend-{name}' for name in metrics]+['explanation']+(['reference-caption'] if caption else [])
    reach=json.loads((folder/'chart-header-reachability.json').read_text())
    assert reach['completed'] and reach['targetKeys']==expected
    assert [x['semanticKey'] for x in reach['initialTargets']]==expected
    assert [x['semanticKey'] for x in reach['reached']]==expected
    assert reach['initialScrollPx']==reach['restoredScrollPx'] and reach['exactScrollRestored']
    state=reach['initialState']
    assert reach['restoredState']==state
    assert state['chartTraceIdentity']==state['traceIdentity']
    assert state['fixedAction']['semanticId']==f'product/extraction/{action}'
    fixed=state['fixedAction']
    assert (fixed['startVisibility']==0)==(action=='start')
    assert (fixed['stopVisibility']==0)==(action=='stop')
    assert {fixed['startVisibility'],fixed['stopVisibility']}=={0,8}
    for key in ('serviceIdentity','chartIdentity','headerIdentity','supportScrollIdentity','supportScrollPx',
        'shotState','manualShotActive','traceIdentity','curveId','ownerPoints','chartPoints','chartReference',
        'referenceId','referenceCurveId','journalExport','journalResultFailed','fixedAction'):
        assert key in state,(folder,key)
    assert len(state['supportScrollPx'])==2 and all(isinstance(x,int) for x in state['supportScrollPx'])
    def fixed_geometry(phase):
        geometry=json.loads((phase/'geometry.json').read_text())
        matches=[x for x in geometry['nodes'] if x.get('semanticId')==fixed['semanticId']]
        assert len(matches)==1 and matches[0]['rectPx']==fixed['rectPx']
        assert matches[0]['complete']
    require_extraction_plot(folder)
    fixed_geometry(folder)
    density=json.loads((folder/'geometry.json').read_text())['densityDpi']/160
    for initial,reached in zip(reach['initialTargets'],reach['reached']):
        assert initial['identity']==reached['identity']
        if initial['semanticKey'].startswith('legend-'):
            for item in (initial,reached):
                assert item['minimumTouchDp']==48 and abs(item['density']-density)<0.001
                assert item['touchWidthPx']>=48*density and item['touchHeightPx']>=48*density
        phase=folder/'states'/f"chart-header-{reached['semanticKey']}-reachable"
        require_extraction_plot(phase)
        fixed_geometry(phase)
        assert reached['frameSync']==json.loads((phase/'window-frame-sync.json').read_text())
        assert reached['state']==state
        require_text_layout(reached['layout'])
    require_extraction_plot(folder/'states/chart-header-restored')
    fixed_geometry(folder/'states/chart-header-restored')
    return reach

def require_special_support(folder,name):
    basic=['reference-choice','overview']
    expected={'UNKNOWN_live':['choose-beans',*basic,'prepare','cancel-prepare'],
        'manual':basic+['prepare','cancel-prepare'],
        'no_service_ready':['connect-devices','choose-beans',*basic,'prepare','cancel-prepare']}.get(name,basic)
    reach=json.loads((folder/'extraction-support-reachability.json').read_text())
    assert reach['completed'] and reach['case']==name and reach['noToolClicks']
    assert reach['targetKeys']==expected and [x['semanticKey'] for x in reach['initialTargets']]==expected
    assert [x['semanticKey'] for x in reach['reached']]==expected and reach['chooseCurveVisibility']==8
    assert reach['initialScrollPx']==reach['restoredScrollPx'] and reach['initialState']==reach['restoredState']
    state=reach['initialState']; assert len(state['headerScrollPx'])==2
    assert [x['semanticKey'] for x in state['toolStates']]==['connect-devices','choose-beans','reference-choice',
        'overview','prepare','cancel-prepare']
    assert [x['semanticKey'] for x in state['toolStates'] if x['effectiveShown']]==expected
    assert state['preparationToolsVisibility']==(0 if name in ('UNKNOWN_live','manual','no_service_ready') else 8)
    assert state['preparationToolsEffectiveShown']==(name in ('UNKNOWN_live','manual','no_service_ready'))
    assert all(x['visibility'] in (0,8) and x['parentVisibility'] in (0,8) for x in state['toolStates'])
    assert all(x['enabled']==(x['semanticKey'] not in ('prepare','cancel-prepare') and
        not (name=='manual' and x['semanticKey']=='reference-choice')) for x in state['toolStates'])
    assert state['fixedAction']['semanticId']==f"product/extraction/{'start' if name in ('manual','no_service_ready') else 'stop'}"
    assert state['chartTraceIdentity']==state['traceIdentity']
    assert state['journalResultFailed']==(name=='journal_unavailable')
    def fixed_geometry(phase):
        observed=json.loads((phase/'geometry.json').read_text())
        fixed=state['fixedAction']; matches=[x for x in observed['nodes'] if x.get('semanticId')==fixed['semanticId']]
        assert len(matches)==1 and matches[0]['rectPx']==fixed['rectPx'] and matches[0]['complete']
    fixed_geometry(folder)
    density=json.loads((folder/'geometry.json').read_text())['densityDpi']/160
    for first,reached in zip(reach['initialTargets'],reach['reached']):
        assert first['identity']==reached['identity'] and first['enabled']==reached['enabled'] and first['text']==reached['text']
        for target in (first,reached):
            assert abs(target['density']-density)<0.001 and target['widthPx']>=48*density and target['heightPx']>=48*density
        phase=folder/'states'/f"extraction-support-{reached['semanticKey']}-reachable"
        assert reached['frameSync']==require_proof(phase/'window-frame-sync.json')
        assert reached['state']==state and len(reached['scrollPx'])==2
        require_text_layout(reached['layout']);fixed_geometry(phase)
    text=reach['initialText'];assert [x['semanticKey'] for x in text] in (["device-summary"],["device-summary","preparation-summary"])
    def complete_text(layout):
        return layout['complete'] and all(line['fullViewVisible'] and line['layoutPresent'] and line['lineCount']>0 and
            line['ellipsisCount']==0 and line['layoutHeightPx']<=line['contentHeightPx'] and
            all(row['leftPx']>=0 and row['rightPx']<=line['contentWidthPx'] for row in line['horizontalLayoutLines'])
            for line in layout['textLineBoxes'])
    clipped=[x['semanticKey'] for x in text if not complete_text(x['layout'])]
    assert [x['semanticKey'] for x in reach['reachedText']]==clipped
    for reached in reach['reachedText']:
        first=next(x for x in text if x['semanticKey']==reached['semanticKey'])
        assert first['identity']==reached['identity'] and reached['state']==state
        original_lines=first['layout']['textLineBoxes'];reached_lines=reached['layout']['textLineBoxes']
        assert len(original_lines)==len(reached_lines) and all(a['text']==b['text'] for a,b in zip(original_lines,reached_lines))
        phase=folder/'states'/f"extraction-support-{reached['semanticKey']}-reachable"
        assert reached['frameSync']==require_proof(phase/'window-frame-sync.json')
        require_text_layout(reached['layout']);fixed_geometry(phase)
    restored=folder/'states/extraction-support-restored'
    assert reach['restoredFrameSync']==require_proof(restored/'window-frame-sync.json')
    fixed_geometry(restored)
    return reach

def require_text_layout(layout,skip_taste=False):
    assert layout['complete']
    for text in layout['textLineBoxes']:
        if skip_taste and text.get('semanticId')=='full-text-details':continue
        assert text['fullViewVisible'] and text['layoutPresent'] and text['lineCount']>0
        assert text['ellipsisCount']==0 and text['layoutHeightPx']<=text['contentHeightPx']
        assert all(x['leftPx']>=0 and x['rightPx']<=text['contentWidthPx'] for x in text['horizontalLayoutLines'])

def require_reach(folder,page,expected_warning=False):
    if page=='history':
        reach=json.loads((folder/'history-preview-reachability.json').read_text())
        assert reach['completed'] and reach['initialScrollY']==reach['restoredScrollY']==0
        assert reach['exactScrollRestored'] and reach['permanentExplanationIdentityPreserved'] and reach['readOnlyJournalUnchanged']
        assert [x['target'] for x in reach['targets']]==['chart','explanation','note-0','note-1']
        assert all(x['visibility']['complete'] for x in reach['targets'])
        phases=[f'history-preview-{x}' for x in ('chart-reachable','explanation-reachable','note-0-reachable','note-1-reachable','restored')]
    elif page=='history-detail':
        reach=json.loads((folder/'detail-business.json').read_text())
        assert reach['completed'] and reach['savedNotesReachable'] and reach['exactScrollRestored']
        assert reach['initialScrollPx']==reach['restoredScrollPx'] and reach['shotIdUnchanged'] and reach['journalUnchanged'] and reach['fixedEditComplete']
        assert len(reach['targets'])==6 and len({x['target'] for x in reach['targets']})==6
        for target in reach['targets']:require_text_layout(target['layout'])
        assert all(x['visibility']['complete'] for x in reach['targets'])
        phases=[f'detail-note-{index}-reachable' for index in range(6)]+['detail-notes-restored']
    else:
        reach=json.loads((folder/'comparison-reachability.json').read_text())
        assert reach['completed'] and reach['exactScrollRestored'] and reach['idsAndMetricUnchanged'] and reach['journalUnchanged']
        assert reach['initialScrollPx']==reach['restoredScrollPx'] and len(reach['initialScrollPx'])==2
        assert reach['summaryRowCount']==4 and reach['tasteTargetCount']==2 and reach['toolbarControlCount']==7
        expected={'comparison-first','comparison-second','comparison-swap',
            'comparison-metric-PRESSURE','comparison-metric-WATER','comparison-metric-WEIGHT','comparison-metric-TEMPERATURE',
            'comparison-source-legend','comparison-source-1','comparison-source-2',
            'summary-header','summary-duration','summary-end-scale','summary-taste','taste-A','taste-B',
            'detail-A','notes-A','detail-B','notes-B'}
        # The unknown state requirement comes from the declared source fixture, never inferred from collected target count.
        assert reach['expectedUnknownWarning']==expected_warning==('UNKNOWN' in reach['selectedSourceStatuses'])
        if expected_warning:expected.add('comparison-unknown-warning')
        assert reach['originalFourActionIdentitiesAndOwnershipVerified']
        actual=[target['semanticKey'] for target in reach['targets']]
        assert set(actual)==expected and len(actual)==len(expected)==reach['expectedTargetCount']
        assert set(reach['requiredSemanticKeys'])==expected and len(reach['requiredSemanticKeys'])==len(expected)
        assert all(x['visibility']['complete'] for x in reach['targets'])
        for target in reach['targets']:
            if not target['tasteEllipsisRetainsFullDialogSemantics']:require_text_layout(target['layout'],skip_taste=True)
        if expected_warning:
            warning=[(index,target) for index,target in enumerate(reach['targets'])
                     if target['semanticKey']=='comparison-unknown-warning']
            assert len(warning)==1 and len(expected)==21
            index,target=warning[0]
            require_text_layout(target['layout'])
            require_proof(folder/'states'/f'comparison-target-{index}-reachable'/'window-frame-sync.json')
        assert reach['tasteBusinessDialogNotInvoked']
        phases=[f'comparison-target-{index}-reachable' for index in range(len(expected))]+['comparison-scrolls-restored']
    for phase in phases:require_proof(folder/'states'/phase/'window-frame-sync.json')

def expected_history_legend(current,reference):
    has_weight=any(point['weightHundredthsGram'] is not None for point in current+reference)
    return ['chart-metric:PRESSURE','chart-metric:WATER']+(['chart-metric:WEIGHT'] if has_weight else [])+['chart-metric:TEMPERATURE']

def require_embedded_header(evidence,expected_warning):
    budget=evidence['budget']
    for key in ('firstScreenMinimumBeforePx','firstScreenMinimumResolvedPx','firstScreenBudgetSatisfiable',
        'titleNaturalWidthPx','warningNaturalWidthPx','moreOccupationPx','titleWarningRowAvailableWidthPx',
        'titleWarningGapPx','titleWarningWidthFits','titleWarningInlineCondition','titleWarningInlineResolved'):
        assert key in budget,key
    assert budget['titleNaturalWidthPx']>0 and budget['moreOccupationPx']>0 and budget['titleWarningGapPx']>0
    warning=evidence['warning']
    assert (warning is not None)==expected_warning
    fits=expected_warning and budget['titleNaturalWidthPx']+budget['titleWarningGapPx']+budget['warningNaturalWidthPx']<=budget['titleWarningRowAvailableWidthPx']
    inline=fits and budget['firstScreenMinimumBeforePx']>budget['contentViewportPx']
    assert budget['titleWarningWidthFits']==fits and budget['titleWarningInlineCondition']==inline
    assert budget['titleWarningInlineResolved']==inline
    assert budget['firstScreenBudgetSatisfiable']==(budget['firstScreenMinimumResolvedPx']<=budget['contentViewportPx'])
    assert budget['firstScreenMinimumResolvedPx']<=budget['firstScreenMinimumBeforePx']
    if inline:assert budget['firstScreenMinimumResolvedPx']<budget['firstScreenMinimumBeforePx']
    else:assert budget['firstScreenMinimumResolvedPx']==budget['firstScreenMinimumBeforePx']
    if expected_warning:
        assert budget['warningNaturalWidthPx']>0 and warning['semanticId']=='product/history/warning'
        assert evidence['warningText']==warning['textLineBoxes'][0]['text'] and evidence['warningText']
        assert evidence['warningScreenVisibility']['complete']
        require_text_layout(warning)
    else:
        assert budget['warningNaturalWidthPx']==0 and evidence['warningText'] is None and evidence['warningScreenVisibility'] is None

def require_history_special(folder,window,empty_expected,current,reference,expected_warning):
    evidence=window['embeddedHistoryPreview']
    assert json.loads((folder/'history-initial-chart.json').read_text())==evidence
    assert evidence['samePermanentProducerView'] and evidence['externalSiblingAfterChart'] and evidence['sameProjectionSamplingDescription']
    budget=evidence['budget'];assert budget['minimumPlotDp']==96
    require_embedded_header(evidence,expected_warning)
    assert budget['explanationPlacement']=='same_permanent_producer_view_after_complete_chart_before_notes'
    if evidence['actualPlotHeightDp'] is None:
        assert empty_expected and evidence['actualSampleCount']==0
        assert not any(x.get('operation')=='path' for x in evidence['actualCanvas'])
    else:
        assert evidence['actualPlotHeightDp']>=96 and evidence['initialVisiblePlotHeightDp']>=96
        if window['fontScale']==1.0:
            assert evidence['chart']['complete'] and evidence['axisInk']
            assert all(x['completeInVisiblePlotView'] for x in evidence['axisInk'])
    assert [action['observation']['semanticId'] for action in evidence['legendActions']]==expected_history_legend(current,reference)
    assert all(x['screenVisibility']['complete'] and x['heightPx']>=108 for x in evidence['legendActions'])

def require_standalone(folder,window,empty_expected):
    evidence=window['standaloneHistoryPlot']
    assert json.loads((folder/'standalone-history-plot.json').read_text())==evidence
    assert evidence['budgetSource']=='deep_copy_existing_StandaloneHistoryPlot.productLayoutEvidence'
    budget=evidence['budget']
    assert budget['minimumPlotDp']==96
    for key in ('availableHeightPx','availableWidthPx','verticalChromeNaturalPx','resolvedChromeNaturalPx',
                'metricsInlineNaturalWidthPx','chartNativeHeaderNaturalPx','chartActualPaintAxisMinimumPx','chartMinimumPx','primaryBudgetSatisfiable'):
        assert key in budget,(folder,key)
    assert evidence['emptySamples']==empty_expected
    if evidence['plotPresent']:
        assert evidence['actualPlotHeightDp']>=96 and evidence['visiblePlotHeightDp']>=96
        assert evidence['plotView']['screenMappingVerified']
        assert evidence['plotBoundsScreenPx'] and evidence['visiblePlotBoundsScreenPx']
    else:
        assert empty_expected and evidence['actualSampleCount']==0
        assert not any(x.get('operation')=='path' for x in evidence['actualCanvas'])

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
    require_proof(folder/'window-frame-sync.json')
    for name,value in m['fileSha256'].items(): assert sha(folder/name)==value, (path,name)
    isolation=json.loads((folder/'fixture-isolation.json').read_text())
    assert isolation['sampleFailures']==[] and isolation['sampleFailureScopeCase']==folder.name and isolation['sampleFailureOwnedScope']
    assert isolation['status']=='PASS_ISOLATION_ONLY' and isolation['businessDiskByteUnchanged'] and isolation['typedPreferencesRestored']
    assert 'androidxProfileInstallerMarkerBeforeSha256' in isolation and 'androidxProfileInstallerMarkerAfterSha256' in isolation
    for marker in ('androidxProfileInstallerMarkerBeforeSha256','androidxProfileInstallerMarkerAfterSha256'):
        assert isolation[marker] is None or (isinstance(isolation[marker],str) and len(isolation[marker])==64)
    proof_files=list(folder.rglob('*-frame-sync.json'))
    assert proof_files, path
    for proof_file in proof_files:
        proof=require_proof(proof_file)
        if proof['captureWindowKind']=='ACTIVITY': require_same_frame_capture(proof_file.parent,proof)
    assert isolation['originalLazyIdentityAndInitializedRestored'] and isolation['legacyUnchanged']
    assert isolation['allActivitiesDestroyed'] and isolation['historyDetailReadersTerminated'] and isolation['comparisonReaderPoolsTerminated']
    assert isolation['mainQueueBarrierAfterReaders'] and isolation['scopedWritersTerminated']
    assert (folder/'geometry.json').is_file() and (folder/'fixture.json').is_file()
    fixture=json.loads((folder/'fixture.json').read_text())
    if m['page']=='history':
        preview=m['window']['embeddedHistoryPreview']
        assert preview['budgetSource']=='deep_copy_existing_EmbeddedHistoryPreview.productLayoutEvidence'
        assert preview['samePermanentProducerView'] and preview['externalSiblingAfterChart'] and preview['sameProjectionSamplingDescription']
        budget=preview['budget']
        require_embedded_header(preview,False)
        for name in ('availableHeightPx','fixedFooterNaturalPx','contentViewportPx','headerAndMetricsNaturalPx',
            'chartNativeHeaderNaturalPx','chartActualPaintAxisMinimumPx','chartMinimumPx','chartMarginsPx','fullNaturalPx',
            'chartLegendNaturalPx','externalExplanationNaturalPx','externalExplanationTopMarginPx','chartOverlayPaddingPx'):
            assert name in budget,(path,name)
        assert budget['minimumPlotDp']==96 and budget['chartLegendNaturalPx']>0 and budget['externalExplanationNaturalPx']>0
        assert budget['explanationPlacement']=='same_permanent_producer_view_after_complete_chart_before_notes'
        assert preview['actualPlotHeightDp']>=96 and preview['initialVisiblePlotHeightDp']>=96
        assert fixture['historyReferenceSamples']==[] and any(point['weightHundredthsGram'] is not None for point in fixture['firstSamples'])
        assert [action['observation']['semanticId'] for action in preview['legendActions']]==expected_history_legend(fixture['firstSamples'],fixture['historyReferenceSamples'])
        assert all(action['screenVisibility']['complete'] and action['heightPx']>=108 for action in preview['legendActions'])
        assert json.loads((folder/'history-initial-chart.json').read_text())==preview
        assert json.loads((folder/'geometry.json').read_text())['embeddedHistoryPreview']==preview
        if m['window']['fontScale']==1.0:
            assert preview['chart']['complete'] and preview['axisInk']
            assert all(ink['completeInVisiblePlotView'] for ink in preview['axisInk'])
        reach=json.loads((folder/'history-preview-reachability.json').read_text())
        assert reach['completed'] and reach['initialScrollY']==reach['restoredScrollY']==0
        assert reach['exactScrollRestored'] and reach['permanentExplanationIdentityPreserved'] and reach['readOnlyJournalUnchanged']
        assert [target['target'] for target in reach['targets']]==['chart','explanation','note-0','note-1']
        assert all(target['visibility']['complete'] for target in reach['targets'])
        for phase in ('chart-reachable','explanation-reachable','note-0-reachable','note-1-reachable','restored'):
            expected=folder/'states'/f'history-preview-{phase}'/'window-frame-sync.json'
            assert expected.is_file(),('Missing expected History reach/restore proof',str(expected))
            proof=json.loads(expected.read_text());assert proof['protocolVersion']==2 and proof['accepted'] and proof['timeoutMs']==5000
    if m['page']!='extraction':
        require_reach(folder,m['page'])
        if m['page']=='history-detail':require_standalone(folder,m['window'],False)
        assert isolation['noServiceBeforeOriginalDelegateRestore']
        if m['page']=='history':
            assert isolation['historyRootSingleReadOnlyServiceObserved'] and isolation['historyRootServiceDestroyed']
        else:
            assert not isolation['historyRootSingleReadOnlyServiceObserved'] and not isolation['historyRootServiceDestroyed']
        business_name={'history':'history-business.json','history-detail':'detail-business.json','comparison':'comparison-business.json'}[m['page']]
        business=json.loads((folder/business_name).read_text()); assert business['completed']
        special=json.loads((folder/'special-states/manifest.json').read_text())
        assert special['sourceSha']==m['sourceSha'] and special['profile']==m['profile'] and special['page']==m['page']
        assert special['completed'] and special['realProductActivity'] and special['synthetic']
        assert special['mainConfigurationCountContribution']==0 and special['actualAdditionalActivityCaptures']==5
        assert special['visualAcceptance']=='ASTRA_PENDING' and set(special['expectedCases'])==set(expected_specials)
        assert len(special['cases'])==5 and {case['case'] for case in special['cases']}==set(expected_specials)
        assert m['historySpecialStates']==special
        for case in special['cases']:
            assert case['status']=='PASS_MECHANICAL_ONLY_NOT_VISUAL_ACCEPTANCE' and case['visualAcceptance']=='ASTRA_PENDING'
            assert case['shotId']==expected_specials[case['case']] and case['page']==m['page']
            assert case['readOnlyModelsUnchanged']
            if m['page']=='history':
                assert case['singleReadOnlyHistoryRootService'] and not case['noServiceOrClockOverride']
            else:
                assert not case['singleReadOnlyHistoryRootService'] and case['noServiceOrClockOverride']
            assert case['actualRawSamples']==case['declaredSamples']
            target=folder/'special-states'/case['case']
            assert json.loads((target/'case.json').read_text())==case
            assert struct.unpack('>II',(target/'window.png').read_bytes()[16:24])==(2560,1600)
            observed=json.loads((target/'geometry.json').read_text())
            assert observed['densityDpi']==m['window']['densityDpi'] and observed['fontScale']==m['window']['fontScale']
            assert observed['uiModeNightMeaning']==m['window']['uiModeNightMeaning'] and not observed['imeVisible']
            require_proof(target/'window-frame-sync.json')
            require_reach(target,m['page'],expected_warning=case['observation']['status']=='UNKNOWN')
            if m['page']=='history-detail':require_standalone(target,observed,case['case']=='missing-samples')
            if m['page']=='history':
                assert case['declaredReferenceSamples']==[]
                if case['case']=='missing-samples':assert case['declaredSamples']==[]
                require_history_special(target,observed,case['case']=='missing-samples',case['declaredSamples'],case['declaredReferenceSamples'],case['case']=='unknown-observation')
            assert case['frameSync']['protocolVersion']==2 and case['frameSync']['accepted']
            segments=case['actualProjectedSegments']; vertices=case['actualCanvasCurrentMetricVertexCounts']
            assert vertices==[len(segment) for segment in segments] and len(segments)==case['actualCanvasCurrentMetricPathCount']
            if case['case']=='missing-samples':
                assert not case['actualRawSamples'] and not segments and not vertices
                if m['page']!='comparison': assert case['displayedElapsed']=='30.0' and case['displayedWeight']=='36.00' and case['displayedLastSampleWater']=='—'
            elif case['case']=='unknown-observation':
                fact=case['observation']; assert fact['status']=='UNKNOWN' and fact['endedAtMs'] is None and fact['elapsedMs'] is None and fact['weightHundredthsGram'] is None
                assert case['unknownIsNotEnded'] and case['unknownWarning']['complete']
                if m['page']!='comparison': assert case['displayedElapsed']=='—' and case['displayedWeight']=='—'
            elif case['case']=='no-notes':
                if m['page']=='comparison': assert case['blankNotesPlaceholder']=='—' and case['blankTasteDisclosureDisabled']
                else: assert case['blankNotesPlaceholderCount']>0
            elif case['case']=='sample-gap':
                assert len(segments)==2 and segments[0][-1]==3000 and segments[1][0]==8000
                assert not case['actualWaterResetTimesMs']
            elif case['case']=='water-reset':
                assert case['metric']=='WATER' and case['actualWaterResetTimesMs']==[15000]
                assert len(segments)==2 and segments[0][-1]==14000 and segments[1][0]==15000
            special_captures+=1
    else:
        # Full M5 may not report success without the reviewed real RUNNING service proof.
        main_header=require_chart_header(folder,['pressure','water','weight','temperature'])
        assert m['chartHeaderReachability']==main_header
        assert main_header['initialState']['shotState']=='RUNNING'
        assert main_header['initialState']['traceIdentity']=='m5-live-shot'
        live=json.loads((folder/'extraction-business.json').read_text())
        assert live['actualMockStart'] and live['running'] and live['lastSampleElapsedMs']==24600
        assert live['sameMonotonicDomain'] and live['realSystemClockEvidenceDeadline'] and live['originalRecoveryAndCredentialsUntouched']
        assert live['activeTraceIdentity']=='m5-live-shot' and live['scaleMeaning']=='scale_reading_not_tared_cup_mass'
        assert isolation['scopedServiceDestroyed'] and isolation['serviceOwnerCallbacksRemoved'] and isolation['serviceHandlersDrained']
        assert isolation['serviceHubClosed'] and isolation['extractionActivityUnboundAndRefreshRemoved']
        assert isolation['originalVisibilityObserversPreserved'] and isolation['serviceOriginalLazyIdentityAndInitializedRestored']
        assert isolation['mockClockRemovedAfterAllOwnersDrained']
        reach=json.loads((folder/'extraction-reachability.json').read_text())
        assert reach['completed'] and reach['targetKeys']==['reference-choice','overview']
        assert [target['semanticKey'] for target in reach['initialTargets']]==reach['targetKeys']
        assert [target['semanticKey'] for target in reach['reached']]==reach['targetKeys']
        assert reach['initialScrollPx']==reach['restoredScrollPx']==[0,0] and reach['exactScrollRestored']
        assert reach['traceIdentity']=='m5-live-shot' and reach['shotState']=='RUNNING'
        assert reach['noToolClicks'] and reach['modelAndSelectionUnchanged']
        assert reach['modeLocation']=='header_outside_support_scroll_in_current_version'
        assert reach['modeSubtitle']['complete']
        for target in reach['reached']:
            assert target['layout']['complete'] and target['layout']['textLineBoxes']
            assert all(line['fullViewVisible'] and line['layoutPresent'] and line['lineCount']>0 and line['ellipsisCount']==0 and
                line['layoutHeightPx']<=line['contentHeightPx'] for line in target['layout']['textLineBoxes'])
        for phase in ('reference-choice-reachable','overview-reachable','tools-restored'):
            require_proof(folder/'states'/f'extraction-{phase}'/'window-frame-sync.json')
        special=json.loads((folder/'special-states/manifest.json').read_text())
        assert m['extractionSpecialStates']==special and special['completed']
        assert special['sourceSha']==m['sourceSha'] and special['profile']==m['profile'] and special['page']=='extraction'
        assert special['synthetic'] and special['hardwareMeasurement'] is False and special['realProductActivity'] and special['visualAcceptance']=='ASTRA_PENDING'
        assert special['expectedCases']==extraction_special_names and special['mainConfigurationCountContribution']==0
        assert special['scenarioShotIds']==extraction_scenario_ids
        contract=json.loads((folder/'special-states/id-contract.json').read_text())
        assert contract['realShotSamplesStoreSaveAndLoad'] and set(contract['cases'])==set(extraction_special_names)
        for name,id in extraction_scenario_ids.items():
            evidence=contract['cases'][name]
            sample=folder/'special-states/id-contract'/f'samples-{id}.tsv'
            assert evidence['shotId']==id and sample.is_file() and sha(sample)==evidence['sha256']
            assert sample.read_text().splitlines()==['# openhoyi-shot-points-v3','0\t90\t100\t6\t9300\t\t\ttrue']
        assert special['actualAdditionalActivityCaptures']==len(special['cases'])==12
        assert [case['case'] for case in special['cases']]==extraction_special_names
        assert m['completedSpecialCases']==extraction_special_names and m['pendingSpecialCases']==[]
        states={'STARTING':'STARTING','STOP_REQUESTED':'STOP_REQUESTED','UNKNOWN_live':'OUTCOME_UNKNOWN',
            'no_service_ready':'IDLE','manual':'IDLE'}
        three_metric_cases={'STARTING','no_scale','stale_scale','no_service_ready','missing_samples'}
        for case in special['cases']:
            name=case['case'];target=folder/'special-states'/name
            metrics=['pressure','water','weight','temperature']
            header=require_chart_header(target,metrics,action='start' if name in ('manual','no_service_ready') else 'stop')
            assert case['chartHeaderReachability']==header
            assert case['supportReachability']==require_special_support(target,name)
            assert header['initialState']['shotState']==case['actualState']
            assert header['initialState']['manualShotActive']==case['actualManualShotActive']
            assert header['initialState']['traceIdentity']==case['actualTraceIdentity']
            assert header['initialState']['curveId']==case['actualCurveId']
            assert len(header['initialState']['ownerPoints'])==case['actualSampleCount']
            assert header['initialState']['journalResultFailed']==(name=='journal_unavailable')
            assert case['status']=='PASS_MECHANICAL_ONLY_NOT_VISUAL_ACCEPTANCE'
            assert case['synthetic'] and case['hardwareMeasurement'] is False and case['realProductActivity'] and case['visualAcceptance']=='ASTRA_PENDING'
            assert case['actualState']==states.get(name,'RUNNING')
            assert case['actualManualShotActive']==(name=='manual')
            assert case['actualRunningService']==(name!='no_service_ready')
            assert case['actualOfferedFrameSerial']==len(case['offeredMachineFrames'])
            assert case['actualSampleCount']==case['actualOfferedFrameSerial']
            assert case['declaredShotId']==extraction_scenario_ids[name]
            assert case['actualTraceIdentity']==(None if name=='no_service_ready' else extraction_scenario_ids[name])
            assert case['actualCurveId']==('manual' if name=='manual' else None if name=='no_service_ready' else 'factory-v3-002')
            assert case['shotId']==(None if name in ('manual','no_service_ready') else extraction_scenario_ids[name])
            assert case['frameSync']['protocolVersion']==2 and case['frameSync']['accepted']
            assert json.loads((target/'case.json').read_text())==case
            fixture=json.loads((target/'fixture.json').read_text())
            assert fixture['case']==name and fixture['synthetic'] and fixture['hardwareMeasurement'] is False
            assert fixture['declaredShotId']==extraction_scenario_ids[name]
            assert fixture['offeredMachineFrames']==case['offeredMachineFrames']
            assert fixture['operationInputs']==case['operationInputs']
            operations=case['operationInputs']
            expected_operations=[] if name in ('manual','no_service_ready') else ['start_requested']
            if name not in ('manual','no_service_ready','STARTING'):expected_operations.append('start_completed')
            if name=='STOP_REQUESTED':expected_operations.append('manual_stop_requested')
            if name=='UNKNOWN_live':expected_operations.append('coffee_ready_changed')
            assert [item['kind'] for item in operations]==expected_operations
            assert [item['ordinal'] for item in operations]==list(range(1,len(operations)+1))
            assert all(item['synthetic'] for item in operations)
            if operations:
                request=operations[0]['requestId']
                assert isinstance(request,int) and request>0
                if len(operations)>1 and operations[1]['kind']=='start_completed':
                    assert operations[1]['requestId']==request and operations[1]['result']=='synthetic_Success'
            if name=='STARTING':assert case['actualState']=='STARTING' and len(operations)==1
            if name=='STOP_REQUESTED':
                assert operations[-1]['completion']=='not_offered' and operations[-1]['requestId']>operations[0]['requestId']
            if name=='UNKNOWN_live':assert operations[-1]['state']=='DISCONNECTED'
            own=json.loads((target/'fixture-isolation.json').read_text())
            assert own['sampleFailures']==[] and own['sampleFailureScopeCase']==name and own['sampleFailureOwnedScope']
            assert own['status']=='PASS_ISOLATION_ONLY' and own['businessDiskByteUnchanged'] and own['allActivitiesDestroyed']
            assert own['scopedServiceDestroyed'] and own['mockClockRemovedAfterAllOwnersDrained']
            assert len(case['offeredMachineFrames'])==len({x['serial'] for x in case['offeredMachineFrames']})
            assert [x['serial'] for x in case['offeredMachineFrames']]==list(range(1,len(case['offeredMachineFrames'])+1))
            assert all(x['actualNewPoints']==x['pointsAfter']-x['pointsBefore'] for x in case['offeredMachineFrames'])
            require_proof(target/'window-frame-sync.json')
            assert tuple(struct.unpack('>II',(target/'window.png').read_bytes()[16:24]))==(2560,1600)
            if name=='missing_samples':assert case['actualSampleCount']==0 and not case['actualWaterSegments']
            if name=='water_reset':assert case['actualWaterResetsMs']==[3000] and len(case['actualWaterSegments'])==2
            if name=='sample_gap':assert len(case['actualWaterSegments'])==2 and not case['actualWaterResetsMs']
            if name=='no_scale':assert case['actualScaleState']=='DISCONNECTED'
            if name=='UNKNOWN_live':assert case['actualCoffeeState']=='DISCONNECTED' and case['actualSampleCount']>0
            if name=='historical_reference_toggle':
                assert case['referenceToggleActualSavedJournal'] and case['selectionAndScrollRestored'] and case['currentCupAndJournalUnchanged']
                assert case['referenceOnCount']>0
                for phase in ('on','off','restored'):
                    require_proof(target/'states'/f'extraction-reference-{phase}'/'window-frame-sync.json')
                for phase,field in (('on','referenceOnChartHeaderReachability'),('off','referenceOffChartHeaderReachability'),
                    ('restored','referenceRestoredChartHeaderReachability')):
                    reference_folder=target/'states'/f'extraction-reference-{phase}'
                    assert case[field]==require_chart_header(reference_folder,
                        ['pressure','water','weight','temperature'],caption=phase=='on')
                    assert (case[field]['initialState']['referenceId'] is not None)==(phase=='on')
            if name=='journal_unavailable':
                assert case['actualJournalResultFailed'] and case['chooserOnlyOffNoFakeRecord']
                dialog=target/'states/extraction-journal-unavailable-dialog'
                dialog_frame=require_proof(dialog/'window-frame-sync.json')
                actual=json.loads((dialog/'geometry.json').read_text())
                assert case['journalUnavailableDialog']==actual and case['journalUnavailableDialogFrameSync']==dialog_frame
                assert json.loads((dialog/'typography.json').read_text())==actual['allNativeText']
                assert tuple(struct.unpack('>II',(dialog/'window.png').read_bytes()[16:24]))==(2560,1600)
                assert actual['listCount']==1 and actual['journalResultFailed'] and actual['referenceId'] is None
                assert actual['chartReferenceCount']==0 and actual['shotState']==case['actualState']
                assert actual['traceIdentity']==case['actualTraceIdentity'] and actual['sampleCount']==case['actualSampleCount']
                activity_state=case['journalUnavailableInitialState']
                assert case['journalUnavailableBeforeDialogState']==activity_state
                assert actual['activityState']==activity_state
                assert case['journalUnavailableChoiceState']==activity_state
                assert case['journalUnavailableRestoredState']==activity_state
                assert activity_state['journalResultFailed'] and activity_state['referenceId'] is None
                assert activity_state['chartReference']==[] and len(activity_state['ownerPoints'])==case['actualSampleCount']
                assert isinstance(activity_state['chartPoints'],list)
                assert activity_state['shotState']==case['actualState'] and activity_state['traceIdentity']==case['actualTraceIdentity']
                assert activity_state['chartTraceIdentity']==case['actualTraceIdentity']
                assert isinstance(activity_state['journalExport'],str) and activity_state['journalExport']
                assert len(activity_state['headerScrollPx'])==2
                assert activity_state['fixedAction']['semanticId'] in ('product/extraction/start','product/extraction/stop')
                assert activity_state['fixedAction']['identity']>0 and len(activity_state['fixedAction']['rectPx'])==4
                assert activity_state['fixedAction']['visibility']==0
                assert all(key in activity_state for key in ('manualShotActive','curveId','pendingStartId','pendingStopId','referenceCurveId'))
                assert actual['windowIdentity']>0 and actual['decorIdentity']>0 and actual['tokenIdentity']>0
                assert actual['ownerActivityIdentity']>0 and len(actual['decorOriginScreenPx'])==2
                assert all(x>0 for x in actual['decorSizePx']) and len(actual['safeRectScreenPx'])==4
                assert actual['title']['text'] and actual['off']['text'] and actual['cancel']['text']
                for role in ('title','off','cancel'):
                    require_text_layout(actual[role]['layout'])
                    assert actual[role]['identity']>0 and actual[role]['textSizePx']>0
                    assert isinstance(actual[role]['textColor'],int)
                assert len(actual['allNativeText'])>=3 and actual['density']>0
                assert all(value>=48*actual['density'] for value in actual['offTouchPx']+actual['cancelTouchPx'])
                choice=target/'states/extraction-journal-unavailable-choice'
                restored=target/'states/extraction-journal-unavailable-restored'
                assert case['journalUnavailableChoiceFrameSync']==require_proof(choice/'window-frame-sync.json')
                assert case['journalUnavailableRestoredFrameSync']==require_proof(restored/'window-frame-sync.json')
                assert case['journalUnavailableInitialScrollPx']==case['journalUnavailableRestoredScrollPx']
            extraction_special_captures+=1
    assert 'noBackupFilesDir' in isolation and isolation['noBackupFilesDir'] in isolation['businessDiskRoots']
    assert (folder/'baseline-alignment.json').is_file()
    history_pending=['STARTING','STOP_REQUESTED','UNKNOWN_live','no_scale','stale_scale',
        'no_service_ready','manual','historical_reference_toggle','journal_unavailable']
    assert m['pendingSpecialCases']==([] if m['page']=='extraction' else history_pending)
    for chart in m['window']['charts']: assert chart['canvasEvidence'], path

assert seen=={(page,f'tablet-user-landscape-{theme}-font-{scale}') for page in ('extraction','history','history-detail','comparison') for theme in ('light','dark') for scale in ('1.0','1.3')}
assert special_captures==60, f'Expected 60 separate historical special-state page captures, got {special_captures}'
assert extraction_special_captures==48, f'Expected 48 separate Extraction special-state captures, got {extraction_special_captures}'
(root/'capture-summary.json').write_text(json.dumps({'realConfigurations':16,'sourceSha':root.name,
    'historicalSpecialStateActivityCaptures':special_captures,'specialStatesCountedAsMainConfigurations':False,
    'extractionSpecialStateActivityCaptures':extraction_special_captures,
    'visualAcceptance':'ASTRA_PENDING','specimenImagesUsedAsProductEvidence':False},indent=2)+'\n')
print('EXTRACTION_HISTORY_VISUAL_MIGRATION_RETAINED configurations=16 historicalSpecialStateActivities=60 actualActivities=true visualAcceptance=ASTRA_PENDING')
VERIFY_PY
