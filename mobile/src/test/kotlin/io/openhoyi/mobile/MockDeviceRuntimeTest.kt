package io.openhoyi.mobile

import io.openhoyi.protocol.ExtractionTelemetry
import io.openhoyi.protocol.IdleTelemetry
import io.openhoyi.protocol.MachineSettingChange
import io.openhoyi.protocol.WeeklySleepDay
import io.openhoyi.protocol.WeeklySleepSchedule
import io.openhoyi.session.DeviceState
import io.openhoyi.session.ExtractionState
import io.openhoyi.session.OperationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class MockDeviceRuntimeTest {
    @Test fun idleShotAndAutomaticEndUseOnlySyntheticSamples() {
        val mock = MockDeviceRuntime()
        val idle = mock.sample(1_000)
        assertEquals(DeviceState.READY, idle.coffeeState)
        assertEquals(DeviceState.READY, idle.scaleState)
        assertTrue(idle.coffee is IdleTelemetry)
        assertEquals(1250, idle.weight?.weightHundredthsGram)
        val schedule = WeeklySleepSchedule.fromReadback(idle.sleepFirst, idle.sleepSecond)
        assertEquals(7, schedule?.days?.size)
        assertEquals(5, schedule?.days?.count { it.enabled })

        mock.start(2_000)
        val running = mock.sample(7_000)
        assertEquals(ExtractionState.RUNNING, mock.shotState)
        assertTrue(running.coffee is ExtractionTelemetry)
        assertEquals(350, running.weight?.weightHundredthsGram)

        val ended = mock.sample(34_000)
        assertEquals(ExtractionState.ENDED_OBSERVED, mock.shotState)
        assertTrue(ended.coffee is IdleTelemetry)
        assertEquals(2240, ended.weight?.weightHundredthsGram)
    }

    @Test fun settingsUpdateSyntheticReadbackWithoutChangingUnrelatedFields() {
        val mock = MockDeviceRuntime()
        val before = requireNotNull(mock.sample(1_000).settings)
        assertNull(mock.changeSetting(MachineSettingChange.BrewTemperature(96)))
        assertNull(mock.changeSetting(MachineSettingChange.Light(true)))
        assertNull(mock.changeSetting(MachineSettingChange.StandbyDelay(60, before.standbyTemperatureC)))
        val after = requireNotNull(mock.sample(2_000).settings)
        assertEquals(96, after.brewTemperatureC)
        assertEquals(60, after.standbyMinutes)
        assertEquals(0x08, after.flags and 0x08)
        assertEquals(before.steamTemperatureC, after.steamTemperatureC)
        assertEquals(before.cupCount, after.cupCount)
        assertEquals(MachineSettingChange.StandbyDelay(60, before.standbyTemperatureC), mock.lastSetting)
    }

    @Test fun allSettingFamiliesAppearInSyntheticReadback() {
        val mock = MockDeviceRuntime()
        val changes = listOf(
            MachineSettingChange.RunMode(true), MachineSettingChange.WaterSupply(true),
            MachineSettingChange.SleepScheduleEnabled(false), MachineSettingChange.StandbyTemperature(75, 30),
            MachineSettingChange.LeverMode(true, true), MachineSettingChange.BrewCompensation(3),
            MachineSettingChange.SteamTemperature(130), MachineSettingChange.BrewHeating(false),
            MachineSettingChange.SteamHeating(false), MachineSettingChange.Light(true),
        )
        changes.forEach { assertNull(it.toString(), mock.changeSetting(it)) }
        val settings = requireNotNull(mock.sample(2_000).settings)
        changes.forEach { assertTrue(it.toString(), it.matches(settings)) }
        assertEquals(75, settings.standbyTemperatureC)
        assertEquals(30, settings.brewCompensationTenthsC)
    }

    @Test fun oneDayScheduleEditPreservesOtherSixDaysAndRejectsStaleBaseline() {
        val mock = MockDeviceRuntime()
        val baseline = requireNotNull(WeeklySleepSchedule.fromReadback(
            mock.sample(1_000).sleepFirst, mock.sample(1_000).sleepSecond))
        val days = baseline.days.toMutableList()
        days[2] = WeeklySleepDay(false, days[2].time.copy(sleepHour = 22))
        val target = WeeklySleepSchedule(days)
        assertNull(mock.changeSchedule(baseline, target))
        val readback = requireNotNull(WeeklySleepSchedule.fromReadback(
            mock.sample(2_000).sleepFirst, mock.sample(2_000).sleepSecond))
        assertEquals(target.days, readback.days)
        assertTrue(mock.scheduleChanged)
        assertTrue(mock.changeSchedule(baseline, target) != null)
    }

    @Test fun tareCupResetAndSleepAffectOnlyMockReadback() {
        val mock = MockDeviceRuntime()
        assertTrue(requireNotNull(mock.sample(1_000).weight).weightHundredthsGram > 0)
        assertNull(mock.tare())
        assertEquals(0, mock.sample(2_000).weight?.weightHundredthsGram)
        assertTrue(mock.tareChanged)
        assertTrue(mock.resetCupCount(41) != null)
        assertNull(mock.resetCupCount(42))
        assertEquals(0, mock.sample(3_000).settings?.cupCount)
        assertEquals(0, (mock.sample(3_000).coffee as IdleTelemetry).cupCount)
        assertNull(mock.sleepNow())
        assertEquals(1, (mock.sample(4_000).coffee as IdleTelemetry).sleepStateRaw)
        assertTrue(mock.changeSetting(MachineSettingChange.BrewTemperature(95)) != null)
    }

    @Test fun simulatedShotBlocksSettingAndSleepMutations() {
        val mock = MockDeviceRuntime()
        mock.start(1_000)
        assertTrue(mock.changeSetting(MachineSettingChange.BrewTemperature(95)) != null)
        assertTrue(mock.tare() != null)
        assertTrue(mock.sleepNow() != null)
        assertEquals(93, mock.sample(2_000).settings?.brewTemperatureC)
    }

    @Test fun studioPreheatProducesFreshTemperatureStepsAndCanBeCancelled() {
        val mock = MockDeviceRuntime()
        assertNull(mock.changeSetting(MachineSettingChange.RunMode(true)))
        assertNull(mock.beginPreheat(99, 1_000))
        assertTrue(mock.preheatActive)
        assertEquals(9300, (mock.sample(1_000).coffee as IdleTelemetry).brewTemperatureHundredthsC)
        assertEquals(9600, (mock.sample(3_500).coffee as IdleTelemetry).brewTemperatureHundredthsC)
        assertEquals(9900, (mock.sample(6_000).coffee as IdleTelemetry).brewTemperatureHundredthsC)
        assertTrue(mock.changeSetting(MachineSettingChange.BrewTemperature(95)) != null)
        assertNull(mock.cancelPreheat(6_000))
        assertEquals(9900, (mock.sample(8_000).coffee as IdleTelemetry).brewTemperatureHundredthsC)
        assertTrue(!mock.preheatActive)
    }

    @Test fun preheatUsesTemperatureCompensationAndRejectsInvalidState() {
        val mock = MockDeviceRuntime()
        assertTrue(mock.beginPreheat(99, 1_000) != null)
        assertNull(mock.changeSetting(MachineSettingChange.RunMode(true)))
        assertNull(mock.changeSetting(MachineSettingChange.BrewCompensation(2)))
        assertNull(mock.beginPreheat(99, 1_000))
        val arrived = mock.sample(6_000).coffee as IdleTelemetry
        assertEquals(10100, arrived.brewTemperatureHundredthsC)
        assertEquals(9900, BrewPreparation.correctedTemperature(arrived.brewTemperatureHundredthsC,
            requireNotNull(mock.sample(6_000).settings).brewCompensationTenthsC))
        assertTrue(mock.beginPreheat(99, 6_000) != null)
        assertNull(mock.cancelPreheat(6_000))
        mock.start(7_000)
        assertTrue(mock.beginPreheat(100, 7_000) != null)
    }

    @Test fun syntheticTemperatureCanDriveTheSamePreparationStateMachine() {
        val mock = MockDeviceRuntime()
        val preparation = BrewPreparation()
        assertNull(mock.changeSetting(MachineSettingChange.RunMode(true)))
        val token = requireNotNull(preparation.begin("factory-v3-001", 99))
        assertNull(mock.beginPreheat(99, 1_000))
        assertTrue(preparation.written(token, OperationResult.Success(), 0))
        val early = mock.sample(2_000).coffee as IdleTelemetry
        assertTrue(!preparation.observe(1, early.brewTemperatureHundredthsC))
        val ready = mock.sample(6_000).coffee as IdleTelemetry
        assertTrue(preparation.observe(2, ready.brewTemperatureHundredthsC))
        assertTrue(preparation.matches("factory-v3-001", 99))
        assertNull(mock.cancelPreheat(6_000))
        preparation.consumed()
        assertEquals(BrewPreparation.State.IDLE, preparation.state)
    }
}
