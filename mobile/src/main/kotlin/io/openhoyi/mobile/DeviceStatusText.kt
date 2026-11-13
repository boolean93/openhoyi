package io.openhoyi.mobile

import android.content.Context
import io.openhoyi.session.CoffeeSessionFailure
import io.openhoyi.session.DeviceState

/** User-facing connection state shared by device screens. */
internal object DeviceStatusText {
    /** Keep current state separate from the last successfully remembered identity. */
    fun settingsSummary(status: String, rememberedIdentity: String): String = "$status\n$rememberedIdentity"

    fun label(context: Context, state: DeviceState): String = context.getString(resource(state))

    /** Coffee status line; the failure reason only changes display text, never control eligibility. */
    fun coffeeLabel(context: Context, state: DeviceState, failure: CoffeeSessionFailure?): String =
        context.getString(coffeeResource(state, failure))

    internal fun coffeeResource(state: DeviceState, failure: CoffeeSessionFailure?): Int = when {
        state == DeviceState.FAILED && failure == CoffeeSessionFailure.PROTOCOL_TIMEOUT ->
            R.string.device_state_protocol_timeout
        state == DeviceState.FAILED && failure == CoffeeSessionFailure.AUTH ->
            R.string.device_state_auth_failed
        else -> resource(state)
    }

    fun resource(state: DeviceState): Int = when (state) {
        DeviceState.DISCONNECTED -> R.string.device_state_disconnected
        DeviceState.CONNECTING -> R.string.device_state_connecting
        DeviceState.DISCOVERING, DeviceState.SUBSCRIBING, DeviceState.INITIALIZING -> R.string.device_state_connecting
        DeviceState.SYNCHRONIZING -> R.string.device_state_synchronizing
        DeviceState.READY -> R.string.device_state_ready
        DeviceState.UNSUPPORTED -> R.string.device_state_unsupported
        DeviceState.FAILED -> R.string.device_state_failed
    }
}
