#!/usr/bin/env bash
set -Eeuo pipefail
# This entry point installs/runs only in the existing disposable GitHub CI emulator.
[[ "${GITHUB_ACTIONS:-}" == true ]] || { echo 'Strict native specimens require GitHub CI' >&2; exit 1; }
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || { echo 'Disposable emulator required' >&2; exit 1; }
package=io.openhoyi.mobile.mock
source_sha=$(git rev-parse HEAD)
source_dirty=false
[[ -z "$(git status --porcelain)" ]] || source_dirty=true
output_dir="$PWD/build/task-first-evidence/strict-native-samples"
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
  # The successful main pull is finalized on the host below. Never pull raw files
  # over that tree on exit; failed runs keep a separate unmodified diagnostic copy.
  if (( exit_status != 0 )); then
    mkdir -p "$output_dir/raw-device" || true
    adb pull "/sdcard/Android/data/$package/files/strict-native-samples/$source_sha" "$output_dir/raw-device/" || true
  fi
  adb logcat -d -s AndroidRuntime:E > "$output_dir/runtime-errors.txt" || true
  adb shell dumpsys activity activities > "$output_dir/activities.txt" || true
  adb shell settings put system font_scale "$original_font_scale" || true
  return "$exit_status"
}
trap 'collect "$?"' EXIT
adb shell wm size 2560x1600
adb shell wm density 360
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 0
adb install -r mobile/build/outputs/apk/mock/mobile-mock.apk
adb install -r mobile/build/outputs/apk/androidTest/mock/mobile-mock-androidTest.apk
for sample_theme in light dark; do
  if [[ "$sample_theme" == dark ]]; then adb shell cmd uimode night yes; else adb shell cmd uimode night no; fi
  for font_scale in 1.0 1.3; do
    adb shell settings put system font_scale "$font_scale"
    adb shell am force-stop "$package"
    python3 - "$output_dir/$sample_theme-$font_scale-result.txt" "$source_sha" "$source_dirty" "$apk_sha" "$sample_theme" "$font_scale" <<'RUN_PY'
from pathlib import Path
import os, subprocess, sys
result_path, source, dirty, apk, theme, scale = sys.argv[1:]
command = ['adb','shell','am','instrument','-w']
for key,value in {'strictVisualSamples':'true','disposableCiEmulator':'true','sourceSha':source,
    'sourceDirty':dirty,'apkSha':apk,'sampleTheme':theme,'expectedFontScale':scale,
    'ciRun':os.environ['GITHUB_RUN_ID'],'ciJob':os.environ['GITHUB_JOB']}.items():
    command += ['-e',key,value]
command += ['io.openhoyi.mobile.mock.test/io.openhoyi.mobile.BrewAudioInstrumentation']
with Path(result_path).open('w') as capture:
    result = subprocess.run(command, stdout=capture, stderr=subprocess.STDOUT, timeout=240)
text = Path(result_path).read_text()
if result.returncode or 'STRICT_VISUAL_SAMPLES_CAPTURED fixtures=3 specimenOnly=true productAcceptance=false noBle=true storageUnchanged=true' not in text:
    print(text)
    raise SystemExit('Native specimen capture failed; partial evidence retained, no automatic reset')
RUN_PY
  done
done
adb pull "/sdcard/Android/data/$package/files/strict-native-samples/$source_sha" "$output_dir/"
# Preserve exact original PNGs and Astra annotations; attach differences, never approve measured values.
python3 - "$output_dir/$source_sha" <<'FINALIZE_PY'
import hashlib, json, shutil, struct, sys
from pathlib import Path
root = Path(sys.argv[1])
annotation_path = Path('docs/research/task-first-ui-concepts/strict-native-sample-anchors.json')
annotations = json.loads(annotation_path.read_text())
assert annotations['author'] == 'Astra' and annotations['status'] == 'proposed'
assert annotations['pendingPages'] == [] and len(annotations['pages']) == 3
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
originals = root / 'originals'
originals.mkdir(exist_ok=True)
shutil.copyfile(annotation_path, originals / annotation_path.name)
for page in annotations['pages'].values():
    source = Path(page['source']['path'])
    assert sha(source) == page['source']['sha256'], f'Original SHA mismatch: {source}'
    assert struct.unpack('>II', source.read_bytes()[16:24]) == (page['source']['widthPx'], page['source']['heightPx'])
    shutil.copyfile(source, originals / source.name)
