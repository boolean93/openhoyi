package io.openhoyi.session

import io.openhoyi.protocol.MachineSettingChange
import io.openhoyi.protocol.SleepPart
import io.openhoyi.protocol.WeeklySleepSchedule

/** Registers a durable write intent. Has no transport or automatic retry capability. */
object MachineWriteRegistration {
    sealed interface Result {
        data class Registered(val token: Long) : Result
        data object Busy : Result
        data object RecordFailed : Result
    }
    sealed interface Request {
        data class Setting(val tracker: SettingsWriteTracker, val change: MachineSettingChange,
            val sampleSerial: Long) : Request
        data class CupReset(val tracker: CupResetTracker, val expectedCount: Int,
            val settingsSerial: Long, val idleSerial: Long) : Request
        data class Schedule(val tracker: SleepScheduleWriteTracker, val target: WeeklySleepSchedule,
            val firstSerial: Long, val secondSerial: Long, val first: SleepPart?, val second: SleepPart?) : Request
        data class Sleep(val tracker: SleepNowTracker, val sampleSerial: Long) : Request
        data class BrewWait(val tracker: BrewPreparation, val profileId: String, val temperatureC: Int) : Request
    }
    fun begin(recovery: MachineWriteRecoveryState, address: String?, request: Request): Result {
        val (kind, token) = when (request) {
            is Request.Setting -> MachineWriteRecoveryState.Kind.SETTING to request.tracker.begin(request.change)
            is Request.CupReset -> MachineWriteRecoveryState.Kind.CUP_RESET to request.tracker.begin(request.expectedCount)
            is Request.Schedule -> MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE to
                request.tracker.begin(request.target, request.firstSerial, request.secondSerial)
            is Request.Sleep -> MachineWriteRecoveryState.Kind.SLEEP_NOW to request.tracker.begin()
            is Request.BrewWait -> MachineWriteRecoveryState.Kind.BREW_WAIT to
                request.tracker.begin(request.profileId, request.temperatureC)
        }
        if (token == null) return Result.Busy
        if (recovery.arm(kind, address)) return Result.Registered(token)
        val failure = OperationResult.Failed("safety record unavailable")
        when (request) {
            is Request.Setting -> request.tracker.written(token, failure, request.sampleSerial)
            is Request.CupReset -> request.tracker.written(token, failure, request.settingsSerial, request.idleSerial)
            is Request.Schedule -> request.tracker.written(token, failure, request.firstSerial,
                request.secondSerial, request.first, request.second)
            is Request.Sleep -> request.tracker.written(token, failure, request.sampleSerial)
            is Request.BrewWait -> request.tracker.consumed()
        }
        return Result.RecordFailed
    }
}
