package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class MachineLeverRecipeStoreTest {
    private class Disk : MachineLeverRecipeStore.Storage {
        var bytes: String? = null
        var accept = true
        var throws = false
        var writes = 0
        override fun read() = bytes
        override fun write(value: String): Boolean {
            writes++
            if (throws) error("disk unavailable")
            if (!accept) return false
            bytes = value; return true
        }
    }
    private fun snapshot(address: String = "aa:bb:cc:dd:ee:ff", name: String = "Recipe") =
        MachineLeverRecipeStore.Snapshot(address, "draft-one", name,
            "08825c0046031e5a0000009608019000000000d8", 1234L, "1.1.5")
    private val sent = MachineLeverRecipeStore.State.SENT
    private val unknown = MachineLeverRecipeStore.State.UNKNOWN
    private val failed = MachineLeverRecipeStore.State.FAILED

    @Test fun persistsBeforeDispatchAndRetainsImmutableLatestSentPerMachine() {
        val disk = Disk(); val store = MachineLeverRecipeStore(disk)
        val first = snapshot()
        val token = requireNotNull(store.begin(first))
        assertTrue(store.active); assertTrue(store.blocked); assertTrue(store.pending(token))
        assertNotNull(disk.bytes); assertNull(store.recordFor(first.address))
        assertNull(store.begin(snapshot("11:22:33:44:55:66"))) // single operation globally
        assertTrue(store.complete(token, sent))
        assertFalse(store.active); assertFalse(store.blocked)
        assertEquals("Recipe", store.recordFor("AA:BB:CC:DD:EE:FF")?.snapshot?.name)
        assertNull(store.recordFor("11:22:33:44:55:66"))
        val edited = first.copy(name = "Edited")
        val next = requireNotNull(store.begin(edited))
        assertEquals("Recipe", store.recordFor(first.address)?.snapshot?.name) // pending is not sent
        assertTrue(store.complete(next, failed))
        assertEquals("Recipe", store.recordFor(first.address)?.snapshot?.name) // failed does not erase last sent
        assertEquals("Edited", store.attemptFor(first.address)?.snapshot?.name)
        val reloaded = MachineLeverRecipeStore(disk)
        assertEquals(store.recordFor(first.address), reloaded.recordFor(first.address))
        assertEquals(failed, reloaded.attemptFor(first.address)?.state)
    }

    @Test fun restartPendingIsUnknownAndAcknowledgementNeverVerifiesOrErases() {
        val disk = Disk(); val original = MachineLeverRecipeStore(disk)
        val token = requireNotNull(original.begin(snapshot()))
        val store = MachineLeverRecipeStore(disk)
        assertFalse(store.active); assertTrue(store.blocked)
        assertEquals(unknown, store.blockingAttempt?.state)
        assertFalse(store.complete(token, sent)) // old callback cannot convert restarted unknown to sent
        assertNull(store.recordFor(snapshot().address))
        assertTrue(store.acknowledge(token)); assertFalse(store.blocked)
        val record = store.attemptFor(snapshot().address)!!
        assertEquals(unknown, record.state); assertTrue(record.acknowledged)
        val second = requireNotNull(store.begin(snapshot(name = "Later")))
        assertFalse(store.complete(token, sent)); assertTrue(store.pending(second))
        assertTrue(disk.bytes!!.contains(token)) // later operations do not discard the unknown audit record
        assertTrue(store.complete(second, sent))
        assertEquals("Later", store.recordFor(snapshot().address)?.snapshot?.name)
    }

    @Test fun timeoutIsTerminalAndLateCallbacksCannotMutateANewerAttempt() {
        val store = MachineLeverRecipeStore(Disk())
        val token = requireNotNull(store.begin(snapshot()))
        assertFalse(store.acknowledge(token)) // pending cannot be unlocked while physical write may run
        assertTrue(store.complete(token, unknown)); assertTrue(store.blocked)
        assertFalse(store.complete(token, sent)); assertNull(store.recordFor(snapshot().address))
        assertTrue(store.acknowledge(token))
        val next = requireNotNull(store.begin(snapshot(name = "Next")))
        assertFalse(store.complete(token, failed)); assertTrue(store.pending(next))
        assertTrue(store.complete(next, sent)); assertFalse(store.complete(next, sent))
    }

    @Test fun persistenceFailuresFailClosedBeforeAndAfterDispatch() {
        val disk = Disk(); val store = MachineLeverRecipeStore(disk)
        disk.accept = false
        assertNull(store.begin(snapshot())); assertFalse(store.available); assertTrue(store.blocked)
        assertNull(disk.bytes)
        val disk2 = Disk(); val store2 = MachineLeverRecipeStore(disk2)
        val token = requireNotNull(store2.begin(snapshot()))
        disk2.throws = true
        assertFalse(store2.complete(token, sent))
        assertFalse(store2.available); assertTrue(store2.blocked)
        assertEquals(unknown, store2.attemptFor(snapshot().address)?.state)
        assertNull(store2.recordFor(snapshot().address))
        assertEquals(unknown, MachineLeverRecipeStore(disk2).attemptFor(snapshot().address)?.state)
        assertFalse(store2.acknowledge(token))
    }

    @Test fun corruptAndUnknownSchemaArePreservedAndBlockWrites() {
        for (bytes in listOf("oops", "{\"version\":2,\"records\":[]}", "{}")) {
            val disk = Disk().apply { this.bytes = bytes }; val store = MachineLeverRecipeStore(disk)
            assertFalse(store.available); assertTrue(store.blocked); assertNull(store.begin(snapshot()))
            assertEquals(bytes, disk.bytes); assertEquals(0, disk.writes)
        }
        val disk = Disk(); val original = MachineLeverRecipeStore(disk)
        val token = requireNotNull(original.begin(snapshot())); assertTrue(original.complete(token, sent))
        disk.bytes = disk.bytes!!.replace("Recipe", "Tampered")
        val corrupt = MachineLeverRecipeStore(disk)
        assertFalse(corrupt.available); assertNull(corrupt.recordFor(snapshot().address))
    }

    @Test fun acknowledgementPersistenceFailureKeepsUnknownBlockedAndOtherMachineSeparated() {
        val disk = Disk(); val store = MachineLeverRecipeStore(disk)
        val first = requireNotNull(store.begin(snapshot()))
        assertTrue(store.complete(first, sent))
        val secondAddress = "11:22:33:44:55:66"
        val second = requireNotNull(store.begin(snapshot(secondAddress, "Other machine")))
        assertTrue(store.complete(second, unknown))
        assertEquals("Recipe", store.recordFor(snapshot().address)?.snapshot?.name)
        assertNull(store.recordFor(secondAddress))
        disk.accept = false
        assertFalse(store.acknowledge(second)); assertTrue(store.blocked)
        val reloaded = MachineLeverRecipeStore(disk)
        assertTrue(reloaded.blocked)
        assertFalse(requireNotNull(reloaded.attemptFor(secondAddress)).acknowledged)
        assertEquals("Recipe", reloaded.recordFor(snapshot().address)?.snapshot?.name)
    }

    @Test fun invalidSnapshotCannotEnterJournalAndFramesAreCanonical() {
        val disk = Disk(); val store = MachineLeverRecipeStore(disk)
        for (value in listOf(snapshot().copy(frameHex = "00"), snapshot().copy(frameHex = "08825c0046031e5a000000960801900000000000"),
            snapshot().copy(address = ""), snapshot().copy(createdAtMs = -1))) {
            assertNull(store.begin(value))
        }
        assertEquals(0, disk.writes); assertTrue(store.available)
        val token = requireNotNull(store.begin(snapshot().copy(frameHex = snapshot().frameHex.uppercase())))
        assertEquals(snapshot().frameHex, store.blockingAttempt?.snapshot?.frameHex)
        assertTrue(store.complete(token, sent))
    }
}
