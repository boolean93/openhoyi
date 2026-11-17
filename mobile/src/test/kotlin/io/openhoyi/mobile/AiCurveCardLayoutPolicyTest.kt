package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

/** Pure pixel decisions only. These assertions are not Android View measurement evidence. */
class AiCurveCardLayoutPolicyTest {
    @Test fun cardHasTheAssistantWidthCapInsteadOfTakingTheWholeTranscript() {
        assertEquals(292, AiCurveCardLayoutPolicy.cardWidth(375, 520))
        assertEquals(249, AiCurveCardLayoutPolicy.cardWidth(320, 520))
        assertEquals(520, AiCurveCardLayoutPolicy.cardWidth(1200, 520))
        assertEquals(0, AiCurveCardLayoutPolicy.cardWidth(0, 520))
    }
    @Test fun finalViewportWidthIsChosenBeforeAnyHeightDependingOnWrappedParameters() {
        assertEquals(520, AiCurveCardLayoutPolicy.diagramWidth(520, 180))
        assertEquals(300, AiCurveCardLayoutPolicy.diagramWidth(220, 300))
        assertEquals(300, AiCurveCardLayoutPolicy.diagramWidth(0, 300))
        // A changed window/font requirement selects its own final width, not a previous pass width.
        assertEquals(260, AiCurveCardLayoutPolicy.diagramWidth(260, 180))
        assertEquals(360, AiCurveCardLayoutPolicy.diagramWidth(260, 360))
    }
    @Test fun actionsStayNaturalWidthAndStackOnlyWhenMeasuredLabelsDoNotFit() {
        assertFalse(AiCurveCardLayoutPolicy.stackActions(232, listOf(96, 128), 8))
        assertTrue(AiCurveCardLayoutPolicy.stackActions(231, listOf(96, 128), 8))
        assertTrue(AiCurveCardLayoutPolicy.stackActions(232, listOf(130, 166), 8))
        assertFalse(AiCurveCardLayoutPolicy.stackActions(232, listOf(128), 8))
    }
    @Test fun twoLineStageUsesWiderMeasuredLineNotTheirSum() {
        assertEquals(63, AiCurveCardLayoutPolicy.stageLabelWidth(46.1f, 39.4f, 16))
        assertEquals(90, AiCurveCardLayoutPolicy.stageLabelWidth(42f, 73.2f, 16))
        assertEquals(110, AiCurveCardLayoutPolicy.stageLabelWidth(93.5f, 80f, 16))
        assertEquals(0, AiCurveCardLayoutPolicy.stageLabelWidth(0f, 0f, 0))
        // Three complete columns fit234px rather than forcing wider combined-line columns.
        assertEquals(197, 8 + 3 * AiCurveCardLayoutPolicy.stageLabelWidth(46.1f, 39.4f, 16))
    }
    @Test fun everyStageMarkStaysShortAndInsideItsCentredSlot() {
        for (slot in listOf(20f, 50f, 130f, 500f)) {
            val length = AiCurveCardLayoutPolicy.stageMarkerLength(slot, 8f, 40f)
            assertTrue(length <= 40f)
            assertTrue(slot / 2 - length / 2 >= minOf(8f, slot / 2))
            assertTrue(slot / 2 + length / 2 <= maxOf(slot - 8f, slot / 2))
        }
        assertEquals(40f, AiCurveCardLayoutPolicy.stageMarkerLength(500f, 8f, 40f), 0f)
        assertEquals(4f, AiCurveCardLayoutPolicy.stageMarkerLength(20f, 8f, 40f), 0f)
        assertEquals(0f, AiCurveCardLayoutPolicy.stageMarkerLength(8f, 8f, 40f), 0f)
        // Caller converts the40dp visual cap at its real density; the measured label font
        // may grow independently and still cause truthful horizontal scroll when needed.
        assertEquals(90f, AiCurveCardLayoutPolicy.stageMarkerLength(500f, 18f, 90f), 0f)
    }

}
