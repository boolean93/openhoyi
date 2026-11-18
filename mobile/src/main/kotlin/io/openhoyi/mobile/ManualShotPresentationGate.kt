package io.openhoyi.mobile

/** Main-thread, process-local handoff of observations, not an owner of machine state. */
internal class ManualShotPresentationGate {
    enum class Outcome { ENDED, UNKNOWN }

    var activeId: String? = null
        private set
    private var awaitIdle = false
    private var presentedId: String? = null
    private var terminal: Pair<String, Outcome>? = null
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

    fun outcome(id: String): Outcome? = terminal?.takeIf { it.first == id }?.second

    fun started(id: String) {
        require(id.isNotBlank())
        if (!awaitIdle) {
            activeId = id
            changed()
        }
    }
    private fun finish(outcome: Outcome) {
        val id = activeId ?: return
        terminal = id to outcome
        activeId = null
        changed()
    }
    fun ended() = finish(Outcome.ENDED)
    fun disconnected(requireIdle: Boolean = true) {
        if (requireIdle) awaitIdle = true
        finish(Outcome.UNKNOWN)
    }
    fun observedIdle() { awaitIdle = false }
}
