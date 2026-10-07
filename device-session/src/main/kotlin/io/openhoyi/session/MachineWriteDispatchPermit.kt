package io.openhoyi.session

/** Pure caller policy; transport freshness remains in DeviceSession. */
object MachineWriteDispatchPermit {
    sealed interface Request {
        data class Setting(val tracker:SettingsWriteTracker):Request
        data class CupReset(val tracker:CupResetTracker):Request
        data class Sleep(val tracker:SleepNowTracker):Request
        data class Schedule(val tracker:SleepScheduleWriteTracker):Request
    }
    data class Context(val recovery:MachineWriteRecoveryState,val originalAddress:String?,val currentAddress:String?,
        val manualShotActive:Boolean,val shotRecoveryPending:Boolean,val shotActive:Boolean,val preparationActive:Boolean)
    fun allows(token:Long,request:Request,context:Context):Boolean {
        val kind=when(request) {
            is Request.Setting->MachineWriteRecoveryState.Kind.SETTING
            is Request.CupReset->MachineWriteRecoveryState.Kind.CUP_RESET
            is Request.Sleep->MachineWriteRecoveryState.Kind.SLEEP_NOW
            is Request.Schedule->MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE
        }
        val ownsWrite=when(request) {
            is Request.Setting->request.tracker.permitsWrite(token)
            is Request.CupReset->request.tracker.permitsWrite(token)
            is Request.Sleep->request.tracker.permitsWrite(token)
            is Request.Schedule->request.tracker.permitsWrite(token)
        }
        val original=context.originalAddress ?: return false
        // matchesDevice intentionally tolerates corrupt legacy identity for read-only recovery;
        // dispatch must require the exact original durable identity instead.
        return ownsWrite && context.recovery.kind==kind && context.recovery.address==original.uppercase() &&
            context.currentAddress?.equals(original,ignoreCase=true)==true &&
            !context.manualShotActive && !context.shotRecoveryPending && !context.shotActive && !context.preparationActive
    }
}
