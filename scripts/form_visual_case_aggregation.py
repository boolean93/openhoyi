#!/usr/bin/env python3
"""Host-only M2 case classification; no device calls at import time."""

import argparse
import hashlib
import json
import os
import re
import struct
import subprocess
import sys
from pathlib import Path


ISOLATION_BOOLEANS = (
    "allActivitiesDestroyed",
    "serviceRemovedAndOnDestroyPostconditions",
    "mainQueueBarrierAfterDestroy",
    "scopedWritersTerminated",
    "originalLazyIdentityAndInitializedRestored",
    "legacyUnchanged",
    "typedPreferencesRestored",
    "businessDiskByteUnchanged",
)


def case_keys():
    return [
        (theme, scale, page)
        for theme in ("light", "dark")
        for scale in ("1.0", "1.3")
        for page in ("curve-editor", "bean-entry", "review")
    ]


def validate_isolation(proof):
    if not isinstance(proof, dict) or proof.get("status") != "PASS_ISOLATION_ONLY" or "failure" in proof:
        raise ValueError("isolation status/failure is not clean")
    if any(proof.get(key) is not True for key in ISOLATION_BOOLEANS):
        raise ValueError("required isolation boolean absent or false")
    before, after = proof.get("businessDiskBefore"), proof.get("businessDiskAfter")
    if not isinstance(before, dict) or not isinstance(after, dict) or before != after:
        raise ValueError("business disk snapshots absent or changed")
    for key in ("whitePrefFilesBefore", "whitePrefFilesAfter"):
        value = proof.get(key)
        if (not isinstance(value, dict) or not {"appearance.xml", "curves.xml"}.issubset(value)
                or not set(value).issubset({"appearance.xml", "appearance.xml.bak", "curves.xml", "curves.xml.bak"})
                or not all(isinstance(v, str) and re.fullmatch(r"[0-9a-f]{64}", v) for v in value.values())):
            raise ValueError("preference SHA evidence absent")


SUCCESS_MARKER = "FORM_VISUAL_MIGRATION_CAPTURED fixtures=1 realActivities=true visualAcceptance=ASTRA_PENDING noBle=true isolationVerified=true"
FAILURE_MARKER = "FORM_VISUAL_MIGRATION_FAILED "
FONT_SHA = "a3041811a78c361b1de50f953c805e0244951c21c5bd412f7232ef0d899af0da"


def classify_terminal(output, return_code):
    if return_code != 0:
        raise ValueError("instrument transport returned nonzero")
    lines = output.splitlines()
    success = sum(line == SUCCESS_MARKER for line in lines)
    failure = sum(line.startswith(FAILURE_MARKER) and bool(line[len(FAILURE_MARKER):].strip()) for line in lines)
    empty_failure = any(line.startswith(FAILURE_MARKER) and not line[len(FAILURE_MARKER):].strip() for line in lines)
    if success == 1 and failure == 0 and not empty_failure:
        return "PASS"
    if failure == 1 and success == 0 and not empty_failure:
        return "FAIL"
    raise ValueError("instrument terminal missing, repeated, or contradictory")


