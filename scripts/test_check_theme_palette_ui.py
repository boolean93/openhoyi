"""Host-side evidence collection checks; no emulator or APK needed."""
import subprocess
from pathlib import Path
import tempfile
import unittest

from check_theme_palette_ui import APP, BACKGROUND_MARKER, BACKGROUNDS, run_fixture
from shared_mock_artifact import digest

PNG = b'\x89PNG\r\n\x1a\nfixture'
DIRECTORY = 'page-background-evidence'
LOG = 'background-instrumentation.txt'


class AppearanceEvidenceTest(unittest.TestCase):
    def run_background(self, adb, output, hashes):
        run_fixture(adb, output, hashes, 'pageBackgroundChecks', DIRECTORY, BACKGROUND_MARKER,
                    BACKGROUNDS, LOG, retain_partial=True)

    def test_failed_fixture_collects_available_allowlisted_evidence_but_still_fails(self):
        reads = []

        def adb(*args, **_):
            if args[0] == 'shell':
                return 'failed during light Home\nINSTRUMENTATION_CODE: 0'
            self.assertEqual(args[:4], ('exec-out', 'run-as', APP, 'cat'))
            reads.append(args[4])
            if len(reads) == 1:
                return PNG
            if len(reads) == 2:
                raise subprocess.TimeoutExpired('cat', 5)
            if len(reads) == 3:
                return b'not a screenshot'
            raise subprocess.CalledProcessError(1, 'cat')

        with tempfile.TemporaryDirectory() as tmp:
            output = Path(tmp)
            hashes = {}
            with self.assertRaisesRegex(ValueError, 'pageBackgroundChecks fixture failed'):
                self.run_background(adb, output, hashes)
            name = BACKGROUNDS[0]
            self.assertEqual(reads, [f'files/{DIRECTORY}/{entry}' for entry in BACKGROUNDS])
            self.assertEqual({p.name for p in output.iterdir()}, {name, LOG})
            self.assertEqual(hashes, {name: digest(output / name)})
            self.assertIn('failed during light Home', (output / LOG).read_text())

    def test_success_marker_still_requires_all_screenshots(self):
        def adb(*args, **_):
            if args[0] == 'shell':
                return BACKGROUND_MARKER + '\nINSTRUMENTATION_CODE: -1'
            return b'bad' if args[-1].endswith(BACKGROUNDS[-1]) else PNG

        with tempfile.TemporaryDirectory() as tmp:
            hashes = {}
            with self.assertRaisesRegex(ValueError, 'Invalid appearance screenshot output'):
                self.run_background(adb, Path(tmp), hashes)
            self.assertEqual(set(hashes), set(BACKGROUNDS[:-1]))


if __name__ == '__main__':
    unittest.main()
