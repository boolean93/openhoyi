package io.openhoyi.mobile

import io.openhoyi.session.SplitMachineWriteJournal
import io.openhoyi.session.MachineWriteRecoveryState
import io.openhoyi.protocol.SleepDay
import io.openhoyi.protocol.WeeklySleepDay
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SplitMachineWriteFileStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private val entry = SplitMachineWriteJournal.Entry(SplitMachineWriteJournal.Kind.SCHEDULE, "AA:BB:CC:DD:EE:01")

    private class MemoryOps : SplitMachineWriteFileStore.FileOps {
        var current: ByteArray? = null
        var lateFailure = false
        var lateUnlinkFailure = false
        var beforeReplace:(()->Unit)?=null
        val events = mutableListOf<String>()
        override fun exists(file: File) = current != null
        override fun readBounded(file: File, max: Int) = requireNotNull(current).also { require(it.size <= max) }
        override fun replace(file: File, bytes: ByteArray) {
            events += "replace"
            beforeReplace?.invoke()
            current = bytes.copyOf()
            if (lateFailure) error("after rename before directory sync")
        }
        override fun unlink(file: File) {
            events += "unlink"
            current = null
            if (lateUnlinkFailure) error("after unlink before directory sync")
        }
    }

    @Test fun leaseIdentityUsesCanonicalPathAndCannotAdoptRestartedFence() {
        val ops = MemoryOps()
        val parent = temporary.newFolder("marker")
        val alias = File(parent, ".")
        val open = requireNotNull(SplitMachineWriteFileStore.tryOpen(parent, {false}, {true}, ops))
        assertNull(SplitMachineWriteFileStore.tryOpen(alias, {false}, {true}, ops))
        val journal = SplitMachineWriteJournal(open)
        assertNotNull(journal.reserve(entry))
        open.close()
        assertFalse(open.replace(listOf(entry)))
        requireNotNull(SplitMachineWriteFileStore.tryOpen(alias, {false}, {true}, ops)).use {
            assertTrue(it.read() is SplitMachineWriteJournal.Snapshot.Pending)
            assertFalse(it.replace(listOf(entry)))
            assertFalse(it.clear())
        }
        assertEquals(listOf("replace"), ops.events)
    }

    @Test fun clearNeverUnlinksWithUncertainOrPendingLegacyCache() {
        for (clears in listOf(false, true)) {
            val ops = MemoryOps()
            var cached = false
            val parent = temporary.newFolder()
            requireNotNull(SplitMachineWriteFileStore.tryOpen(parent, {cached}, {
                ops.events += "cache"
                clears // even true is insufficient when the subsequent cache read still says pending
            }, ops)).use {
                assertTrue(it.replace(listOf(entry)))
                cached = true
                assertFalse(it.clear())
                assertFalse(it.clear())
                assertEquals(listOf("replace", "cache"), ops.events)
                assertNotNull(ops.current)
            }
        }
    }

    @Test fun failedPostRenameSyncLatchesAndNeverClearsPendingMarker() {
        val ops = MemoryOps().apply { lateFailure = true }
        requireNotNull(SplitMachineWriteFileStore.tryOpen(temporary.newFolder(), {false}, {true}, ops)).use {
            assertFalse(it.replace(listOf(entry)))
            assertFalse(it.replace(listOf(entry)))
            assertFalse(it.clear())
            assertEquals(listOf("replace"), ops.events)
            assertTrue(it.read() is SplitMachineWriteJournal.Snapshot.Pending)
        }
    }

    @Test fun failedFinalUnlinkRestoresExactMarkerAndLatchesMutation() {
        for (recovering in listOf(false,true)) {
            val bytes=SplitMachineWriteMarkerCodec.encode(listOf(typedWeekly)).toString().toByteArray()
            val ops=MemoryOps().apply { if(recovering) current=bytes.copyOf() }
            val parent=temporary.newFolder()
            requireNotNull(SplitMachineWriteFileStore.tryOpen(parent,{false},{true},ops)).use { store ->
                val journal=SplitMachineWriteJournal(store)
                val live=if(recovering) null else requireNotNull(journal.reserve(typedWeekly))
                val restored=if(recovering) requireNotNull(journal.recoveryOwner(typedWeekly)) else null
                ops.lateUnlinkFailure=true
                assertFalse(if(restored!=null) journal.settleRecovered(restored) else journal.settle(requireNotNull(live)))
                assertEquals(SplitMachineWriteJournal.Snapshot.Blocked,journal.pending)
                assertArrayEquals(bytes,ops.current)
                assertFalse(store.clear())
            }
            requireNotNull(SplitMachineWriteFileStore.tryOpen(parent,{false},{true},ops)).use { restarted ->
                assertEquals(SplitMachineWriteJournal.Snapshot.Pending(listOf(typedWeekly)),restarted.read())
            }
        }
    }

    @Test fun boundedStrictDecoderRejectsDuplicateUnknownAndCoercibleFields() {
        val valid = SplitMachineWriteMarkerCodec.encode(listOf(entry)).toString()
        assertTrue(SplitMachineWriteMarkerCodec.decode(valid.toByteArray()) is SplitMachineWriteJournal.Snapshot.Pending)
        for (bad in listOf(
            "{\"kind\":\"SETTING\"," + valid.drop(1), "{\"unexpected\":null," + valid.drop(1),
            valid.replace("\"version\":1", "\"version\":\"1\""),
            valid.replace("\"version\":1", "\"version\":1.0"),
            valid.replace("AA:BB:CC:DD:EE:01", "AA:bb:CC:DD:EE:01"),
            valid.replace("\"operation\":", "\"other\":\"SCHEDULE\",\"operation\":"),
            valid.replace("SCHEDULE", "SCHEDULÉ"), valid + "{}", valid + " ".repeat(4096)
        )) assertEquals(bad, SplitMachineWriteJournal.Snapshot.Blocked, SplitMachineWriteMarkerCodec.decode(bad.toByteArray()))
    }

    private val typedWeekly=entry.copy(target=SplitMachineWriteJournal.Target.Weekly(
        List(7) { day -> WeeklySleepDay(day%2==0,SleepDay(day,59-day,7+day,30)) }))
    private val typedSteam=SplitMachineWriteJournal.Entry(SplitMachineWriteJournal.Kind.BOILER,entry.address,
        SplitMachineWriteJournal.Target.Boiler(SplitMachineWriteJournal.Target.BoilerId.STEAM,true))

    @Test fun typedMarkerKeepsLegacyUnknownFenceAndValidatesExactTargets() {
        val originals=listOf(typedWeekly,typedSteam)
        val json=SplitMachineWriteMarkerCodec.encode(originals)
        assertEquals(MachineWriteRecoveryState.Kind.UNKNOWN,
            RecoveryPreferenceStorage.decodeMachineMarker(json).kind)
        assertNull(RecoveryPreferenceStorage.decodeMachineMarker(json).address)
        val raw=json.toString()
        assertEquals(SplitMachineWriteJournal.Snapshot.Pending(originals),
            SplitMachineWriteMarkerCodec.decode(raw.toByteArray()))
        val mutations=listOf("STEAM" to "WATER", "B:STEAM:1" to "B:STEAM:2", "B:STEAM:1" to "W:1",
            "100590730" to "199990730", """"version":2""" to """"version":1""")
        for ((old,new) in mutations) {
            assertTrue(old,old in raw)
            assertEquals(SplitMachineWriteJournal.Snapshot.Blocked,SplitMachineWriteMarkerCodec.decode(raw.replace(old,new).toByteArray()))
        }
        assertFalse(SplitMachineWriteJournal.valid(listOf(typedWeekly,typedSteam.copy(target=null))))
        assertFalse(SplitMachineWriteJournal.valid(listOf(typedSteam.copy(kind=SplitMachineWriteJournal.Kind.SCHEDULE))))
    }

    @Test fun recoveredTokensCannotDispatchAndEachCasKeepsTheSiblingAndDowngradeFence() {
        val ops=MemoryOps().apply { current=SplitMachineWriteMarkerCodec.encode(listOf(typedWeekly,typedSteam)).toString().toByteArray() }
        val parent=temporary.newFolder()
        requireNotNull(SplitMachineWriteFileStore.tryOpen(parent,{false},{ops.events+="verify-cache";true},ops)).use { store ->
            val restored=SplitMachineWriteJournal(store)
            val unrelated=SplitMachineWriteJournal(store)
            val week=requireNotNull(restored.recoveryOwner(typedWeekly))
            val steam=requireNotNull(restored.recoveryOwner(typedSteam))
            assertNull(restored.reserve(typedSteam))
            assertFalse(restored.settleRecovered(requireNotNull(unrelated.recoveryOwner(typedWeekly))))
            assertTrue(restored.settleRecovered(week))
            assertFalse(restored.settleRecovered(week))
            assertEquals(SplitMachineWriteJournal.Snapshot.Pending(listOf(typedSteam)),store.read())
            assertEquals(MachineWriteRecoveryState.Kind.UNKNOWN,RecoveryPreferenceStorage.decodeMachineMarker(
                org.json.JSONObject(String(requireNotNull(ops.current)))).kind)
            assertTrue(restored.settleRecovered(steam))
            assertEquals(listOf("replace","verify-cache","unlink"),ops.events)
            assertNull(restored.reserve(typedSteam))
            assertFalse(store.replace(listOf(typedSteam)))
        }
        requireNotNull(SplitMachineWriteFileStore.tryOpen(parent,{false},{true},ops)).use { store ->
            assertNotNull(SplitMachineWriteJournal(store).reserve(typedSteam))
        }
    }

    @Test fun semanticEqualityDoesNotPermitRecoveryAfterRawMarkerReplacementOrCacheConflict() {
        val raw=SplitMachineWriteMarkerCodec.encode(listOf(typedSteam)).toString().toByteArray()
        for (replaceRaw in listOf(false,true)) {
            var cached=false
            val ops=MemoryOps().apply {current=raw}
            requireNotNull(SplitMachineWriteFileStore.tryOpen(temporary.newFolder(),{cached},{!cached},ops)).use { store ->
                val journal=SplitMachineWriteJournal(store)
                val owner=requireNotNull(journal.recoveryOwner(typedSteam))
                if(replaceRaw)ops.current=" ".toByteArray()+raw else cached=true
                val before=requireNotNull(ops.current).copyOf()
                assertFalse(journal.settleRecovered(owner))
                assertEquals(SplitMachineWriteJournal.Snapshot.Blocked,journal.pending)
                assertArrayEquals(before,ops.current)
                assertTrue(ops.events.isEmpty())
                assertFalse(journal.settleRecovered(owner))
            }
        }
    }

    @Test fun borrowedViewNeverUnpinsTheServiceOrAllowsLegacyWritesToReturn() {
        val ops=MemoryOps()
        val parent=temporary.newFolder()
        val marker=File(parent,"pending_machine_write.json")
        var legacyWrites=0
        val underlying=object:MachineWriteRecoveryState.Storage {
            override fun read()=MachineWriteRecoveryState.Record(null,null)
            override fun write(record:MachineWriteRecoveryState.Record):Boolean {legacyWrites++;return true}
        }
        RecoveryPreferenceStorage.PinnedMachine(requireNotNull(MachineRecoveryMarkerLease.tryAcquire(marker)),underlying,{false}).use { pin ->
            requireNotNull(pin.openSplitViewForV2({true},ops)).use { view ->
                assertNull(pin.openSplitViewForV2({true},ops))
                assertFalse(pin.storage.write(MachineWriteRecoveryState.Record(null,null)))
                assertNotNull(SplitMachineWriteJournal(view).reserve(typedSteam))
            }
            assertTrue(pin.held)
            assertNull(MachineRecoveryMarkerLease.tryAcquire(marker))
            assertFalse(pin.storage.write(MachineWriteRecoveryState.Record(null,null)))
            assertEquals(0,legacyWrites)
        }
        requireNotNull(MachineRecoveryMarkerLease.tryAcquire(marker)).close()
    }

    @Test fun pinnedTransitionRejectsContendingThreadWithoutOpeningWritingOrReleasing() {
        val ops=MemoryOps()
        val parent=temporary.newFolder()
        val marker=File(parent,"pending_machine_write.json")
        val empty=MachineWriteRecoveryState.Record(null,null)
        var legacyReads=0
        var legacyWrites=0
        val mem=object:MachineWriteRecoveryState.Storage {
            override fun read()=empty.also {legacyReads++}
            override fun write(record:MachineWriteRecoveryState.Record)=true.also {legacyWrites++}
        }
        fun challenge(call:()->List<Boolean>) {
            val outcome=java.util.concurrent.FutureTask(call)
            Thread(outcome,"alien-marker-writer").start()
            assertTrue(outcome.get(2,java.util.concurrent.TimeUnit.SECONDS).all {it})
        }
        RecoveryPreferenceStorage.PinnedMachine(requireNotNull(MachineRecoveryMarkerLease.tryAcquire(marker)),mem,{false}).use { pin ->
            challenge {listOf(!pin.held,pin.storage.read().kind==MachineWriteRecoveryState.Kind.UNKNOWN,
                !pin.storage.write(empty),pin.openSplitViewForV2({true},ops)==null,runCatching(pin::close).isFailure)}
            assertEquals(0,legacyReads)
            assertEquals(0,legacyWrites)
            requireNotNull(pin.openSplitViewForV2({true},ops)).use { view ->
                challenge {listOf(view.read()==SplitMachineWriteJournal.Snapshot.Blocked,
                    !view.replace(listOf(typedWeekly)),!view.clear(),runCatching(view::close).isFailure)}
                assertTrue(pin.held && ops.events.isEmpty())
                assertNotNull(SplitMachineWriteJournal(view).reserve(typedWeekly))
            }
            assertTrue(pin.held)
            assertNull(MachineRecoveryMarkerLease.tryAcquire(marker))
        }
    }

    @Test fun validLegacyOrActiveLegacyCallbacksDoNotChangePinnedWriterMode() {
        val parent=temporary.newFolder()
        val marker=File(parent,"pending_machine_write.json")
        val legacy="""{"kind":"SETTING","address":"AA:BB:CC:DD:EE:01"}"""
        marker.writeText(legacy)
        val ops=MemoryOps().apply {current=legacy.toByteArray()}
        var legacyCachePending=true
        val record=MachineWriteRecoveryState.Record(MachineWriteRecoveryState.Kind.SETTING,entry.address)
        var writes=0
        val delegate=object:MachineWriteRecoveryState.Storage {
            override fun read()=record
            override fun write(record:MachineWriteRecoveryState.Record)=true.also {writes++}
        }
        RecoveryPreferenceStorage.PinnedMachine(requireNotNull(MachineRecoveryMarkerLease.tryAcquire(marker)),delegate,{legacyCachePending}).use { pin ->
            assertNull(pin.openSplitViewForV2({true},ops))
            legacyCachePending=false
            assertNull(pin.openSplitViewForV2({true},ops)) // Raw legacy typed marker still blocks.
            assertEquals(record,pin.storage.read())
            assertTrue(pin.storage.write(MachineWriteRecoveryState.Record(null,null)))
            assertEquals(1,writes)
            assertEquals(legacy,marker.readText())
            assertTrue(ops.events.isEmpty())
            ops.current=null
            assertNull(pin.openSplitViewForV2({false},ops))
            assertTrue(pin.storage.write(record))
            assertEquals(2,writes)
        }
    }

    @Test fun sameThreadReentryCannotMixLegacyMutationAndSplitOpening() {
        val ops=MemoryOps()
        val marker=File(temporary.newFolder(),"pending_machine_write.json")
        val empty=MachineWriteRecoveryState.Record(null,null)
        lateinit var pin:RecoveryPreferenceStorage.PinnedMachine
        var legacyWrites=0
        val mem=object:MachineWriteRecoveryState.Storage {
            override fun read()=empty
            override fun write(record:MachineWriteRecoveryState.Record):Boolean {
                legacyWrites++
                assertNull(pin.openSplitViewForV2({true},ops))
                assertTrue(runCatching(pin::close).isFailure)
                return true
            }
        }
        pin=RecoveryPreferenceStorage.PinnedMachine(requireNotNull(MachineRecoveryMarkerLease.tryAcquire(marker)),mem,{
            assertFalse(pin.storage.write(empty))
            assertNull(pin.openSplitViewForV2({true},ops))
            assertTrue(runCatching(pin::close).isFailure)
            false
        })
        pin.use {
            assertTrue(pin.storage.write(empty))
            requireNotNull(pin.openSplitViewForV2({true},ops)).use {
                assertEquals(1,legacyWrites)
                assertTrue(pin.held)
                assertFalse(pin.storage.write(empty))
            }
        }
    }

    @Test fun reentrantSplitCasCannotRetireTheCanonicalLeaseMidWrite() {
        val ops=MemoryOps()
        val marker=File(temporary.newFolder(),"pending_machine_write.json")
        RecoveryPreferenceStorage.PinnedMachine(requireNotNull(MachineRecoveryMarkerLease.tryAcquire(marker)),null,{false}).use { pin ->
            val view=requireNotNull(pin.openSplitViewForV2({true},ops))
            ops.beforeReplace={
                assertFalse(view.replace(listOf(typedSteam)))
                assertTrue(runCatching(view::close).isFailure)
                assertTrue(runCatching(pin::close).isFailure)
                assertNull(MachineRecoveryMarkerLease.tryAcquire(marker))
            }
            val journal=SplitMachineWriteJournal(view)
            assertNotNull(journal.reserve(typedSteam))
            assertTrue(pin.held)
            assertEquals(listOf("replace"),ops.events)
        }
    }

    @Test fun cachedEmptyJournalCannotReplaceAnotherLiveReservationInTheSameView() {
        val ops=MemoryOps()
        val marker=File(temporary.newFolder(),"pending_machine_write.json")
        RecoveryPreferenceStorage.PinnedMachine(requireNotNull(MachineRecoveryMarkerLease.tryAcquire(marker)),null,{false}).use { pin ->
            val view=requireNotNull(pin.openSplitViewForV2({true},ops))
            val first=SplitMachineWriteJournal(view)
            val stale=SplitMachineWriteJournal(view)
            val owner=requireNotNull(first.reserve(typedWeekly))
            val bytes=requireNotNull(ops.current).copyOf()
            assertNull(stale.reserve(typedSteam))
            assertEquals(SplitMachineWriteJournal.Snapshot.Blocked,stale.pending)
            assertArrayEquals(bytes,ops.current)
            assertFalse(view.replace(listOf(typedSteam)))
            assertFalse(view.clear())
            assertTrue(first.owns(owner))
            assertTrue(first.settle(owner))
            assertEquals(listOf("replace","unlink"),ops.events)
        }
    }
}
