package io.openhoyi.session

import kotlin.math.abs

/** Shared tare outcome; a GATT write is not evidence that the scale has zeroed. */
class StandaloneTare(private val clock: () -> Long) {
    enum class State { IDLE, WRITING, WAITING_ZERO, CONFIRMED, FAILED, UNKNOWN }
    var state = State.IDLE
        private set
    val unresolved: Boolean get() = state in setOf(State.WRITING, State.WAITING_ZERO, State.UNKNOWN)
    private var priorUnknown = false
    private var serial = 0L
    private var writtenAfterSample = 0L
    private var deadline: Long? = null

    fun begin(): Long? {
        if (state == State.WRITING || state == State.WAITING_ZERO) return null
        deadline = null
        priorUnknown = state == State.UNKNOWN
        state = State.WRITING
        return ++serial
    }

    fun written(token: Long, result: OperationResult, sampleSerial: Long): Boolean {
        if (token != serial || state != State.WRITING) return false
        state = when (result) {
            is OperationResult.Success -> {
                writtenAfterSample = sampleSerial
                deadline = clock() + 5000L
                State.WAITING_ZERO
            }
            is OperationResult.Failed, is OperationResult.Cancelled -> if (priorUnknown) State.UNKNOWN else State.FAILED
            is OperationResult.Unknown -> State.UNKNOWN
        }
        return true
    }

    fun sample(sampleSerial: Long, hundredthsGram: Int) {
        tick()
        if (state != State.WAITING_ZERO || sampleSerial <= writtenAfterSample) return
        writtenAfterSample = sampleSerial
        if (abs(hundredthsGram.toLong()) <= 50) state = State.CONFIRMED
    }

    fun tick() {
        if (state == State.WAITING_ZERO && deadline?.let { clock() >= it } == true) state = State.UNKNOWN
    }

    fun timeout(token: Long) {
        if (token == serial && state == State.WAITING_ZERO) state = State.UNKNOWN
    }

    fun disconnected() {
        if (state == State.WRITING || state == State.WAITING_ZERO) state = State.UNKNOWN
    }
}
