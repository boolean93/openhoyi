package io.openhoyi.session

/** Product authorization for a manual tare still waiting in the scale queue. */
object StandaloneTareDispatchPermit {
    fun allows(originalAddress:String?,currentAddress:String?,sameHub:Boolean,ready:Boolean,
        manualShot:Boolean,shotState:ExtractionState):Boolean = !originalAddress.isNullOrBlank() && originalAddress.equals(currentAddress,ignoreCase=true) &&
        sameHub && ready && !manualShot && DeviceConnectionGate.mayChangeScale(shotState)
}
