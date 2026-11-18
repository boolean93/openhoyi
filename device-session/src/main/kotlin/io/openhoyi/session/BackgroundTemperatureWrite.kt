package io.openhoyi.session

import io.openhoyi.protocol.MachineSettingChange
import io.openhoyi.protocol.Settings

/** A local, non-blocking temperature confirmation journal. Never gates machine connectivity.
 * The owner still applies device identity, fresh telemetry, alarms and control interlocks. */
class BackgroundTemperatureWrite(storage: MachineWriteRecoveryState.Storage) {
    val tracker = SettingsWriteTracker()
    private val recovery = MachineWriteRecoveryState(storage)
    private var owner: MachineWriteRecoveryState.Ownership? = null
    private var operationAddress: String? = null
    private var pairedAfterSerial: Long? = null
    private var priorPairedAfterSerial: Long? = null
    val state: SettingsWriteTracker.State get() = if (tracker.state == SettingsWriteTracker.State.IDLE && recovery.pending)
        SettingsWriteTracker.State.UNKNOWN else tracker.state
    val requiresExplicitRetry: Boolean get() = state in setOf(SettingsWriteTracker.State.WAITING_READBACK,SettingsWriteTracker.State.UNKNOWN)
    val busy: Boolean get() = state == SettingsWriteTracker.State.WRITING
    val change: MachineSettingChange? get() = tracker.change
    val pendingAfterRestart: Boolean get() = tracker.change == null && recovery.pending

    /** Shared standby frame carries two fields: don't reuse a companion from before this request. */
    fun companionAvailable(change: MachineSettingChange, sampleSerial: Long, address:String?=operationAddress): Boolean =
        operationAddress?.equals(address,ignoreCase=true)!=true ||
        !(change is MachineSettingChange.StandbyTemperature || change is MachineSettingChange.StandbyDelay) ||
            pairedAfterSerial?.let { sampleSerial > it } != false

    fun begin(change: MachineSettingChange, address: String, sampleSerial: Long): Long? {
        if (!SettingsReadbackPolicy.isTemperature(change) || busy || (operationAddress?.equals(address,ignoreCase=true)==true && !companionAvailable(change,sampleSerial))) return null
        if (!recovery.replaceSetting(recovery.captureOwnership(),address)) return null
        val sameDevice=operationAddress?.equals(address,ignoreCase=true)==true
        val token = requireNotNull(tracker.beginTemperature(change))
        owner = recovery.captureOwnership()
        operationAddress = address
        if(!sameDevice) pairedAfterSerial=null
        priorPairedAfterSerial=pairedAfterSerial
        if (change is MachineSettingChange.StandbyTemperature || change is MachineSettingChange.StandbyDelay)
            pairedAfterSerial = sampleSerial
        return token
    }
    fun permitsWrite(token:Long,address:String?):Boolean = tracker.permitsWrite(token) && owns(address)
    fun permitsRefresh(token:Long,address:String?):Boolean = tracker.permitsBoilerRefresh(token) && owns(address)
    private fun owns(address:String?):Boolean = recovery.owns(owner) && operationAddress?.equals(address,ignoreCase=true)==true
    fun written(token:Long,result:OperationResult,sampleSerial:Long):Boolean {
        if (!tracker.written(token,result,sampleSerial)) return false
        if (change is MachineSettingChange.StandbyTemperature || change is MachineSettingChange.StandbyDelay)
            pairedAfterSerial = if(result is OperationResult.Failed || result is OperationResult.Cancelled)
                priorPairedAfterSerial else sampleSerial
        if (result is OperationResult.Failed || result is OperationResult.Cancelled) recovery.clear(owner)
        return true
    }
    fun observe(address:String?,sampleSerial:Long,frame:Settings):SettingsWriteTracker.State? {
        if (!owns(address)) return null
        val before=tracker.state
        tracker.observe(sampleSerial,frame)
        if (tracker.state in setOf(SettingsWriteTracker.State.CONFIRMED,SettingsWriteTracker.State.RECONCILED))
            recovery.clear(owner)
        return tracker.state.takeIf { it != before }
    }
    fun disconnected(sampleSerial:Long) = tracker.disconnected(sampleSerial)
}
