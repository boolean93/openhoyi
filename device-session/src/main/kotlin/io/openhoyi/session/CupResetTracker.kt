package io.openhoyi.session


/** A reset needs matching post-write settings and idle counts; transport alone proves nothing.
 * Each channel accepts only increasing host sample serials, not firmware transaction IDs.
 */
class CupResetTracker {
    enum class State { IDLE, WRITING, WAITING_ZERO, CONFIRMED, FAILED, UNKNOWN, RECONCILED }
    var state = State.IDLE
        private set
    var expectedCount: Int? = null
        private set
    private var serial = 0L
    private var afterSettings = 0L
    private var afterIdle = 0L
    private var freshSettingsCount: Int? = null
    private var freshIdleCount: Int? = null

    fun begin(count: Int): Long? {
        require(count in 1..65535)
        if (state == State.WRITING || state == State.WAITING_ZERO || state == State.UNKNOWN) return null
        expectedCount = count
        markAfter(0L, 0L)
        state = State.WRITING
        return ++serial
    }

    fun written(token: Long, result: OperationResult, settingsSerial: Long, idleSerial: Long): Boolean {
        if (token != serial || state != State.WRITING) return false
        state = when (result) {
            is OperationResult.Success -> {
                markAfter(settingsSerial, idleSerial)
                State.WAITING_ZERO
            }
            is OperationResult.Failed, is OperationResult.Cancelled -> State.FAILED
            is OperationResult.Unknown -> {
                markAfter(settingsSerial, idleSerial)
                State.UNKNOWN
            }
        }
        return true
    }

    fun observeSettings(sampleSerial: Long, count: Int): Boolean {
        if (state !in setOf(State.WAITING_ZERO, State.UNKNOWN) || sampleSerial <= afterSettings) return false
        afterSettings = sampleSerial
        freshSettingsCount = count
        return resolve()
    }

    fun observeIdle(sampleSerial: Long, count: Int): Boolean {
        if (state !in setOf(State.WAITING_ZERO, State.UNKNOWN) || sampleSerial <= afterIdle) return false
        afterIdle = sampleSerial
        freshIdleCount = count
        return resolve()
    }

    private fun resolve(): Boolean {
        val settings = freshSettingsCount ?: return false
        val idle = freshIdleCount ?: return false
        if (settings == 0 && idle == 0) {
            state = State.CONFIRMED
            return true
        }
        if (state == State.UNKNOWN && settings == idle) state = State.RECONCILED
        return false
    }

    private fun markAfter(settingsSerial: Long, idleSerial: Long) {
        afterSettings = settingsSerial
        afterIdle = idleSerial
        freshSettingsCount = null
        freshIdleCount = null
    }

    fun timeout(token: Long, settingsSerial: Long, idleSerial: Long): Boolean {
        if (token != serial || state != State.WAITING_ZERO) return false
        markAfter(maxOf(afterSettings, settingsSerial), maxOf(afterIdle, idleSerial))
        state = State.UNKNOWN
        return true
    }

    fun disconnected(settingsSerial: Long, idleSerial: Long) {
        if (state == State.WRITING || state == State.WAITING_ZERO) {
            markAfter(maxOf(afterSettings, settingsSerial), maxOf(afterIdle, idleSerial))
            state = State.UNKNOWN
        }
    }
}
