package io.openhoyi.session

import io.openhoyi.protocol.MachineSettingChange
import io.openhoyi.protocol.Settings
import io.openhoyi.protocol.SleepPart
import io.openhoyi.protocol.WeeklySleepSchedule

/** Opaque connection identity; even identical address + generation in another hub do not match. */
class CoffeeReadbackSource internal constructor(val address: String, val generation: Long)
class CoffeeReadbackCursor internal constructor(val source: CoffeeReadbackSource, val sequence: Long)

sealed interface CoffeeReadbackFrame {
    val cursor: CoffeeReadbackCursor
    val receivedAtMs: Long
    class OrdinarySettings internal constructor(
        override val cursor: CoffeeReadbackCursor, override val receivedAtMs: Long, val value: Settings,
    ) : CoffeeReadbackFrame
    class WeeklyPart internal constructor(
        override val cursor: CoffeeReadbackCursor, override val receivedAtMs: Long, val value: SleepPart,
    ) : CoffeeReadbackFrame
}

/** Read-only evidence, NOT a transaction ID or evidence of command causality on the wire. */
object CoffeeReadbackEvidence {
    private fun eligible(baseline: CoffeeReadbackCursor, current: CoffeeReadbackCursor?, frame: CoffeeReadbackFrame): Boolean =
        current != null && baseline.source === current.source && frame.cursor.source === current.source &&
            frame.cursor.sequence > baseline.sequence && frame.cursor.sequence == current.sequence

    /** Baseline must be taken after transport success. An already-matching target is not a change. */
    class Boiler private constructor(private val target: MachineSettingChange, private val baseline: CoffeeReadbackCursor) {
        fun matches(frame: CoffeeReadbackFrame, current: CoffeeReadbackCursor?, nowMs: Long): Boolean =
            frame is CoffeeReadbackFrame.OrdinarySettings && eligible(baseline, current, frame) &&
                SettingsFreshness.isFresh(frame.receivedAtMs, nowMs) && target.matches(frame.value)

        companion object {
            /** Original observation must be source-bound and still represent the pre-dispatch state. */
            fun start(target: MachineSettingChange, original: CoffeeReadbackFrame.OrdinarySettings,
                beforeDispatch: CoffeeReadbackCursor, afterWrite: CoffeeReadbackCursor, dispatchedAtMs: Long): Boiler? {
                if (target !is MachineSettingChange.BrewHeating && target !is MachineSettingChange.SteamHeating) return null
                if (original.cursor.source !== beforeDispatch.source || beforeDispatch.source !== afterWrite.source ||
                    original.cursor.sequence != beforeDispatch.sequence || beforeDispatch.sequence > afterWrite.sequence ||
                    !SettingsFreshness.isFresh(original.receivedAtMs, dispatchedAtMs) || target.matches(original.value)) return null
                return Boiler(target, afterWrite)
            }
        }
    }

    /** Match an ordered, post-write pair. A new first half discards the previous candidate pair. */
    class Weekly(private val target: WeeklySleepSchedule, private val baseline: CoffeeReadbackCursor) {
        private var first: CoffeeReadbackFrame.WeeklyPart? = null
        private var lastSequence = baseline.sequence

        fun observe(frame: CoffeeReadbackFrame, current: CoffeeReadbackCursor?, nowMs: Long): Boolean {
            if (current?.source !== baseline.source) { first = null; return false }
            if (frame !is CoffeeReadbackFrame.WeeklyPart || !eligible(baseline, current, frame) ||
                frame.cursor.sequence <= lastSequence || !SettingsFreshness.isFresh(frame.receivedAtMs, nowMs)) return false
            lastSequence = frame.cursor.sequence
            if (frame.value.firstDaySundayIndex == 0) {
                first = frame
                return false
            }
            val beginning = first ?: return false
            first = null
            return SleepScheduleFreshness.isFresh(beginning.value, beginning.receivedAtMs, frame.value, frame.receivedAtMs, nowMs) &&
                WeeklySleepSchedule.fromReadback(beginning.value, frame.value)?.days == target.days
        }
    }
}
