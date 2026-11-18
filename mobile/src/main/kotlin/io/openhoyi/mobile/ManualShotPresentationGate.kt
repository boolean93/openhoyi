package io.openhoyi.mobile

/** Main-thread, process-local handoff from passive observation to foreground UI. */
internal class ManualShotPresentationGate {
    var activeId: String? = null
        private set
    private var awaitIdle = false
    private var lastPresentedId: String? = null

    /** Main and comparison share one claim per active observation. */
    fun claim(isResumed: Boolean): Boolean {
        if (!isResumed) return false
        val id = activeId ?: return false
        if (id == lastPresentedId) return false
        lastPresentedId = id
        return true
    }

    fun started(id: String) {
        require(id.isNotBlank())
        activeId = if (awaitIdle) null else id
    }
    fun ended() { activeId = null }
    fun disconnected() { activeId = null; awaitIdle = true }
    fun observedIdle() { awaitIdle = false }
}
