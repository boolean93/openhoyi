"""Source wiring guards; Android gesture delivery still needs UI verification."""
import pathlib
import unittest

SOURCE = pathlib.Path(__file__).resolve().parents[1] / 'mobile/src/main/kotlin/io/openhoyi/mobile/AiChatPresentation.kt'

class MessageCopyTest(unittest.TestCase):
    def setUp(self):
        self.s = SOURCE.read_text()

    def test_completed_and_padding_share_rendered_body(self):
        self.assertIn('bindMessageCopy(this) { this.text.toString() }', self.s)
        self.assertIn('if (text.isNotBlank() && images.isEmpty()) bindMessageCopy(content) { message.text.toString() }', self.s)
        self.assertIn('movementMethod = MessageLinkMovement()', self.s)

    def test_partial_uses_current_content_without_labels(self):
        partial = self.s.split('private val partialAnswer by lazy {', 1)[1].split('private val toolProgress', 1)[0]
        self.assertIn('bindMessageCopy(this) { streamSnapshot.content }', partial)
        self.assertNotIn('setTextIsSelectable', partial)
        self.assertNotIn('partialAnswer.text', partial)

    def test_one_guarded_clipboard_write_no_global_binding(self):
        self.assertEqual(self.s.count('clipboard.setPrimaryClip('), 1)
        self.assertEqual(self.s.count('bindMessageCopy('), 4)  # definition + three sites
        helper = self.s.split('private fun bindMessageCopy', 1)[1].split('private fun bubble', 1)[0]
        self.assertIn('if (text.isBlank()) return@setOnLongClickListener false', helper)
        self.assertNotIn('setOnTouchListener', helper)
        self.assertNotIn('reasoning', helper)
        self.assertIn('copiedByLongPress = true', helper)
        movement = self.s.split('private class MessageLinkMovement', 1)[1].split('private fun bindMessageCopy', 1)[0]
        self.assertIn('ACTION_DOWN -> copiedByLongPress = false', movement)
        self.assertIn('ACTION_CANCEL -> copiedByLongPress = false', movement)
        self.assertIn('ACTION_UP -> if (copiedByLongPress)', movement)
        self.assertIn('return super.onTouchEvent(widget, buffer, event)', movement)

if __name__ == '__main__':
    unittest.main()
