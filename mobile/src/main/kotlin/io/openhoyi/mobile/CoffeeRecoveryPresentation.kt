package io.openhoyi.mobile

import io.openhoyi.session.DeviceState

/** Display-only episode: retries cannot hide a long wait by resetting the clock. */
internal class CoffeeRecoveryPresentation {
    private var startedAt:Long?=null
    private var failures=0
    fun reset(){startedAt=null;failures=0}
    fun observe(state:DeviceState,now:Long){
        if(state==DeviceState.READY){reset();return}
        if(TaskShellDeviceStatus.connecting(state) && startedAt==null)startedAt=now
        if(state==DeviceState.FAILED)failures++
    }
    fun hint(state:DeviceState,now:Long):Int? {
        if(state==DeviceState.READY || state==DeviceState.UNSUPPORTED)return null
        val delayed=startedAt?.let {now>=it && now-it>=8_000}==true || failures>=2
        return when {
            delayed && state==DeviceState.SYNCHRONIZING -> R.string.coffee_connection_no_state_hint
            delayed -> R.string.coffee_connection_recovery_hint
            state==DeviceState.FAILED -> R.string.coffee_connection_retry_hint
            else -> null
        }
    }
}
