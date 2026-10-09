package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class ExtractionReferenceTest {
    private fun point(at:Long, brewing:Boolean?)=ShotPoint(at,90,20,100,9200,1000,brewing=brewing)
    @Test fun actualBrewingOriginAlignsWithoutRewritingRawSamples() {
        val raw=listOf(point(500,false),point(2400,true),point(3200,true),point(4000,false))
        val result=ExtractionReference.align(raw)
        assertTrue(result.observedOrigin)
        assertEquals(listOf(0L,800L,1600L),result.points.map {it.elapsedMs})
        assertEquals(listOf(500L,2400L,3200L,4000L),raw.map {it.elapsedMs})
        assertEquals(false,result.points.last().brewing)
    }
    @Test fun unknownLegacyPhaseUsesRecordedOriginAndNeverInventsBrewing() {
        val result=ExtractionReference.align(listOf(point(700,null),point(1500,null)))
        assertFalse(result.observedOrigin)
        assertEquals(listOf(0L,800L),result.points.map {it.elapsedMs})
        assertTrue(result.points.all {it.brewing==null})
    }
    @Test fun onlyPreheatHasNoBrewingTrace() {
        assertTrue(ExtractionReference.align(listOf(point(10,false),point(500,false))).points.isEmpty())
        assertTrue(ExtractionReference.align(emptyList()).points.isEmpty())
    }
    @Test fun malformedTimelineIsRejectedRatherThanSilentlyResorted() {
        for(raw in listOf(listOf(point(2,true),point(1,true)),listOf(point(-1,true)),listOf(point(1,true),point(1,true))))
            assertTrue(runCatching {ExtractionReference.align(raw)}.isFailure)
    }
}
