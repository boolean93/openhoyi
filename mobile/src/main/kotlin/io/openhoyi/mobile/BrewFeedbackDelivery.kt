package io.openhoyi.mobile

/** Main-thread, once-per-cup display delivery. No lifecycle, playback or device calls. */
internal class BrewFeedbackDelivery {
    private var publishedId: String? = null
    private var pending: BrewFeedbackResult? = null
    fun publish(result: BrewFeedbackResult?, enabledAtEnd: Boolean) {
        if (result == null) { clear(); return }
        if (publishedId == result.shotId) return
        publishedId = result.shotId
        pending = result.takeIf { enabledAtEnd }
    }
    fun claim(enabledNow: Boolean): BrewFeedbackResult? {
        val value = pending
        pending = null
        return value.takeIf { enabledNow }
    }
    fun clear() { pending = null }
}
