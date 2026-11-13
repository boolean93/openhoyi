package io.openhoyi.session

/** One explicit use action, never reconstructed from navigation or persisted on reconnect. */
class AutoPreheatIntent(val profileId: String, val targetC: Int, val slot: Int,
    private val connection: Any, private val revision: Long) {
    var requestId: Long? = null
    private var leftAt: Long? = null
    private var invalidated = false

    fun invalidate() { invalidated = true }
    fun permitsStart(currentConnection: Any?, currentRevision: Long): Boolean =
        !invalidated && leftAt == null && connection == currentConnection && revision == currentRevision

    /** Consume the leave event before the caller checks state or sends anything. */
    fun leave(now: Long): Boolean {
        if (leftAt != null) return false
        leftAt = now
        return true
    }

    fun permitsCancel(currentConnection: Any?, currentRevision: Long, now: Long): Boolean =
        !invalidated && connection == currentConnection && revision == currentRevision &&
            TelemetryFreshness.isFresh(leftAt, now, 1500)
}
