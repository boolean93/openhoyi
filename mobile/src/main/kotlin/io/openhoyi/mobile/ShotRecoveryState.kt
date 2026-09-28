package io.openhoyi.mobile

/** Durable reminder that a started extraction may still need physical inspection after restart. */
class ShotRecoveryState(private val storage: Storage) {
    interface Storage {
        fun read(): Boolean
        /** Must be synchronous and report failure; an asynchronous apply is insufficient. */
        fun write(pending: Boolean): Boolean
    }

    var pending: Boolean = runCatching(storage::read).getOrDefault(true)
        private set

    fun arm(): Boolean {
        if (pending) return true
        if (!runCatching { storage.write(true) }.getOrDefault(false)) return false
        pending = true
        return true
    }

    fun clear(): Boolean {
        if (!pending) return true
        if (!runCatching { storage.write(false) }.getOrDefault(false)) return false
        pending = false
        return true
    }
}
