package io.openhoyi.session

/** Recovery may reconnect only the machine that owns an unsettled app shot. */
object DeviceConnectionGate {
    fun unsettled(state:ExtractionState):Boolean = state in setOf(
        ExtractionState.STARTING,ExtractionState.RUNNING,ExtractionState.STOP_REQUESTED,
        ExtractionState.OUTCOME_UNKNOWN)
    fun mayConnectCoffee(state:ExtractionState,originalAddress:String?,requestedAddress:String):Boolean =
        if(state==ExtractionState.OUTCOME_UNKNOWN)
            !originalAddress.isNullOrBlank() && originalAddress.equals(requestedAddress,ignoreCase=true)
        else !unsettled(state)
    fun mayChangeScale(state:ExtractionState):Boolean = !unsettled(state)
    fun mayDisconnect(state:ExtractionState):Boolean = !unsettled(state)
}
