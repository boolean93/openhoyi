"""Source/host-only regressions. These do not compile or execute Android, AVD or CI."""
import ast
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


class HistoryCEntryContract(unittest.TestCase):
    def test_tap_proof_does_not_replace_settings_restore_snapshot(self):
        code = (ROOT / "scripts/check_history_detail_c_visual.sh").read_text().split("<<'PY'\n", 1)[1].rsplit("\nPY", 1)[0]
        tree = ast.parse(code)
        self.assertEqual(sum(isinstance(n, ast.Name) and isinstance(n.ctx, ast.Store) and n.id == "before" for n in ast.walk(tree)), 1)
        proof = next(n for n in ast.walk(tree) if isinstance(n, ast.Assign) and any(isinstance(t, ast.Name) and t.id == "tap_proof" for t in n.targets))
        restore = next(n for n in ast.walk(tree) if isinstance(n, ast.For) and ast.unparse(n.iter) == "before.items()")
        original = {"font_scale": None, "accelerometer_rotation": "1", "user_rotation": "0"}
        calls = []
        scope = dict(before=dict(original), details=Path("/unused"), event={"preTapProof": "proof.json"},
                     require_proof=lambda _: {"accepted": True, "attempts": [], "protocolVersion": 2},
                     shell=lambda *args: calls.append(args), restore_errors=[])
        exec(compile(ast.Module(body=[proof, restore], type_ignores=[]), "actual_host_statements", "exec"), scope)
        self.assertEqual(scope["before"], original)
        self.assertEqual(calls, [("settings", "delete", "system", "font_scale"),
                                 ("settings", "put", "system", "accelerometer_rotation", "1"),
                                 ("settings", "put", "system", "user_rotation", "0")])

    def test_api34_mode_request_is_wired_and_diagnostic_precedes_gate(self):
        code = (ROOT / "mobile/src/androidTest/kotlin/io/openhoyi/mobile/ExtractionHistoryVisualMigrationChecks.kt").read_text()
        block = code.split('if(label=="main" && arguments.getString("taskResize")=="true")', 1)[1].split("active.closeActivities()", 1)[0]
        self.assertIn("check(Build.VERSION.SDK_INT==34)", block)
        self.assertIn("AOSP API34 PRIVATE", block)
        request = block.index('launchOptions.putInt("android.activity.windowingMode",5)')
        launch = block.index("test.startActivitySync(launch,launchOptions)")
        diagnostic = block.index('.put("actualMultiWindow",floating.isInMultiWindowMode)')
        gate = block.index("check(floating.isInMultiWindowMode)")
        self.assertLess(request, launch)
        self.assertLess(launch, diagnostic)
        self.assertLess(diagnostic, gate)
        for required in ("featureFreeform", "featurePip", "actualBounds", "requestedBounds",
                         "actual.windowManager.currentWindowMetrics.bounds!=original",
                         "actual.windowManager.currentWindowMetrics.bounds==original && actual.isInMultiWindowMode==originalMode"):
            self.assertIn(required, block)
        self.assertNotIn("adoptShellPermissionIdentity", block)
        self.assertNotIn("getDeclaredMethod", block)


if __name__ == "__main__":
    unittest.main()
