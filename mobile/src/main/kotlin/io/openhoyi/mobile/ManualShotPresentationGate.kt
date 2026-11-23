package io.openhoyi.mobile

/** Main-thread, process-local handoff of observations, not an owner of machine state. */
internal class ManualShotPresentationGate {
    enum class Outcome { ENDED, UNKNOWN, DISCARDED }
    data class Terminal(val id: String, val outcome: Outcome, val points: List<ShotPoint>)

    var activeId: String? = null
        private set
    private var awaitIdle = false
    private var presentedId: String? = null
    private var terminal: Terminal? = null
    private val observers = mutableSetOf<() -> Unit>()

    fun observe(observer: () -> Unit) { observers += observer }
    fun unobserve(observer: () -> Unit) { observers -= observer }
    private fun changed() { observers.toList().forEach { it() } }

    fun eligible(id: String, foreground: Boolean): Boolean =
        foreground && activeId == id && presentedId != id

    fun claim(id: String, foreground: Boolean): Boolean {
        if (!eligible(id, foreground)) return false
        presentedId = id
        return true
    }

    fun terminalFor(id: String): Terminal? = terminal?.takeIf { it.id == id }

    fun started(id: String) {
        require(id.isNotBlank())
        if (!awaitIdle) {
            activeId = id
            changed()
        }
    }
    private fun finish(outcome: Outcome, points: List<ShotPoint>) {
        val id = activeId ?: return
        terminal = Terminal(id, outcome, points)
        activeId = null
        changed()
    }
    fun ended(points: List<ShotPoint> = emptyList()) = finish(Outcome.ENDED, points)
    fun discarded() = finish(Outcome.DISCARDED, emptyList())
    fun disconnected(requireIdle: Boolean = true, points: List<ShotPoint> = emptyList()) {
        if (requireIdle) awaitIdle = true
        finish(Outcome.UNKNOWN, points)
    }
    fun observedIdle() { awaitIdle = false }
}
