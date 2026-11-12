package io.openhoyi.mobile

/** Local shortcut behavior only. No stored permission can bypass the service's start gates. */
internal object ShortcutStartPolicy {
    const val PREFERENCES = "shortcut_start"
    const val CONFIGURED = "configured"
    const val DIRECT_START = "direct_start"
    const val REQUEST_LIFETIME_MS = 5_000L

    data class Preference(val configured: Boolean = false, val directStart: Boolean = false) {
        val showPrompt get() = !configured
        val startsOnTap get() = configured && directStart
        fun choose(directStart: Boolean) = Preference(true, directStart)
    }
}

/** One tap may submit once. Repeated dequeue checks are allowed, but revocation never reverses. */
internal class ShortcutStartPermit(
    private val createdAt: Long,
    private val clock: () -> Long,
    private val stillValid: () -> Boolean,
) {
    private var revoked = false
    private var claimed = false

    fun revoke() { revoked = true }
    fun allows(): Boolean {
        if (revoked) return false
        val now = clock()
        if (now < createdAt || now - createdAt >= ShortcutStartPolicy.REQUEST_LIFETIME_MS ||
            !runCatching(stillValid).getOrDefault(false)) revoke()
        return !revoked
    }
    fun claim(): Boolean {
        if (claimed || !allows()) return false
        claimed = true
        return true
    }
}

/** Tracks value transitions, not sample timestamps; A -> B -> A is still a changed context. */
internal class ShortcutContentRevision<T>(initial: T) {
    private var value = initial
    var revision = 0L
        private set
    fun observe(next: T) {
        if (next != value) { value = next; revision++ }
    }
}

/** Disk failure must not leave a previous direct-start choice active in another screen. */
internal class ShortcutPreferenceSafety {
    @Volatile var suppressed = false
        private set
    fun failed() { suppressed = true }
    fun saved() { suppressed = false }
}
