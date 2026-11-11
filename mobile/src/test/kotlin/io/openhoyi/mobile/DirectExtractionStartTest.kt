package io.openhoyi.mobile

import io.openhoyi.session.PreheatGate.StartBlock
import io.openhoyi.session.ExtractionState
import org.junit.Assert.*
import org.junit.Test

class DirectExtractionStartTest {
    @Test fun onlyKnownLowStudioTemperatureOffersPreheatNeverAnOverride() {
        assertTrue(DirectExtractionStart.offerPreheat(StartBlock.TEMPERATURE_NOT_READY,9099,92))
        for(value in listOf(null,9100,9200,9301))
            assertFalse(DirectExtractionStart.offerPreheat(StartBlock.TEMPERATURE_NOT_READY,value,92))
        for(reason in listOf(null,StartBlock.SETTINGS_MISSING,StartBlock.MODE_CHANGED,StartBlock.PREPARATION_MISMATCH))
            assertFalse(DirectExtractionStart.offerPreheat(reason,8000,92))
    }
    @Test fun resolvedNoScaleModeClearlyStatesWaterStopNotLiquidWeight() {
        assertTrue(DirectExtractionStart.showStopModePreview(false,true,ExtractionState.IDLE))
        assertFalse(DirectExtractionStart.showStopModePreview(true,true,ExtractionState.RUNNING))
        assertFalse(DirectExtractionStart.showStopModePreview(false,false,ExtractionState.IDLE))
        assertFalse(DirectExtractionStart.showStopModePreview(false,true,ExtractionState.OUTCOME_UNKNOWN))
        val weighted=CurveCatalog.profiles.first {it.targetHundredthsGram>0}
        val water=weighted.copy(targetHundredthsGram=0,scaleMode=false)
        assertEquals("使用电子秤控制停止",DirectExtractionStart.stopMode(weighted).render(DefaultStringResources::resolve))
        assertEquals("本次不按秤重停机，按曲线水量结束",DirectExtractionStart.stopMode(water).render(DefaultStringResources::resolve))
    }
}
