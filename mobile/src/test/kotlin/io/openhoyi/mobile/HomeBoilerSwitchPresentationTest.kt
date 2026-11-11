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
    @Test fun failedUnknownAndConfirmedNeverInventAReportedState() {
        val change=MachineSettingChange.BrewHeating(true)
        assertEquals(R.string.home_boiler_refresh,HomeBoilerSwitchPresentation.label(true,false,change,State.UNKNOWN))
        assertEquals(R.string.home_boiler_syncing,HomeBoilerSwitchPresentation.label(true,false,change,State.UNKNOWN,recovering=true))
        assertEquals(R.string.home_boiler_reconnecting,HomeBoilerSwitchPresentation.label(true,false,change,State.UNKNOWN,reconnecting=true))
        assertEquals(R.string.home_boiler_retry_on,HomeBoilerSwitchPresentation.label(true,false,change,State.RECONCILED))
        assertEquals(R.string.setting_on,HomeBoilerSwitchPresentation.label(true,false,change,State.FAILED))
        assertEquals(R.string.setting_off,HomeBoilerSwitchPresentation.label(true,true,change,State.CONFIRMED))
        assertEquals(R.string.setting_on,HomeBoilerSwitchPresentation.label(true,false,MachineSettingChange.Light(true),State.WRITING))
    }
}
