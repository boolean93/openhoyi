"""Focused host tests for the isolated quick-curve capture shell."""
import struct
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from check_quick_curve_visual import confirm_display, display_value, save_failure, validate_capture, validate_settings_movie


def frame(width, height):
    return b"\x89PNG\r\n\x1a\n" + bytes(8) + struct.pack(">II", width, height) + bytes(10000)


class TestQuickCurveVisual(unittest.TestCase):
    def test_settings_movie_decodes_exact_display_geometry(self):
        for width, height in ((720, 1560), (1280, 800)):
            stream = dict(codec_name="h264", width=width, height=height, duration="22.35", nb_read_frames="420")
            self.assertEqual(420, validate_settings_movie({"streams": [stream]}, width, height)["decodedFrames"])
            for invalid in (dict(width=height, height=width), dict(duration="nan"), dict(duration="4"),
                            dict(nb_read_frames="1"), dict(codec_name="vp9")):
                with self.assertRaises(ValueError):
                    validate_settings_movie({"streams": [dict(stream, **invalid)]}, width, height)
        with self.assertRaises(ValueError):
            validate_settings_movie({"streams": []}, 720, 1560)

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

    def test_five_card_last_must_span_both_columns_for_both_states(self):
        last = dict(label="上一杯", rect=[4, 100, 296, 160])
        geometry = dict(density=2., dark=True, portrait=True, wallpaper=True, noLever=True, emptyRecent=True,
                        items=[{}, {}, dict(name="HOYI Limit Extraction", rect=[4, 40, 143, 99]),
                               dict(rect=[157, 40, 296, 99]), last])
        image = frame(720, 1440)
        validate_capture(image, geometry, 720, 1440, 320, "dark", empty_recent=True, no_lever=True)
        geometry["emptyRecent"] = False
        last["label"] = "复用上一杯"
        validate_capture(image, geometry, 720, 1440, 320, "dark", no_lever=True)
        last["rect"][0] = 157
        with self.assertRaisesRegex(ValueError, "span both"):
            validate_capture(image, geometry, 720, 1440, 320, "dark", no_lever=True)


if __name__ == "__main__":
    unittest.main()
