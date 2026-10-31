package io.openhoyi.mobile

/** Only a new manual shot after observed idle may request navigation. */
internal class ManualShotPresentationGate {
    var activeId: String? = null
        private set
    private var awaitIdle = false

    fun started(id: String) {
        require(id.isNotBlank())
        activeId = if (awaitIdle) null else id
    }
    fun ended() { activeId = null }
    fun disconnected() { activeId = null; awaitIdle = true }
    fun observedIdle() { awaitIdle = false }
}
