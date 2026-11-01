package io.openhoyi.mobile

/** Native observations only: never requests input, schedules a timer, or retries a show. */
internal class RawImportRestoreReadiness(private val savedImeVisible: Boolean) {
    val animations = mutableSetOf<Any>()
    var drawCount = 0; private set
    private var drawn: String? = null
    private var ready: String? = null
    private var cancelled = false

    fun reset() { drawn = null; ready = null; drawCount = 0 }
    fun cancel() { cancelled = true; reset() }
    fun ownsNativeWindow(currentOwner: Boolean): Boolean {
        if (!currentOwner) cancel()
        return !cancelled
    }
    fun connectionReady(active: Boolean) = !savedImeVisible || active
    fun preDraw(candidate: String?): Boolean {
        if (cancelled) return false
        if ((candidate == null || candidate != drawn) && candidate != ready) reset()
        ready = candidate
        return candidate != null && candidate == drawn && drawCount >= 2
    }
    /** True requests only the finite next draw needed to consume two real observations. */
    fun drawn(candidate: String?): Boolean {
        if (cancelled) return false
        if (candidate == null || candidate != ready) reset()
        else {
            drawCount = if (drawn == candidate) drawCount + 1 else 1
            drawn = candidate
        }
        return candidate != null && drawCount <= 2
    }
    fun animationPrepared(animation: Any) { reset(); animations.add(animation) }
    fun runningAnimations(values: Set<Any>): Boolean {
        val changed = animations != values
        if (changed) reset()
        animations.clear(); animations.addAll(values)
        return changed
    }
    fun animationEnded(animation: Any) { reset(); animations.remove(animation) }
    fun nativeResult() = reset()
}
