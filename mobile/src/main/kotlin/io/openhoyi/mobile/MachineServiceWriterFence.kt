package io.openhoyi.mobile

/** One service instance's transport lifetime. A failed close poisons it until process restart. */
internal class MachineServiceWriterFence {
    private enum class State { OPEN, DRAINING, POISONED, RETIRED }
    private var state=State.OPEN
    private var retiring=false

    val mayStartOrDispatch:Boolean get()=state==State.OPEN
    val mayReleaseWriter:Boolean get()=state==State.RETIRED

    fun beginDrain(retire:Boolean=false) {
        retiring=retiring || retire
        if (state==State.OPEN) state=State.DRAINING
    }
    fun finishDrain(safe:Boolean) {
        if (state!=State.DRAINING) return
        state=if(!safe) State.POISONED else if(retiring) State.RETIRED else State.OPEN
    }
}
