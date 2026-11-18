package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class AiHoyiCurveGuidanceTest {
    @Test fun guidanceCoversMissingDataAndDoesNotTreatGraphAsMeasuredTimeline() {
        val text = AiHoyiCurveGuidance.SCREENSHOT
        for (required in listOf("schema=3", "Checklist:", "unseen never means false/0", "BEFORE proposing",
            "not measured timelines", "do not tune or optimize", "independent water ceiling",
            "first-stage time limit", "final-stage water is not an independent hard stop", "preserve the complete source", "failed local lookup")) {
            assertTrue(required, text.contains(required))
        }
    }
    @Test fun flowLogicAndFirstStageUnitsRemainIndependentInNativeRecipe() {
        // Representative HS first-pressure and LS first-flow configurations, not OCR evidence.
        for ((fast, firstFlow) in listOf(true to false, false to true)) {
            val document = CustomCurveDocument(CustomCurveDocument.newId(), "Flow", 92, 3600,
                CustomCurveDocument.ControlMode.FLOW,
                listOf(CustomCurveDocument.Stage(if (firstFlow) 25 else 90, 100), CustomCurveDocument.Stage(30, 200)),
                totalWaterLimitMl = 35,
                configuration = CustomCurveDocument.Configuration(pressureLogic = fast, variableFlowLogic = true,
                    firstSegmentFlowMode = firstFlow, preinfusionSeconds = 5, firstDurationSeconds = 10,
                    compensationHundredthsGram = -50, inactiveStages = List(2) { CustomCurveDocument.Stage(0, 0) }))
            val decoded = CustomCurveDocument.decode(document.encode())
            assertEquals(document, decoded)
            assertEquals(firstFlow, decoded.isFlowStage(0)); assertTrue(decoded.isFlowStage(1))
            val presentation = AiCurveWritePresentation.recipe(decoded, true)
            assertTrue(presentation.contains(if (fast) "Hs" else "Ls"))
            assertTrue(presentation.contains(if (firstFlow) "2.5 ml/s; 10 ml" else "9 bar; 10 ml"))
            assertTrue(presentation.contains("3 ml/s; 20 ml")); assertTrue(presentation.contains("-0.5 g"))
        }
        assertTrue(AiHoyiCurveGuidance.SCREENSHOT.contains("慢速/Ls=pressureLogic false, 快速/Hs=true"))
        assertTrue(AiHoyiCurveGuidance.SCREENSHOT.contains("firstSegmentFlowMode is false for first-stage bar, true for first-stage mL/s"))
    }
}
