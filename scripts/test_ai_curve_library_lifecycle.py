"""Cold-launch source guard; not a substitute for Android lifecycle execution."""
from pathlib import Path
import unittest


class AiCurveLibraryLifecycleTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = (Path(__file__).resolve().parents[1] /
                      'mobile/src/main/kotlin/io/openhoyi/mobile/MainActivity.kt').read_text()
        cls.consume = cls.source.split('private fun consumeAiCurveLibraryRoute() {', 1)[1].split(
            'private fun routeHistorySource(', 1)[0]

    def test_cold_launch_defers_until_resumed_and_view_created(self):
        self.assertLess(self.consume.index('Lifecycle.State.RESUMED'),
                        self.consume.index('AiCurveLibraryRoute.receive'))
        self.assertLess(self.consume.index('fragment.view == null'),
                        self.consume.index('fragment.openAiCurveReference'))
        ready = self.consume.index('fragment.view == null')
        self.assertLess(ready, self.consume.index('pendingAiCurveReference = null', ready))

    def test_pending_request_retried_after_resume_and_superseded_by_new_intent(self):
        resume = self.source.split('override fun onPostResume()', 1)[1].split('override fun onNewIntent', 1)[0]
        self.assertIn('window.decorView.post { consumeAiCurveLibraryRoute() }', resume)
        self.assertLess(resume.index('super.onPostResume()'), resume.index('window.decorView.post'))
        self.assertLess(resume.index('if (pendingShow) showCurrent()'), resume.index('window.decorView.post'))
        # Direct consumption inside this callback observes pre-RESUMED registry state.
        self.assertEqual(1, resume.count('consumeAiCurveLibraryRoute()'))
        self.assertIn('supportFragmentManager.isStateSaved', self.consume)
        newer = self.source.split('override fun onNewIntent', 1)[1].split('private fun routeAiCurveLibrary', 1)[0]
        self.assertLess(newer.index('pendingAiCurveReference = null'),
                        newer.index('routeAiCurveLibrary(intent)'))
        self.assertIn('AiCurveLibraryRoute.receive(encoded, library::find)', self.consume)
