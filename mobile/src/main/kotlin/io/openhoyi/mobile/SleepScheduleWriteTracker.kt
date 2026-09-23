package io.openhoyi.mobile

import io.openhoyi.protocol.SleepPart
import io.openhoyi.protocol.WeeklySleepSchedule
import io.openhoyi.session.OperationResult

/** Both 0x83 sleep fragments must arrive after transport success and match the full requested week. */
class SleepScheduleWriteTracker {
    enum class State { IDLE, WRITING, WAITING_READBACK, CONFIRMED, FAILED, UNKNOWN, RECONCILED }
    var state = State.IDLE
        private set
    var target: WeeklySleepSchedule? = null
        private set
    private var serial = 0L
    private var afterFirst = 0L
    private var afterSecond = 0L

    fun begin(schedule: WeeklySleepSchedule, firstSerial: Long, secondSerial: Long): Long? {
        if (state == State.WRITING || state == State.WAITING_READBACK || state == State.UNKNOWN) return null
        target = schedule
        afterFirst = firstSerial
        afterSecond = secondSerial
        state = State.WRITING
        return ++serial
    }

    fun written(token: Long, result: OperationResult, firstSerial: Long, secondSerial: Long,
        first: SleepPart?, second: SleepPart?): Boolean {
        if (token != serial || state != State.WRITING) return false
        state = when (result) {
            is OperationResult.Success -> if (matches(firstSerial, secondSerial, first, second))
                State.CONFIRMED else State.WAITING_READBACK
            is OperationResult.Failed, is OperationResult.Cancelled -> State.FAILED
            is OperationResult.Unknown -> {
                afterFirst = firstSerial
                afterSecond = secondSerial
                State.UNKNOWN
            }
        }
        return true
    }

    fun observe(firstSerial: Long, secondSerial: Long, first: SleepPart?, second: SleepPart?): Boolean {
        if (state == State.UNKNOWN && firstSerial > afterFirst && secondSerial > afterSecond &&
            WeeklySleepSchedule.fromReadback(first, second) != null) {
            state = State.RECONCILED
            return true
        }
        if (state != State.WAITING_READBACK || !matches(firstSerial, secondSerial, first, second)) return false
        state = State.CONFIRMED
        return true
    }

    private fun matches(firstSerial: Long, secondSerial: Long, first: SleepPart?, second: SleepPart?) =
        firstSerial > afterFirst && secondSerial > afterSecond &&
            WeeklySleepSchedule.fromReadback(first, second)?.days == target?.days

    fun timeout(token: Long, firstSerial: Long, secondSerial: Long): Boolean {
        if (token != serial || state != State.WAITING_READBACK) return false
        afterFirst = firstSerial
        afterSecond = secondSerial
        state = State.UNKNOWN
        return true
    }

    fun disconnected(firstSerial: Long, secondSerial: Long) {
        if (state == State.WRITING || state == State.WAITING_READBACK) {
            afterFirst = firstSerial
            afterSecond = secondSerial
            state = State.UNKNOWN
        }
    }
}
