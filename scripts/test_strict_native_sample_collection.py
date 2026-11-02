"""Offline regression plan for the strict specimen EXIT collector.

Source/work branch: feature/task-first-ui at 79868c7; PR target: develop (#4).
Scope: only check_strict_native_samples.sh and this regression; no device, network,
Gradle, product, or current CI artifact changes. Extract the actual collector/trap
and replace its command name with a local mock before executing any shell code.
Verify host finalization survives success/failure, raw failure diagnostics remain,
and the original exit status/font restoration are preserved. No gate is relaxed.
No release/signing/version/upgrade changes. Root batches an ordinary push/PR;
rollback is an additive revert of these two files, never deleting user records.
"""

from pathlib import Path
import json
import re
import subprocess
import tempfile
import unittest


SCRIPT = Path(__file__).with_name("check_strict_native_samples.sh")


class StrictNativeSampleCollectionTest(unittest.TestCase):
    def run_collector(self, exit_status, *, finalized=True, partial_finalizer=False, fail_pull=False):
        temporary = tempfile.TemporaryDirectory(prefix="strict-sample-collection-")
        self.addCleanup(temporary.cleanup)
        root = Path(temporary.name)
        output = root / "output"
        output.mkdir()
        raw = root / "synthetic-device-fixture"
        raw.mkdir()
        (raw / "manifest.json").write_text(json.dumps({"rawFixture": True}))
        (raw / "anchors.json").write_text(json.dumps({"actual": [], "original": None}))
        (raw / "comparison.md").write_text("synthetic raw report\n")
        source = "synthetic-source-not-a-ci-artifact"
        host = output / source
        finalized_fixture = root / "synthetic-host-finalizer-result"
        finalized_fixture.mkdir()
        expected = {}
        if finalized:
            (finalized_fixture / "originals").mkdir()
            expected = {
                "manifest.json": json.dumps({"fileSha256": {"anchors.json": "host-hash"},
                    "original": {"path": "original.png"}, "originalAnnotationsSha256": "annotation-hash"}),
                "anchors.json": json.dumps({"original": {"author": "Astra"}, "differences": ["host delta"]}),
                "comparison.md": "host finalized comparison\n",
                "contact-sheet.md": "host contact sheet\n",
                "originals/annotation.json": "host original annotations\n",
            }
            if partial_finalizer:
                # Model failure after per-fixture manifest/anchors augmentation but
                # before comparison/contact-sheet finalization, not only full success.
                expected["comparison.md"] = "synthetic raw report\n"
                expected.pop("contact-sheet.md")
            for name, content in expected.items():
                (finalized_fixture / name).write_text(content)

        text = SCRIPT.read_text()
        collector = re.search(r"(?ms)^collect\(\) \{\n.*?^\}\n", text)
        self.assertIsNotNone(collector, "Actual EXIT collector was not found")
        trap = re.search(r"(?m)^trap .+ EXIT$", text)
        self.assertIsNotNone(trap, "Actual EXIT trap was not found")
        capture_pull = re.findall(r"(?m)^adb pull .+$", text)
        self.assertEqual(1, len(capture_pull), "Expected the actual single pre-finalizer capture pull")
        self.assertLess(text.index(capture_pull[0]), text.index("<<'FINALIZE_PY'"))
        # This test never executes adb, including a fake executable with that name.
        # Only the extracted test copy's command token becomes our local function.
        collector_code = re.sub(r"\badb\b", "mock_device", collector.group())
        capture_pull_code = re.sub(r"\badb\b", "mock_device", capture_pull[0])
        self.assertNotRegex(collector_code, r"\badb\b")
        stub = r'''
mock_device() {
  printf '%s\n' "$*" >> "$calls"
  if [[ "$1" == pull ]]; then
    if [[ "$fail_pull" == true ]]; then return 91; fi
    mkdir -p "$3/$source_sha"
    cp -R "$raw_dir/." "$3/$source_sha/"
  elif [[ "$1" == logcat ]]; then
    printf 'synthetic runtime diagnostics\n'
  elif [[ "$1" == shell && "$2" == dumpsys ]]; then
    printf 'synthetic activity diagnostics\n'
  fi
}
'''
        command = "\n".join([
            "set -Eeuo pipefail", 'output_dir="$1"', 'source_sha="$2"',
            'raw_dir="$3"', 'calls="$4"', 'fail_diagnostic_pull="$5"',
            'finalized_fixture="$7"', "fail_pull=false",
            "package=synthetic.package", "original_font_scale=synthetic-original-scale",
            stub, collector_code, trap.group(),
            capture_pull_code if finalized else ":", # Actual main pull, then a synthetic host finalizer.
            'cp -R "$finalized_fixture/." "$output_dir/$source_sha/"' if finalized else ":",
            'fail_pull="$fail_diagnostic_pull"', 'exit "$6"',
        ])
        calls = root / "mock-calls.txt"
        result = subprocess.run(["bash", "-c", command, "offline-collector-regression",
            str(output), source, str(raw), str(calls), str(fail_pull).lower(), str(exit_status), str(finalized_fixture)],
            capture_output=True, text=True, timeout=10)
        self.assertEqual(exit_status, result.returncode, result.stderr)
        lines = calls.read_text().splitlines()
        self.assertIn("shell settings put system font_scale synthetic-original-scale", lines)
        self.assertEqual("synthetic runtime diagnostics\n", (output / "runtime-errors.txt").read_text())
        self.assertEqual("synthetic activity diagnostics\n", (output / "activities.txt").read_text())
        return output, host, raw, expected, lines

    def assert_host_unchanged(self, host, expected):
        for name, content in expected.items():
            self.assertEqual(content, (host / name).read_text(), f"Host artifact overwritten: {name}")

    def test_success_does_not_pull_over_finalized_host_artifacts(self):
        _, host, _, expected, calls = self.run_collector(0)
        self.assert_host_unchanged(host, expected)
        self.assertEqual(1, sum(line.startswith("pull ") for line in calls))

    def test_finalizer_failure_retains_raw_in_separate_tree(self):
        output, host, raw, expected, calls = self.run_collector(23, partial_finalizer=True)
        self.assert_host_unchanged(host, expected)
        diagnostic = output / "raw-device" / host.name
        self.assertEqual((raw / "manifest.json").read_bytes(), (diagnostic / "manifest.json").read_bytes())
        self.assertEqual((raw / "anchors.json").read_bytes(), (diagnostic / "anchors.json").read_bytes())
        self.assertEqual(2, sum(line.startswith("pull ") for line in calls))

    def test_early_capture_failure_keeps_partial_raw_diagnostics(self):
        output, host, raw, _, _ = self.run_collector(17, finalized=False)
        self.assertFalse(host.exists())
        diagnostic = output / "raw-device" / host.name
        self.assertEqual((raw / "manifest.json").read_bytes(), (diagnostic / "manifest.json").read_bytes())

    def test_failed_diagnostic_pull_does_not_mask_original_failure_or_cleanup(self):
        _, host, _, expected, calls = self.run_collector(29, fail_pull=True)
        self.assert_host_unchanged(host, expected)
        self.assertEqual(2, sum(line.startswith("pull ") for line in calls))


if __name__ == "__main__":
    unittest.main()
