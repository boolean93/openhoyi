"""Source-wiring regressions only; does not prove Android layout or visibility."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / 'mobile/src/main/kotlin/io/openhoyi/mobile'
TEST = ROOT / 'mobile/src/androidTest/kotlin/io/openhoyi/mobile'

class HistoryCExceptionDisclosureContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.overlay = (MAIN / 'ExtractionOverlayView.kt').read_text()
        cls.content = (MAIN / 'HistoryDetailContent.kt').read_text()
        cls.native = (TEST / 'ExtractionHistoryVisualMigrationChecks.kt').read_text()

    def test_fixed_axis_warning_has_height_and_native_layout(self):
        self.assertNotIn('fixedAxisNotice.layout(0,0,0,0)', self.overlay)
        self.assertIn('fixedAxisNotice.layout(0,header.measuredHeight,width,header.measuredHeight+noticeHeight)', self.overlay)
        self.assertIn('(overlay as ExtractionOverlayView).fixedAxisNoticeHeight(width)', self.content)

    def test_abnormal_explanation_stays_in_header_and_disables_overlap(self):
        self.assertNotIn('headerContent.removeView(explanation)', self.overlay)
        self.assertIn('explanation.visibility != VISIBLE && fixedAxisNotice.visibility != VISIBLE', self.overlay)
        self.assertIn('if (standalonePlotMinimumDp != null) warnings.filter { it != axisWarning }', self.overlay)
        self.assertIn('if (inline.isEmpty()) GONE else VISIBLE', self.overlay)

    def test_only_ended_nonempty_status_is_silent(self):
        self.assertIn('val discloseSamples = it.isEmpty() || entry.status != ShotHistory.Status.ENDED', self.content)
        self.assertIn('else if (discloseSamples) getString(R.string.history_samples_partial, it.size.toString()) else ""', self.content)
        self.assertIn('if (discloseSamples) android.view.View.VISIBLE else android.view.View.GONE', self.content)
        self.assertIn('expected.isNotEmpty() && source.observation.status=="ENDED"', self.native)

    def test_missing_journal_has_explicit_inline_reason(self):
        standalone = self.content.split('/** M5-only composition;', 1)[1].split('/** Embedded collection preview:', 1)[0]
        self.assertIn('if (permanent == null) style.add(summary, style.text(', standalone)
        self.assertIn('if (app.journalResult.isFailure) R.string.journal_unavailable else R.string.journal_migrate_hint', standalone)
        self.assertIn('"product/history/journal-status"', standalone)

    def test_real_runner_asserts_each_exception_and_full_visibility(self):
        route = self.native.split('fun runHistoryC(arguments: Bundle)', 1)[1]
        for marker in ('fixed-overflow-visible', 'anomaly-visible', 'partial-visible', 'journal-failure-visible'):
            self.assertIn(marker, route)
        for marker in ('"sample-gap"),"gap"', '"water-reset"),"reset"', '"journal-failure"'):
            self.assertIn(marker, route)
        self.assertIn('historyVisibility(notice).getBoolean("complete")', route)
        self.assertIn('LanguageUiLayoutChecks.textFits(notice', route)
        self.assertIn('finally { main {write(overlay,"fixedAxes",oldFixed);chart.points=expected}', route)

if __name__ == '__main__':
    unittest.main()
