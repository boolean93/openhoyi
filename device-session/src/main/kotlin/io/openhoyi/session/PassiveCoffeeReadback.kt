package io.openhoyi.session

import io.openhoyi.protocol.ExtractionTelemetry
import io.openhoyi.protocol.IdleTelemetry
import io.openhoyi.protocol.WeeklySleepSchedule

/** Read-only authenticated ingress. Retained displays are not a receipt for a write. */
class NativeCoffeeSample<T> internal constructor(
    val cursor:CoffeeReadbackCursor,
    val capturedAtMs:Long,
    val value:T,
)

class PassiveCoffeeReadback private constructor(private val cut:CoffeeReadbackCursor, private val afterMs:Long) {
    private fun follows(cursor:CoffeeReadbackCursor, at:Long):Boolean =
        cursor.source === cut.source && cursor.sequence > cut.sequence && at > afterMs

    fun idle(sample:NativeCoffeeSample<IdleTelemetry>?, observed:IdleTelemetry, now:Long):Boolean =
        sample!=null && sample.value === observed && follows(sample.cursor,sample.capturedAtMs) &&
            TelemetryFreshness.isFresh(sample.capturedAtMs,now)

    fun machineWaiting(sample:NativeCoffeeSample<ExtractionTelemetry>?, observed:ExtractionTelemetry, now:Long):Boolean =
        sample!=null && sample.value === observed && follows(sample.cursor,sample.capturedAtMs) &&
            TelemetryFreshness.isFresh(sample.capturedAtMs,now) && observed.brewWait && !observed.valveOpen

    fun cupPair(settings:CoffeeReadbackFrame.OrdinarySettings?, idle:NativeCoffeeSample<IdleTelemetry>?, now:Long):Boolean =
        settings!=null && idle!=null && follows(settings.cursor,settings.receivedAtMs) &&
            follows(idle.cursor,idle.capturedAtMs) && SettingsFreshness.isFresh(settings.receivedAtMs,now) &&
            TelemetryFreshness.isFresh(idle.capturedAtMs,now)

    fun weeklyPair(first:CoffeeReadbackFrame.WeeklyPart?, second:CoffeeReadbackFrame.WeeklyPart?, now:Long):WeeklySleepSchedule? =
        if (first!=null && second!=null && follows(first.cursor,first.receivedAtMs) &&
            follows(second.cursor,second.receivedAtMs) && first.cursor.sequence < second.cursor.sequence &&
            SleepScheduleFreshness.isFresh(first.value,first.receivedAtMs,second.value,second.receivedAtMs,now))
            WeeklySleepSchedule.fromReadback(first.value,second.value) else null

    companion object {
        /** Called at the actual logical transport result, not when the view accepts a tap. */
        fun after(requestSource:CoffeeReadbackSource?, current:CoffeeReadbackCursor?, afterMs:Long):PassiveCoffeeReadback? =
            if (requestSource!=null && current?.source === requestSource && afterMs>=0)
                PassiveCoffeeReadback(current,afterMs) else null
    }
}
