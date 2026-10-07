package io.openhoyi.session


/** A write confirms transport only; a later idle frame with sleepStateRaw=1 confirms sleep. */
class SleepNowTracker {
    enum class State { IDLE, WRITING, WAITING_ASLEEP, CONFIRMED, FAILED, UNKNOWN, RECONCILED }
    var state = State.IDLE
        private set
    private var serial = 0L
    private var afterSample = 0L

    fun begin(): Long? {
        if (state == State.WRITING || state == State.WAITING_ASLEEP || state == State.UNKNOWN) return null
        afterSample = 0L
        state = State.WRITING
        return ++serial
    }

    /** Only the original operation in WRITING owns dispatch permission. */
    fun permitsWrite(token:Long):Boolean = token == serial && state == State.WRITING

    fun written(token: Long, result: OperationResult, sampleSerial: Long): Boolean {
        if (token != serial || state != State.WRITING) return false
        state = when (result) {
            is OperationResult.Success -> {
                afterSample = sampleSerial
                State.WAITING_ASLEEP
            }
            is OperationResult.Failed, is OperationResult.Cancelled -> State.FAILED
            is OperationResult.Unknown -> {
                afterSample = sampleSerial
                State.UNKNOWN
            }
        }
        return true
    }

    fun observe(sampleSerial: Long, sleepStateRaw: Int): Boolean {
        if (state !in setOf(State.WAITING_ASLEEP, State.UNKNOWN) || sampleSerial <= afterSample)
            return false
        afterSample = sampleSerial
        if (sleepStateRaw == 1) {
            state = State.CONFIRMED
            return true
        }
        if (state == State.UNKNOWN && sleepStateRaw == 0) state = State.RECONCILED
        return false
    }

    fun timeout(token: Long, sampleSerial: Long): Boolean {
        if (token != serial || state != State.WAITING_ASLEEP) return false
        afterSample = maxOf(afterSample, sampleSerial)
        state = State.UNKNOWN
        return true
    }

    fun disconnected(sampleSerial: Long) {
        if (state == State.WRITING || state == State.WAITING_ASLEEP) {
            afterSample = maxOf(afterSample, sampleSerial)
            state = State.UNKNOWN
        }
    }
}
