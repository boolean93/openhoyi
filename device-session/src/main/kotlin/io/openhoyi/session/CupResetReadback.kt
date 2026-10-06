package io.openhoyi.session

/** Readback correlation and durable clear; host renders ordered feedback after updating safety. */
object CupResetReadback {
    enum class Event { CONFIRMED, RECONCILED, CLEAR_FAILED }
    sealed interface Sample {
        data class Settings(val serial:Long,val count:Int):Sample
        data class Idle(val serial:Long,val count:Int):Sample
    }
    fun observe(tracker:CupResetTracker,recovery:MachineWriteRecoveryState,sample:Sample):List<Event> {
        val previous=tracker.state
        val confirmed=when(sample) {
            is Sample.Settings -> tracker.observeSettings(sample.serial,sample.count)
            is Sample.Idle -> tracker.observeIdle(sample.serial,sample.count)
        }
        val clearFailed=tracker.state==CupResetTracker.State.CONFIRMED &&
            recovery.kind==MachineWriteRecoveryState.Kind.CUP_RESET && !recovery.clear()
        // Preserve confirmation diagnostics, but keep a failed durable clear as final feedback.
        return buildList {
            if(confirmed)add(Event.CONFIRMED)
            else if(previous==CupResetTracker.State.UNKNOWN && tracker.state==CupResetTracker.State.RECONCILED)
                add(Event.RECONCILED)
            if(clearFailed)add(Event.CLEAR_FAILED)
        }
    }
}
