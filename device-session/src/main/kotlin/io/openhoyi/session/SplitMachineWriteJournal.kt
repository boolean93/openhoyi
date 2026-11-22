package io.openhoyi.session

/**
 * Recovery-only ownership for the potential schedule and boiler split. Unwired: no dispatch
 * decision, payload, acknowledgement evidence, retry, or reconnect is implemented here.
 *
 * A caller must be single-threaded and exclusive with the legacy writer. Store.replace must
 * durably replace the exact legacy marker, preserving its old-or-new contents on any crash;
 * Store.clear must keep the fence until legacy cached state is durably cleared.
 */
class SplitMachineWriteJournal(private val store: Store) {
    enum class Kind { SCHEDULE, BOILER }

    data class Entry(val kind: Kind, val address: String)

    sealed interface Snapshot {
        data object Empty : Snapshot
        /** Includes old typed records, corrupt data and unsupported extensions. */
        data object Blocked : Snapshot
        data class Pending(val entries: List<Entry>) : Snapshot
    }

    interface Store {
        fun read(): Snapshot
        fun replace(entries: List<Entry>): Boolean
        fun clear(): Boolean
    }

    class Ownership internal constructor(internal val kind: Kind)

    private var snapshot = runCatching(store::read).getOrDefault(Snapshot.Blocked).let {
        if (it is Snapshot.Pending && !valid(it.entries)) Snapshot.Blocked
        else if (it is Snapshot.Pending) Snapshot.Pending(it.entries.toList())
        else it
    }
    private val liveOwners = mutableMapOf<Kind, Ownership>()
    val pending: Snapshot get() = when (val current = snapshot) {
        is Snapshot.Pending -> Snapshot.Pending(current.entries.toList())
        else -> current
    }

    fun reserve(entry: Entry): Ownership? {
        val old = when (val current = snapshot) {
            Snapshot.Blocked -> return null
            Snapshot.Empty -> emptyList()
            is Snapshot.Pending -> current.entries
        }
        // Entries obtained through recovery are never adopted for sending.
        if (old.size != liveOwners.size || entry.kind in liveOwners ||
            old.any { it.address != entry.address } || !isCanonicalAddress(entry.address)) return null
        if (!persist(old + entry)) return null
        return Ownership(entry.kind).also { liveOwners[entry.kind] = it }
    }

    fun owns(owner: Ownership?): Boolean =
        snapshot is Snapshot.Pending && owner != null && liveOwners[owner.kind] === owner

    /** Caller supplies independently verified evidence; ownership itself is not that evidence. */
    fun settle(owner: Ownership): Boolean {
        val current = snapshot as? Snapshot.Pending ?: return false
        if (!owns(owner)) return false
        if (!persist(current.entries.filterNot { it.kind == owner.kind })) return false
        liveOwners.remove(owner.kind)
        return true
    }

    private fun persist(entries: List<Entry>): Boolean {
        val ok = runCatching { if (entries.isEmpty()) store.clear() else store.replace(entries.toList()) }.getOrDefault(false)
        // A false/throw may mean nothing or partial/complete persistence. Don't make another
        // mutation from this object's stale view or retain any active ownership.
        if (!ok) {
            snapshot = Snapshot.Blocked
            liveOwners.clear()
        } else snapshot = if (entries.isEmpty()) Snapshot.Empty else Snapshot.Pending(entries)
        return ok
    }

    companion object {
        private val canonicalAddress = Regex("([0-9A-F]{2}:){5}[0-9A-F]{2}")
        fun isCanonicalAddress(value: String) = canonicalAddress.matches(value)
        fun valid(entries: List<Entry>) = entries.isNotEmpty() &&
            entries.distinctBy { it.kind }.size == entries.size &&
            entries.map { it.address }.distinct().size == 1 &&
            entries.all { isCanonicalAddress(it.address) }
    }
}
