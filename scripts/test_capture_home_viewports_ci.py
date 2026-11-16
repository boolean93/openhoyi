import importlib.util
import os
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("capture_home", Path(__file__).with_name("capture_home_viewports_ci.py"))
capture = importlib.util.module_from_spec(spec)
spec.loader.exec_module(capture)


class EnvironmentGuardTests(unittest.TestCase):
    def test_mac_rejected_before_any_adb(self):
        with patch.object(capture.sys, "platform", "darwin"), patch.object(capture, "adb") as adb:
            with self.assertRaises(RuntimeError):
                capture.require_environment()
            adb.assert_not_called()

    def test_manual_linux_ci_requires_fresh_emulator(self):
        env = {"GITHUB_ACTIONS": "true", "GITHUB_REPOSITORY": "boolean93/openhoyi",
               "GITHUB_EVENT_NAME": "workflow_dispatch", "GITHUB_RUN_ID": "123"}
        for answers in (["0"], ["1", "package:io.openhoyi.mobile.mock"]):
            with self.subTest(answers=answers), patch.dict(os.environ, env, clear=True), \
                    patch.object(capture.sys, "platform", "linux"), \
                    patch.object(capture, "adb", side_effect=answers) as adb:
                with self.assertRaises(RuntimeError):
                    capture.require_environment()
                self.assertTrue(all(call.args[0] == "shell" for call in adb.call_args_list))
                self.assertFalse(any("install" in call.args for call in adb.call_args_list))

    def test_requested_original_feedback_scenarios(self):
        self.assertEqual({row[3] for row in capture.CASES}, {"medium", "large"})
        self.assertEqual(len(capture.CASES), 2)
        for _, width, height, _ in capture.CASES:
            self.assertEqual((width, height), (2048, 1365))
            self.assertAlmostEqual(width / height, 1.5, places=3)

    def test_scroll_geometry_comes_from_actual_insets_and_excludes_rail(self):
        for bottom in (325, 340):
            nodes = [ET.Element("node", {"package": capture.PACKAGE,
                "class": "android.widget.ScrollView", "scrollable": "true", "bounds": value})
                for value in (f"[0,35][130,{bottom}]", f"[130,35][1600,{bottom}]")]
            self.assertEqual([130, 35, 1600, bottom], capture.home_scroll(nodes, 1600))
            with self.assertRaises(RuntimeError):
                capture.home_scroll(nodes[:1], 1600)


if __name__ == "__main__":
    unittest.main()
