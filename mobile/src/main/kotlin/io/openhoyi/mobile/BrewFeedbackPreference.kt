package io.openhoyi.mobile

/** Local display/audio preference only. Failed disk writes never change process authority. */
internal class BrewFeedbackPreference(private val storage: Storage) {
    interface Storage {
        fun read(): Boolean?
        fun write(enabled: Boolean): Boolean
    }
    @Volatile private var current = try { storage.read() ?: false } catch (_: RuntimeException) { false }
    private val observers = mutableMapOf<Any, (Boolean) -> Unit>()
    private var revision = 0L
    val enabled: Boolean get() = current
    @Synchronized fun observe(owner: Any, callback: (Boolean) -> Unit) {
        observers[owner] = callback
        try { callback(current) } catch (_: RuntimeException) { /* Local display failure only. */ }
    }
    @Synchronized fun unobserve(owner: Any) { observers.remove(owner) }
    @Synchronized fun setEnabled(enabled: Boolean): Boolean {
        if (current == enabled) return true
        if (!(try { storage.write(enabled) } catch (_: RuntimeException) { false })) return false
        current = enabled
        val version = ++revision
        for ((owner, callback) in observers.toMap()) {
            if (revision != version) break
            if (observers[owner] !== callback) continue
            try { callback(enabled) } catch (_: RuntimeException) { /* Do not block other display owners. */ }
        }
        return true
    }
}
