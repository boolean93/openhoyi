package io.openhoyi.mobile

/** Application-owned operation, with callbacks dispatched by its owner. No View or Activity retained. */
internal class DiagnosticStorageController(
    private val request: (Boolean, (Result<Long>) -> Unit) -> Unit,
) {
    enum class Outcome { CLEARED, FAILED }
    data class State(val busy: Boolean = false, val clearing: Boolean = false,
        val bytes: Long? = null, val outcome: Outcome? = null, val readFailed: Boolean = false)
    var state = State()
        private set
    private val observers = mutableSetOf<(State) -> Unit>()
    fun observe(observer: (State) -> Unit) { observers += observer; observer(state) }
    fun unobserve(observer: (State) -> Unit) { observers -= observer }
    private fun publish(value: State) { state = value; observers.toList().forEach { it(value) } }
    fun refresh() = run(false)
    fun clear() = run(true)
    private fun run(clear: Boolean): Boolean {
        if (state.busy) return false
        val previousOutcome = state.outcome
        publish(state.copy(busy = true, clearing = clear, outcome = if (clear) null else previousOutcome, readFailed = false))
        var completed = false
        fun finish(result: Result<Long>) {
            if (completed) return
            completed = true
            publish(State(bytes = result.getOrNull(), readFailed = result.isFailure,
                outcome = if (!clear) previousOutcome else if (result.isSuccess) Outcome.CLEARED else Outcome.FAILED))
        }
        try { request(clear, ::finish) }
        catch (failure: RuntimeException) { finish(Result.failure(failure)) }
        return true
    }
}
