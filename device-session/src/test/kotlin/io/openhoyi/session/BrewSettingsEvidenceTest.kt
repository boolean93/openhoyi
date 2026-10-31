package io.openhoyi.session

import io.openhoyi.protocol.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class BrewSettingsEvidenceTest {
    private val parameters = StartParameters(true,true,3,7,92,136,false,0,20,35,18,0,150,5,400,130,0)
    private fun awake(f: CoffeeSessionFixture, now: Long) {
        f.now = now
        f.session.onNotification(f.session.generation, KnownGatt.coffeeNotify,
            f.hex("400023F02F1C770B00000000000000190321AF"))
    }
    @Test fun policyRequiresCurrentReadyConnectionAndRealNonfutureEvidence() {
        assertTrue(BrewSettingsEvidence.isAvailable(true,true,0,180_001))
        assertFalse(SettingsFreshness.isFresh(0,180_001))
        for (at in listOf(null,-1L,180_002L))
            assertFalse(BrewSettingsEvidence.isAvailable(true,true,at,180_001))
        assertFalse(BrewSettingsEvidence.isAvailable(false,true,0,180_001))
        assertFalse(BrewSettingsEvidence.isAvailable(true,false,0,180_001))
    }
    @Test fun continuousFreshIdleKeepsBrewingEligibleWithoutRenewingSettingsTimestamp() {
        for (start in listOf(false,true)) {
            val f = CoffeeSessionFixture()
            for (now in 1000L..181_000L step 1000) awake(f,now)
            val before = f.calls.size
            var result: OperationResult? = null
            if (start) f.session.startExtraction(parameters) { result = it }
            else f.session.setBrewWait(92,{true}) { result = it }
            assertEquals(before+1,f.calls.size); assertNull(result)
            f.complete(); assertTrue(result is OperationResult.Success)
            f.session.disconnect()
        }
    }
    @Test fun oldConfigurationStillCannotAuthorizeSettingPairedWriteOrCupReset() {
        val f = CoffeeSessionFixture(); awake(f,181_000)
        val before = f.calls.size
        val outcomes = mutableListOf<OperationResult>()
        f.session.writeSetting(MachineSettingChange.BrewTemperature(93),outcomes::add)
        f.session.writeSetting(MachineSettingChange.StandbyDelay(30,70),outcomes::add)
        f.session.resetCupCount(25,outcomes::add)
        assertEquals(3,outcomes.size); assertTrue(outcomes.all { it is OperationResult.Failed })
        assertEquals(before,f.calls.size)
        f.session.disconnect()
    }
    @Test fun disconnectAndNewAuthenticationCannotReuseOldConnectionSettings() {
        val f = CoffeeSessionFixture(); awake(f,181_000)
        assertNotNull(f.session.captureStartContext(parameters))
        f.session.disconnect()
        assertNull(f.session.captureStartContext(parameters))
        f.session.connect("other-device",CoffeeAuthentication(LocalDateTime.of(2026,9,20,12,0),"123456"))
        f.complete()
        f.complete(OperationResult.Success(listOf(
            CharacteristicInfo(KnownGatt.coffeeWrite,true,false,false,false),
            CharacteristicInfo(KnownGatt.coffeeNotify,false,false,true,false))))
        f.complete(); f.complete()
        awake(f,181_100)
        val before = f.calls.size
        var result: OperationResult? = null
        f.session.setBrewWait(92,{true}) { result = it }
        assertNull(f.session.captureStartContext(parameters)); assertTrue(result is OperationResult.Failed)
        assertEquals(before,f.calls.size)
        f.session.disconnect()
    }
    @Test fun oldConfigurationDoesNotBypassUnknownHostOrStaleIdleAtDispatch() {
        for (unknown in listOf(false,true)) {
            val f = CoffeeSessionFixture(); awake(f,181_000)
            f.session.setBrewWait(0,{true}) { }
            val before = f.calls.size
            var result: OperationResult? = null
            f.session.setBrewWait(92,{ !unknown }) { result = it }
            assertNull(result)
            if (!unknown) f.now += 1501
            f.complete()
            assertTrue(result is OperationResult.Failed); assertEquals(before,f.calls.size)
            f.session.disconnect()
        }
    }
}
