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
    for extraction_history_page in history history-detail comparison extraction; do
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
import hashlib, json, struct, sys
root = Path(sys.argv[1])
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
manifests = sorted(root.glob('*/*/manifest.json'))
assert len(manifests)==16, f'Expected 16 real Activity configurations, got {len(manifests)}'
seen=set()
special_captures=0
expected_specials={'missing-samples':'m5-history-extra-38','no-notes':'m5-history-extra-6',
    'unknown-observation':'m5-history-extra-7','sample-gap':'m5-history-extra-8','water-reset':'m5-history-extra-9'}
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
    assert isolation['originalLazyIdentityAndInitializedRestored'] and isolation['legacyUnchanged']
    assert isolation['allActivitiesDestroyed'] and isolation['historyDetailReadersTerminated'] and isolation['comparisonReaderPoolsTerminated']
    assert isolation['mainQueueBarrierAfterReaders'] and isolation['scopedWritersTerminated']
    assert (folder/'geometry.json').is_file() and (folder/'fixture.json').is_file()
    if m['page']!='extraction':
        assert isolation['noServiceBeforeOriginalDelegateRestore']
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
            assert case['readOnlyModelsUnchanged'] and case['noServiceOrClockOverride']
            assert case['actualRawSamples']==case['declaredSamples']
            target=folder/'special-states'/case['case']
            assert json.loads((target/'case.json').read_text())==case
            assert struct.unpack('>II',(target/'window.png').read_bytes()[16:24])==(2560,1600)
            observed=json.loads((target/'geometry.json').read_text())
            assert observed['densityDpi']==m['window']['densityDpi'] and observed['fontScale']==m['window']['fontScale']
            assert observed['uiModeNightMeaning']==m['window']['uiModeNightMeaning'] and not observed['imeVisible']
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
        live=json.loads((folder/'extraction-business.json').read_text())
        assert live['actualMockStart'] and live['running'] and live['lastSampleElapsedMs']==24600
        assert live['sameMonotonicDomain'] and live['realSystemClockEvidenceDeadline'] and live['originalRecoveryAndCredentialsUntouched']
        assert live['activeTraceIdentity']=='m5-live-shot' and live['scaleMeaning']=='scale_reading_not_tared_cup_mass'
        assert isolation['scopedServiceDestroyed'] and isolation['serviceOwnerCallbacksRemoved'] and isolation['serviceHandlersDrained']
        assert isolation['serviceHubClosed'] and isolation['extractionActivityUnboundAndRefreshRemoved']
        assert isolation['originalVisibilityObserversPreserved'] and isolation['serviceOriginalLazyIdentityAndInitializedRestored']
        assert isolation['mockClockRemovedAfterAllOwnersDrained']
    assert 'noBackupFilesDir' in isolation and isolation['noBackupFilesDir'] in isolation['businessDiskRoots']
    assert (folder/'baseline-alignment.json').is_file()
    assert m['pendingSpecialCases']  # Explicit outstanding states are not silently relabelled as complete.
    for chart in m['window']['charts']: assert chart['canvasEvidence'], path

assert seen=={(page,f'tablet-user-landscape-{theme}-font-{scale}') for page in ('extraction','history','history-detail','comparison') for theme in ('light','dark') for scale in ('1.0','1.3')}
assert special_captures==60, f'Expected 60 separate historical special-state page captures, got {special_captures}'
(root/'capture-summary.json').write_text(json.dumps({'realConfigurations':16,'sourceSha':root.name,
    'historicalSpecialStateActivityCaptures':special_captures,'specialStatesCountedAsMainConfigurations':False,
    'visualAcceptance':'ASTRA_PENDING','specimenImagesUsedAsProductEvidence':False},indent=2)+'\n')
print('EXTRACTION_HISTORY_VISUAL_MIGRATION_RETAINED configurations=16 historicalSpecialStateActivities=60 actualActivities=true visualAcceptance=ASTRA_PENDING')
VERIFY_PY
