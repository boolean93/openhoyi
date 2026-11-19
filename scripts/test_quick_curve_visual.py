"""Focused host tests for the isolated quick-curve capture shell."""
import struct
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from check_quick_curve_visual import confirm_display, display_value, save_failure, validate_capture


def frame(width, height):
    return b"\x89PNG\r\n\x1a\n" + bytes(8) + struct.pack(">II", width, height) + bytes(10000)


class TestQuickCurveVisual(unittest.TestCase):
    def test_effective_size_prefers_override_and_handles_crlf(self):
        self.assertEqual("2560x1600", display_value("Physical size: 2560x1800\r\nOverride size: 2560x1600", "size"))
        self.assertEqual("360", display_value("Physical density: 360", "density"))
        with self.assertRaises(ValueError):
            display_value("Unable to find window manager", "size")

    def test_tablet_preflight_waits_for_real_capture_orientation(self):
        images = iter((frame(1600, 2560), frame(2560, 1600)))

        def adb(*args, **kwargs):
            if args[-1] == "size":
                return "Override size: 2560x1600"
            if args[-1] == "density":
                return "Override density: 360"
            return next(images)

        with patch("check_quick_curve_visual.time.sleep") as sleep:
            self.assertEqual(dict(width=2560, height=1600, dpi=360), confirm_display(adb, 2560, 1600, 360))
            sleep.assert_called_once_with(1)

    def test_diagnostics_are_bounded_and_profile_scoped(self):
        commands = []

        def adb(*args, **kwargs):
            commands.append(args)
            return frame(2560, 1600) if kwargs.get("binary") else "a" * 100000

        with tempfile.TemporaryDirectory() as folder:
            out = Path(folder)
            save_failure(adb, out, "tablet-light")
            self.assertIn(("-b", "crash"), tuple(zip(commands[0], commands[0][1:])))
            self.assertIn("AndroidRuntime:E", commands[1])
            self.assertLess(len((out / "tablet-light-android-runtime.txt").read_text()), 33000)
            self.assertTrue((out / "tablet-light-failed.png").is_file())

    def test_populated_card_assertion_remains_required(self):
        geometry = dict(density=2.25, dark=False, portrait=False, wallpaper=True,
                        items=[dict(name="HOYI Limit Extraction")] * 6)
        validate_capture(frame(2560, 1600), geometry, 2560, 1600, 360, "light")
        with self.assertRaises(ValueError):
            validate_capture(frame(1600, 2560), geometry, 2560, 1600, 360, "light")
        with self.assertRaisesRegex(ValueError, "backdrop"):
            validate_capture(frame(2560, 1600), geometry, 2560, 1600, 360, "light", "solid")
        geometry["wallpaper"] = False
        validate_capture(frame(2560, 1600), geometry, 2560, 1600, 360, "light", "solid")
        geometry["wallpaper"] = True
        geometry["items"] = geometry["items"][:-1]
        with self.assertRaises(ValueError):
            validate_capture(frame(2560, 1600), geometry, 2560, 1600, 360, "light")


if __name__ == "__main__":
    unittest.main()
