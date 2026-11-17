import os
import unittest
from unittest.mock import Mock, patch

import check_ai_card_construction as check


class CardConstructionEntryTest(unittest.TestCase):
    def test_non_ci_cannot_reach_adb(self):
        with patch.dict(os.environ, {}, clear=True), patch.object(check.subprocess, 'run') as run:
            with self.assertRaisesRegex(ValueError, 'GitHub runner'):
                check.main()
            run.assert_not_called()

    def test_non_dedicated_avd_cannot_reach_adb(self):
        with patch.dict(os.environ, {'GITHUB_ACTIONS': 'true', 'CI_CARD_AVD': 'physical'}, clear=True), patch.object(check.subprocess, 'run') as run:
            with self.assertRaisesRegex(ValueError, 'AVD identity'):
                check.main()
            run.assert_not_called()

    def test_fresh_emulator_accepts_absent_target_and_exact_names_only(self):
        adb = Mock(return_value='package:android\npackage:' + check.APP + '.other')
        check.require_fresh_emulator(adb)
        adb.assert_called_once_with('shell', 'pm', 'list', 'packages')

    def test_existing_app_or_test_is_rejected(self):
        for package in (check.APP, check.TEST):
            with self.assertRaisesRegex(ValueError, 'Fresh emulator'):
                check.require_fresh_emulator(Mock(return_value='package:' + package))

    def test_package_query_failure_is_not_fresh(self):
        error = check.subprocess.CalledProcessError(1, 'adb')
        with self.assertRaises(check.subprocess.CalledProcessError):
            check.require_fresh_emulator(Mock(side_effect=error))

    def test_workflow_is_opt_in_and_preserves_default_runner(self):
        source = (check.Path(__file__).parent.parent / '.github/workflows/mock-lifecycle.yml').read_text()
        self.assertIn("github.event_name == 'workflow_dispatch' && inputs.ai_card_checks == true", source)
        self.assertIn('else bash scripts/check_mock_or_update_acceptance.sh; fi', source)
        self.assertIn('test "$UPDATE_CHECKS" = false', source)
        self.assertIn("env.AI_CARD_CHECKS == 'true' && env.CI_CARD_AVD || 'test'", source)


if __name__ == '__main__':
    unittest.main()
