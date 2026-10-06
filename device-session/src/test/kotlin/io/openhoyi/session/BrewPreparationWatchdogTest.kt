package io.openhoyi.session

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class BrewPreparationWatchdogTest(private val ready:Boolean) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name="ready={0}") fun states()=listOf(arrayOf(false),arrayOf(true))
    }
    private class Fixture(ready:Boolean) {
        val tracker=BrewPreparation()
        val token=requireNotNull(tracker.begin("factory",91))
        var cancels=0
        init {
            assertTrue(tracker.written(token,OperationResult.Success(),7))
            if(ready)assertTrue(tracker.observe(8,9100))
        }
        fun expire(action:()->String?):BrewPreparationWatchdog.Result<String>?=
            BrewPreparationWatchdog.expire(tracker,token){cancels++;action()}
    }
    @Test fun blockedExpiryBecomesUnknownOnceAndKeepsBlockReason() {
        val f=Fixture(ready)
        assertEquals(BrewPreparationWatchdog.Result(BrewPreparationWatchdog.Outcome.CANCEL_BLOCKED,"stale idle"),f.expire { "stale idle" })
        assertEquals(BrewPreparation.State.UNKNOWN,f.tracker.state);assertEquals(1,f.cancels)
        assertNull(f.expire { error("repeated expiry") });assertEquals(1,f.cancels)
        assertFalse(f.tracker.written(f.token,OperationResult.Success(),10));assertFalse(f.tracker.isActive(f.token))
    }
    @Test fun asynchronousCancellationIsRequestedOnlyOnce() {
        val f=Fixture(ready);var cancelToken=0L
        val result=f.expire { cancelToken=requireNotNull(f.tracker.beginCancel());null }
        assertEquals(BrewPreparationWatchdog.Outcome.CANCEL_REQUESTED,result?.outcome);assertNull(result?.blocked)
        assertEquals(BrewPreparation.State.CANCELLING,f.tracker.state);assertNull(f.expire { error("old timer") })
        assertTrue(f.tracker.cancelled(cancelToken,OperationResult.Success()))
        assertEquals(BrewPreparation.State.CANCEL_WRITTEN,f.tracker.state);assertEquals(1,f.cancels)
    }
    @Test fun synchronousSuccessRemainsCancelWrittenNotIdle() {
        val f=Fixture(ready)
        val result=f.expire { val token=requireNotNull(f.tracker.beginCancel());assertTrue(f.tracker.cancelled(token,OperationResult.Success()));null }
        assertEquals(BrewPreparationWatchdog.Outcome.CANCEL_REQUESTED,result?.outcome)
        assertEquals(BrewPreparation.State.CANCEL_WRITTEN,f.tracker.state)
        assertNull(f.expire { error("completed timer") });assertEquals(1,f.cancels)
    }
    @Test fun synchronousUnknownUsesCancellationCallbackOutcomeWithoutSecondExpiryEvent() {
        val f=Fixture(ready)
        assertNull(f.expire { val token=requireNotNull(f.tracker.beginCancel());assertTrue(f.tracker.cancelled(token,OperationResult.Unknown("link")));null })
        assertEquals(BrewPreparation.State.UNKNOWN,f.tracker.state);assertEquals(1,f.cancels)
        assertNull(f.expire { error("unknown cannot retry") })
    }
    @Test fun disconnectDuringCancelCannotBeOverwrittenByOldTimer() {
        val f=Fixture(ready)
        assertNull(f.expire { f.tracker.disconnected();"not connected" })
        assertEquals(BrewPreparation.State.UNKNOWN,f.tracker.state);assertFalse(f.tracker.isActive(f.token))
    }
    @Test fun newerRequestDuringBlockedCancelIsPreserved() {
        val f=Fixture(ready);var newer=0L
        assertNull(f.expire { f.tracker.consumed();newer=requireNotNull(f.tracker.begin("next",93));"blocked old" })
        assertEquals(BrewPreparation.State.WRITING,f.tracker.state);assertEquals("next",f.tracker.profileId)
        assertTrue(f.tracker.permitsWrite(newer,93));assertFalse(f.tracker.permitsWrite(f.token,91))
    }
    @Test fun consumedAndNewerTokenPreventCancellation() {
        val f=Fixture(ready);f.tracker.consumed()
        assertNull(f.expire { error("consumed timer") });assertEquals(0,f.cancels)
        val token=requireNotNull(f.tracker.begin("new",93));f.tracker.written(token,OperationResult.Success(),10)
        assertNull(f.expire { error("stale timer") });assertEquals(0,f.cancels)
        assertTrue(f.tracker.isActive(token));assertEquals(BrewPreparation.State.WAITING_TEMP,f.tracker.state)
    }
    @Test fun alreadyCancellingOrCancelWrittenTimerDoesNothing() {
        val f=Fixture(ready);val token=requireNotNull(f.tracker.beginCancel())
        assertNull(f.expire { error("cancelling timer") });assertEquals(0,f.cancels)
        f.tracker.cancelled(token,OperationResult.Success())
        assertNull(f.expire { error("cancel written timer") });assertEquals(0,f.cancels)
    }
    @Test fun writingTimerAndWrongTokenCannotCancel() {
        val tracker=BrewPreparation();val token=requireNotNull(tracker.begin("factory",91));var calls=0
        assertNull(BrewPreparationWatchdog.expire<String>(tracker,token){calls++;"blocked"})
        tracker.written(token,OperationResult.Success(),7)
        assertNull(BrewPreparationWatchdog.expire<String>(tracker,token+1){calls++;"blocked"})
        assertEquals(0,calls);assertTrue(tracker.isActive(token));assertEquals(600_000L,BrewPreparationWatchdog.TIMEOUT_MS)
    }
}