def validate_case_files(folder, identity, terminal):
    folder = Path(folder)
    manifest_path = folder / "manifest.json"
    manifest_bytes = manifest_path.read_bytes()
    manifest = json.loads(manifest_bytes)
    if not isinstance(manifest, dict):
        raise ValueError("manifest is not an object")
    for key, expected in identity.items():
        if manifest.get(key) != expected:
            raise ValueError(f"manifest identity mismatch: {key}")
    if manifest.get("realProductActivity") is not True or manifest.get("specimenOnly") is not False:
        raise ValueError("not a real product Activity")
    if manifest.get("visualAcceptance") != "ASTRA_PENDING":
        raise ValueError("unexpected visual acceptance claim")
    if type(manifest.get("pid")) is not int or manifest["pid"] <= 0:
        raise ValueError("no positive process identity")
    expected_isolation_status = "SEE_fixture-isolation.json" if terminal == "FAIL" else "PASS"
    if manifest.get("isolationStatus") != expected_isolation_status:
        raise ValueError("manifest isolation status contradicts terminal")
    files = manifest.get("fileSha256")
    if not isinstance(files, dict) or "fixture-isolation.json" not in files or not files:
        raise ValueError("file hash inventory absent")
    for name, expected_sha in files.items():
        if not isinstance(name, str) or not name:
            raise ValueError("unsafe case file path")
        path = Path(name)
        if (path.is_absolute() or ".." in path.parts
                or "\\" in name or ":" in name or str(path) != name):
            raise ValueError("unsafe case file path")
        if not isinstance(expected_sha, str) or len(expected_sha) != 64 or any(c not in "0123456789abcdef" for c in expected_sha):
            raise ValueError("invalid file hash")
        target = folder / path
        if not target.is_file() or target.is_symlink() or hashlib.sha256(target.read_bytes()).hexdigest() != expected_sha:
            raise ValueError(f"case file absent or hash mismatch: {name}")
    validate_isolation(json.loads((folder / "fixture-isolation.json").read_text()))
    failure = manifest.get("failure")
    if terminal == "FAIL":
        if manifest.get("layoutStatus") != "FAIL" or not isinstance(failure, str) or not failure.strip():
            raise ValueError("failure terminal without original manifest failure")
    elif terminal == "PASS":
        if "layoutStatus" not in manifest or manifest["layoutStatus"] == "FAIL" or failure not in (None, ""):
            raise ValueError("success terminal contradicts manifest")
        validate_original_success(folder, manifest)
    else:
        raise ValueError("unrecognized terminal")
    return {"failure": failure, "manifestSha256": hashlib.sha256(manifest_bytes).hexdigest(),
            "isolationProof": str(folder / "fixture-isolation.json")}


def validate_original_success(folder, manifest):
    """The former final VERIFY_PY per-case assertions, without weakening a condition."""
    if manifest.get("fontSha256") != FONT_SHA:
        raise ValueError("font identity changed")
    size = tuple(manifest.get("wholeWindowPngSizePx", ()))
    png = (folder / "window.png").read_bytes()
    if size != (2560, 1600) or len(png) < 24 or struct.unpack(">II", png[16:24]) != size:
        raise ValueError("original whole window size differs")
    window = manifest.get("window", {})
    profile = manifest["profile"]
    if (window.get("densityDpi") != 360 or not str(window.get("locale", "")).startswith("zh")
            or window.get("uiModeNightMeaning") != profile.split("-font-")[0].rsplit("-", 1)[-1]
            or window.get("imeVisible") is not False):
        raise ValueError("original window/theme/IME assertion failed")
    if struct.pack(">f", window.get("fontScale")) != struct.pack(">f", float(profile.rsplit("-font-", 1)[-1])):
        raise ValueError("font scale float32 mismatch")
    initial = manifest.get("initialFrameSync", {})
    if initial.get("protocolVersion") != 2 or initial.get("accepted") is not True:
        raise ValueError("initial frame not protocol2 accepted")
    if not (folder / "window-frame-sync.json").is_file():
        raise ValueError("initial frame proof absent")
    for proof_file in folder.rglob("*-frame-sync.json"):
        proof = json.loads(proof_file.read_text())
        if proof.get("protocolVersion") != 2 or proof.get("accepted") is not True:
            raise ValueError(f"frame proof not accepted: {proof_file}")
    business = json.loads((folder / "form-business.json").read_text())
    if business.get("completed") is not True or business.get("noMachineOrServiceCommands") is not True:
        raise ValueError("original business assertion failed")
    if not (folder / "geometry.json").is_file():
        raise ValueError("geometry absent")


