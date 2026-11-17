"""Focused source/geometry regression for the shared composer, not rendered Android QA."""
from pathlib import Path
import unittest


class AiComposerGeometryTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = (Path(__file__).resolve().parents[1] /
                      'mobile/src/main/kotlin/io/openhoyi/mobile/AiChatPresentation.kt').read_text()
        cls.plus = cls.source.split('attachmentButton = style.button(Button(context))', 1)[1].split(
            'inputFrame.addView(input,', 1)[0]
        cls.send = cls.source.split('send = object : Button(context)', 1)[1].split(
            'root.addView(View(context)', 1)[0]

    def test_plus_idle_paint_cannot_erase_shared_outline(self):
        self.assertIn('android.graphics.drawable.InsetDrawable(', self.plus)
        self.assertIn('ColorStateList.valueOf(style.selected), null, style.shape(Color.WHITE, radius = 20)', self.plus)
        self.assertIn('style.dp(4))', self.plus)
        self.assertNotIn('style.shape(style.surface', self.plus)
        self.assertIn('LinearLayout.LayoutParams(style.dp(48), style.dp(48))', self.plus)

    def test_send_visual_circle_is_inset_not_touch_target(self):
        self.assertIn('android.graphics.drawable.InsetDrawable(', self.send)
        self.assertIn('style.shape(style.primary, radius = 20), null), style.dp(4))', self.send)
        self.assertIn('LinearLayout.LayoutParams(style.dp(48), style.dp(48))', self.send)
        self.assertIn('minWidth = style.dp(48); minHeight = style.dp(48)', self.send)
        self.assertIn('setOnClickListener { onSendOrStop() }', self.send)

    def test_outline_clearance_without_consuming_input_width_or_height(self):
        frame = self.source.split('val inputFrame = ', 1)[1].split('composer.addView(inputFrame', 1)[0]
        self.assertIn('setPadding(style.dp(2), 0, style.dp(2), 0)', frame)
        self.assertIn('inputFrame.addView(input, LinearLayout.LayoutParams(0, -2, 1f))', self.source)
        # Same dp->px integer conversion as TaskProductVisualStyle; taller multiline
        # composers centre the unchanged 48dp controls and only increase clearance.
        for density in [1., 1.5, 2.25, 3.]:
            target, inset, stroke = int(48 * density), int(4 * density), max(1, int(density))
            for frame_height in [target, target + int(24 * density), target + int(48 * density)]:
                paint_top = (frame_height - target) // 2 + inset
                paint_bottom = paint_top + target - 2 * inset
                self.assertGreater(paint_top, stroke)
                self.assertLess(paint_bottom, frame_height - stroke)
