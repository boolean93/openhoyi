package io.openhoyi.session

/** Explicit recovery acknowledgement; no transport, clock, Android or display dependencies. */
object MachineWriteAcknowledgement {
    enum class Result { WAITING, CLEAR_FAILED, ACKNOWLEDGED }
    sealed interface Request {
        data class CupReset(val evidence: MachineWriteRecoveryState.CupResetEvidence,
            val afterSettingsSerial: Long, val afterIdleSerial: Long) : Request
        data class Setting(val evidence: MachineWriteRecoveryState.SettingEvidence,
            val afterSettingsSerial: Long) : Request
        data class Schedule(val evidence: MachineWriteRecoveryState.ScheduleEvidence,
            val afterFirstSerial: Long, val afterSecondSerial: Long) : Request
        data class Sleep(val evidence: MachineWriteRecoveryState.SleepEvidence,
            val afterSleepSerial: Long) : Request
        data class BrewWait(val evidence: MachineWriteRecoveryState.BrewWaitEvidence,
            val afterIdleSerial: Long) : Request
    }

    fun acknowledge(recovery: MachineWriteRecoveryState, ready: Boolean, request: Request): Result {
        if (!ready) return Result.WAITING
        val eligible = when (request) {
            is Request.CupReset -> recovery.canClearCupReset(request.evidence,
                request.afterSettingsSerial, request.afterIdleSerial)
            is Request.Setting -> recovery.canClearSetting(request.evidence, request.afterSettingsSerial)
            is Request.Schedule -> recovery.canClearSchedule(request.evidence,
                request.afterFirstSerial, request.afterSecondSerial)
            is Request.Sleep -> recovery.canClearSleep(request.evidence, request.afterSleepSerial)
            is Request.BrewWait -> recovery.canClearBrewWait(request.evidence, request.afterIdleSerial)
        }
        if (!eligible) return Result.WAITING
        return if (recovery.clear()) Result.ACKNOWLEDGED else Result.CLEAR_FAILED
    }
}
