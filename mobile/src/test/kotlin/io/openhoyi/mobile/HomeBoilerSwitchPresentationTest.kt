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

    @Test fun pendingDirectionAppearsOnlyOnTheMatchingBoiler() {
        for(state in listOf(State.WRITING,State.WAITING_READBACK)) {
            assertEquals(R.string.home_boiler_turning_on,HomeBoilerSwitchPresentation.label(true,false,MachineSettingChange.BrewHeating(true),state))
            assertEquals(R.string.home_boiler_turning_off,HomeBoilerSwitchPresentation.label(false,true,MachineSettingChange.SteamHeating(false),state))
            assertEquals(R.string.setting_off,HomeBoilerSwitchPresentation.label(false,true,MachineSettingChange.BrewHeating(false),state))
        }
    }
    @Test fun onlyMatchingOwnedSwitchStaysOptimisticUntilConfirmationOrTerminalOutcome() {
        val p = HomeBoilerSwitchPresentation
        val brew = MachineSettingChange.BrewHeating(true)
        val steam = MachineSettingChange.SteamHeating(false)
        for (state in listOf(State.WRITING, State.WAITING_READBACK)) {
            assertTrue(p.checked(true, false, brew, state, ownsPending = true))
            assertFalse(p.checked(false, true, steam, state, ownsPending = true))
            assertTrue(p.checked(false, true, brew, state, ownsPending = true))
            assertFalse(p.checked(true, false, brew, state, ownsPending = false))
            assertTrue(p.pendingAppearance(true, brew, state, ownsPending = true))
            assertFalse(p.pendingAppearance(false, brew, state, ownsPending = true))
            assertFalse(p.pendingAppearance(true, brew, state, ownsPending = false))
        }
        assertTrue(p.checked(true, true, brew, State.CONFIRMED, ownsPending = false))
        for (terminal in listOf(State.FAILED, State.UNKNOWN, State.RECONCILED)) {
            assertFalse(p.checked(true, false, brew, terminal, ownsPending = true))
            assertFalse(p.pendingAppearance(true, brew, terminal, ownsPending = true))
        }
        assertTrue(p.checked(true, null, brew, State.UNKNOWN, ownsPending = true, recoveryRunning = true))
        assertFalse(p.checked(true, null, brew, State.UNKNOWN, ownsPending = false, recoveryRunning = true))
    }

    @Test fun transientLabelsStayAccessibleButDoNotAddAVisibleRow() {
        val p = HomeBoilerSwitchPresentation
        for (label in listOf(R.string.setting_on, R.string.setting_off, R.string.home_boiler_turning_on,
            R.string.home_boiler_turning_off, R.string.home_boiler_syncing, R.string.home_boiler_reconnecting)) {
            assertFalse(p.showInline(label))
        }
        for (label in listOf(R.string.home_boiler_refresh, R.string.home_setting_failed,
            R.string.home_boiler_retry_on, R.string.home_boiler_preheat_occupied)) {
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
        assertEquals(R.string.setting_off,HomeBoilerSwitchPresentation.label(true,true,change,State.CONFIRMED))
        assertEquals(R.string.setting_on,HomeBoilerSwitchPresentation.label(true,false,MachineSettingChange.Light(true),State.WRITING))
    }
}
