package io.openhoyi.session

import io.openhoyi.protocol.SleepPart
import io.openhoyi.protocol.WeeklySleepSchedule

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

    private var latestFirst = 0L
    private var latestSecond = 0L
    private var observedFirst: SleepPart? = null
    private var observedSecond: SleepPart? = null

    private fun baseline(firstSerial: Long, secondSerial: Long) {
        afterFirst = firstSerial
        afterSecond = secondSerial
        latestFirst = firstSerial
        latestSecond = secondSerial
        observedFirst = null
        observedSecond = null
    }

    fun begin(schedule: WeeklySleepSchedule, firstSerial: Long, secondSerial: Long): Long? {
        if (state == State.WRITING || state == State.WAITING_READBACK || state == State.UNKNOWN) return null
        target = schedule
        baseline(firstSerial, secondSerial)
        state = State.WRITING
        return ++serial
    }

    /** Only the original operation in WRITING owns dispatch permission. */
    fun permitsWrite(token:Long):Boolean = token == serial && state == State.WRITING

    fun written(token: Long, result: OperationResult, firstSerial: Long, secondSerial: Long,
        first: SleepPart?, second: SleepPart?): Boolean {
        if (token != serial || state != State.WRITING) return false
        state = when (result) {
            is OperationResult.Success -> {
                // Fragments seen while the two writes were in flight may describe the old plan.
                baseline(firstSerial, secondSerial)
                State.WAITING_READBACK
            }
            is OperationResult.Failed, is OperationResult.Cancelled -> State.FAILED
            is OperationResult.Unknown -> {
                baseline(firstSerial, secondSerial)
                State.UNKNOWN
            }
        }
        return true
    }

    fun observe(firstSerial: Long, secondSerial: Long, first: SleepPart?, second: SleepPart?): Boolean {
        if (state != State.WAITING_READBACK && state != State.UNKNOWN) return false
        // A snapshot may keep one fragment unchanged while the other advances.
        // The same host serial must continue to denote the same fragment value.
        if (firstSerial < latestFirst || secondSerial < latestSecond) return false
        if (firstSerial == latestFirst && latestFirst > afterFirst && !sameFragment(first, observedFirst)) return false
        if (secondSerial == latestSecond && latestSecond > afterSecond && !sameFragment(second, observedSecond)) return false
        if (firstSerial > latestFirst) {
            latestFirst = firstSerial
            observedFirst = first
        }
        if (secondSerial > latestSecond) {
            latestSecond = secondSerial
            observedSecond = second
        }
        if (state == State.UNKNOWN && firstSerial > afterFirst && secondSerial > afterSecond &&
            WeeklySleepSchedule.fromReadback(first, second) != null) {
            state = State.RECONCILED
            return true
        }
        if (state != State.WAITING_READBACK || !matches(firstSerial, secondSerial, first, second)) return false
        state = State.CONFIRMED
        return true
    }

    private fun sameFragment(a: SleepPart?, b: SleepPart?): Boolean =
        if (a == null || b == null) a == b else
            a.firstDaySundayIndex == b.firstDaySundayIndex && a.enabledBits == b.enabledBits &&
                a.days == b.days && a.raw == b.raw

    private fun matches(firstSerial: Long, secondSerial: Long, first: SleepPart?, second: SleepPart?) =
        firstSerial > afterFirst && secondSerial > afterSecond &&
            WeeklySleepSchedule.fromReadback(first, second)?.days == target?.days

    fun timeout(token: Long, firstSerial: Long, secondSerial: Long): Boolean {
        if (token != serial || state != State.WAITING_READBACK) return false
        baseline(maxOf(latestFirst, firstSerial), maxOf(latestSecond, secondSerial))
        state = State.UNKNOWN
        return true
    }

    fun disconnected(firstSerial: Long, secondSerial: Long) {
        if (state == State.WRITING || state == State.WAITING_READBACK) {
            baseline(maxOf(latestFirst, firstSerial), maxOf(latestSecond, secondSerial))
            state = State.UNKNOWN
        }
    }
}
