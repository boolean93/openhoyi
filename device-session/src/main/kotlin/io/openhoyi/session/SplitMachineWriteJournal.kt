package io.openhoyi.session

import io.openhoyi.protocol.WeeklySleepDay
import io.openhoyi.protocol.WeeklySleepSchedule

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

    sealed interface Target {
        enum class BoilerId { BREW, STEAM }
        data class Boiler(val boiler:BoilerId,val enabled:Boolean):Target
        class Weekly(days:List<WeeklySleepDay>):Target {
            val days=WeeklySleepSchedule(days).days
            override fun equals(other:Any?)=other is Weekly && days==other.days
            override fun hashCode()=days.hashCode()
        }
    }
    // null is a legacy v1 record whose original exact value was not saved.
    data class Entry(val kind: Kind,val address:String,val target:Target?=null)

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
        fun replaceExpected(expected:List<Entry>,replacement:List<Entry>):Boolean =
            currentMatches(expected) && replace(replacement)
        fun clearExpected(expected:List<Entry>):Boolean =
            currentMatches(expected) && clear()
        private fun currentMatches(expected:List<Entry>):Boolean = when(val observed=read()) {
            Snapshot.Empty -> expected.isEmpty()
            is Snapshot.Pending -> observed.entries==expected
            Snapshot.Blocked -> false
        }
        fun settleRecovered(expected:List<Entry>,entry:Entry):Boolean=false
    }

    class Ownership internal constructor(internal val kind: Kind)
    class RecoveryOwnership internal constructor(val entry:Entry)

    private var snapshot = runCatching(store::read).getOrDefault(Snapshot.Blocked).let {
        if (it is Snapshot.Pending && !valid(it.entries)) Snapshot.Blocked
        else if (it is Snapshot.Pending) Snapshot.Pending(it.entries.toList())
        else it
    }
    private val liveOwners = mutableMapOf<Kind, Ownership>()
    private val recoveredEpoch=snapshot is Snapshot.Pending
    private val recoveryOwners=mutableMapOf<Kind,RecoveryOwnership>()
    val pending: Snapshot get() = when (val current = snapshot) {
        is Snapshot.Pending -> Snapshot.Pending(current.entries.toList())
        else -> current
    }

    fun reserve(entry: Entry): Ownership? {
        if(recoveredEpoch)return null
        val old = when (val current = snapshot) {
            Snapshot.Blocked -> return null
            Snapshot.Empty -> emptyList()
            is Snapshot.Pending -> current.entries
        }
        // Entries obtained through recovery are never adopted for sending.
        if (old.size != liveOwners.size || entry.kind in liveOwners ||
            old.any { it.address != entry.address } || !isCanonicalAddress(entry.address)) return null
        if (!valid(old + entry) || !persist(old + entry)) return null
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

    /** A restarted marker may be acknowledged with new evidence, never adopted for a send. */
    fun recoveryOwner(entry:Entry):RecoveryOwnership? {
        val current=snapshot as? Snapshot.Pending ?: return null
        if(!recoveredEpoch || entry !in current.entries) return null
        return recoveryOwners.getOrPut(entry.kind){RecoveryOwnership(entry)}
    }

    fun settleRecovered(owner:RecoveryOwnership):Boolean {
        val current=snapshot as? Snapshot.Pending ?: return false
        if(!recoveredEpoch || recoveryOwners[owner.entry.kind]!==owner || owner.entry !in current.entries) return false
        val ok=runCatching { store.settleRecovered(current.entries,owner.entry) }.getOrDefault(false)
        if(!ok) {
            snapshot=Snapshot.Blocked
            recoveryOwners.clear()
            return false
        }
        val remaining=current.entries.filterNot {it==owner.entry}
        snapshot=if(remaining.isEmpty()) Snapshot.Empty else Snapshot.Pending(remaining)
        recoveryOwners.remove(owner.entry.kind)
        return true
    }

    private fun persist(entries: List<Entry>): Boolean {
        val expected=(snapshot as? Snapshot.Pending)?.entries.orEmpty()
        val ok = runCatching {
            if(entries.isEmpty()) store.clearExpected(expected) else store.replaceExpected(expected,entries.toList())
        }.getOrDefault(false)
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
            (entries.all {it.target==null} || entries.all {it.target!=null}) &&
            entries.all { entry ->
                isCanonicalAddress(entry.address) && when(entry.target) {
                    null -> true
                    is Target.Boiler -> entry.kind==Kind.BOILER
                    is Target.Weekly -> entry.kind==Kind.SCHEDULE
                }
            }
    }
}