manifests = sorted(root.glob('*/*/manifest.json'))
assert len(manifests) == 12, f'Expected exactly 12 specimen configurations, got {len(manifests)}'
seen = set()
geometry = None
layout_failures = []
contacts = ['# 原稿与候选原生样张 — 待 Astra 逐图审查', '',
    'specimenOnly=true; productAcceptance=false; baselineStatus=proposed', '',
    '| Fixture | 原稿 | 浅色 1.0 | 浅色 1.3 | 深色 1.0 | 深色 1.3 |', '|---|---|---|---|---|---|']
for manifest_path in manifests:
    folder = manifest_path.parent
    manifest = json.loads(manifest_path.read_text())
    fixture = manifest['fixture']['id']
    profile = manifest['profile']
    assert (fixture, profile) not in seen
    seen.add((fixture,profile))
    assert manifest['specimenOnly'] and not manifest['productAcceptance'] and manifest['baselineStatus'] == 'proposed'
    assert manifest['storageUnchanged'], 'Specimen changed persistent app state'
    if manifest['layoutStatus'] == 'FAIL': layout_failures.append((fixture,profile))
    assert manifest['window']['displayPx'] == [2560,1600]
    signature = [manifest['window'][key] for key in ('appOriginPx','appSizePx','systemInsetsPx')]
    if geometry is None: geometry = signature
    assert geometry == signature, 'Window geometry mismatch; comparison stopped, no image stretching'
    original = annotations['pages'][fixture]
    anchors_path = folder / 'anchors.json'
    anchors = json.loads(anchors_path.read_text())
    anchors['original'] = original
    anchors['coordinateContract'] = annotations['coordinateContract']
    anchors['sharedNavigation'] = annotations['sharedNavigation']
    anchors['originalStatus'] = 'Astra_proposed_semantic_annotations_not_frozen'
    actual = {node['semanticId']:node for node in anchors['actual']}
    differences = []
    for anchor in original['anchors']:
        node = actual.get(anchor['id'])
        expected = anchor.get('targetDp',{}).get('rect')
        measured = node.get('appRectDp') if node else None
        differences.append({'semanticId':anchor['id'], 'targetReferenceDp':expected, 'actualDp':measured,
            'deltaDp': [b-a for a,b in zip(expected,measured)] if expected and measured else None,
            'status':'BASELINE_PENDING' if measured else 'EVIDENCE_PENDING',
            'note':'font1.0 semantic reference; 1.3 uses documented natural-layout rules; no tolerance or auto approval'})
    anchors['differences'] = differences
    missing = [row['semanticId'] for row in differences if row['actualDp'] is None]
    assert not missing, f'Missing actual semantic anchors: {fixture}: {missing}'
    anchors_path.write_text(json.dumps(anchors,ensure_ascii=False,indent=2)+'\n')
    manifest['original'] = original['source']
    manifest['originalAnnotationsSha256'] = sha(annotation_path)
    manifest['fileSha256'] = {name:sha(folder/name) for name in ('window.png','visual.json','tokens.json','anchors.json')}
    assert manifest['fileSha256']['window.png'] == manifest['windowPngSha256']
    manifest_path.write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
    with (folder/'comparison.md').open('a') as report:
        report.write('\n## 锚点精确差异（候选，非自动通过）\n\n| semanticId | target dp | actual dp | delta dp | status |\n|---|---|---|---|---|\n')
        for row in differences:
            report.write('| {semanticId} | {targetReferenceDp} | {actualDp} | {deltaDp} | {status} |\n'.format(**row))
        report.write('\n原稿适配决策：\n\n'+json.dumps(original.get('adaptations',[]),ensure_ascii=False,indent=2)+'\n')
    manifest['fileSha256']['comparison.md'] = sha(folder/'comparison.md')
    manifest['fileSha256']['StrictVisualSampleActivity-typography.json'] = sha(folder/'StrictVisualSampleActivity-typography.json')
    manifest_path.write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
for fixture, original in annotations['pages'].items():
    links = [f'![原稿](originals/{Path(original["source"]["path"]).name})']
    for theme,scale in [('light','1.0'),('light','1.3'),('dark','1.0'),('dark','1.3')]:
        profile = f'tablet-user-landscape-{theme}-font-{scale}'
        assert (fixture,profile) in seen
        links.append(f'![{theme} {scale}]({profile}/{fixture}/window.png)')
    contacts.append('| '+fixture+' | '+' | '.join(links)+' |')
(root/'contact-sheet.md').write_text('\n'.join(contacts)+'\n')
if layout_failures:
    raise SystemExit(f'Strict specimen layout failed; all 12 configurations retained: {layout_failures}')
print('STRICT_NATIVE_SPECIMENS_RETAINED configurations=12 specimenOnly=true productAcceptance=false visualAcceptance=pending')
FINALIZE_PY