TRACE_FILES = ("ime_trace_clients.winscope", "ime_trace_service.winscope", "ime_trace_managerservice.winscope")
STOP_ACK = ("Stopping tracing and writing traces in /data/misc/wmtrace/: "
            "ime_trace_clients.winscope, ime_trace_service.winscope, ime_trace_managerservice.winscope")
TRACE_SAFETY = "OWNED_STOP_ACK_COLLECTOR_EXITED_ARTIFACTS_RETAINED"


def _owned_stop_confirmed(owner):
    if owner.get("stopAttempted") is not True or owner.get("stopAcknowledged") is not True:
        return False
    stops = [c for c in owner.get("commands", []) if c.get("name") == "stop"]
    if len(stops) != 1:
        return False
    command = stops[0]
    nonce = command.get("commandNonce")
    if not isinstance(nonce, str) or not re.fullmatch(r"[0-9a-f-]{36}", nonce):
        return False
    flags = ("completedWithExitMarker", "workerTerminated", "stdinWrittenAndClosed", "stdoutEof",
             "stderrEof", "withinTotalBudget")
    return (command.get("command") == "cmd input_method tracing stop" and command.get("exitCode") == 0
            and all(command.get(key) is True for key in flags)
            and command.get("output") == STOP_ACK + "\n" and command.get("stderr") == ""
            and command.get("stdout") == STOP_ACK + "\n\nIME_TRACE_RC_" + nonce + "=0\n")


def _fallback_stop_confirmed(directory, token):
    path = directory / "fallback-stop.json"
    if not path.is_file():
        return False
    fallback = json.loads(path.read_text())
    if (fallback.get("traceOwnerToken") != token or fallback.get("attempted") is not True
            or fallback.get("acknowledged") is not True):
        return False
    command = fallback.get("command", {})
    nonce = command.get("commandNonce")
    if not isinstance(nonce, str) or not re.fullmatch(r"[0-9a-f-]{36}", nonce):
        return False
    flags = ("completed", "completedWithExitMarker", "processReaped", "stdinWrittenAndClosed",
             "stdoutEof", "stderrEof", "withinTotalBudget")
    return (command.get("name") == "stop" and command.get("returnCode") == 0
            and command.get("targetExitCode") == 0 and all(command.get(key) is True for key in flags)
            and command.get("stdout") == STOP_ACK + "\n" and command.get("stderr") == ""
            and command.get("rawStdout") == STOP_ACK + "\n\nIME_TRACE_RC_" + nonce + "=0\n")


