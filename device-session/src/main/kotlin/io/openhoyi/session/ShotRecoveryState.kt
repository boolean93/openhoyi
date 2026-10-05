package io.openhoyi.session

/** Durable reminder that a started extraction may still need physical inspection after restart. */
class ShotRecoveryState(private val storage: Storage) {
    data class Record(val pending: Boolean, val address: String?)
    interface Storage {
        fun read(): Record
        /** Must be synchronous and report failure; an asynchronous apply is insufficient. */
        fun write(record: Record): Boolean
    }

    private val addressPattern = Regex("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}")
    private var record = runCatching(storage::read).getOrDefault(Record(true, null)).let {
        // A durable clear removes both fields; a residual identity lacks completion evidence.
        if (!it.pending && it.address == null) Record(false, null)
        else Record(true, it.address?.uppercase()?.takeIf(addressPattern::matches))
    }
    val pending: Boolean get() = record.pending
    val address: String? get() = record.address

    /** Unknown address is reserved for a legacy marker or passively observed machine shot. */
    fun matchesDevice(candidate: String?): Boolean = !pending || address == null ||
        candidate?.equals(address, ignoreCase = true) == true

    /** A legacy marker without identity cannot be cleared by a later, unrelated manual shot. */
    fun mayClearAfterPassiveShot(preExisting: Boolean, candidate: String?): Boolean =
        pending && matchesDevice(candidate) && (!preExisting || address != null)

    fun arm(address: String?): Boolean {
        val normalized = address?.uppercase()?.takeIf(addressPattern::matches)
        if (address != null && normalized == null) return false
        if (pending) return this.address == normalized
        val next = Record(true, normalized)
        if (!runCatching { storage.write(next) }.getOrDefault(false)) return false
        record = next
        return true
    }

    fun clear(): Boolean {
        if (!pending) return true
        val next = Record(false, null)
        if (!runCatching { storage.write(next) }.getOrDefault(false)) return false
        record = next
        return true
    }
}
