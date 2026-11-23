package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class BoilerSwitchGestureGateTest {
    @Test fun freshFingerDownCannotWriteIfOwnerOrReportedBitChanges() {
        val a=Any();val b=Any()
        val original=a to false
        assertTrue(BoilerSwitchGestureGate.allows(original,a to false,original,true))
        assertFalse(BoilerSwitchGestureGate.allows(b to false,b to false,original,true))
        assertFalse(BoilerSwitchGestureGate.allows(a to true,a to true,original,true))
        assertFalse(BoilerSwitchGestureGate.allows(original,null,original,true))
    }
    @Test fun postedClickStillUsesDownIdentityAfterNativeTouchHasEnded() {
        val old=Any() to true
        val replacement=Any() to true
        assertFalse(BoilerSwitchGestureGate.allows(replacement,replacement,old,touching=true))
        assertTrue(BoilerSwitchGestureGate.allows(old,old,old,touching=true))
    }
    @Test fun staleFingerDownNeverUpgradesButLaterKeyboardClickCanBeFresh() {
        val current=Any() to true
        assertFalse(BoilerSwitchGestureGate.allows(current,current,null,true))
        assertTrue(BoilerSwitchGestureGate.allows(current,current,null,false))
    }
}
