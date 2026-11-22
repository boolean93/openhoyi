package io.openhoyi.mobile

import io.openhoyi.session.CoffeeReadbackSource
import io.openhoyi.session.OperationResult
import io.openhoyi.session.SplitMachineWriteJournal
import java.io.Closeable

/** One canonical durable writer for the service lifetime. This does not own the ATT queue. */
internal class CanonicalMachineOperationBroker(private val pinned:RecoveryPreferenceStorage.PinnedMachine,
    private val transport:MachineServiceWriterFence,
    private val currentBinding:()->Binding?) : Closeable {
    class Binding(val hub:Any,val source:CoffeeReadbackSource) {
        fun same(other:Binding)=hub===other.hub && source===other.source
    }

    enum class Phase { QUEUED, WAITING_READBACK, UNKNOWN }
    class Send private constructor(val entry:SplitMachineWriteJournal.Entry,internal val binding:Binding,
        internal val durable:SplitMachineWriteJournal.Ownership) {
        internal var phase=Phase.QUEUED
        companion object {
            internal fun issue(entry:SplitMachineWriteJournal.Entry,binding:Binding,
                token:SplitMachineWriteJournal.Ownership)=Send(entry,binding,token)
        }
    }
    class RecoveryChoice internal constructor(val entry:SplitMachineWriteJournal.Entry)
    private class Epoch(val view:SplitMachineWriteFileStore) {
        val journal=SplitMachineWriteJournal(view)
        val live=mutableMapOf<SplitMachineWriteJournal.Kind,Send>()
        val restored=journal.pending is SplitMachineWriteJournal.Snapshot.Pending
        var boilerOverlapConsumed=false
        val choices=mutableMapOf<SplitMachineWriteJournal.Kind,RecoveryChoice>()
    }

    val legacyStorage get()=pinned.storage
    fun legacyShapeTrusted():Boolean=pinned.legacyShapeTrusted()
    val canStartHub:Boolean get()=transport.mayStartOrDispatch && pinned.held
    val legacyMayDispatch:Boolean get()=canStartHub && pinned.hasLegacyAuthority
    private var epoch:Epoch?=null
    val splitOwned:Boolean get()=epoch!=null
    private var mutating=false
    private inline fun <T> mutate(refused:T,action:()->T):T {
        if(mutating || !pinned.held)return refused
        mutating=true
        try { return action() } finally {mutating=false}
    }

    /** Product must still prove that no legacy control phases occupy ATT, ownership or recovery. */
    fun openSplit(legacyDispatchIdle:()->Boolean,ops:SplitMachineWriteFileStore.FileOps?=null):Boolean=mutate(false) {
        if(!legacyMayDispatch || epoch!=null)return@mutate false
        val view=pinned.openSplitViewForV2(legacyDispatchIdle,ops) ?: return@mutate false
        epoch=Epoch(view)
        true
    }

    fun restoreSplit(ops:SplitMachineWriteFileStore.FileOps?=null):Boolean=mutate(false) {
        if(!legacyMayDispatch || epoch!=null)return@mutate false
        val view=pinned.openSplitViewForV2({true},ops,restoreOnly=true) ?: return@mutate false
        epoch=Epoch(view)
        true
    }

    val splitRecoveryBlocked:Boolean get()=epoch?.journal?.pending==SplitMachineWriteJournal.Snapshot.Blocked

    val restoredAndResolved:Boolean get()=epoch?.let {
        it.restored && it.journal.pending==SplitMachineWriteJournal.Snapshot.Empty
    }==true

    fun canObserveOriginal(address:String):Boolean {
        val current=epoch?.takeIf { !mutating && active(it) } ?: return false
        val pending=current.journal.pending as? SplitMachineWriteJournal.Snapshot.Pending ?: return false
        return pending.entries.all { it.target!=null && it.address.equals(address,ignoreCase=true) } &&
            (current.restored || current.live.values.all { it.phase==Phase.UNKNOWN })
    }

    fun recoveryChoices():List<RecoveryChoice> {
        val current=epoch?.takeIf { !mutating && active(it) } ?: return emptyList()
        val pending=current.journal.pending as? SplitMachineWriteJournal.Snapshot.Pending ?: return emptyList()
        return pending.entries.filter { entry -> entry.target!=null &&
            (current.restored || current.live[entry.kind]?.phase==Phase.UNKNOWN)
        }.map { entry -> current.choices.getOrPut(entry.kind){ RecoveryChoice(entry) } }
    }

    /** An explicit, proven observation resolves exactly one record; this never grants send authority. */
    fun resolveObserved(choice:RecoveryChoice,binding:Binding,verified:()->Boolean):Boolean=mutate(false) {
        val current=epoch?.takeIf { active(it) && isCurrent(binding) } ?: return@mutate false
        val entry=choice.entry
        if(current.choices[entry.kind]!==choice || !entry.address.equals(binding.source.address,ignoreCase=true) ||
            !verified() || !isCurrent(binding)) return@mutate false
        val ok=if(current.restored) {
            val owner=current.journal.recoveryOwner(entry) ?: return@mutate false
            current.journal.settleRecovered(owner)
        } else {
            val send=current.live[entry.kind]?.takeIf {it.phase==Phase.UNKNOWN && it.entry==entry} ?: return@mutate false
            current.journal.settle(send.durable)
        }
        if(ok) { current.live.remove(entry.kind);current.choices.remove(entry.kind) }
        ok
    }

    fun acceptsBoilerAfterWeek(binding:Binding):Boolean {
        val current=epoch?.takeIf { !mutating && active(it) && !it.restored && !it.boilerOverlapConsumed && isCurrent(binding) } ?: return false
        val weekly=current.live.values.singleOrNull() ?: return false
        return weekly.entry.kind==SplitMachineWriteJournal.Kind.SCHEDULE && weekly.phase==Phase.WAITING_READBACK &&
            weekly.binding.same(binding) && current.journal.owns(weekly.durable)
    }

    /** Readback-only recovery tokens are never converted to this send token. */
    fun reserve(entry:SplitMachineWriteJournal.Entry,binding:Binding):Send? = mutate(null) {
        val current=epoch?.takeIf{active(it) && !it.restored} ?: return@mutate null
        if(!isCurrent(binding) || entry.target==null || !entry.address.equals(binding.source.address,ignoreCase=true) ||
            current.live.values.any { !it.binding.same(binding) } || entry.kind in current.live) return@mutate null
        val existing=current.live.values.singleOrNull()
        if(current.live.size>1 || existing!=null && (entry.kind!=SplitMachineWriteJournal.Kind.BOILER ||
            existing.entry.kind!=SplitMachineWriteJournal.Kind.SCHEDULE || existing.phase!=Phase.WAITING_READBACK ||
            current.boilerOverlapConsumed)) return@mutate null
        val token=current.journal.reserve(entry) ?: return@mutate null
        if(existing!=null)current.boilerOverlapConsumed=true
        Send.issue(entry,binding,token).also { current.live[entry.kind]=it }
    }

    fun mayDispatch(send:Send,binding:Binding):Boolean {
        val current=exact(send,binding) ?: return false
        return !mutating && send.phase==Phase.QUEUED &&
            (send.entry.kind!=SplitMachineWriteJournal.Kind.BOILER ||
                current.live[SplitMachineWriteJournal.Kind.SCHEDULE]?.phase==Phase.WAITING_READBACK)
    }

    /** Called only from the logical transport callback, after schedule packet two if applicable. */
    fun enteredReadback(send:Send,binding:Binding,result:OperationResult):Boolean {
        if(result !is OperationResult.Success || mutating || exact(send,binding)==null || send.phase!=Phase.QUEUED)return false
        send.phase=Phase.WAITING_READBACK
        return true
    }

    fun markUnknown(send:Send):Boolean {
        val current=epoch ?: return false
        if(mutating || !pinned.holdsSplit(current.view) || current.live[send.entry.kind]!==send || !current.journal.owns(send.durable))return false
        send.phase=Phase.UNKNOWN
        return true
    }

    /** Caller independently proves operation-specific readback; this only validates durable ownership. */
    fun settleVerified(send:Send,binding:Binding):Boolean=mutate(false) {
        val current=exact(send,binding) ?: return@mutate false
        if(send.phase!=Phase.WAITING_READBACK && send.phase!=Phase.UNKNOWN)return@mutate false
        if(!current.journal.settle(send.durable))return@mutate false
        current.live.remove(send.entry.kind)
        current.choices.remove(send.entry.kind)
        true
    }

    /** Logical known non-submission only. A denied schedule tail is already reported as Unknown. */
    fun abortUnsent(send:Send,binding:Binding,result:OperationResult):Boolean=mutate(false) {
        val current=exact(send,binding) ?: return@mutate false
        if(result !is OperationResult.Failed && result !is OperationResult.Cancelled ||
            send.phase!=Phase.QUEUED || !current.journal.settle(send.durable))return@mutate false
        current.live.remove(send.entry.kind)
        current.choices.remove(send.entry.kind)
        true
    }

    fun returnToLegacy():Boolean=mutate(false) {
        val current=epoch ?: return@mutate false
        if(!active(current) || current.restored || current.live.isNotEmpty() ||
            current.journal.pending!=SplitMachineWriteJournal.Snapshot.Empty ||
            !pinned.retireEmptySplit(current.view))return@mutate false
        epoch=null
        true
    }

    private fun active(current:Epoch):Boolean =
        transport.mayStartOrDispatch && pinned.holdsSplit(current.view)
    private fun isCurrent(binding:Binding):Boolean = currentBinding()?.same(binding)==true
    private fun exact(send:Send,binding:Binding):Epoch? = epoch?.takeIf {
        active(it) && isCurrent(binding) && send.binding.same(binding) && it.live[send.entry.kind]===send && it.journal.owns(send.durable)
    }

    override fun close() {
        if(transport.mayReleaseWriter && !mutating) pinned.close()
    }
}
