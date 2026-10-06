package io.openhoyi.session

import io.openhoyi.protocol.SleepPart

/** Applies transport callbacks only. No transport, storage, timer or automatic retry. */
object MachineWriteResult {
    enum class Outcome { IGNORED, WAITING, CONFIRMED, FAILED, UNKNOWN }
    sealed interface Request {
        data class Setting(val tracker: SettingsWriteTracker, val sampleSerial: Long) : Request
        data class CupReset(val tracker: CupResetTracker, val settingsSerial: Long, val idleSerial: Long) : Request
        data class Schedule(val tracker: SleepScheduleWriteTracker, val firstSerial: Long,
            val secondSerial: Long, val first: SleepPart?, val second: SleepPart?) : Request
        data class Sleep(val tracker: SleepNowTracker, val sampleSerial: Long) : Request
        data class BrewWait(val tracker: BrewPreparation, val sampleSerial: Long) : Request
    }
    fun apply(token: Long, result: OperationResult, request: Request): Outcome = when (request) {
        is Request.Setting -> if (!request.tracker.written(token,result,request.sampleSerial)) Outcome.IGNORED else when (request.tracker.state) {
            SettingsWriteTracker.State.WAITING_READBACK -> Outcome.WAITING
            SettingsWriteTracker.State.FAILED -> Outcome.FAILED
            SettingsWriteTracker.State.UNKNOWN -> Outcome.UNKNOWN
            else -> Outcome.IGNORED
        }
        is Request.CupReset -> if (!request.tracker.written(token,result,request.settingsSerial,request.idleSerial)) Outcome.IGNORED else when (request.tracker.state) {
            CupResetTracker.State.WAITING_ZERO -> Outcome.WAITING
            CupResetTracker.State.FAILED -> Outcome.FAILED
            CupResetTracker.State.UNKNOWN -> Outcome.UNKNOWN
            else -> Outcome.IGNORED
        }
        is Request.Schedule -> if (!request.tracker.written(token,result,request.firstSerial,request.secondSerial,request.first,request.second)) Outcome.IGNORED else when (request.tracker.state) {
            SleepScheduleWriteTracker.State.WAITING_READBACK -> Outcome.WAITING
            SleepScheduleWriteTracker.State.CONFIRMED -> Outcome.CONFIRMED
            SleepScheduleWriteTracker.State.FAILED -> Outcome.FAILED
            SleepScheduleWriteTracker.State.UNKNOWN -> Outcome.UNKNOWN
            else -> Outcome.IGNORED
        }
        is Request.Sleep -> if (!request.tracker.written(token,result,request.sampleSerial)) Outcome.IGNORED else when (request.tracker.state) {
            SleepNowTracker.State.WAITING_ASLEEP -> Outcome.WAITING
            SleepNowTracker.State.FAILED -> Outcome.FAILED
            SleepNowTracker.State.UNKNOWN -> Outcome.UNKNOWN
            else -> Outcome.IGNORED
        }
        is Request.BrewWait -> if (!request.tracker.written(token,result,request.sampleSerial)) Outcome.IGNORED else when (request.tracker.state) {
            BrewPreparation.State.WAITING_TEMP -> Outcome.WAITING
            BrewPreparation.State.FAILED -> Outcome.FAILED
            BrewPreparation.State.UNKNOWN -> Outcome.UNKNOWN
            else -> Outcome.IGNORED
        }
    }
}
