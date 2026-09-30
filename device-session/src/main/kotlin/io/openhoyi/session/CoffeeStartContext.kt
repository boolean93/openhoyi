package io.openhoyi.session

import io.openhoyi.protocol.StartParameters

/** Captured before tare; callers cannot replace it with newer machine settings. */
class CoffeeStartContext internal constructor(
    internal val generation:Long,
    internal val address:String?,
    internal val parameters:StartParameters,
    internal val settingsFlags:Int,
    internal val brewTemperatureC:Int,
    internal val compensationTenthsC:Int,
    internal val capturedAt:Long,
)
