import os
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import Mock, patch

import check_ai_card_construction as check


class CardConstructionEntryTest(unittest.TestCase):
    def test_non_ci_cannot_reach_adb(self):
        with patch.dict(os.environ, {}, clear=True), patch.object(check.subprocess, 'run') as run:
            for message_copy in (False, True):
                with self.assertRaisesRegex(ValueError, 'GitHub runner'):
                    check.main(message_copy)
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

    def test_instrumentation_retains_both_streams_when_adb_fails_or_times_out(self):
        adb = ['adb', '-s', 'emulator-5554', 'shell', 'am', 'instrument']
        for failure in (check.subprocess.CalledProcessError(255, adb), check.subprocess.TimeoutExpired(adb, 180)):
            with self.subTest(failure=type(failure).__name__), TemporaryDirectory() as temp:
                output = Path(temp)

                def run(command, *, check, stdout, stderr, timeout):
                    self.assertEqual(command, adb)
                    self.assertTrue(check)
                    self.assertEqual(timeout, 180)
                    stdout.write(b'INSTRUMENTATION_STATUS: partial\n')
                    stderr.write(b'adb: transport closed\n')
                    raise failure

                with patch.object(check.subprocess, 'run', side_effect=run):
                    with self.assertRaises(type(failure)) as caught:
                        check.run_instrumentation('emulator-5554', adb[3:], output)
                self.assertIs(caught.exception, failure)
                self.assertEqual((output / 'instrumentation.txt').read_text(), 'INSTRUMENTATION_STATUS: partial\n')
                self.assertEqual((output / 'instrumentation-stderr.txt').read_text(), 'adb: transport closed\n')

    def test_copy_logcat_preserves_last_stage_and_stops_after_failure(self):
        with TemporaryDirectory() as temp:
            output = Path(temp)
            process = Mock()
            process.poll.return_value = None

            def popen(command, *, stdout, stderr):
                self.assertEqual(command[:4], ['adb', '-s', 'emulator-5554', 'logcat'])
                self.assertIn('OpenHoyiAiCopy:I', command)
                self.assertIn('AndroidRuntime:E', command)
                self.assertEqual(stderr, check.subprocess.STDOUT)
                stdout.write(b'stage=link-copy-action\n')
                return process

            with patch.object(check.subprocess, 'Popen', side_effect=popen):
                with self.assertRaisesRegex(RuntimeError, 'emulator disconnected'):
                    with check.capture_copy_logcat('emulator-5554', output):
                        process.terminate.assert_not_called()
                        raise RuntimeError('emulator disconnected')
            process.terminate.assert_called_once()
            process.wait.assert_called_once_with(timeout=5)
            self.assertEqual((output / 'gesture-logcat.txt').read_text(), 'stage=link-copy-action\n')

    def test_workflow_is_opt_in_and_preserves_default_runner(self):
        source = (check.Path(__file__).parent.parent / '.github/workflows/mock-lifecycle.yml').read_text()
        self.assertIn("github.event_name == 'workflow_dispatch' && inputs.ai_card_checks == true", source)
        self.assertIn('else bash scripts/check_mock_or_update_acceptance.sh; fi', source)
        self.assertIn('test "$UPDATE_CHECKS" = false', source)
        self.assertIn("github.event_name == 'workflow_dispatch' && inputs.ai_copy_checks == true", source)
        self.assertIn('test "$AI_CARD_CHECKS" != "$AI_COPY_CHECKS"', source)
        self.assertIn("|| env.AI_COPY_CHECKS == 'true') && env.CI_CARD_AVD || 'test'", source)


if __name__ == '__main__':
    unittest.main()
