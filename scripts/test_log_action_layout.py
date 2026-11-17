"""Source contract for consistent maintenance controls; not pixel/device verification."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'mobile/src/main/kotlin/io/openhoyi/mobile'


class LogActionLayoutTest(unittest.TestCase):
    def test_actions_share_full_width_wrapping_style(self):
        text = (SRC / 'SettingsVisualStyle.kt').read_text()
        helper = text.split('fun maintenanceAction', 1)[1].split('fun dialog', 1)[0]
        self.assertIn('palette.button(view, outline = true)', helper)
        self.assertIn('LinearLayout.LayoutParams(-1, -2)', helper)
        self.assertIn('Gravity.CENTER', helper)
        self.assertIn('topMargin = palette.dp(8)', helper)
        self.assertNotIn('setSingleLine', helper)
        self.assertNotIn('maxLines', helper)

    def test_export_hosts_and_shutdown_use_same_style(self):
        for file in ('AppSettingsActivity.kt', 'AppSettingsFragment.kt', 'SettingsDeviceControls.kt'):
            self.assertIn('visual.maintenanceAction(this)', (SRC / file).read_text())

    def test_clear_action_separate_from_capacity_keeps_operation(self):
        text = (SRC / 'DiagnosticStorageCard.kt').read_text()
        self.assertIn('visual.maintenanceAction(Button(activity))', text)
        self.assertIn('style.add(parent, size, 16)', text)
        self.assertIn('style.add(parent, action, 8)', text)
        self.assertNotIn('visual.pair(size, action', text)
        self.assertIn('setOnClickListener { controller.clear() }', text)
        self.assertIn('action.isEnabled = !state.busy', text)
        self.assertIn('R.string.diagnostic_storage_scope', text)


if __name__ == '__main__':
    unittest.main()
