package io.openhoyi.mobile

/** Main-thread local playback. A request and clip identity reject late/duplicate callbacks.
 * No UI, Context, service, protocol or device dependencies. */
internal class BrewFeedbackAudio(private val driver: Driver) {
    enum class Result { COMPLETED, FAILED, INTERRUPTED }
    fun interface Cancel { fun cancel() }
    interface Driver {
        /** Call done on the owning thread after releasing clip resources. Cancellation is idempotent. */
        fun start(path: String, done: (Result) -> Unit): Cancel
    }
    private var request: Any? = null
    private var clipIndex = -1
    private var cancel: Cancel? = null
    private var clips = emptyList<String>()
    val playing: Boolean get() = request != null

    fun play(level: BrewFeedbackClips.Level, variant: Int) {
        val sequence = BrewFeedbackClips.sequence(level, variant)
        stop()
        clips = sequence
        val token = Any()
        request = token
        start(token, 0)
    }
    fun stop() {
        request = null
        clipIndex = -1
        val previous = cancel
        cancel = null
        previous?.let(::release)
    }
    private fun start(token: Any, index: Int) {
        if (request !== token) return
        if (index == clips.size) { request = null; clipIndex = -1; cancel = null; return }
        clipIndex = index
        val handle = try {
            driver.start(clips[index]) { result ->
                if (request === token && clipIndex == index) {
                    cancel = null
                    if (result == Result.INTERRUPTED) stop() else start(token, index + 1)
                }
            }
        } catch (_: RuntimeException) {
            if (request === token && clipIndex == index) start(token, index + 1)
            null
        }
        if (handle != null) {
            if (request === token && clipIndex == index) cancel = handle else release(handle)
        }
    }
    private fun release(handle: Cancel) { try { handle.cancel() } catch (_: RuntimeException) { /* Local audio failure only. */ } }
}
