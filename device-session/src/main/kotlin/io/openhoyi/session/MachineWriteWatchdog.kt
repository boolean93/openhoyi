package io.openhoyi.session

/** Owner-thread readback deadline coordination; host provides scheduling and live serials. */
class MachineWriteWatchdog(private val schedule: (Long, () -> Unit) -> Unit,
    private val currentSerials: () -> Serials) {
    data class Serials(val settings: Long, val cupSettings: Long, val cupIdle: Long,
        val sleepFirst: Long, val sleepSecond: Long, val sleep: Long)
    sealed interface Request {
        data class Setting(val tracker: SettingsWriteTracker) : Request
        data class CupReset(val tracker: CupResetTracker) : Request
        data class Schedule(val tracker: SleepScheduleWriteTracker) : Request
        data class Sleep(val tracker: SleepNowTracker) : Request
    }
    fun await(token: Long, request: Request, onExpired: (Serials) -> Unit) {
        val delay = when (request) {
            is Request.Setting -> 6000L
            is Request.CupReset -> 12000L
            is Request.Schedule -> 8000L
            is Request.Sleep -> 12000L
        }
        schedule(delay) {
            val serials = currentSerials()
            val expired = when (request) {
                is Request.Setting -> request.tracker.timeout(token,serials.settings)
                is Request.CupReset -> request.tracker.timeout(token,serials.cupSettings,serials.cupIdle)
                is Request.Schedule -> request.tracker.timeout(token,serials.sleepFirst,serials.sleepSecond)
                is Request.Sleep -> request.tracker.timeout(token,serials.sleep)
            }
            if (expired) onExpired(serials)
        }
    }
}
