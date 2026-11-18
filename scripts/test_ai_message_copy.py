"""Source wiring guards; Android gesture delivery still needs UI verification."""
import pathlib
import re
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

    def section(self, start, end):
        return self.s.split(start, 1)[1].split(end, 1)[0]

    def test_long_press_opens_menu_without_copying_and_drag_cancels(self):
        self.assertEqual(self.s.count('bindMessageCopy('), 4)  # definition + three sites
        bind = self.section('private fun bindMessageCopy(', 'private fun showMessageCopy(')
        self.assertIn('scaledTouchSlop', bind)
        self.assertRegex(bind, r'fun suppressLinkRelease\(\)\s*\{\s*if \(view is TextView\)\s*'
                               r'\(view\.movementMethod as\? MessageLinkMovement\)\?\.suppressRelease\(view\)')
        move = bind.split('MotionEvent.ACTION_MOVE', 1)[1].split('MotionEvent.ACTION_UP', 1)[0]
        self.assertRegex(move, r'if \(dx \* dx \+ dy \* dy > slop \* slop\)\s*\{\s*'
                               r'(?://[^\n]*\n\s*)?dragged = true\s*view\.cancelLongPress\(\)\s*'
                               r'suppressLinkRelease\(\)\s*\}')
        long_press = bind.split('view.setOnLongClickListener', 1)[1]
        self.assertRegex(long_press, r'if \(dragged\s*\|\|\s*body\(\)\.isBlank\(\)\s*\|\|\s*'
                                     r'!showMessageCopy\(view, touchLocation, body\)\)\s*'
                                     r'return@setOnLongClickListener false\s*suppressLinkRelease\(\)\s*true')
        self.assertNotIn('copyMessage(', bind)
        self.assertNotIn('setPrimaryClip', bind)
        self.assertNotIn('reasoning', bind)

    def test_only_current_popup_copy_action_writes_once(self):
        menu = self.section('private fun showMessageCopy(', 'private fun copyMessage(')
        click = menu.split('copy.setOnClickListener', 1)[1].split('trackOptions(popup, anchor)', 1)[0]
        self.assertRegex(click, r'if \(options === popup && popup\.isShowing && anchor\.isAttachedToWindow && anchor\.isShown\)')
        self.assertIn('val text = body()', click)
        self.assertEqual(click.count('copyMessage(text)'), 1)
        self.assertEqual(menu.count('copyMessage('), 1)
        self.assertEqual(self.s.count('copyMessage('), 2)  # definition + guarded click
        self.assertIn('isOutsideTouchable = true', menu)
        self.assertNotIn('setPrimaryClip', menu)
        writer = self.section('private fun copyMessage(', 'private fun bubble(')
        self.assertIn('if (text.isBlank()) return', writer)
        self.assertEqual(writer.count('clipboard.setPrimaryClip('), 1)
        self.assertEqual(len(re.findall(r'\bsetPrimaryClip\s*\(', self.s)), 1)

    def test_popup_dismissal_and_session_render_invalidate_copy_owner(self):
        dismissal = self.section('fun dismissOptions()', 'private var waitingDotsFrame')
        self.assertIn('popup?.dismiss()', dismissal)
        self.assertRegex(dismissal, r'popup\.setOnDismissListener\s*\{\s*if \(options === popup\)\s*\{\s*options = null\s*messageCopyAnchor = null')
        self.assertNotIn('copyMessage(', dismissal)
        self.assertNotIn('setPrimaryClip', dismissal)
        render = self.section('fun render(lines:', 'private class ChatActionIcon')
        self.assertIn('ownerEpoch, previewSession', render)
        self.assertRegex(render, r'if \(content != renderedContent\)\s*\{\s*dismissMessageCopy\(\)\s*renderContent\(')

    def test_link_release_after_long_press_or_drag_is_consumed(self):
        movement = self.section('private class MessageLinkMovement', 'private fun bindMessageCopy(')
        self.assertRegex(movement, r'fun suppressRelease\(widget: TextView\)\s*\{\s*suppressRelease = true\b')
        self.assertIn('ACTION_DOWN -> suppressRelease = false', movement)
        self.assertRegex(movement, r'ACTION_CANCEL ->\s*\{\s*suppressRelease = false')
        self.assertRegex(movement, r'ACTION_UP -> if \(suppressRelease\)\s*\{\s*suppressRelease = false\s*return true')
        self.assertIn('return super.onTouchEvent(widget, buffer, event)', movement)

if __name__ == '__main__':
    unittest.main()
