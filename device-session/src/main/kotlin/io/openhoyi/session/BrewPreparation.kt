package io.openhoyi.session


/** Studio preheat: transport result and post-write temperature evidence remain separate. */
class BrewPreparation {
    enum class State { IDLE, WRITING, WAITING_TEMP, READY, CANCELLING, CANCEL_WRITTEN, FAILED, UNKNOWN }
    var state = State.IDLE
        private set
    var profileId: String? = null
        private set
    var targetC: Int? = null
        private set
    private var serial = 0L
    private var afterSample = 0L

    val active: Boolean get() = state != State.IDLE

    fun begin(profile: String, target: Int): Long? {
        if (state != State.IDLE || profile.isBlank() || target !in 75..105) return null
        profileId = profile
        targetC = target
        state = State.WRITING
        return ++serial
    }

    fun written(token: Long, result: OperationResult, sampleSerial: Long): Boolean {
        if (token != serial || state != State.WRITING) return false
        state = when (result) {
            is OperationResult.Success -> {
                afterSample = sampleSerial
                State.WAITING_TEMP
            }
            is OperationResult.Failed, is OperationResult.Cancelled -> State.FAILED
            is OperationResult.Unknown -> State.UNKNOWN
        }
        return true
    }

    fun observe(sampleSerial: Long, correctedHundredthsC: Int): Boolean {
        if (state != State.WAITING_TEMP || sampleSerial <= afterSample ||
            !isAtTarget(correctedHundredthsC, targetC ?: return false)) return false
        state = State.READY
        return true
    }

    fun matches(profile: String, target: Int): Boolean =
        state == State.READY && profileId == profile && targetC == target

    fun isActive(token: Long): Boolean = token == serial && state in setOf(State.WAITING_TEMP, State.READY)

    fun timedOut(token: Long): Boolean {
        if (!isActive(token)) return false
        state = State.UNKNOWN
        ++serial
        return true
    }

    /** The durable intent has no trustworthy profile or target after a process restart. */
    fun restoreUnknown(): Boolean {
        if (state != State.IDLE) return false
        state = State.UNKNOWN
        profileId = null
        targetC = null
        ++serial
        return true
    }

    fun beginCancel(): Long? {
        if (state == State.IDLE || state == State.CANCELLING) return null
        state = State.CANCELLING
        return ++serial
    }

    fun cancelled(token: Long, result: OperationResult): Boolean {
        if (token != serial || state != State.CANCELLING) return false
        state = if (result is OperationResult.Success) State.CANCEL_WRITTEN else State.UNKNOWN
        return true
    }

    fun consumed() {
        state = State.IDLE
        profileId = null
        targetC = null
        ++serial
    }

    fun disconnected() {
        if (state != State.IDLE) {
            state = State.UNKNOWN
            ++serial
        }
    }

    companion object {
        fun correctedTemperature(rawHundredthsC: Int, compensationTenthsC: Int): Int =
            BrewTemperaturePolicy.correctedTemperature(rawHundredthsC, compensationTenthsC)
        fun isAtTarget(correctedHundredthsC: Int, targetC: Int): Boolean =
            BrewTemperaturePolicy.isAtTarget(correctedHundredthsC, targetC)
    }
}
