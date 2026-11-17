"""Small source guard only; not a substitute for rendered Android layout checks."""
from pathlib import Path
import unittest


class AiSessionLayoutTest(unittest.TestCase):
    def test_no_storage_notice_or_reserved_row(self):
        root = Path(__file__).resolve().parents[1]
        source = (root / 'mobile/src/main/kotlin/io/openhoyi/mobile/AiChatPresentation.kt').read_text()
        self.assertEqual(1, source.count('header.addView(toolbar'))
        self.assertIn('addView(scroll, FrameLayout.LayoutParams(-1, -1))', source)
        self.assertIn('addView(header, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))', source)
        self.assertNotIn('ai_session_storage_notice', source)
        self.assertNotIn('ai/chat/storage-notice', source)
        self.assertNotIn('persistedSession', source)
        for locale in ('values', 'values-en'):
            resources = (root / 'mobile/src/main/res' / locale / 'ai_chat.xml').read_text()
            self.assertNotIn('ai_session_storage_notice', resources)
