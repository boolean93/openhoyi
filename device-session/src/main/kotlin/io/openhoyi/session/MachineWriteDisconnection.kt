package io.openhoyi.session

/** Owner-thread invalidation of machine-write evidence; no transport or persistence. */
class MachineWriteDisconnection(private val settings: SettingsWriteTracker,
    private val cups: CupResetTracker, private val schedule: SleepScheduleWriteTracker,
    private val sleep: SleepNowTracker, private val brew: BrewPreparation) {
    data class Samples(val settings:Long,val cupSettings:Long,val cupIdle:Long,
        val sleepFirst:Long,val sleepSecond:Long,val sleep:Long)
    fun apply(samples:Samples) {
        settings.disconnected(samples.settings)
        cups.disconnected(samples.cupSettings,samples.cupIdle)
        schedule.disconnected(samples.sleepFirst,samples.sleepSecond)
        sleep.disconnected(samples.sleep)
        brew.disconnected()
    }
}
