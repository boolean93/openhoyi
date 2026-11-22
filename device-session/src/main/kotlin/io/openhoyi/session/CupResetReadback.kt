package io.openhoyi.session

/** Readback correlation and durable clear; host renders ordered feedback after updating safety. */
object CupResetReadback {
    enum class Event { CONFIRMED, RECONCILED, CLEAR_FAILED }
    sealed interface Sample {
        data class Settings(val serial:Long,val count:Int):Sample
        data class Idle(val serial:Long,val count:Int):Sample
    }
    fun observe(tracker:CupResetTracker,recovery:MachineWriteRecoveryState,sample:Sample):List<Event> =
        observe(tracker,recovery,sample,recovery::clear)
    fun observe(tracker:CupResetTracker,recovery:MachineWriteRecoveryState,sample:Sample,
        clearRecord:()->Boolean):List<Event> {
        return reduce(tracker,recovery,clearRecord) {
            when(sample) {
                is Sample.Settings -> tracker.observeSettings(sample.serial,sample.count)
                is Sample.Idle -> tracker.observeIdle(sample.serial,sample.count)
            }
        }
    }

    fun observePair(tracker:CupResetTracker,recovery:MachineWriteRecoveryState,
        settings:Sample.Settings,idle:Sample.Idle,clearRecord:()->Boolean):List<Event> =
        reduce(tracker,recovery,clearRecord) {
            tracker.observePair(settings.serial,settings.count,idle.serial,idle.count)
        }

    private fun reduce(tracker:CupResetTracker,recovery:MachineWriteRecoveryState,clearRecord:()->Boolean,
        observe:()->Boolean):List<Event> {
        val previous=tracker.state
        val confirmed=observe()
        val clearFailed=tracker.state==CupResetTracker.State.CONFIRMED &&
            recovery.kind==MachineWriteRecoveryState.Kind.CUP_RESET && !runCatching(clearRecord).getOrDefault(false)
        // Preserve confirmation diagnostics, but keep a failed durable clear as final feedback.
        return buildList {
            if(confirmed)add(Event.CONFIRMED)
            else if(previous==CupResetTracker.State.UNKNOWN && tracker.state==CupResetTracker.State.RECONCILED)
                add(Event.RECONCILED)
            if(clearFailed)add(Event.CLEAR_FAILED)
        }
    }
}
