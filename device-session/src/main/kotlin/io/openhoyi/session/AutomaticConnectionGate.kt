package io.openhoyi.session

/** Additional host preflight shared by scan-start and the last-moment scan-result callback.
 * Coffee target/recovery matching and credential authorization still run at dispatch in the host. */
object AutomaticConnectionGate {
    fun allows(role:DeviceRole,enabled:Boolean,paused:Boolean,visible:Boolean,selecting:Boolean,
        manualShot:Boolean,shot:ExtractionState,shotRecoveryPending:Boolean,settingsRecoveryPending:Boolean,
        ordinaryWorkBusy:Boolean):Boolean {
        if(!enabled || paused || selecting || manualShot || ordinaryWorkBusy)return false
        return if(role==DeviceRole.COFFEE)
            (visible || DeviceConnectionGate.unsettled(shot)) && shot !in setOf(
                ExtractionState.STARTING,ExtractionState.RUNNING,ExtractionState.STOP_REQUESTED)
        else visible && !DeviceConnectionGate.unsettled(shot) && !shotRecoveryPending && !settingsRecoveryPending
    }
}
