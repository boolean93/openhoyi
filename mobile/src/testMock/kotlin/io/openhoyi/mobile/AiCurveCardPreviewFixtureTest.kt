package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class AiCurveCardPreviewFixtureTest {
    @Test fun fixedCardsRetainActualStageTargetsAndCompleteBeforeAfter() {
        val cards = AiCurveCardPreviewFixture.cards()
        assertEquals(listOf(AiCurveCard.Kind.RECOMMENDATION, AiCurveCard.Kind.REPLACE), cards.map { it.kind })
        assertEquals(listOf(25, 90, 60), cards[0].document!!.stages.map { it.target })
        val edit = cards[1]
        assertEquals(listOf(180, 400, 140), edit.before!!.stages.map { it.waterTenthsMl })
        assertEquals(listOf(180, 380, 160), edit.document!!.stages.map { it.waterTenthsMl })
        assertEquals(4, AiCurveRecipeChanges.from(edit.before!!, edit.document!!, false).size)
        assertEquals(4, edit.document!!.allStages.size)
        cards.forEach { card ->
            assertEquals(card, AiCurveCard.decode(card.encode()))
            assertFalse(card.encode().toString().contains("offline-fixture-not-a-permit"))
        }
        assertEquals(listOf(cards[0]), AiCurveCardPreviewFixture.lines(0).single().curveCards)
        assertEquals(listOf(cards[1]), AiCurveCardPreviewFixture.lines(1).single().curveCards)
    }
}
