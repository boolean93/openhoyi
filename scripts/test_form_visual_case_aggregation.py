import importlib.util
import hashlib
import json
import os
import struct
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace


SPEC = importlib.util.spec_from_file_location(
    "form_visual_case_aggregation", Path(__file__).with_name("form_visual_case_aggregation.py")
)
module = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(module)


class AggregationTest(unittest.TestCase):
    def test_bash_exit_preserves_priority_and_never_follows_old_trace_marker(self):
        runner = Path(__file__).with_name("check_form_visual_migration.sh")
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "scripts").mkdir()
            apk = root / "mobile/build/outputs/apk/mock/mobile-mock.apk"
            apk.parent.mkdir(parents=True)
            apk.write_bytes(b"mock-test-apk")
            binary = root / "bin"
            binary.mkdir()
            (binary / "git").write_text("#!/bin/bash\nif [[ $1 == rev-parse ]]; then printf '%040d\\n' 0 | tr 0 a; fi\n")
            (binary / "adb").write_text("""#!/bin/bash
printf '%s\\n' "$*" >> "$M2_CALL_LOG"
if [[ "$*" == 'shell getprop ro.kernel.qemu' ]]; then echo 1; exit 0; fi
if [[ "$*" == 'shell settings get system font_scale' ]]; then echo 1.0; exit 0; fi
if [[ "$*" == 'logcat -d -s AndroidRuntime:E' && "$M2_CLEANUP_FAIL" == 1 ]]; then exit 1; fi
exit 0
""")
            (binary / "python3").write_text("""#!/bin/bash
if [[ "$1" == scripts/collect_ime_recreation_trace.py ]]; then
  echo "collector $*" >> "$M2_CALL_LOG"
  exit 0
fi
if [[ "$1" == scripts/form_visual_case_aggregation.py ]]; then
  "$M2_REAL_PYTHON" - "$@" <<'PY'
import json,os,sys
from pathlib import Path
args=sys.argv[2:]
fields={args[i]:args[i+1] for i in range(0,len(args),2)}
root=Path(fields['--output-dir'])
if os.environ['M2_ACTIVE_MODE']=='owned-complete':
    profile='tablet-user-landscape-light-font-1.0'
    directory=root/'ime-native-traces'/'light-1.0-curve-editor'
    directory.mkdir(parents=True)
    (directory/'collection-complete.json').write_text('{}')
    marker={'directory':str(directory),'deviceDirectory':f"/sdcard/Android/data/io.openhoyi.mobile.mock/files/form-visual-migration/{fields['--source']}/{profile}/curve-editor/ime-recreation-native",'sessionNonce':fields['--session-nonce'],'sourceSha':fields['--source'],'headSha':fields['--head'],'apkSha256':fields['--apk'],'ciRun':os.environ['GITHUB_RUN_ID'],'ciJob':os.environ['GITHUB_JOB'],'profile':profile}
    (root/'ime-active-profile.json').write_text(json.dumps(marker))
if os.environ['M2_COMPLETE_MATRIX']=='1':
    (root/'case-aggregation.json').write_text(json.dumps({'counts':{'NOT_RUN':0,'ABORT':0}}))
PY
  exit "$M2_MAIN_EXIT"
fi
exec "$M2_REAL_PYTHON" "$@"
""")
            for name in ("git", "adb", "python3"):
                (binary / name).chmod(0o755)
            output = root / "build/task-first-evidence/form-visual-migration"
            for label, main_exit, cleanup_fail, active, completed, expected in (
                ("main-fail", 1, 0, "none", 1, 1),
                ("main-and-cleanup-fail", 1, 1, "none", 1, 2),
                ("cleanup-fail", 0, 1, "none", 1, 2),
                ("old-owner", 2, 0, "old", 0, 2),
                ("owned-complete", 1, 0, "owned-complete", 1, 1),
            ):
                with self.subTest(label=label):
                    if output.exists():
                        import shutil
                        shutil.rmtree(output)
                    output.mkdir(parents=True)
                    if active == "old":
                        (output / "ime-active-profile.json").write_text(json.dumps({
                            "directory": str(output / "old-owner"), "deviceDirectory": "/old-owner"}))
                    log = root / "calls.log"
                    log.write_text("")
                    env = dict(os.environ, PATH=str(binary) + os.pathsep + os.environ["PATH"],
                               GITHUB_ACTIONS="true", GITHUB_RUN_ID="123", GITHUB_JOB="task-frame",
                               M2_REAL_PYTHON=sys.executable, M2_CALL_LOG=str(log),
                               M2_MAIN_EXIT=str(main_exit), M2_CLEANUP_FAIL=str(cleanup_fail),
                               M2_ACTIVE_MODE=active, M2_COMPLETE_MATRIX=str(completed))
                    finished = subprocess.run(["bash", str(runner)], cwd=root, env=env,
                                              capture_output=True, text=True, timeout=20)
                    self.assertEqual(expected, finished.returncode, finished.stderr)
                    self.assertTrue((output / "exit-collection.json").is_file(),
                                    f"{label}: {finished.stderr}; calls={log.read_text()}")
                    calls = log.read_text()
                    self.assertNotIn("collector ", calls)
                    if active == "old":
                        self.assertTrue((output / "ime-active-profile.json").is_file())
                        self.assertIn("ime_exit_collector_failed", json.loads((output / "exit-collection.json").read_text())["cleanupErrors"])
                    else:
                        self.assertEqual(main_exit, json.loads((output / "exit-collection.json").read_text())["originalExit"])

    def test_fake_transport_runs_twelve_fresh_cases_and_keeps_first_ime_failure(self):
        source, head, apk = "a" * 40, "b" * 40, "c" * 64
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            instruments = []

            def write_case(folder, profile, page, failed):
                folder.mkdir(parents=True)
                isolation = {"status": "PASS_ISOLATION_ONLY", "businessDiskBefore": {}, "businessDiskAfter": {},
                             "whitePrefFilesBefore": {"appearance.xml": "a" * 64, "curves.xml": "b" * 64},
                             "whitePrefFilesAfter": {"appearance.xml": "c" * 64, "curves.xml": "d" * 64}}
                isolation.update({key: True for key in module.ISOLATION_BOOLEANS})
                files = {"fixture-isolation.json": json.dumps(isolation).encode()}
                if not failed:
                    files.update({"window.png": b"\x89PNG\r\n\x1a\n" + b"\x00" * 8 + struct.pack(">II", 2560, 1600),
                                  "window-frame-sync.json": b'{"protocolVersion":2,"accepted":true}',
                                  "form-business.json": b'{"completed":true,"noMachineOrServiceCommands":true}',
                                  "geometry.json": b'{}'})
                for name, data in files.items():
                    (folder / name).write_bytes(data)
                manifest = {"sourceSha": source, "headSha": head, "sourceDirty": "false", "apkSha256": apk,
                            "ciRun": "123", "ciJob": "task-frame", "profile": profile, "page": page,
                            "pid": 17, "realProductActivity": True, "specimenOnly": False,
                            "visualAcceptance": "ASTRA_PENDING", "layoutStatus": "FAIL" if failed else "PASS_LAYOUT_ONLY_NOT_VISUAL_ACCEPTANCE",
                            "isolationStatus": "SEE_fixture-isolation.json" if failed else "PASS",
                            "fileSha256": {name: hashlib.sha256(data).hexdigest() for name, data in files.items()}}
                if failed:
                    manifest["failure"] = "original IME failure"
                else:
                    manifest.update(fontSha256=module.FONT_SHA, wholeWindowPngSizePx=[2560, 1600],
                                    window={"densityDpi": 360, "locale": "zh-CN", "uiModeNightMeaning": profile.split("-font-")[0].rsplit("-", 1)[-1],
                                            "fontScale": float(profile.rsplit("-font-", 1)[-1]), "imeVisible": False},
                                    initialFrameSync={"protocolVersion": 2, "accepted": True})
                (folder / "manifest.json").write_text(json.dumps(manifest))

            def fake_run(command, **kwargs):
                if command[:3] == ["adb", "shell", "sh"]:
                    output = ("M2_CASE_DIR_ABSENT\n" if "M2_CASE_DIR" in kwargs["input"]
                              else "FRESH_PIDOF_STATUS=1\nFRESH_PIDOF_OUTPUT=\n")
                    return SimpleNamespace(returncode=0, stdout=output, stderr="")
                if command[:5] == ["adb", "shell", "am", "instrument", "-w"]:
                    instruments.append(command)
                    failed = len(instruments) == 1
                    kwargs["stdout"].write("FORM_VISUAL_MIGRATION_FAILED original IME failure\n" if failed else module.SUCCESS_MARKER + "\n")
                    return SimpleNamespace(returncode=0)
                if command[:2] == ["adb", "pull"]:
                    remote = command[2]
                    folder = Path(command[3]) / remote.rsplit("/", 1)[-1]
                    write_case(folder, remote.split("/")[-2], remote.split("/")[-1], len(instruments) == 1)
                    return SimpleNamespace(returncode=0, stdout="", stderr="")
                if "collect_ime_recreation_trace.py" in command and "after" in command:
                    # This test exercises the twelve-case transport; owned-stop proof is tested separately.
                    return SimpleNamespace(returncode=1)
                return SimpleNamespace(returncode=0)

            executor = module.CaseExecutor(root, source, "false", apk, head, "123", "task-frame", run=fake_run)
            original_validate = module.validate_collector
            module.validate_collector = lambda *args: {"traceSafety": module.TRACE_SAFETY,
                "traceQualityStatus": "PARTIAL_OR_UNAVAILABLE", "traceQualityFailure": "coverage incomplete",
                "collectorReturnCode": 1, "collectorReport": "synthetic-owned-stop-report"}
            try:
                summary, exit_code = module.run_matrix(root, executor.execute)
            finally:
                module.validate_collector = original_validate
            self.assertEqual(12, len(instruments))
            self.assertEqual(1, exit_code)
            self.assertEqual("original IME failure", summary["cases"][0]["failure"])
            self.assertEqual("FAIL", summary["cases"][0]["businessStatus"])
            self.assertEqual("FAIL", summary["cases"][0]["caseStatus"])
            self.assertEqual(11, summary["counts"]["PASS"])
            self.assertTrue((root / "light-1.0-curve-editor-result.fresh-directory.json").is_file())

    def test_remote_existing_directory_is_saved_and_blocks_instrumentation(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            commands = []

            def fake_run(command, **kwargs):
                commands.append(command)
                if command[:3] == ["adb", "shell", "sh"]:
                    return SimpleNamespace(returncode=0, stdout="M2_CASE_DIR_EXISTS\n", stderr="")
                return SimpleNamespace(returncode=0)

            executor = module.CaseExecutor(root, "a" * 40, "false", "c" * 64, "b" * 40,
                                           "123", "task-frame", run=fake_run)
            summary, exit_code = module.run_matrix(root, executor.execute, executor.source)
            self.assertEqual(2, exit_code)
            self.assertEqual(1, summary["counts"]["attempted"])
            self.assertEqual(11, summary["counts"]["NOT_RUN"])
            self.assertTrue(summary["cases"][0]["manifestPath"].endswith("curve-editor/manifest.json"))
            self.assertFalse(any(command[:5] == ["adb", "shell", "am", "instrument", "-w"] for command in commands))
            saved = json.loads((root / "light-1.0-curve-editor-result.fresh-directory.json").read_text())
            self.assertEqual("M2_CASE_DIR_EXISTS\n", saved["stdout"])
            self.assertFalse(saved["freshDirectoryVerified"])

    def test_existing_native_collector_evidence_aborts_before_probe(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "ime-native-traces" / "light-1.0-curve-editor").mkdir(parents=True)
            calls = []
            executor = module.CaseExecutor(root, "a" * 40, "false", "c" * 64, "b" * 40,
                                           "123", "task-frame", run=lambda *a, **k: calls.append(a))
            with self.assertRaises(ValueError):
                executor._collector_before(self.identity(), "/sdcard/unused")
            self.assertEqual([], calls)
    @staticmethod
    def identity():
        return {"sourceSha": "a" * 40, "headSha": "b" * 40, "sourceDirty": "false",
                "apkSha256": "c" * 64, "ciRun": "123", "ciJob": "task-frame",
                "profile": "tablet-user-landscape-light-font-1.0", "page": "curve-editor"}

    def test_manifest_files_are_fresh_hashed_and_contained(self):
        with tempfile.TemporaryDirectory() as temporary:
            folder = Path(temporary)
            proof = {"status": "PASS_ISOLATION_ONLY", "businessDiskBefore": {}, "businessDiskAfter": {},
                     "whitePrefFilesBefore": {"appearance.xml": "a" * 64, "curves.xml": "b" * 64},
                     "whitePrefFilesAfter": {"appearance.xml": "c" * 64, "curves.xml": "d" * 64}}
            proof.update({key: True for key in module.ISOLATION_BOOLEANS})
            (folder / "fixture-isolation.json").write_text(json.dumps(proof))
            digest = hashlib.sha256((folder / "fixture-isolation.json").read_bytes()).hexdigest()
            manifest = dict(self.identity(), pid=17, realProductActivity=True, specimenOnly=False,
                            visualAcceptance="ASTRA_PENDING", layoutStatus="FAIL", failure="original IME failure",
                            fileSha256={"fixture-isolation.json": digest}, isolationStatus="SEE_fixture-isolation.json")
            (folder / "manifest.json").write_text(json.dumps(manifest))
            result = module.validate_case_files(folder, self.identity(), "FAIL")
            self.assertEqual("original IME failure", result["failure"])
            for mutation in (dict(manifest, headSha="z" * 40),
                             dict(manifest, fileSha256={"fixture-isolation.json": "0" * 64}),
                             dict(manifest, fileSha256={"../outside": digest})):
                (folder / "manifest.json").write_text(json.dumps(mutation))
                with self.assertRaises(ValueError):
                    module.validate_case_files(folder, self.identity(), "FAIL")

    def test_original_success_window_business_and_all_frame_proofs_remain_required(self):
        with tempfile.TemporaryDirectory() as temporary:
            folder = Path(temporary)
            proof = {"status": "PASS_ISOLATION_ONLY", "businessDiskBefore": {}, "businessDiskAfter": {},
                     "whitePrefFilesBefore": {"appearance.xml": "a" * 64, "curves.xml": "b" * 64},
                     "whitePrefFilesAfter": {"appearance.xml": "c" * 64, "curves.xml": "d" * 64}}
            proof.update({key: True for key in module.ISOLATION_BOOLEANS})
            files = {"fixture-isolation.json": json.dumps(proof).encode(),
                     "window.png": b"\x89PNG\r\n\x1a\n" + b"\x00" * 8 + struct.pack(">II", 2560, 1600),
                     "window-frame-sync.json": b'{"protocolVersion":2,"accepted":true}',
                     "form-business.json": b'{"completed":true,"noMachineOrServiceCommands":true}',
                     "geometry.json": b'{}'}
            for name, data in files.items():
                (folder / name).write_bytes(data)
            manifest = dict(self.identity(), pid=17, realProductActivity=True, specimenOnly=False,
                            visualAcceptance="ASTRA_PENDING", layoutStatus="PASS_LAYOUT_ONLY_NOT_VISUAL_ACCEPTANCE",
                            fileSha256={name: hashlib.sha256(data).hexdigest() for name, data in files.items()},
                            isolationStatus="PASS", fontSha256=module.FONT_SHA,
                            wholeWindowPngSizePx=[2560, 1600],
                            window={"densityDpi": 360, "locale": "zh-CN", "uiModeNightMeaning": "light", "fontScale": 1.0,
                                    "imeVisible": False},
                            initialFrameSync={"protocolVersion": 2, "accepted": True})
            (folder / "manifest.json").write_text(json.dumps(manifest))
            module.validate_case_files(folder, self.identity(), "PASS")
            missing_status = dict(manifest)
            missing_status.pop("layoutStatus")
            (folder / "manifest.json").write_text(json.dumps(missing_status))
            with self.assertRaises(ValueError):
                module.validate_case_files(folder, self.identity(), "PASS")
            (folder / "manifest.json").write_text(json.dumps(manifest))
            (folder / "window-frame-sync.json").write_text('{"protocolVersion":2,"accepted":false}')
            manifest["fileSha256"]["window-frame-sync.json"] = hashlib.sha256((folder / "window-frame-sync.json").read_bytes()).hexdigest()
            (folder / "manifest.json").write_text(json.dumps(manifest))
            with self.assertRaises(ValueError):
                module.validate_case_files(folder, self.identity(), "PASS")

    def test_partial_trace_with_owned_stop_and_retained_raw_can_continue(self):
        identity = self.identity()
        token = "6cea1f2d-ec8d-4b38-847e-7cf4a656b72c"
        ack = "Stopping tracing and writing traces in /data/misc/wmtrace/: ime_trace_clients.winscope, ime_trace_service.winscope, ime_trace_managerservice.winscope"
        nonce = "1f6b78af-9850-47b9-b3f3-c8adbe4f9eb5"
        stop = {"name": "stop", "command": "cmd input_method tracing stop", "commandNonce": nonce,
                "exitCode": 0, "completedWithExitMarker": True, "workerTerminated": True,
                "stdinWrittenAndClosed": True, "stdoutEof": True, "stderrEof": True,
                "withinTotalBudget": True, "output": ack + "\n",
                "stdout": ack + "\n\nIME_TRACE_RC_" + nonce + "=0\n", "stderr": ""}
        owner = {"traceOwnerToken": token, "package": "io.openhoyi.mobile.mock",
                 "fixture": "CurveEditor.editor-stage-hidden-values.recreate",
                 "sourceSha": identity["sourceSha"], "profile": identity["profile"],
                 "ownedStart": True, "stopAttempted": True, "stopAcknowledged": True,
                 "commands": [stop]}
        context = dict(identity, fixture=owner["fixture"])
        with tempfile.TemporaryDirectory() as temporary:
            folder = Path(temporary)
            (folder / "trace-state.json").write_text(json.dumps(owner))
            (folder / "fixture-context.json").write_text(json.dumps(context))
            (folder / "ime_trace_clients.winscope").write_bytes(b"raw")
            report = {"status": "PARTIAL_OR_UNAVAILABLE", "failure": "coverage incomplete",
                      "owner": owner, "fixtureContext": context,
                      "rawArtifacts": {"ime_trace_clients.winscope": {"sizeBytes": 3,
                                       "sha256": hashlib.sha256(b"raw").hexdigest()}}}
            (folder / "collection-complete.json").write_text(json.dumps(report))
            result = module.validate_collector(folder, identity, 1)
            self.assertEqual("PARTIAL_OR_UNAVAILABLE", result["traceQualityStatus"])
            self.assertEqual("OWNED_STOP_ACK_COLLECTOR_EXITED_ARTIFACTS_RETAINED", result["traceSafety"])
            owner["commands"][0]["workerTerminated"] = False
            report["owner"] = owner
            (folder / "trace-state.json").write_text(json.dumps(owner))
            (folder / "collection-complete.json").write_text(json.dumps(report))
            with self.assertRaises(ValueError):
                module.validate_collector(folder, identity, 1)

    def test_first_isolated_failure_continues_without_retry(self):
        calls = []

        def execute(key):
            calls.append(key)
            return {"classification": "FAIL" if len(calls) == 1 else "PASS", "failure": "original IME failure" if len(calls) == 1 else None}

        with tempfile.TemporaryDirectory() as temporary:
            summary, exit_code = module.run_matrix(Path(temporary), execute)
        self.assertEqual(12, len(calls))
        self.assertEqual(12, len(set(calls)))
        self.assertEqual(1, exit_code)
        self.assertEqual("original IME failure", summary["cases"][0]["failure"])
        self.assertEqual(1, summary["counts"]["FAIL"])
        self.assertEqual(12, summary["counts"]["attempted"])

    def test_twelve_complete_cases_are_the_only_zero_exit(self):
        with tempfile.TemporaryDirectory() as temporary:
            summary, exit_code = module.run_matrix(Path(temporary), lambda key: {"classification": "PASS"})
        self.assertEqual(0, exit_code)
        self.assertEqual(12, summary["counts"]["PASS"])
        self.assertEqual(0, summary["counts"]["NOT_RUN"])

    def test_business_success_with_partial_trace_is_aggregate_failure(self):
        self.assertEqual("FAIL", module.case_status("PASS", "PARTIAL_OR_UNAVAILABLE"))
        with tempfile.TemporaryDirectory() as temporary:
            summary, exit_code = module.run_matrix(Path(temporary), lambda key: {
                "classification": "FAIL" if key == ("light", "1.0", "curve-editor") else "PASS",
                "businessStatus": "PASS", "traceQualityStatus": "PARTIAL_OR_UNAVAILABLE" if key == ("light", "1.0", "curve-editor") else "NOT_APPLICABLE"})
        self.assertEqual(1, exit_code)
        self.assertEqual("PASS", summary["cases"][0]["businessStatus"])
        self.assertEqual("FAIL", summary["cases"][0]["classification"])

    def test_abort_preserves_remaining_not_run(self):
        calls = []

        def execute(key):
            calls.append(key)
            return {"classification": "ABORT", "reason": "unknown cleanup"}

        with tempfile.TemporaryDirectory() as temporary:
            summary, exit_code = module.run_matrix(Path(temporary), execute)
            self.assertTrue((Path(temporary) / "case-aggregation.json").exists())
        self.assertEqual(2, exit_code)
        self.assertEqual(1, len(calls))
        self.assertEqual(11, summary["counts"]["NOT_RUN"])
        self.assertEqual(1, summary["counts"]["attempted"])

    def test_isolation_requires_real_booleans_and_equal_disk(self):
        proof = {"status": "PASS_ISOLATION_ONLY", "businessDiskBefore": {"a": "sha"}, "businessDiskAfter": {"a": "sha"},
                 "whitePrefFilesBefore": {"appearance.xml": "a" * 64, "curves.xml": "b" * 64},
                 "whitePrefFilesAfter": {"appearance.xml": "c" * 64, "curves.xml": "d" * 64}}
        proof.update({key: True for key in module.ISOLATION_BOOLEANS})
        module.validate_isolation(proof)
        for key in module.ISOLATION_BOOLEANS:
            invalid = dict(proof, **{key: "true"})
            with self.subTest(key=key), self.assertRaises(ValueError):
                module.validate_isolation(invalid)
        with self.assertRaises(ValueError):
            module.validate_isolation(dict(proof, businessDiskAfter={"a": "different"}))
        with self.assertRaises(ValueError):
            module.validate_isolation(dict(proof, failure=""))

    def test_terminal_markers_are_unique_and_consistent(self):
        ok = "FORM_VISUAL_MIGRATION_CAPTURED fixtures=1 realActivities=true visualAcceptance=ASTRA_PENDING noBle=true isolationVerified=true\n"
        failed = "FORM_VISUAL_MIGRATION_FAILED java.lang.IllegalStateException: IME\n"
        self.assertEqual("PASS", module.classify_terminal(ok, 0))
        self.assertEqual("FAIL", module.classify_terminal(failed, 0))
        for text in (ok + failed, failed + failed, "", "FORM_VISUAL_MIGRATION_FAILED \n"):
            with self.subTest(text=text), self.assertRaises(ValueError):
                module.classify_terminal(text, 0)
        with self.assertRaises(ValueError):
            module.classify_terminal(ok, 1)


if __name__ == "__main__":
    unittest.main()
