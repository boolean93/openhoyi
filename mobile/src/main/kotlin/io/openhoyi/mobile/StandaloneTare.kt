package io.openhoyi.mobile

import io.openhoyi.session.OperationResult
import kotlin.math.abs

/** UI-facing tare outcome; a GATT write is not evidence that the scale has zeroed. */
class StandaloneTare {
    enum class State { IDLE, WRITING, WAITING_ZERO, CONFIRMED, FAILED, UNKNOWN }
    var state = State.IDLE
        private set
    private var serial = 0L
    private var writtenAfterSample = 0L

    fun begin(): Long? {
        if (state == State.WRITING || state == State.WAITING_ZERO) return null
        state = State.WRITING
        return ++serial
    }

    fun written(token: Long, result: OperationResult, sampleSerial: Long): Boolean {
        if (token != serial || state != State.WRITING) return false
        state = when (result) {
            is OperationResult.Success -> {
                writtenAfterSample = sampleSerial
                State.WAITING_ZERO
            }
            is OperationResult.Failed, is OperationResult.Cancelled -> State.FAILED
            is OperationResult.Unknown -> State.UNKNOWN
        }
        return true
    }

    fun sample(sampleSerial: Long, hundredthsGram: Int) {
        if (state == State.WAITING_ZERO && sampleSerial > writtenAfterSample && abs(hundredthsGram) <= 50)
            state = State.CONFIRMED
    }

    fun timeout(token: Long) {
        if (token == serial && state == State.WAITING_ZERO) state = State.UNKNOWN
    }

    fun disconnected() {
        if (state == State.WRITING || state == State.WAITING_ZERO) state = State.UNKNOWN
    }
}
