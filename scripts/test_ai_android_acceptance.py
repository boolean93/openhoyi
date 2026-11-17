import unittest
from unittest.mock import patch
from check_ai_android import main, target_contract, assert_no_private_logs, result_pid

class AiAndroidContractTest(unittest.TestCase):
    def test_physical_targets_rejected_before_adb(self):
        for serial in ('198.51.100.10:5555', 'usb-device', '', 'emulator-5554;bad'):
            with self.assertRaises(ValueError): target_contract(serial, 'openhoyi-ai-test', True)
        with self.assertRaises(ValueError): target_contract('emulator-5554', 'my-existing-avd', True)
        with self.assertRaises(ValueError): target_contract('emulator-5554', 'openhoyi-ai-test', False)
        target_contract('emulator-5554', 'openhoyi-ai-test', True)

    def test_main_rejects_physical_serial_before_subprocess(self):
        with patch('sys.argv', ['check_ai_android.py', '--serial', '198.51.100.10:5555',
                                '--avd-name', 'openhoyi-ai-test', '--disposable-emulator',
                                '--evidence', 'build/unused-ai-evidence']), patch('subprocess.run') as run:
            with self.assertRaises(ValueError): main()
            run.assert_not_called()

    def test_logs_and_process_receipt_fail_closed(self):
        for marker in ('PRIVATE_USER_SENTINEL', 'PRIVATE_TOOL_SENTINEL', 'PRIVATE_KEY_SENTINEL',
                       'PRIVATE_ERROR_SENTINEL', 'PRIVATE_THROWABLE_SENTINEL', 'fake-sentinel-123'):
            with self.assertRaises(ValueError): assert_no_private_logs('diagnostic: ' + marker)
        assert_no_private_logs('OpenHoyiLifecycle visible')
        text = 'INSTRUMENTATION_RESULT: appPid=123\nAI_ANDROID_CHECKS_PASSED phase=seed checks=3\nINSTRUMENTATION_CODE: -1'
        self.assertEqual('123', result_pid(text, 'seed'))
        with self.assertRaises(ValueError): result_pid(text, 'verify')
        with self.assertRaises(ValueError): result_pid(text.replace('-1', '0'), 'seed')
        with self.assertRaises(ValueError): result_pid(text.replace('-1', '-10'), 'seed')
        with self.assertRaises(ValueError): result_pid(text.replace('phase=seed ', 'phase=seed-other '), 'seed')

if __name__ == '__main__': unittest.main()
