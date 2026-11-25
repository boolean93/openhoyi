package io.openhoyi.mobile

import io.openhoyi.protocol.MachineSettingChange
import io.openhoyi.session.SettingsWriteTracker.State
import org.junit.Assert.*
import org.junit.Test

class HomeBoilerSwitchPresentationTest {
    @Test fun onlyOwnedTypedBoilerProgressHidesItsGenericWarning() {
        val change=MachineSettingChange.BrewHeating(false)
        for(state in listOf(State.WRITING,State.WAITING_READBACK))
            assertTrue(HomeBoilerSwitchPresentation.localProgress(true,change,state,false,false))
        assertTrue(HomeBoilerSwitchPresentation.localProgress(true,change,State.UNKNOWN,true,false))
        assertTrue(HomeBoilerSwitchPresentation.localProgress(true,change,State.UNKNOWN,false,true))
        assertFalse(HomeBoilerSwitchPresentation.localProgress(true,change,State.UNKNOWN,false,false))
        assertFalse(HomeBoilerSwitchPresentation.localProgress(false,change,State.WRITING,true,true))
        assertFalse(HomeBoilerSwitchPresentation.localProgress(true,null,State.WRITING,true,true))
        assertFalse(HomeBoilerSwitchPresentation.localProgress(true,MachineSettingChange.Light(false),State.WRITING,true,true))
    }

    @Test fun progressShowsTheMatchingBoilerAndWaitsForMachineReadback() {
        val p = HomeBoilerSwitchPresentation
        val brew = MachineSettingChange.BrewHeating(true)
        val steam = MachineSettingChange.SteamHeating(false)
        assertEquals(R.string.home_boiler_turning_on, p.label(true, false, brew, State.WRITING))
        assertEquals(R.string.home_boiler_turning_off, p.label(false, true, steam, State.WRITING))
        assertEquals(R.string.home_boiler_turning_on, p.label(true, false, brew, State.WAITING_READBACK))
        assertEquals(R.string.home_boiler_turning_off, p.label(false, true, steam, State.WAITING_READBACK))
        assertEquals(R.string.setting_on, p.label(false, true, brew, State.WAITING_READBACK))
    }
    @Test fun onlyMatchingOwnedIntentHoldsPendingPresentation() {
        val p = HomeBoilerSwitchPresentation
        val brew = MachineSettingChange.BrewHeating(true)
        for (state in listOf(State.WRITING, State.WAITING_READBACK)) {
            assertTrue(p.pendingAppearance(true, brew, state, ownsPending = true))
            assertFalse(p.pendingAppearance(false, brew, state, ownsPending = true))
            assertFalse(p.pendingAppearance(true, brew, state, ownsPending = false))
        }
        for (terminal in listOf(State.FAILED, State.UNKNOWN, State.RECONCILED)) {
            assertFalse(p.pendingAppearance(true, brew, terminal, ownsPending = true))
        }
    }

    @Test fun normalBoilerToggleNeverAddsTextUnderEitherSwitch() {
        val p = HomeBoilerSwitchPresentation
        for (brew in listOf(true, false)) for (target in listOf(true, false)) {
            val change = if (brew) MachineSettingChange.BrewHeating(target) else MachineSettingChange.SteamHeating(target)
            for (state in listOf(State.WRITING, State.WAITING_READBACK, State.CONFIRMED)) {
                val reported = if (state == State.CONFIRMED) target else !target
                assertFalse(p.showInline(p.label(brew, reported, change, state)))
                assertFalse(p.showInline(p.label(!brew, false, change, state)))
            }
            assertFalse(p.showInline(p.label(brew, !target, change, State.UNKNOWN, recovering = true)))
            assertFalse(p.showInline(p.label(brew, !target, change, State.UNKNOWN, reconnecting = true)))
        }
        assertFalse(p.showInline(R.string.settings_unknown))
        for (label in listOf(R.string.home_boiler_refresh, R.string.home_setting_failed,
            R.string.home_boiler_retry_on, R.string.home_boiler_retry_off, R.string.home_boiler_preheat_occupied)) {
            assertTrue(p.showInline(label))
        }
    }

    @Test fun failedUnknownAndConfirmedNeverInventAReportedState() {
        val change=MachineSettingChange.BrewHeating(true)
        assertEquals(R.string.home_boiler_refresh,HomeBoilerSwitchPresentation.label(true,false,change,State.UNKNOWN))
        assertEquals(R.string.home_boiler_syncing,HomeBoilerSwitchPresentation.label(true,false,change,State.UNKNOWN,recovering=true))
        assertEquals(R.string.home_boiler_reconnecting,HomeBoilerSwitchPresentation.label(true,false,change,State.UNKNOWN,reconnecting=true))
        assertEquals(R.string.home_boiler_retry_on,HomeBoilerSwitchPresentation.label(true,false,change,State.RECONCILED))
        assertEquals(R.string.home_setting_failed,HomeBoilerSwitchPresentation.label(true,false,change,State.FAILED))
        assertEquals(R.string.setting_on,HomeBoilerSwitchPresentation.label(true,true,change,State.CONFIRMED))
        assertEquals(R.string.setting_off,HomeBoilerSwitchPresentation.label(true,false,MachineSettingChange.Light(true),State.WRITING))
    }
}
