#!/usr/bin/env bash
set -Eeuo pipefail
# Additional M2 real-form evidence only, in the existing disposable CI emulator.
[[ "${GITHUB_ACTIONS:-}" == true ]] || { echo 'Form visual captures require GitHub CI' >&2; exit 1; }
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || { echo 'Disposable emulator required' >&2; exit 1; }
package=io.openhoyi.mobile.mock
source_sha=$(git rev-parse HEAD)
source_dirty=false
[[ -z "$(git status --porcelain)" ]] || source_dirty=true
output_dir="$PWD/build/task-first-evidence/form-visual-migration"
mkdir -p "$output_dir"
apk_sha=$(python3 - <<'PY'
import hashlib
from pathlib import Path
print(hashlib.sha256(Path('mobile/build/outputs/apk/mock/mobile-mock.apk').read_bytes()).hexdigest())
PY
)
session_nonce=$(python3 - <<'PY'
import secrets
print(secrets.token_hex(16))
PY
)
original_font_scale=$(adb shell settings get system font_scale | tr -d '\r')
collect() {
  local exit_status=$1
  local final_status=$exit_status
  local -a cleanup_errors=()
  local needs_fallback=1
  if [[ -f "$output_dir/case-aggregation.json" ]] && python3 - "$output_dir/case-aggregation.json" <<'FALLBACK_CHECK_PY'
import json,sys
from pathlib import Path
counts=json.loads(Path(sys.argv[1]).read_text())['counts']
raise SystemExit(0 if counts['NOT_RUN']==0 and counts['ABORT']==0 else 1)
FALLBACK_CHECK_PY
  then needs_fallback=0; fi
  if (( exit_status != 0 && needs_fallback != 0 )); then
    if mkdir "$output_dir/raw-device-exit-fallback"; then
      if ! adb pull "/sdcard/Android/data/$package/files/form-visual-migration/$source_sha" "$output_dir/raw-device-exit-fallback/"; then
        cleanup_errors+=("exit_fallback_pull_failed")
      fi
    else
      cleanup_errors+=("exit_fallback_directory_unavailable")
    fi
  fi
  if [[ -f "$output_dir/ime-active-profile.json" ]]; then
    if ! python3 - "$output_dir" "$source_sha" "${PRODUCT_PR_HEAD_SHA:-$source_sha}" "$apk_sha" "${GITHUB_RUN_ID:-}" "${GITHUB_JOB:-}" "$session_nonce" <<'IME_EXIT_PY'
import json,subprocess,sys
from pathlib import Path
root=Path(sys.argv[1]);source,head,apk,ci_run,ci_job,nonce=sys.argv[2:]
active=json.loads((root/'ime-active-profile.json').read_text())
profile='tablet-user-landscape-light-font-1.0'
expected={'directory':str(root/'ime-native-traces'/'light-1.0-curve-editor'),
    'deviceDirectory':f'/sdcard/Android/data/io.openhoyi.mobile.mock/files/form-visual-migration/{source}/{profile}/curve-editor/ime-recreation-native',
    'sessionNonce':nonce,'sourceSha':source,'headSha':head,'apkSha256':apk,
    'ciRun':ci_run,'ciJob':ci_job,'profile':profile}
if active != expected:
    raise SystemExit('Unknown/old IME active marker retained; no collector after')
if (Path(active['directory'])/'collection-complete.json').is_file():
    raise SystemExit(0)  # Existing after report: never issue a second stop.
result=subprocess.run(['python3','scripts/collect_ime_recreation_trace.py','after','--directory',active['directory'],
    '--device-directory',active['deviceDirectory'],'--probe-directory',str(root/'ime-native-capability')],timeout=120)
raise SystemExit(result.returncode)
IME_EXIT_PY
    then cleanup_errors+=("ime_exit_collector_failed"); fi
  fi
  adb logcat -d -s AndroidRuntime:E > "$output_dir/runtime-errors.txt" || cleanup_errors+=("runtime_logcat_failed")
  adb logcat -d -v threadtime -s ImeTracker:I InputMethodManager:D InsetsController:D > "$output_dir/ime-diagnostics.txt" || cleanup_errors+=("ime_logcat_failed")
  adb shell dumpsys input_method > "$output_dir/input-method.txt" || cleanup_errors+=("input_method_dumpsys_failed")
  adb shell dumpsys activity activities > "$output_dir/activities.txt" || cleanup_errors+=("activities_dumpsys_failed")
  adb shell am force-stop "$package" || cleanup_errors+=("exit_force_stop_failed")
  adb shell settings put system font_scale "$original_font_scale" || cleanup_errors+=("font_scale_restore_failed")
  if (( ${#cleanup_errors[@]} > 0 )); then final_status=2; fi
  if ! python3 - "$output_dir/exit-collection.json" "$exit_status" "$final_status" ${cleanup_errors[@]+"${cleanup_errors[@]}"} <<'EXIT_JSON_PY'
import json,sys
from pathlib import Path
Path(sys.argv[1]).write_text(json.dumps({'originalExit':int(sys.argv[2]),'finalExit':int(sys.argv[3]),
    'cleanupErrors':sys.argv[4:]},indent=2)+'\n')
EXIT_JSON_PY
  then final_status=2; fi
  return "$final_status"
}
trap 'status=$?; trap - EXIT; if collect "$status"; then exit 0; else exit $?; fi' EXIT
adb shell wm size 2560x1600
adb shell wm density 360
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 0
# APKs were built by the workflow. Installation is confined to this guarded emulator.
adb install -r mobile/build/outputs/apk/mock/mobile-mock.apk
adb install -r mobile/build/outputs/apk/androidTest/mock/mobile-mock-androidTest.apk
python3 scripts/form_visual_case_aggregation.py \
  --output-dir "$output_dir" --source "$source_sha" --dirty "$source_dirty" \
  --apk "$apk_sha" --head "${PRODUCT_PR_HEAD_SHA:-$source_sha}" --session-nonce "$session_nonce"
