package io.openhoyi.mobile

import android.content.Context
import io.openhoyi.session.DeviceState

/** User-facing connection state shared by device screens. */
internal object DeviceStatusText {
    fun label(context: Context, state: DeviceState): String = context.getString(resource(state))

    fun resource(state: DeviceState): Int = when (state) {
        DeviceState.DISCONNECTED -> R.string.device_state_disconnected
        DeviceState.CONNECTING -> R.string.device_state_connecting
        DeviceState.DISCOVERING -> R.string.device_state_discovering
        DeviceState.SUBSCRIBING -> R.string.device_state_subscribing
        DeviceState.INITIALIZING -> R.string.device_state_initializing
        DeviceState.SYNCHRONIZING -> R.string.device_state_synchronizing
        DeviceState.READY -> R.string.device_state_ready
        DeviceState.UNSUPPORTED -> R.string.device_state_unsupported
        DeviceState.FAILED -> R.string.device_state_failed
    }
}
