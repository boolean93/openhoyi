package io.openhoyi.mobile

import io.openhoyi.protocol.SleepDay
import io.openhoyi.protocol.WeeklySleepDay
import io.openhoyi.session.*
import java.io.File
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CanonicalMachineOperationBrokerTest {
    @get:Rule val root=TemporaryFolder()
    private val address="AA:BB:CC:DD:EE:01"
    private val week=SplitMachineWriteJournal.Entry(SplitMachineWriteJournal.Kind.SCHEDULE,address,
        SplitMachineWriteJournal.Target.Weekly(List(7){WeeklySleepDay(false,SleepDay(0,0,7,0))}))
    private val boiler=SplitMachineWriteJournal.Entry(SplitMachineWriteJournal.Kind.BOILER,address,
        SplitMachineWriteJournal.Target.Boiler(SplitMachineWriteJournal.Target.BoilerId.BREW,true))
    private class Mem:SplitMachineWriteFileStore.FileOps {
        var data:ByteArray?=null
        var beforeReplace:(()->Unit)?=null
        override fun exists(file:File)=data!=null
        override fun readBounded(file:File,max:Int)=requireNotNull(data).copyOf()
        override fun replace(file:File,bytes:ByteArray){beforeReplace?.invoke();data=bytes.copyOf()}
        override fun unlink(file:File){data=null}
    }
    private fun binding():CanonicalMachineOperationBroker.Binding {
        var command:Pair<Long,Long>?=null
        val driver=object:GattDriver {
            override fun execute(generation:Long,token:Long,operation:GattOperation):Boolean {
                command=generation to token;return true
            }
            override fun close(generation:Long)=Unit
        }
        val session=DeviceSession(DeviceRole.COFFEE,driver,{0})
        fun complete(result:OperationResult=OperationResult.Success()) {
            val (g,t)=requireNotNull(command)
            session.onComplete(g,t,result)
        }
        session.connect(address,CoffeeAuthentication(LocalDateTime.of(2026,1,1,0,0),"123456"))
        complete()
        complete(OperationResult.Success(listOf(
            CharacteristicInfo(KnownGatt.coffeeWrite,true,false,false,false),
            CharacteristicInfo(KnownGatt.coffeeNotify,false,false,true,false))))
        complete();complete()
        session.onNotification(session.generation,KnownGatt.coffeeNotify,
            byteArrayOf(0x83.toByte(),1,0x13,0xfd.toByte(),92,0,125,15,53,0,25,0,0x6e))
        return CanonicalMachineOperationBroker.Binding(session,requireNotNull(session.coffeeReadbackCursor).source)
    }

    @Test fun approvedOverlapOnlyAfterWeekTransportSuccessAndSameAuthenticatedSource() {
        val marker=File(root.newFolder(),"pending_machine_write.json")
        val fence=MachineServiceWriterFence()
        val pin=RecoveryPreferenceStorage.PinnedMachine(requireNotNull(MachineRecoveryMarkerLease.tryAcquire(marker)),null,{false})
        val ops=Mem()
        val source=binding()
        var authoritative:CanonicalMachineOperationBroker.Binding?=source
        val broker=CanonicalMachineOperationBroker(pin,fence) {authoritative}
        try {
            assertTrue(broker.canStartHub && broker.legacyMayDispatch)
            assertTrue(broker.openSplit({true},ops))
            assertTrue(broker.canStartHub && !broker.legacyMayDispatch)
            val wrong=binding()
            val weekly=requireNotNull(broker.reserve(week,source))
            assertTrue(broker.mayDispatch(weekly,source))
            authoritative=null
            assertFalse(broker.mayDispatch(weekly,source))
            authoritative=wrong
            assertFalse(broker.mayDispatch(weekly,source))
            authoritative=source
            assertFalse(broker.mayDispatch(weekly,wrong))
            assertNull(broker.reserve(boiler,source))
            assertFalse(broker.enteredReadback(weekly,wrong,OperationResult.Success()))
            assertFalse(broker.abortUnsent(weekly,source,OperationResult.Unknown("logical tail uncertain")))
            assertFalse(broker.enteredReadback(weekly,source,OperationResult.Unknown("logical tail uncertain")))
            assertTrue(broker.enteredReadback(weekly,source,OperationResult.Success()))
            assertFalse(broker.mayDispatch(weekly,source))
            assertNull(broker.reserve(boiler,wrong))
            ops.beforeReplace={
                assertFalse(broker.settleVerified(weekly,source))
                assertFalse(broker.markUnknown(weekly))
            }
            val heat=requireNotNull(broker.reserve(boiler,source))
            ops.beforeReplace=null
            assertNull(broker.reserve(boiler,source))
            assertTrue(broker.mayDispatch(heat,source))
            assertFalse(broker.returnToLegacy())
            assertTrue(broker.enteredReadback(heat,source,OperationResult.Success()))
            assertTrue(broker.settleVerified(heat,source))
            assertNotNull(ops.data)
            assertFalse(broker.acceptsBoilerAfterWeek(source))
            assertNull(broker.reserve(boiler,source))
            assertTrue(broker.settleVerified(weekly,source))
            assertNull(ops.data)
            assertTrue(broker.returnToLegacy())
            assertTrue(broker.legacyMayDispatch)
            assertFalse(broker.mayDispatch(heat,source))
        } finally {
            fence.beginDrain(retire=true);fence.finishDrain(safe=true);broker.close()
        }
    }

    @Test fun delayedBoilerDoesNotDispatchAfterWeeklyReceiptBecomesUnknown() {
        val marker=File(root.newFolder(),"pending_machine_write.json")
        val fence=MachineServiceWriterFence()
        val pin=RecoveryPreferenceStorage.PinnedMachine(requireNotNull(MachineRecoveryMarkerLease.tryAcquire(marker)),null,{false})
        val source=binding()
        val broker=CanonicalMachineOperationBroker(pin,fence) {source}
        val ops=Mem()
        try {
            assertTrue(broker.openSplit({true},ops))
            val weekly=requireNotNull(broker.reserve(week,source))
            assertTrue(broker.enteredReadback(weekly,source,OperationResult.Success()))
            val heat=requireNotNull(broker.reserve(boiler,source))
            assertTrue(broker.mayDispatch(heat,source))
            assertTrue(broker.markUnknown(weekly))
            assertFalse(broker.mayDispatch(heat,source))
            assertTrue(broker.abortUnsent(heat,source,OperationResult.Cancelled("denied before handoff")))
            assertNotNull(ops.data)
            assertFalse(broker.returnToLegacy())
        } finally {
            fence.beginDrain(retire=true);fence.finishDrain(safe=true);broker.close()
        }
    }

    @Test fun recoveredAndUncertainEpochsDoNotAuthorizeAnySendOrLegacyReturn() {
        val marker=File(root.newFolder(),"pending_machine_write.json")
        val fence=MachineServiceWriterFence()
        val pin=RecoveryPreferenceStorage.PinnedMachine(requireNotNull(MachineRecoveryMarkerLease.tryAcquire(marker)),null,{false})
        val ops=Mem().apply {data=SplitMachineWriteMarkerCodec.encode(listOf(week)).toString().toByteArray()}
        val live=binding()
        val broker=CanonicalMachineOperationBroker(pin,fence) {live}
        try {
            assertTrue(broker.openSplit({true},ops))
            assertFalse(broker.legacyMayDispatch)
            assertNull(broker.reserve(boiler,binding()))
            assertFalse(broker.returnToLegacy())
            fence.beginDrain()
            assertFalse(broker.canStartHub)
            broker.close()
            assertNull(MachineRecoveryMarkerLease.tryAcquire(marker))
        } finally {
            fence.beginDrain(retire=true);fence.finishDrain(safe=true);broker.close()
        }
    }
}
