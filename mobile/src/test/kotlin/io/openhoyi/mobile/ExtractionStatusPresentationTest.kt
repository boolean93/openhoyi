package io.openhoyi.mobile

import io.openhoyi.session.BrewPreparation
import io.openhoyi.session.ExtractionState
import org.junit.Assert.*
import org.junit.Test

class ExtractionStatusPresentationTest {
    private val normal=ExtractionStatusPresentation.Facts()
    @Test fun freshAllClearNeverReservesAStatusForIdleRunningOrEnded() {
        for(state in listOf(ExtractionState.IDLE,ExtractionState.RUNNING,ExtractionState.ENDED_OBSERVED))
            assertTrue(ExtractionStatusPresentation.signals(normal.copy(state=state,active=state==ExtractionState.RUNNING)).isEmpty())
    }
    @Test fun simultaneousSafetyConditionsAllRemainReviewableWithCriticalFirst() {
        val signals=ExtractionStatusPresentation.signals(normal.copy(state=ExtractionState.OUTCOME_UNKNOWN,active=true,
            alarmBits=1,machineSafety=true,manualSafety=true,writePending=true,writeUnknown=true,
            notificationsBlocked=true,machineFresh=false,scaleExpected=true,scaleFresh=false))
        assertEquals(8,signals.size)
        assertEquals(ExtractionStatusPresentation.Severity.ADVICE,signals.last().severity)
        assertTrue(signals.take(7).all { it.severity==ExtractionStatusPresentation.Severity.CRITICAL })
    }
    @Test fun c16AdviceAndMissingOrStaleAlarmRemainUntilRealClear() {
        assertEquals(ExtractionStatusPresentation.Severity.ADVICE,ExtractionStatusPresentation.signals(normal.copy(alarmBits=1 shl 14)).single().severity)
        for(input in listOf(normal.copy(alarmBits=null),normal.copy(alarmAt=null),normal.copy(now=1501),normal.copy(alarmAt=1)))
            assertTrue(ExtractionStatusPresentation.signals(input).any { it.kind==ExtractionStatusPresentation.Kind.ALARM })
        assertTrue(ExtractionStatusPresentation.signals(normal.copy(now=1500)).isEmpty())
    }
    @Test fun progressAndUnknownPreparationArePersistentNotOrdinarySuccess() {
        for(state in listOf(ExtractionState.STARTING,ExtractionState.STOP_REQUESTED))
            assertEquals(ExtractionStatusPresentation.Severity.PROGRESS,ExtractionStatusPresentation.signals(normal.copy(state=state)).single().severity)
        assertEquals(ExtractionStatusPresentation.Severity.CRITICAL,ExtractionStatusPresentation.signals(normal.copy(preparationNeeded=true,preparation=BrewPreparation.State.UNKNOWN)).single().severity)
        assertTrue(ExtractionStatusPresentation.signals(normal.copy(active=true,scaleExpected=false,scaleFresh=false)).isEmpty())
    }
    @Test fun disconnectsAndStaleReadingsStayReviewableAfterEndingDespiteFreshAllClear() {
        for(state in listOf(ExtractionState.IDLE,ExtractionState.ENDED_OBSERVED)) {
            val signals=ExtractionStatusPresentation.signals(normal.copy(state=state,machineFresh=false,scaleExpected=true,scaleFresh=false))
            assertEquals(setOf(ExtractionStatusPresentation.Kind.MACHINE_DATA,ExtractionStatusPresentation.Kind.SCALE_DATA),signals.map { it.kind }.toSet())
            assertTrue(signals.all { it.severity==ExtractionStatusPresentation.Severity.ADVICE })
        }
    }
    @Test fun pollTicksDoNotRepeatCriticalAnnouncementOrCompletionToast() {
        val t=ExtractionStatusPresentation.Transitions()
        val fault=ExtractionStatusPresentation.signals(normal.copy(alarmBits=1))
        assertTrue(t.observe(ExtractionState.RUNNING,fault).announceCritical)
        repeat(20) { val next=t.observe(ExtractionState.RUNNING,fault);assertFalse(next.announceCritical);assertFalse(next.completed) }
        assertTrue(t.observe(ExtractionState.ENDED_OBSERVED,emptyList()).completed)
        repeat(20) { assertFalse(t.observe(ExtractionState.ENDED_OBSERVED,emptyList()).completed) }
        assertTrue(t.observe(ExtractionState.IDLE,fault).announceCritical)
        assertFalse(ExtractionStatusPresentation.Transitions().observe(ExtractionState.ENDED_OBSERVED,emptyList()).completed)
    }
}
