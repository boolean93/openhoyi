package io.openhoyi.mobile

import android.os.SystemClock

/** One monotonic domain for the service and its live page. Wall time and test deadlines stay separate. */
internal object MobileMonotonicClock {
    private data class MockClock(val owner: Any, var elapsedMs: Long)
    private var mockClock: MockClock? = null

    fun elapsedRealtime(): Long {
        // A real build never observes an override, even if one were present in memory.
        if (!BuildConfig.MOCK_MODE) return SystemClock.elapsedRealtime()
        return synchronized(this) { mockClock?.elapsedMs ?: SystemClock.elapsedRealtime() }
    }

    /** Install before any scoped service/readers exist; the owner must drain them before removal. */
    @Synchronized fun installForMock(owner: Any, initialMs: Long) {
        check(BuildConfig.MOCK_MODE) { "Mock clock is unavailable in a real build" }
        require(initialMs >= 0) { "Negative monotonic time" }
        check(mockClock == null) { "A mock clock owner is already installed" }
        mockClock = MockClock(owner, initialMs)
    }

    @Synchronized fun advanceForMock(owner: Any, nextMs: Long) {
        check(BuildConfig.MOCK_MODE) { "Mock clock is unavailable in a real build" }
        val clock = checkNotNull(mockClock) { "No mock clock owner installed" }
        check(clock.owner === owner) { "Mock clock owner identity mismatch" }
        require(nextMs >= clock.elapsedMs) { "Monotonic time cannot move backwards" }
        clock.elapsedMs = nextMs
    }

    @Synchronized fun uninstallForMock(owner: Any) {
        check(BuildConfig.MOCK_MODE) { "Mock clock is unavailable in a real build" }
        check(mockClock?.owner === owner) { "Mock clock owner identity mismatch" }
        mockClock = null
    }

    @Synchronized fun ownsMockClock(owner: Any): Boolean = BuildConfig.MOCK_MODE && mockClock?.owner === owner
    @Synchronized fun mockOwnerIdentity(): Any? = if (BuildConfig.MOCK_MODE) mockClock?.owner else null
}
