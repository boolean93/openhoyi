package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.RejectedExecutionException

class DiagnosticStorageControllerTest {
    @Test fun repeatedTapsCannotOverlapAndARecreatedViewCanObserveCompletion() {
        lateinit var done: (Result<Long>) -> Unit
        val calls = mutableListOf<Boolean>()
        val controller = DiagnosticStorageController { clear, callback -> calls += clear; done = callback }
        var firstUpdates = 0
        val first: (DiagnosticStorageController.State) -> Unit = { firstUpdates++ }
        controller.observe(first)
        assertTrue(controller.clear()); assertFalse(controller.clear()); assertFalse(controller.refresh())
        controller.unobserve(first)
        val before = firstUpdates
        var secondState: DiagnosticStorageController.State? = null
        controller.observe { secondState = it }
        done(Result.success(0))
        assertEquals(listOf(true), calls); assertEquals(before, firstUpdates)
        assertEquals(DiagnosticStorageController.Outcome.CLEARED, secondState?.outcome)
        assertFalse(controller.state.busy)
    }
    @Test fun queueRejectionAndReadFailuresAreNotPresentedAsZeroBytes() {
        val rejected = DiagnosticStorageController { _, _ -> throw RejectedExecutionException() }
        assertTrue(rejected.clear())
        assertEquals(DiagnosticStorageController.Outcome.FAILED, rejected.state.outcome)
        assertNull(rejected.state.bytes); assertFalse(rejected.state.busy)
        val failed = DiagnosticStorageController { _, done -> done(Result.failure(IllegalStateException())) }
        failed.refresh(); assertTrue(failed.state.readFailed); assertNull(failed.state.bytes)
        assertNull(failed.state.outcome)
    }
    @Test fun duplicateCompletionCannotOverwriteNewOperation() {
        val callbacks = mutableListOf<(Result<Long>) -> Unit>()
        val controller = DiagnosticStorageController { _, done -> callbacks += done }
        controller.refresh(); callbacks[0](Result.success(42))
        assertEquals(42L, controller.state.bytes)
        controller.clear(); callbacks[0](Result.success(99))
        assertTrue(controller.state.busy)
        callbacks[1](Result.success(0)); assertEquals(0L, controller.state.bytes)
    }
    @Test fun refreshAfterReturningKeepsCompletedClearOutcomeIncludingFailure() {
        listOf(Result.success(0L), Result.failure<Long>(IllegalStateException())).forEach { clearResult ->
            val callbacks = mutableListOf<(Result<Long>) -> Unit>()
            val controller = DiagnosticStorageController { _, done -> callbacks += done }
            controller.clear()
            callbacks[0](clearResult) // Completes while the page has no observer.
            val expected = if (clearResult.isSuccess) DiagnosticStorageController.Outcome.CLEARED else DiagnosticStorageController.Outcome.FAILED
            var shown: DiagnosticStorageController.State? = null
            controller.observe { shown = it }
            controller.refresh(); callbacks[1](Result.success(0))
            assertEquals(expected, shown?.outcome)
            assertEquals(0L, shown?.bytes)
        }
    }
}
