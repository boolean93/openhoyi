package io.openhoyi.session

/** Timing defaults require real tablet tuning; never a connection/command authorization. */
object AutoConnectTiming {
    const val NEW_SESSION_MS = 30 * 60_000L
    const val SCAN_MS = 5_000L
    const val FAST_PHASE_MS = 60_000L
    const val FAST_INTERVAL_MS = 10_000L
    const val WAIT_INTERVAL_MS = 30_000L
}

/** Persist the last interactive heartbeat too: process death must not erase a recent pause.
 * Same-boot elapsed time is authoritative. Reboot or invalid clocks starts a new session,
 * retaining permanent preferences and credential blocks outside this object. */
class DeviceUseSession(var saved: Saved? = null) {
    data class Stamp(val elapsed: Long, val wall: Long, val boot: Int)
    data class Saved(val id: Long, val stamp: Stamp, val coffeePaused: Boolean, val scalePaused: Boolean)
    private var interactive = false
    fun visibility(visible: Boolean, now: Stamp): Boolean {
        val old = saved
        val gap = old?.let {
            if (it.stamp.boot == now.boot && now.elapsed >= it.stamp.elapsed)
                now.elapsed - it.stamp.elapsed else null
        }
        val fresh = visible && !interactive && (old == null || gap == null || gap >= AutoConnectTiming.NEW_SESSION_MS)
        saved = if (fresh) Saved((old?.id ?: 0) + 1, now, false, false)
            else old?.copy(stamp = if (interactive || visible) now else old.stamp)
        interactive = visible
        return fresh
    }
    fun pause(role: DeviceRole, paused: Boolean) {
        saved = saved?.let { if (role == DeviceRole.COFFEE) it.copy(coffeePaused = paused) else it.copy(scalePaused = paused) }
    }
}
