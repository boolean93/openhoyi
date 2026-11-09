package io.openhoyi.mobile

import io.openhoyi.session.CoffeeFirmware
import io.openhoyi.session.CoffeeSessionFailure
import io.openhoyi.session.DeviceState

class FirmwarePresentation(private val resolve: (Int, Array<out Any>) -> String) {
    constructor(context: android.content.Context) : this({ id, args -> context.getString(id, *args) })

    private fun text(id: Int, vararg args: Any): String = resolve(id, args)
    fun describe(firmware: CoffeeFirmware?, state: DeviceState, mock: Boolean,
        failure: CoffeeSessionFailure? = null): String {
        if(state==DeviceState.FAILED && failure==CoffeeSessionFailure.PROTOCOL_TIMEOUT)
            return text(R.string.firmware_protocol_timeout)
        if(firmware==null || state !in setOf(DeviceState.READY,DeviceState.UNSUPPORTED))
            return text(R.string.home_firmware_initial)
        if(mock) return text(R.string.firmware_mock, firmware.toString())
        return if(state==DeviceState.READY) text(R.string.firmware_verified, firmware.toString())
            else text(R.string.firmware_unsupported, firmware.toString())
    }
}
