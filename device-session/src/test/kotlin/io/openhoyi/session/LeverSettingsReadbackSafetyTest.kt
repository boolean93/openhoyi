package io.openhoyi.session

import io.openhoyi.protocol.*
import org.junit.Assert.*
import org.junit.Test

/** Negative policy coverage only; no physical-device behavior is implied. */
class LeverSettingsReadbackSafetyTest {
    private val address = "AA:BB:CC:DD:EE:01"
    private val otherAddress = "AA:BB:CC:DD:EE:02"
    private val settings = (HoyiCodec.decode("830113FD5C007D0F350019006E".chunked(2)
        .map { it.toInt(16).toByte() }.toByteArray()) as DecodeResult.Valid).value as Settings

    private inner class Fixture(unknown: Boolean = false) {
        val tracker = SettingsWriteTracker()
        val token = requireNotNull(tracker.begin(MachineSettingChange.LeverMode(false, false)))
        var writes = 0
        val recovery = MachineWriteRecoveryState(object : MachineWriteRecoveryState.Storage {
            override fun read() = MachineWriteRecoveryState.Record(null, null)
            override fun write(record: MachineWriteRecoveryState.Record): Boolean { writes++; return true }
        }).also { check(it.arm(MachineWriteRecoveryState.Kind.SETTING, address)) }
        val owner = requireNotNull(recovery.captureOwnership())
        init {
            check(tracker.written(token, OperationResult.Success(), 1))
            if (unknown) check(tracker.timeout(token, 1))
        }
        fun context() = MachineWriteDispatchPermit.Context(recovery, address, address, false, false, false, false)
        fun request(unknown: Boolean): MachineWriteDispatchPermit.Request = if (unknown)
            MachineWriteDispatchPermit.Request.BoilerSettingsRecovery(tracker)
        else MachineWriteDispatchPermit.Request.BoilerSettingsRefresh(tracker)
        // Mirror Service's ownership conjunction, not just the pure dispatch permit.
        fun allowed(unknown: Boolean, context: MachineWriteDispatchPermit.Context = context(), candidate: Long = token) =
            recovery.owns(owner) && MachineWriteDispatchPermit.allows(candidate, request(unknown), context)
    }

    @Test fun mismatchedLeverReportCannotUseBoilerReconciliationWhileWaiting() {
        val f = Fixture(); val events = mutableListOf<OrdinaryWriteReadback.Event>()
        assertFalse(MachineSettingChange.LeverMode(false, false).matches(settings))
        assertFalse(OrdinaryWriteReadback.settings(f.tracker, f.recovery, 2, settings,
            { f.recovery.clear(f.owner) }, true, events::add))
        assertEquals(SettingsWriteTracker.State.WAITING_READBACK, f.tracker.state)
        assertTrue(f.recovery.pending); assertEquals(1, f.writes); assertTrue(events.isEmpty())
    }

    @Test fun mismatchedLeverReportAfterUnknownNeverClearsDurableProtectionOrClaimsSuccess() {
        val f = Fixture(true); val events = mutableListOf<OrdinaryWriteReadback.Event>()
        assertFalse(OrdinaryWriteReadback.settings(f.tracker, f.recovery, 2, settings,
            { f.recovery.clear(f.owner) }, true, events::add))
        assertEquals(SettingsWriteTracker.State.RECONCILED, f.tracker.state)
        assertTrue(f.recovery.pending); assertEquals(1, f.writes)
        assertEquals(listOf(OrdinaryWriteReadback.Event.RECONCILED), events)
        assertNull(f.tracker.boilerRecoveryToken())
    }

    @Test fun automaticAndExplicitRefreshRejectChangedIdentityTokenAndExtractionGates() {
        for (unknown in listOf(false, true)) {
            val f = Fixture(unknown); val c = f.context()
            assertTrue(f.allowed(unknown))
            for (blocked in listOf(c.copy(currentAddress = null), c.copy(currentAddress = otherAddress),
                c.copy(originalAddress = null), c.copy(originalAddress = otherAddress),
                c.copy(manualShotActive = true), c.copy(shotRecoveryPending = true),
                c.copy(shotActive = true), c.copy(preparationActive = true))) {
                assertFalse(f.allowed(unknown, blocked))
            }
            assertFalse(f.allowed(unknown, candidate = f.token - 1))
            assertFalse(f.allowed(unknown, candidate = f.token + 1))
            assertTrue(f.recovery.pending); assertEquals(1, f.writes)
        }
    }

    @Test fun replacementOwnerCannotReuseRefreshPermissionEvenForTheSameDevice() {
        for (unknown in listOf(false, true)) {
            val f = Fixture(unknown)
            check(f.recovery.clear(f.owner))
            check(f.recovery.arm(MachineWriteRecoveryState.Kind.SETTING, address))
            assertFalse(f.recovery.owns(f.owner)); assertFalse(f.allowed(unknown))
            assertFalse(f.recovery.clear(f.owner)); assertTrue(f.recovery.pending)
            assertEquals(3, f.writes)
        }
    }
}
