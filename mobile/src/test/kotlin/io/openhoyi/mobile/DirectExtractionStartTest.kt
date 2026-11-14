package io.openhoyi.mobile

import io.openhoyi.session.PreheatGate.StartBlock
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
}
