package io.openhoyi.session

/** Product ownership only; wire/freshness checks stay in DeviceSession and ExtractionController. */
object ShotDispatchPermit {
    data class Context(val originalAddress:String?,val currentAddress:String?,val sameHub:Boolean,
        val ready:Boolean,val sameValidatedProfile:Boolean,val manualShotActive:Boolean,
        val ordinaryWriteBusy:Boolean,val shotPending:Boolean,val shotAddress:String?,
        val originalMachineIntent:MachineWriteRecoveryState.Record,
        val currentMachineIntent:MachineWriteRecoveryState.Record)
    fun allows(context:Context):Boolean {
        val original=context.originalAddress ?: return false
        val intent=context.originalMachineIntent
        val ownsIntent=if(intent.kind==null) intent.address==null else
            intent.kind==MachineWriteRecoveryState.Kind.BREW_WAIT && intent.address==original.uppercase()
        return ownsIntent && context.currentMachineIntent==intent && context.sameHub && context.ready &&
            context.sameValidatedProfile && !context.manualShotActive && !context.ordinaryWriteBusy &&
            context.shotPending && context.shotAddress==original.uppercase() &&
            context.currentAddress?.equals(original,ignoreCase=true)==true
    }
}
