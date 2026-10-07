package io.openhoyi.session

/** Caller ownership; DeviceSession retains protocol/freshness checks. */
object BrewWriteDispatchPermit {
    data class Context(val recovery:MachineWriteRecoveryState,val owner:MachineWriteRecoveryState.Ownership?,
        val originalAddress:String?,val currentAddress:String?,val sameHub:Boolean,val ready:Boolean,
        val manualShot:Boolean,val shotPending:Boolean,val shotActive:Boolean,val sameValidatedProfile:Boolean)
    fun allows(token:Long,preparation:BrewPreparation,targetC:Int,context:Context):Boolean {
        val original=context.originalAddress ?: return false
        return preparation.permitsWrite(token,targetC) && context.recovery.owns(context.owner) &&
            context.recovery.kind==MachineWriteRecoveryState.Kind.BREW_WAIT &&
            context.recovery.address==original.uppercase() &&
            context.currentAddress?.equals(original,ignoreCase=true)==true && context.sameHub && context.ready &&
            !context.manualShot && !context.shotPending && !context.shotActive &&
            (targetC==0 || context.sameValidatedProfile)
    }
}
