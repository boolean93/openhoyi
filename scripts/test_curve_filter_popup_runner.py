import os
from pathlib import Path
import subprocess
import unittest

ROOT = Path(__file__).resolve().parent.parent


class CurveFilterPopupRunnerTest(unittest.TestCase):
    def test_emulator_runner_receives_one_complete_shell_command(self):
        workflow = (ROOT / '.github/workflows/curve-visual-ui.yml').read_text()
        self.assertIn('script: bash scripts/check_curve_filter_popup.sh ${{ inputs.filter_only }}', workflow)
        subprocess.run(['bash', '-n', 'scripts/check_curve_filter_popup.sh'], cwd=ROOT, check=True)

    def test_focused_mode_refuses_local_execution_before_adb(self):
        env = dict(os.environ, GITHUB_ACTIONS='false')
        result = subprocess.run(['bash', 'scripts/check_curve_filter_popup.sh', 'true'],
                                cwd=ROOT, env=env, capture_output=True, text=True)
        self.assertEqual(1, result.returncode)
        self.assertIn('CI-only check', result.stderr)
