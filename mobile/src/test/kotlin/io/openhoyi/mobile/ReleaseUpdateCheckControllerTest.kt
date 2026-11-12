package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class ReleaseUpdateCheckControllerTest {
    private val update = ReleaseUpdate(94, "0.2.0-dev.94", "a".repeat(64), "b".repeat(64), "https://github.com/fixture.apk", "https://github.com/fixture")
    private class Request {
        val callbacks = mutableListOf<(Result<ReleaseUpdate>) -> Unit>()
        var cancelled = 0
        fun start(callback: (Result<ReleaseUpdate>) -> Unit): () -> Unit {
            callbacks += callback
            return { cancelled++ }
        }
    }
    @Test fun manualOnlyAndRepeatedTapIsSingleFlight() {
        val request = Request()
        val states = mutableListOf<ReleaseUpdateCheckController.State>()
        val controller = ReleaseUpdateCheckController(request::start, states::add)
        controller.check()
        assertTrue(request.callbacks.isEmpty())
        controller.resume()
        assertTrue(request.callbacks.isEmpty())
        controller.check(); controller.check(); controller.check()
        assertEquals(1, request.callbacks.size)
        assertEquals(listOf(ReleaseUpdateCheckController.State.Checking), states)
        request.callbacks[0](Result.success(update))
        assertEquals(ReleaseUpdateCheckController.State.Checked(update), controller.state)
        controller.check()
        assertEquals(2, request.callbacks.size)
    }
    @Test fun failureAllowsRetryWithoutClaimingCurrentVersion() {
        val request = Request()
        val controller = ReleaseUpdateCheckController(request::start) { }
        controller.resume(); controller.check()
        request.callbacks[0](Result.failure(java.io.IOException()))
        assertEquals(ReleaseUpdateCheckController.State.Failed, controller.state)
        controller.check()
        assertEquals(2, request.callbacks.size)
        request.callbacks[1](Result.success(update))
        assertEquals(ReleaseUpdateCheckController.State.Checked(update), controller.state)
    }
    @Test fun pauseDiscardsLateSuccessAndFailureAndReentryCanRetry() {
        val request = Request()
        val states = mutableListOf<ReleaseUpdateCheckController.State>()
        val controller = ReleaseUpdateCheckController(request::start, states::add)
        controller.resume(); controller.check(); controller.pause()
        assertEquals(1, request.cancelled)
        assertEquals(ReleaseUpdateCheckController.State.Idle, controller.state)
        val count = states.size
        request.callbacks[0](Result.success(update))
        request.callbacks[0](Result.failure(java.io.IOException()))
        assertEquals(count, states.size)
        controller.resume()
        assertEquals(1, request.callbacks.size)
        controller.check()
        request.callbacks[0](Result.success(update))
        assertEquals(ReleaseUpdateCheckController.State.Checking, controller.state)
        request.callbacks[1](Result.success(update))
        assertEquals(ReleaseUpdateCheckController.State.Checked(update), controller.state)
        controller.pause(); controller.pause()
        assertEquals(1, request.cancelled)
    }
    @Test fun destroyedOwnerCannotPublishIntoReplacementView() {
        val request = Request()
        val old = ReleaseUpdateCheckController(request::start) { }
        old.resume(); old.check(); old.pause()
        val freshStates = mutableListOf<ReleaseUpdateCheckController.State>()
        val fresh = ReleaseUpdateCheckController(request::start, freshStates::add)
        fresh.resume()
        request.callbacks[0](Result.success(update))
        assertTrue(freshStates.isEmpty())
        assertEquals(ReleaseUpdateCheckController.State.Idle, fresh.state)
    }
    @Test fun synchronousCompletionAndStartFailureDoNotLeaveBusyOrCancellationOwner() {
        var stops = 0
        val controller = ReleaseUpdateCheckController({ result -> result(Result.success(update)); { stops++ } }) { }
        controller.resume(); controller.check(); controller.pause()
        assertEquals(ReleaseUpdateCheckController.State.Checked(update), controller.state)
        assertEquals(1, stops)
        val failing = ReleaseUpdateCheckController({ throw IllegalStateException() }) { }
        failing.resume(); failing.check()
        assertEquals(ReleaseUpdateCheckController.State.Failed, failing.state)
        failing.check()
        assertEquals(ReleaseUpdateCheckController.State.Failed, failing.state)
    }
}