def validate_collector(directory, identity, exit_code):
    """Prove stop safety separately from trace-content quality; never issue a stop."""
    directory = Path(directory)
    if type(exit_code) is not int or exit_code not in (0, 1):
        raise ValueError("collector did not exit normally with its known status")
    context = json.loads((directory / "fixture-context.json").read_text())
    owner = json.loads((directory / "trace-state.json").read_text())
    report = json.loads((directory / "collection-complete.json").read_text())
    token = owner.get("traceOwnerToken")
    if (not isinstance(token, str) or not re.fullmatch(r"[0-9a-f-]{36}", token)
            or owner.get("package") != "io.openhoyi.mobile.mock"
            or owner.get("fixture") != "CurveEditor.editor-stage-hidden-values.recreate"
            or owner.get("ownedStart") is not True
            or report.get("owner") != owner or report.get("fixtureContext") != context):
        raise ValueError("trace owner/context identity differs")
    for key in ("sourceSha", "profile"):
        if owner.get(key) != identity[key]:
            raise ValueError(f"trace owner mismatch: {key}")
    for key in ("sourceSha", "headSha", "sourceDirty", "apkSha256", "ciRun", "ciJob", "profile"):
        if context.get(key) != identity[key]:
            raise ValueError(f"trace context mismatch: {key}")
    if context.get("fixture") != owner["fixture"]:
        raise ValueError("trace fixture differs")
    if not (_owned_stop_confirmed(owner) or _fallback_stop_confirmed(directory, token)):
        raise ValueError("no uniquely acknowledged owned trace stop")
    raw = report.get("rawArtifacts")
    if not isinstance(raw, dict):
        raise ValueError("raw trace inventory absent")
    for name in TRACE_FILES:
        path = directory / name
        listed = raw.get(name)
        if path.is_file() != (listed is not None):
            raise ValueError("obtained raw trace unlisted or listed raw trace absent")
        if listed is not None:
            data = path.read_bytes()
            if listed.get("sizeBytes") != len(data) or listed.get("sha256") != hashlib.sha256(data).hexdigest():
                raise ValueError("retained raw trace hash differs")
    quality = report.get("status")
    if quality == "COMPLETE_NATIVE_TRACE_ONLY_NOT_ROOT_CAUSE":
        if exit_code != 0 or report.get("failure"):
            raise ValueError("complete trace status contradicts collector result")
        decoded = report.get("decoded", {})
        if set(decoded) != set(TRACE_FILES):
            raise ValueError("complete trace decoded inventory differs")
        for name in TRACE_FILES:
            item = decoded[name]
            if (item.get("fileSha256") != raw[name]["sha256"] or item.get("differentFromBefore") is not True
                    or item.get("coversRecreateAndComparisonTime") is not True):
                raise ValueError("complete trace quality proof differs")
    elif quality == "PARTIAL_OR_UNAVAILABLE":
        if exit_code != 1 or not isinstance(report.get("failure"), str) or not report["failure"]:
            raise ValueError("partial trace status contradicts collector result")
    else:
        raise ValueError("collector completion status missing")
    return {"traceSafety": TRACE_SAFETY, "traceQualityStatus": quality,
            "traceQualityFailure": report.get("failure"), "collectorReturnCode": exit_code,
            "collectorReport": str(directory / "collection-complete.json")}


def case_status(business_status, trace_quality):
    if business_status not in ("PASS", "FAIL") or trace_quality not in (
            "NOT_APPLICABLE", "COMPLETE_NATIVE_TRACE_ONLY_NOT_ROOT_CAUSE", "PARTIAL_OR_UNAVAILABLE"):
        raise ValueError("unverified case or trace classification")
    return "FAIL" if business_status == "FAIL" or trace_quality == "PARTIAL_OR_UNAVAILABLE" else "PASS"


