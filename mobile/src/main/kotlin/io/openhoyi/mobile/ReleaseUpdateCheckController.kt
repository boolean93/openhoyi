package io.openhoyi.mobile

/** Main-thread owner of one manual check. No request is started by resume/recreation. */
internal class ReleaseUpdateCheckController(
    private val request: ((Result<ReleaseUpdate>) -> Unit) -> (() -> Unit),
    private val changed: (State) -> Unit,
) {
    sealed interface State {
        data object Idle : State
        data object Checking : State
        data class Checked(val update: ReleaseUpdate) : State
        data object Failed : State
    }
    var state: State = State.Idle
        private set
    private var active = false
    private var generation = 0L
    private var cancel: (() -> Unit)? = null

    fun resume() { active = true }
    fun check() {
        if (!active || state == State.Checking) return
        val token = ++generation
        publish(State.Checking)
        try {
            val stop = request { result ->
                if (active && token == generation && state == State.Checking) {
                    cancel = null
                    publish(result.fold({ State.Checked(it) }, { State.Failed }))
                }
            }
            if (active && token == generation && state == State.Checking) cancel = stop else stop()
        } catch (_: Exception) {
            if (active && token == generation) publish(State.Failed)
        }
    }
    fun pause() {
        active = false
        generation++
        cancel?.let { runCatching(it) }
        cancel = null
        if (state == State.Checking) publish(State.Idle)
    }
    private fun publish(value: State) { state = value; changed(value) }
}
