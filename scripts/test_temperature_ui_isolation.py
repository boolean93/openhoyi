"""Regression guard for view wiring: local temperature feedback is not a machine gate."""
import pathlib
import re
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

    def test_detached_recovery_preference_lists_include_temperature_namespace(self):
        # Scan both compact check(name in names) and fixtures[name] wrappers.
        # A missing namespace aborts instrumentation before its safety assertions.
        root = MOBILE.parents[4] / 'androidTest/kotlin/io/openhoyi/mobile'
        checked = []
        for path in root.glob('*.kt'):
            source = path.read_text()
            if 'override fun getSharedPreferences' not in source:
                continue
            for match in re.finditer(r'val\s+names\s*=\s*listOf\(([^)]*)\)', source):
                names = re.findall(r'"([^"]+)"', match.group(1))
                if 'machine_write_safety' not in names:
                    continue
                with self.subTest(fixture=path.name):
                    self.assertIn('temperature_confirmation', names)
                checked.append(path.name)
        self.assertIn('ServiceStopFeedbackChecks.kt', checked)
        self.assertIn('ServiceRecoveryPersistenceChecks.kt', checked)
        self.assertGreaterEqual(len(checked), 17)


if __name__ == '__main__':
    unittest.main()