def run_matrix(output_dir, execute, source=None):
    """Run one unique key at a time; execute returns a classified evidence record."""
    keys = case_keys()
    summary = {
        "cases": [
            {"theme": theme, "fontScale": scale, "page": page, "classification": "NOT_RUN",
             "attempted": False,
             "logPath": str(output_dir / f"{theme}-{scale}-{page}-result.txt")}
            for theme, scale, page in keys
        ],
        "counts": {status: 0 for status in ("PASS", "FAIL", "ABORT", "NOT_RUN")},
    }
    output_dir = Path(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    def persist():
        summary["counts"] = {
            status: sum(item["classification"] == status for item in summary["cases"])
            for status in ("PASS", "FAIL", "ABORT", "NOT_RUN")
        }
        summary["counts"]["attempted"] = sum(item["attempted"] for item in summary["cases"])
        (output_dir / "case-aggregation.json").write_text(json.dumps(summary, indent=2, ensure_ascii=False) + "\n")

    persist()
    for index, key in enumerate(keys):
        summary["cases"][index]["attempted"] = True
        theme, scale, page = key
        profile = f"tablet-user-landscape-{theme}-font-{scale}"
        if source is not None:
            case_folder = output_dir / "raw-device" / source / profile / page
            summary["cases"][index]["manifestPath"] = str(case_folder / "manifest.json")
            summary["cases"][index]["isolationProof"] = str(case_folder / "fixture-isolation.json")
        persist()
        try:
            evidence = execute(key)
            if evidence.get("classification") not in ("PASS", "FAIL", "ABORT"):
                raise ValueError("case classification unknown")
        except Exception as error:
            evidence = {"classification": "ABORT", "reason": repr(error)}
        summary["cases"][index].update(evidence)
        persist()
        if evidence["classification"] == "ABORT":
            return summary, 2
    return summary, 1 if summary["counts"]["FAIL"] else 0


class CaseExecutor:
    """One fresh formPage instrumentation per key, using the existing CI emulator."""

    def __init__(self, output_dir, source, dirty, apk, head, ci_run, ci_job, run=subprocess.run, session_nonce=None):
        self.output_dir = Path(output_dir)
        self.source, self.dirty, self.apk, self.head = source, dirty, apk, head
        self.ci_run, self.ci_job, self.run = ci_run, ci_job, run
        self.session_nonce = session_nonce
        self.last_theme = self.last_scale = None
        if not re.fullmatch(r"[0-9a-f]{40}", source) or not re.fullmatch(r"[0-9a-f]{40}", head):
            raise ValueError("source/head SHA invalid")
        if session_nonce is not None and not re.fullmatch(r"[0-9a-f]{32}", session_nonce):
            raise ValueError("invalid host session nonce")

    def _checked(self, command, **kwargs):
        result = self.run(command, **kwargs)
        if result.returncode != 0:
            raise ValueError(f"host/device command failed: {command!r}; returncode={result.returncode}")
        return result

    def _remote_case_absent(self, remote, result_path):
        script = """base=/sdcard/Android/data
[ -d "$base" ] && [ -r "$base" ] && [ -x "$base" ] || exit 70
path="$base"
for child in io.openhoyi.mobile.mock files form-visual-migration "$source" "$profile"; do
  path="$path/$child"
  if [ -e "$path" ] || [ -L "$path" ]; then
    [ -d "$path" ] && [ -r "$path" ] && [ -x "$path" ] || exit 71
  else
    break
  fi
done
if [ -e "$target" ] || [ -L "$target" ]; then printf 'M2_CASE_DIR_EXISTS\\n'; else printf 'M2_CASE_DIR_ABSENT\\n'; fi
"""
        profile = remote.split("/")[-2]
        result = self.run(["adb", "shell", "sh"],
                          input=f"source='{self.source}'\nprofile='{profile}'\ntarget='{remote}'\n" + script,
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=10)
        verified = (result.returncode == 0 and result.stderr == ""
                    and result.stdout.replace("\r\n", "\n") == "M2_CASE_DIR_ABSENT\n")
        result_path.with_suffix(".fresh-directory.json").write_text(json.dumps({
            "remoteCommand": "fixed sh directory presence check", "deviceDirectory": remote,
            "adbReturnCode": result.returncode, "stdout": result.stdout, "stderr": result.stderr,
            "freshDirectoryVerified": verified}, indent=2) + "\n")
        if not verified:
            raise ValueError("remote case directory pre-exists or absence unproven")

    def _fresh_process(self, result_path):
        script = r"""
command -v pidof >/dev/null 2>&1 || exit 70
fresh_process_pid_output=$(pidof io.openhoyi.mobile.mock 2>&1)
fresh_process_pid_status=$?
printf 'FRESH_PIDOF_STATUS=%s\n' "$fresh_process_pid_status"
printf 'FRESH_PIDOF_OUTPUT=%s\n' "$fresh_process_pid_output"
"""
        probe = self.run(["adb", "shell", "sh"], input=script, stdout=subprocess.PIPE,
                         stderr=subprocess.PIPE, text=True, timeout=10)
        verified = (probe.returncode == 0 and probe.stdout.replace("\r\n", "\n") ==
                    "FRESH_PIDOF_STATUS=1\nFRESH_PIDOF_OUTPUT=\n" and probe.stderr == "")
        result_path.with_suffix(".fresh-process.json").write_text(json.dumps({
            "remoteCommand": "pidof io.openhoyi.mobile.mock", "adbReturnCode": probe.returncode,
            "stdout": probe.stdout, "stderr": probe.stderr, "freshProcessVerified": verified,
            "policy": "exact_completed_remote_pidof_exit1_empty_output_only; all_unknowns_fail_closed"
        }, indent=2) + "\n")
        if not verified:
            raise ValueError("fresh-process guard failed or unknown")

    def _collector_before(self, identity, remote):
        root = self.output_dir
        native_dir = root / "ime-native-traces" / "light-1.0-curve-editor"
        capability = root / "ime-native-capability"
        device = remote + "/ime-recreation-native"
        if (native_dir.exists() or native_dir.is_symlink() or capability.exists() or capability.is_symlink()
                or (root / "ime-active-profile.json").exists() or (root / "ime-active-profile.json").is_symlink()):
            raise ValueError("collector evidence or active marker already exists; no old report reuse")
        self._checked(["python3", "scripts/collect_ime_recreation_trace.py", "probe",
                       "--directory", str(capability)], timeout=90)
        self._checked(["python3", "scripts/collect_ime_recreation_trace.py", "before",
                       "--directory", str(native_dir)], timeout=90)
        native_dir.mkdir(parents=True, exist_ok=True)
        (native_dir / "fixture-context.json").write_text(json.dumps(dict(identity,
            fixture="CurveEditor.editor-stage-hidden-values.recreate"), indent=2) + "\n")
        (root / "ime-active-profile.json").write_text(json.dumps({
            "directory": str(native_dir), "deviceDirectory": device,
            "sessionNonce": self.session_nonce, "sourceSha": self.source,
            "headSha": self.head, "apkSha256": self.apk,
            "ciRun": self.ci_run, "ciJob": self.ci_job,
            "profile": identity["profile"]}, indent=2) + "\n")
        return native_dir, capability, device

    def _collector_after(self, native):
        native_dir, capability, device = native
        try:
            result = self.run(["python3", "scripts/collect_ime_recreation_trace.py", "after",
                               "--directory", str(native_dir), "--device-directory", device,
                               "--probe-directory", str(capability)], timeout=120)
            return result.returncode
        finally:
            # after has been attempted; EXIT must not issue a second stop.
            (self.output_dir / "ime-active-profile.json").unlink(missing_ok=True)

    def execute(self, key):
        theme, scale, page = key
        profile = f"tablet-user-landscape-{theme}-font-{scale}"
        case_dir = self.output_dir / "raw-device" / self.source / profile / page
        result_path = self.output_dir / f"{theme}-{scale}-{page}-result.txt"
        identity = {"sourceSha": self.source, "sourceDirty": self.dirty, "headSha": self.head,
                    "apkSha256": self.apk, "ciRun": self.ci_run, "ciJob": self.ci_job,
                    "profile": profile, "page": page}
        if case_dir.exists() or case_dir.is_symlink() or result_path.exists():
            raise ValueError("host case directory or result already exists")
        if theme != self.last_theme:
            self._checked(["adb", "shell", "cmd", "uimode", "night", "yes" if theme == "dark" else "no"])
            self.last_theme = theme
        if scale != self.last_scale:
            self._checked(["adb", "shell", "settings", "put", "system", "font_scale", scale])
            self.last_scale = scale
        self._checked(["adb", "shell", "am", "force-stop", "io.openhoyi.mobile.mock"])
        remote = (f"/sdcard/Android/data/io.openhoyi.mobile.mock/files/form-visual-migration/"
                  f"{self.source}/{profile}/{page}")
        self._remote_case_absent(remote, result_path)
        self._fresh_process(result_path)
        command = ["adb", "shell", "am", "instrument", "-w"]
        extras = {"formVisualMigration": "true", "disposableCiEmulator": "true",
                  "freshProcessVerified": "true", "formPage": page, "sourceSha": self.source,
                  "headSha": self.head, "sourceDirty": self.dirty, "apkSha": self.apk,
                  "sampleTheme": theme, "expectedFontScale": scale,
                  "ciRun": self.ci_run, "ciJob": self.ci_job}
        for name, value in extras.items():
            command += ["-e", name, value]
        command += ["io.openhoyi.mobile.mock.test/io.openhoyi.mobile.BrewAudioInstrumentation"]
        first = key == ("light", "1.0", "curve-editor")
        native = self._collector_before(identity, remote) if first else None
        collector_code = None
        instrument = None
        instrument_error = None
        try:
            with result_path.open("w") as capture:
                instrument = self.run(command, stdout=capture, stderr=subprocess.STDOUT, timeout=600)
        except Exception as error:
            instrument_error = error
        finally:
            if native is not None:
                try:
                    collector_code = self._collector_after(native)
                except Exception as error:
                    if instrument_error is None:
                        instrument_error = error
                    else:
                        instrument_error = RuntimeError(f"instrument={instrument_error!r}; collector={error!r}")
        if instrument_error is not None:
            raise ValueError(f"instrument/collector did not terminate normally: {instrument_error!r}")
        output = result_path.read_text()
        case_dir.parent.mkdir(parents=True, exist_ok=True)
        pull = self.run(["adb", "pull", remote, str(case_dir.parent) + "/"],
                        stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=120)
        if pull.returncode != 0:
            raise ValueError("case pull failed; original device evidence retained")
        terminal = classify_terminal(output, instrument.returncode)
        details = validate_case_files(case_dir, identity, terminal)
        trace = {"traceQualityStatus": "NOT_APPLICABLE", "traceSafety": "NOT_APPLICABLE"}
        if first:
            trace = validate_collector(native[0], identity, collector_code)
        status = case_status(terminal, trace["traceQualityStatus"])
        return {"classification": status, "caseStatus": status, "businessStatus": terminal,
                "diagnosticQualityFailure": trace["traceQualityStatus"] == "PARTIAL_OR_UNAVAILABLE",
                "continuationReason": "independent case isolation and trace stop safe" if status == "FAIL" else "verified case complete",
                "manifestPath": str(case_dir / "manifest.json"), **details, **trace,
                "instrumentReturnCode": instrument.returncode}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-dir", required=True, type=Path)
    parser.add_argument("--source", required=True)
    parser.add_argument("--dirty", required=True)
    parser.add_argument("--apk", required=True)
    parser.add_argument("--head", required=True)
    parser.add_argument("--session-nonce", required=True)
    args = parser.parse_args(argv)
    executor = CaseExecutor(args.output_dir, args.source, args.dirty, args.apk, args.head,
                            os.environ["GITHUB_RUN_ID"], os.environ["GITHUB_JOB"],
                            session_nonce=args.session_nonce)
    summary, code = run_matrix(args.output_dir, executor.execute, args.source)
    summary["hostRunAttempt"] = os.environ.get("GITHUB_RUN_ATTEMPT")
    (args.output_dir / "case-aggregation.json").write_text(json.dumps(summary, indent=2, ensure_ascii=False) + "\n")
    if code == 0:
        (args.output_dir / "raw-device" / args.source / "capture-summary.json").write_text(json.dumps({
            "realConfigurations": 12, "sourceSha": args.source, "visualAcceptance": "ASTRA_PENDING",
            "specimenImagesUsedAsProductEvidence": False}, indent=2) + "\n")
        print("FORM_VISUAL_MIGRATION_RETAINED configurations=12 actualActivities=true visualAcceptance=ASTRA_PENDING")
    else:
        print("FORM_VISUAL_MIGRATION_" + ("ABORTED" if code == 2 else "AGGREGATE_FAILED") +
              f" pass={summary['counts']['PASS']} fail={summary['counts']['FAIL']} abort={summary['counts']['ABORT']}",
              file=sys.stderr)
    return code


if __name__ == "__main__":
    raise SystemExit(main())
