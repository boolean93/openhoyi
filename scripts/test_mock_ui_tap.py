import unittest
from unittest.mock import patch
import subprocess
from mock_ui_tap import find_target, read_hierarchy


class MockUiTapTest(unittest.TestCase):
    def test_zero_exit_without_dump_is_not_success_or_stale_data(self):
        error = subprocess.CalledProcessError(1, ['adb'], stderr='No such file')
        with patch('mock_ui_tap.adb', side_effect=['', 'ERROR: idle timeout', error]) as calls:
            with self.assertRaisesRegex(ValueError, 'idle timeout'):
                read_hierarchy()
            self.assertEqual(('shell', 'rm', '-f', '/data/local/tmp/openhoyi-mock-ui.xml'), calls.call_args_list[0].args)
    def test_list_label_taps_its_row_not_the_list_center(self):
        xml = '<hierarchy><node class="android.widget.ListView" clickable="true" enabled="true" bounds="[0,0][100,500]"><node bounds="[0,20][100,80]"><node text="Shot" /></node></node></hierarchy>'
        self.assertEqual((50, 50), find_target(xml, 'Shot'))
    def test_nonclickable_label_uses_enabled_parent(self):
        xml = '<hierarchy><node clickable="true" enabled="true" bounds="[10,20][110,80]"><node text="应用设置" clickable="false" /></node></hierarchy>'
        self.assertEqual((60, 50), find_target(xml, '应用设置'))
        self.assertIsNone(find_target(xml, '设置'))
        self.assertEqual((60, 50), find_target(xml, '设置', True))

    def test_disabled_or_empty_control_is_not_tapped(self):
        for enabled, bounds in [('false', '[0,0][50,50]'), ('true', '[0,0][0,0]')]:
            xml = f'<hierarchy><node text="Start" clickable="true" enabled="{enabled}" bounds="{bounds}" /></hierarchy>'
            self.assertIsNone(find_target(xml, 'Start'))

    def test_duplicate_targets_fail_instead_of_guessing(self):
        xml = '<hierarchy><node text="Start" clickable="true" enabled="true" bounds="[0,0][50,50]"/><node text="Start" clickable="true" enabled="true" bounds="[0,60][50,100]"/></hierarchy>'
        with self.assertRaises(ValueError):
            find_target(xml, 'Start')
