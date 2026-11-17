"""Narrow source guards; pure geometry tests do not establish rendered Android fit."""
from pathlib import Path
import unittest


class AiStageDiagramLayoutTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = (Path(__file__).resolve().parents[1] /
                      'mobile/src/main/kotlin/io/openhoyi/mobile/AiCurveRecipeDiagramView.kt').read_text()

    def test_visible_two_lines_and_measured_font_match(self):
        self.assertIn('style.text("", TaskProductVisualStyle.auxiliary).paint', self.source)
        self.assertIn('parameterPaint.measureText(targetLabel(stage))', self.source)
        self.assertIn('parameterPaint.measureText(waterLabel(stage))', self.source)
        self.assertIn('parameterLabels[index].text = targetLabel(stage) + "\\n" + waterLabel(stage)', self.source)
        self.assertNotIn('" · "', self.source)
        # Accessibility still reads ordinal, complete target/unit and water, not only one line.
        description = self.source.split('contentDescription = ', 1)[1].split('parameterLabels +=', 1)[0]
        self.assertIn('stage.ordinal', description)
        self.assertIn('number(stage.targetTenths)', description)
        self.assertIn('unit(stage)', description)
        self.assertIn('stage.waterTenthsMl', description)

    def test_short_centred_marks_keep_target_semantics_and_truthful_scroll(self):
        self.assertIn('val center = left + step * (index + .5f)', self.source)
        self.assertIn('stageMarkerLength(step, style.dp(8).toFloat(), style.dp(40).toFloat())', self.source)
        self.assertIn('canvas.drawLine(center - half, y, center + half, y, paint)', self.source)
        self.assertIn('val fraction = model.targetFraction(stage)', self.source)
        self.assertIn('AiCurveCardLayoutPolicy.diagramWidth(viewport, diagram.requiredWidth)', self.source)
        self.assertIn('LayoutParams(0, -2, 1f)', self.source)
