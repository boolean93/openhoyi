package io.openhoyi.mobile

import io.openhoyi.protocol.ByteFrame
import io.openhoyi.protocol.ExtractionTelemetry
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class BrewFeedbackTrackerTest {
    private fun frame(seconds: Int, water: Int, slot: Int = 1, pressure: Int = 70,
        flow: Int = 20, flags: Int = 64) = ExtractionTelemetry(slot, seconds, pressure,
        water, 9300, flow, flags, 0, ByteFrame(byteArrayOf()))
    private fun start(tracker: BrewFeedbackTracker, id: String = "cup", slot: Int = 1, wait: Int = 0) =
        tracker.begin(id, slot, wait)
    private fun observe(tracker: BrewFeedbackTracker, seconds: Int, water: Int, at: Long,
        id: String = "cup", slot: Int = 1, pressure: Int = 70, flow: Int = 20, flags: Int = 64) =
        tracker.observe(id, frame(seconds, water, slot, pressure, flow, flags), at)

    @Test fun usesMachineSecondsAndLatestExtractingSnapshotAtOrBeforeTenSeconds() {
        val tracker = BrewFeedbackTracker(); start(tracker, wait = 5)
        observe(tracker, 5, 50, 1000)
        observe(tracker, 10, 100, 1500)
        observe(tracker, 11, 150, 2000)
        observe(tracker, 30, 400, 2500)
        val result = tracker.finish("cup", true)!!
        assertEquals("cup", result.shotId)
        assertEquals(30, result.machineSeconds)
        assertEquals(100, result.earlyWaterTenthsMl)
        assertEquals(400, result.finalWaterTenthsMl)
        assertEquals(5, result.preinfusionSeconds)
        assertEquals(2.0, result.flowHeuristic, 0.0)
        assertEquals(BrewFeedbackClips.Level.BRAVO, result.level)
        assertNull(tracker.finish("cup", true))
    }
    @Test fun unknownFinishAndNewCupCannotReusePreviousEarlySnapshot() {
        val tracker = BrewFeedbackTracker(); start(tracker)
        observe(tracker, 5, 0, 1000); observe(tracker, 30, 400, 2000)
        assertNull(tracker.finish("cup", false))
        start(tracker, "next")
        observe(tracker, 30, 400, 3000, id = "next")
        assertNull(tracker.finish("next", true))
        start(tracker, "third")
        observe(tracker, 5, 0, 4000, id = "third")
        assertNull(tracker.finish("cup", true))
        observe(tracker, 30, 400, 5000, id = "third")
        assertNotNull(tracker.finish("third", true))
    }
    @Test fun minimumDurationIsExclusiveAndDenominatorIsClampedToOne() {
        for(seconds in listOf(14, 15)) {
            val tracker = BrewFeedbackTracker(); start(tracker, wait = 127)
            observe(tracker, 5, 0, 1000); observe(tracker, seconds, 22, 2000)
            val result = tracker.finish("cup", true)
            if(seconds == 14) assertNull(result) else {
                assertEquals(2.2, result!!.flowHeuristic, 0.0)
                assertEquals(BrewFeedbackClips.Level.BRAVO, result.level)
            }
        }
    }
    @Test fun slotAndCounterChangesInvalidateWholeCupButStaleOwnershipDoesNot() {
        for(kind in 0..2) {
            val tracker = BrewFeedbackTracker(); start(tracker)
            observe(tracker, 5, 50, 1000)
            when(kind) {
                0 -> observe(tracker, 6, 60, 1500, slot = 2)
                1 -> observe(tracker, 4, 60, 1500)
                2 -> observe(tracker, 6, 49, 1500)
            }
            observe(tracker, 30, 400, 2000)
            assertNull(tracker.finish("cup", true))
        }
        val tracker = BrewFeedbackTracker(); start(tracker)
        observe(tracker, 5, 50, 1000)
        observe(tracker, 0, 0, 5000, id = "old", slot = 2)
        observe(tracker, 30, 400, 2000)
        assertNotNull(tracker.finish("cup", true))
    }
    @Test fun lateAndDuplicateTimestampsDoNotReplaceFreshEvidence() {
        val tracker = BrewFeedbackTracker(); start(tracker)
        observe(tracker, 5, 50, 1000); observe(tracker, 10, 100, 2000)
        observe(tracker, 0, 0, 1500); observe(tracker, 0, 0, 2000)
        observe(tracker, 30, 400, 3000)
        assertEquals(100, tracker.finish("cup", true)!!.earlyWaterTenthsMl)
    }
    @Test fun preheatInactiveAndMissingEarlyExtractionCannotSupplySnapshot() {
        for(flags in listOf(32, 64 or 32)) {
            val tracker = BrewFeedbackTracker(); start(tracker)
            observe(tracker, 5, 0, 1000, flags = flags)
            observe(tracker, 30, 400, 5000)
            assertNull(tracker.finish("cup", true))
        }
        val tracker = BrewFeedbackTracker(); start(tracker)
        observe(tracker, 5, 0, 1000, pressure = 10, flow = 8)
        observe(tracker, 30, 400, 5000)
        assertNull(tracker.finish("cup", true))
    }
    @Test fun phaseUsesOriginalSlotThresholdsAndFreshProgressFallback() {
        for(slot in listOf(1, 6, 7)) {
            val tracker = BrewFeedbackTracker(); start(tracker, slot = slot)
            observe(tracker, 5, 0, 1000, slot = slot, pressure = 1, flow = 0)
            observe(tracker, 30, 400, 5000, slot = slot)
            val result = tracker.finish("cup", true)
            if(slot == 1) assertNull(result) else assertNotNull(result)
        }
        for(gap in listOf(2999L, 3000L)) {
            val tracker = BrewFeedbackTracker(); start(tracker)
            observe(tracker, 5, 0, 1000, pressure = 0, flow = 0)
            observe(tracker, 6, 1, 1000 + gap, pressure = 0, flow = 0)
            observe(tracker, 30, 400, 5000)
            val result = tracker.finish("cup", true)
            if(gap < 3000) assertNotNull(result) else assertNull(result)
        }
    }
    @Test fun validStatusWithoutValveFlagsKeepsOriginalDisplayOnlyFallback() {
        val tracker = BrewFeedbackTracker(); start(tracker)
        observe(tracker, 5, 0, 1000, flags = 0)
        observe(tracker, 30, 400, 2000, flags = 0)
        assertNotNull(tracker.finish("cup", true))
    }
    @Test fun invalidDecodedDisplayInputsCannotProduceSummary() {
        for(bad in listOf(frame(-1, 0), frame(5, 65536), frame(5, 0, pressure = -1),
            frame(5, 0, flow = 256), frame(5, 0, flags = 256))) {
            val tracker = BrewFeedbackTracker(); start(tracker)
            tracker.observe("cup", bad, 1000)
            observe(tracker, 5, 0, 1500); observe(tracker, 30, 400, 2000)
            assertNull(tracker.finish("cup", true))
        }
    }
    @Test fun beforeBeginAndAfterFinishAreIgnored() {
        val tracker = BrewFeedbackTracker()
        observe(tracker, 5, 0, 1000)
        assertNull(tracker.finish("cup", true))
        start(tracker); observe(tracker, 30, 400, 2000)
        assertNull(tracker.finish("cup", true))
        observe(tracker, 5, 0, 3000)
        assertNull(tracker.finish("cup", true))
    }
    private fun oracle(): JSONObject = JSONObject(javaClass.getResourceAsStream("/legacy-brew-feedback.json")!!
        .bufferedReader().use { it.readText() }).also {
        assertEquals("legacy-brew-feedback-v1", it.getString("format"))
        assertEquals("1451bddc95735fdc86b071aef98d9d5b88add12b0b3612c66f666faca7572ddc", it.getString("sourceSha256"))
    }
    @Test fun levelsMatchActualOldAppMethodAcrossFloatingPointAndWireBoundaries() {
        val cases = oracle().getJSONArray("levels")
        assertEquals(1893, cases.length())
        for(index in 0 until cases.length()) {
            val row = cases.getJSONArray(index)
            val tracker = BrewFeedbackTracker(); start(tracker, wait = row.getInt(1))
            observe(tracker, 5, row.getInt(2), 1000)
            observe(tracker, row.getInt(0), row.getInt(3), 2000)
            val result = tracker.finish("cup", true)
            if(row.isNull(4)) assertNull(row.toString(), result)
            else assertEquals(row.toString(), when(row.getInt(4)) {
                0 -> BrewFeedbackClips.Level.BRAVO
                1 -> BrewFeedbackClips.Level.HIGH_FLOW
                else -> BrewFeedbackClips.Level.LOW_FLOW
            }, result?.level)
        }
    }
    @Test fun earlyPhaseMatchesActualOldAppHelperAcrossFlagsSlotsAndProgress() {
        val cases = oracle().getJSONArray("phases")
        assertEquals(1024, cases.length())
        for(index in 0 until cases.length()) {
            val row = cases.getJSONArray(index); val slot = row.getInt(0)
            val tracker = BrewFeedbackTracker(); start(tracker, slot = slot)
            observe(tracker, if(row.getBoolean(4)) 4 else 5,
                if(row.getBoolean(5)) 0 else 1, 1000, slot = slot, pressure = 0, flow = 0, flags = 32)
            observe(tracker, 5, 1, 1500, slot = slot, pressure = row.getInt(2), flow = row.getInt(3), flags = row.getInt(1))
            observe(tracker, 30, 400, 5000, slot = slot)
            assertEquals(row.toString(), row.getBoolean(6), tracker.finish("cup", true) != null)
        }
    }

}
