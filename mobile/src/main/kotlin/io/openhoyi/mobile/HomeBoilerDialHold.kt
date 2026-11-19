package io.openhoyi.mobile

import io.openhoyi.protocol.HoyiMessage
import io.openhoyi.protocol.IdleTelemetry

/** Short presentation-only bridge over a local boiler write's telemetry gap. */
internal class HomeBoilerDialHold {
    data class Display(val frame: HoyiMessage?, val brewTarget: Int?, val steamTarget: Int?)
    private data class Sample(val owner: Any, val address: String, val at: Long, val display: Display)
    private var sample: Sample? = null

    fun resolve(owner: Any?, address: String?, fresh: HoyiMessage?, at: Long?,
        brewTarget: Int?, steamTarget: Int?, now: Long, hold: Boolean): Display {
        val ordinary = Display(fresh, brewTarget, steamTarget)
        val cached = sample?.takeIf { it.owner === owner && it.address.equals(address, ignoreCase = true) }
        val retained = cached?.takeIf { hold && now - it.at in 0..5_000L }?.display
        val shown = if (retained != null && (fresh == null || fresh is IdleTelemetry))
            Display(fresh ?: retained.frame, brewTarget ?: retained.brewTarget, steamTarget ?: retained.steamTarget)
        else ordinary
        if (owner == null || address == null) {
            sample = null
        } else if (fresh is IdleTelemetry && at != null) {
            sample = Sample(owner, address, at, ordinary)
        } else if (fresh == null && retained != null) {
            return shown
        } else {
            sample = null
        }
        return shown
    }
}
