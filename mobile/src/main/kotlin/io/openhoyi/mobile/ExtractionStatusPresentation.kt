package io.openhoyi.mobile

import io.openhoyi.session.BrewPreparation
import io.openhoyi.session.CoffeeAlarmPolicy
import io.openhoyi.session.ExtractionState

/** Presentation only. Never grants control, clears recovery, or changes machine state. */
internal object ExtractionStatusPresentation {
    enum class Severity { CRITICAL, ADVICE, PROGRESS }
    enum class Kind { GUIDANCE, PREPARATION, ALARM, MACHINE_SAFETY, MANUAL_SAFETY, WRITE, NOTIFICATION, MACHINE_DATA, SCALE_DATA }
    data class Signal(val kind: Kind, val severity: Severity, val identity: String = kind.name)
    data class Facts(
        val state: ExtractionState = ExtractionState.IDLE,
        val active: Boolean = false,
        val serviceReady: Boolean = true,
        val startBlocked: Boolean = false,
        val manualShot: Boolean = false,
        val scalePreflight: Boolean = false,
        val preparation: BrewPreparation.State = BrewPreparation.State.IDLE,
        val preparationNeeded: Boolean = false,
        val alarmBits: Int? = 0,
        val alarmAt: Long? = 0,
        val now: Long = 0,
        val machineSafety: Boolean = false,
        val manualSafety: Boolean = false,
        val writePending: Boolean = false,
        val writeUnknown: Boolean = false,
        val notificationsBlocked: Boolean = false,
        val machineFresh: Boolean = true,
        val scaleExpected: Boolean = false,
        val scaleFresh: Boolean = true,
    )
    fun signals(f: Facts): List<Signal> = buildList {
        if (!f.serviceReady || f.manualShot || f.scalePreflight ||
            f.state in setOf(ExtractionState.STARTING,ExtractionState.STOP_REQUESTED,ExtractionState.OUTCOME_UNKNOWN) ||
            f.state == ExtractionState.IDLE && f.startBlocked)
            add(Signal(Kind.GUIDANCE,if(f.state==ExtractionState.OUTCOME_UNKNOWN) Severity.CRITICAL else Severity.PROGRESS,"guidance:${f.state}:${f.serviceReady}:${f.startBlocked}:${f.manualShot}:${f.scalePreflight}"))
        if(f.preparationNeeded) add(Signal(Kind.PREPARATION,when(f.preparation) {
            BrewPreparation.State.UNKNOWN -> Severity.CRITICAL
            BrewPreparation.State.FAILED, BrewPreparation.State.IDLE, BrewPreparation.State.READY -> Severity.ADVICE
            else -> Severity.PROGRESS
        },"preparation:${f.preparation}"))
        val fresh=f.alarmAt?.let { it>=0 && it<=f.now && f.now-it<=1500 } == true
        if(!fresh || f.alarmBits!=0) add(Signal(Kind.ALARM,
            if(f.alarmBits?.let { CoffeeAlarmPolicy.firstBlockingBit(it) }!=null || (!fresh || f.alarmBits==null) && f.active) Severity.CRITICAL else Severity.ADVICE,
            "alarm:${f.alarmBits}:$fresh"))
        if(f.machineSafety) add(Signal(Kind.MACHINE_SAFETY,Severity.CRITICAL))
        if(f.manualSafety) add(Signal(Kind.MANUAL_SAFETY,Severity.CRITICAL))
        if(f.writePending) add(Signal(Kind.WRITE,if(f.writeUnknown) Severity.CRITICAL else Severity.PROGRESS,"write:${f.writeUnknown}"))
        if(f.notificationsBlocked) add(Signal(Kind.NOTIFICATION,Severity.ADVICE))
        if(!f.machineFresh) add(Signal(Kind.MACHINE_DATA,if(f.active) Severity.CRITICAL else Severity.ADVICE))
        if(f.scaleExpected && !f.scaleFresh) add(Signal(Kind.SCALE_DATA,if(f.active) Severity.CRITICAL else Severity.ADVICE))
    }.sortedBy { it.severity.ordinal }

    /** Render polling must not repeat announcements or ordinary completion feedback. */
    class Transitions {
        private var state: ExtractionState? = null
        private var critical = ""
        data class Feedback(val announceCritical: Boolean,val completed: Boolean)
        fun observe(next: ExtractionState, signals: List<Signal>): Feedback {
            val identity=signals.filter { it.severity==Severity.CRITICAL }.joinToString("|") { it.identity }
            val feedback=Feedback(identity.isNotEmpty() && identity!=critical,state!=null && state!=next && next==ExtractionState.ENDED_OBSERVED)
            state=next; critical=identity
            return feedback
        }
    }
}
