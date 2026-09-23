package io.openhoyi.mobile

import io.openhoyi.session.OperationResult

/** The fixed reset write is only transport evidence; a new zero cup-count frame confirms it. */
class CupResetTracker {
    enum class State { IDLE, WRITING, WAITING_ZERO, CONFIRMED, FAILED, UNKNOWN }
    var state = State.IDLE
        private set
    var expectedCount: Int? = null
        private set
    private var serial = 0L
    private var afterSample = 0L

    fun begin(count: Int): Long? {
        require(count in 1..65535)
        if (state == State.WRITING || state == State.WAITING_ZERO) return null
        expectedCount = count
        state = State.WRITING
        return ++serial
    }

    fun written(token: Long, result: OperationResult, sampleSerial: Long): Boolean {
        if (token != serial || state != State.WRITING) return false
        state = when (result) {
            is OperationResult.Success -> {
                afterSample = sampleSerial
                State.WAITING_ZERO
            }
            is OperationResult.Failed, is OperationResult.Cancelled -> State.FAILED
            is OperationResult.Unknown -> State.UNKNOWN
        }
        return true
    }

    fun observe(sampleSerial: Long, count: Int): Boolean {
        if (state != State.WAITING_ZERO || sampleSerial <= afterSample || count != 0) return false
        state = State.CONFIRMED
        return true
    }

    fun timeout(token: Long): Boolean {
        if (token != serial || state != State.WAITING_ZERO) return false
        state = State.UNKNOWN
        return true
    }

    fun disconnected() {
        if (state == State.WRITING || state == State.WAITING_ZERO) state = State.UNKNOWN
    }
}
