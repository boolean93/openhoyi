"""Regression guard for view wiring: local temperature feedback is not a machine gate."""
import pathlib
import unittest

MOBILE = pathlib.Path(__file__).resolve().parents[1] / 'mobile/src/main/kotlin/io/openhoyi/mobile'


class TemperatureUiIsolationTest(unittest.TestCase):
    def test_home_and_extraction_busy_use_control_projection(self):
        for name in ('HomeActivity', 'HomeFragment', 'ExtractionActivity', 'ExtractionFragment'):
            with self.subTest(view=name):
                source = (MOBILE / (name + '.kt')).read_text()
                self.assertNotIn('val settingBusy = owner?.settingWriteState', source)
                self.assertIn('val settingBusy = owner?.blockingSettingWriteState', source)

    def test_navigation_uses_machine_projection_not_temperature_feedback(self):
        source = (MOBILE / 'TaskShellStatusView.kt').read_text()
        self.assertNotIn('owner?.settingWriteState', source)
        self.assertIn('owner?.machineSettingStatusState', source)
        self.assertIn('owner?.machineControlSafetyMessage', source)
        self.assertIn('CoffeeAlarmPolicy.permitsNewControl', source)

    def test_edit_gate_and_dispatch_recheck_use_control_projection(self):
        self.assertIn('owner?.blockingSettingWriteState', (MOBILE / 'MachineSettingsEditGate.kt').read_text())
        source = (MOBILE / 'MobileService.kt').read_text()
        dispatch = source.split('if (!current.extraction.start(', 1)[1].split('callerPermits()', 1)[0]
        self.assertIn('blockingSettingWriteState', dispatch)


if __name__ == '__main__':
    unittest.main()
