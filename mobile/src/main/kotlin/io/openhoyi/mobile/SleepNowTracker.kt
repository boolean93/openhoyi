package io.openhoyi.mobile

import io.openhoyi.session.OperationResult

/** A write confirms transport only; a later idle frame with sleepStateRaw=1 confirms sleep. */
class SleepNowTracker {
    enum class State { IDLE, WRITING, WAITING_ASLEEP, CONFIRMED, FAILED, UNKNOWN }
    var state = State.IDLE
        private set
    private var serial = 0L
    private var afterSample = 0L

    fun begin(): Long? {
        if (state == State.WRITING || state == State.WAITING_ASLEEP) return null
        state = State.WRITING
        return ++serial
    }

    fun written(token: Long, result: OperationResult, sampleSerial: Long): Boolean {
        if (token != serial || state != State.WRITING) return false
        state = when (result) {
            is OperationResult.Success -> {
                afterSample = sampleSerial
                State.WAITING_ASLEEP
            }
            is OperationResult.Failed, is OperationResult.Cancelled -> State.FAILED
            is OperationResult.Unknown -> State.UNKNOWN
        }
        return true
    }

    fun observe(sampleSerial: Long, sleepStateRaw: Int): Boolean {
        if (state != State.WAITING_ASLEEP || sampleSerial <= afterSample || sleepStateRaw != 1) return false
        state = State.CONFIRMED
        return true
    }

    fun timeout(token: Long): Boolean {
        if (token != serial || state != State.WAITING_ASLEEP) return false
        state = State.UNKNOWN
        return true
    }

    fun disconnected() {
        if (state == State.WRITING || state == State.WAITING_ASLEEP) state = State.UNKNOWN
    }
}
