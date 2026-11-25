package io.openhoyi.mobile

import io.openhoyi.protocol.HoyiMessage
import io.openhoyi.protocol.IdleTelemetry
import io.openhoyi.protocol.Settings

/** Display-only continuity across an initiated boiler write, never input to a control gate. */
internal class HomeBoilerDialHold {
    data class Display(val frame: HoyiMessage?, val settings: Settings?) {
        val brewTarget get() = settings?.brewTemperatureC
        val steamTarget get() = settings?.steamTemperatureC
    }

    private class Gap(val frame: IdleTelemetry?, val frameAt: Long?, val settings: Settings?, val target: Boolean)

    private var identity: Any? = null
    private var lastFrame: IdleTelemetry? = null
    private var lastFrameAt: Long? = null
    private var lastSettings: Settings? = null
    private var gap: Gap? = null
    val targetDisplayGap: Boolean get() = gap?.target == true

    fun clear() {
        identity = null
        lastFrame = null
        lastFrameAt = null
        lastSettings = null
        gap = null
    }

    fun arm(device: Any?, target: Boolean = false) {
        if (device != null && identity == device) gap = Gap(lastFrame, lastFrameAt, lastSettings, target)
    }

    fun resolve(device: Any?, liveFrame: HoyiMessage?, frameAt: Long?, settings: Settings?,
        canHold: Boolean, active: Boolean, targetActive: Boolean = false): Display {
        if (identity != device) clear()
        val idle = liveFrame as? IdleTelemetry
        if (device == null || !canHold || liveFrame != null && (idle == null || idle.sleepStateRaw != 0)) {
            clear()
            return Display(liveFrame, settings)
        }
        identity = device
        if ((active || targetActive) && (gap == null || targetActive && !targetDisplayGap)) arm(device, targetActive)
        val waiting = gap
        if (!active && !targetActive && waiting != null && idle != null && settings != null &&
            (idle !== waiting.frame || frameAt != waiting.frameAt) && settings !== waiting.settings) gap = null

        // The two streams recover independently. A setting readback cannot clear a temperature
        // or pressure gap; equally, new telemetry cannot replace the last reported settings.
        if (idle != null || gap == null) {
            lastFrame = idle
            lastFrameAt = frameAt
        }
        if (settings != null || gap == null) lastSettings = settings
        return Display(lastFrame, lastSettings)
    }
}
