package io.openhoyi.mobile

/** Durable shot uncertainty takes priority over any in-memory idle session state. */
object MachineControlGate {
    fun block(unresolvedShot: Boolean, machineWriteWarning: String?, shotWarning: String): String? =
        if (unresolvedShot) shotWarning else machineWriteWarning
}
