package io.openhoyi.session

import org.junit.Assert.*
import org.junit.Test

class CoffeeRecoveryTimingTest {
    private val address="AA:BB:CC:DD:EE:01"
    @Test fun immediateRestoreThenTwoThreeFiveSecondRetriesAndLongWait(){
        val p=CoffeeReconnectPolicy().apply {foreground(0);ensureKnown(address,0)}
        assertEquals(address,p.shouldAttempt(0))
        p.connectionFailed(100);assertNull(p.shouldAttempt(2099));assertEquals(address,p.shouldAttempt(2100))
        p.connectionFailed(2200);assertNull(p.shouldAttempt(5199));assertEquals(address,p.shouldAttempt(5200))
        p.connectionFailed(5300);assertNull(p.shouldAttempt(10299));assertEquals(address,p.shouldAttempt(10300))
        p.connectionFailed(31_000);assertNull(p.shouldAttempt(60_999));assertEquals(address,p.shouldAttempt(61_000))
    }
    @Test fun activityShortensWaitButCoalescesAndPreservesEveryBlock(){
        val p=CoffeeReconnectPolicy().apply {foreground(0);ensureKnown(address,0)}
        assertEquals(address,p.shouldAttempt(0));p.requestSoon(1);assertNull(p.shouldAttempt(2_000))
        p.connectionFailed(100);repeat(5){p.requestSoon(200)}
        assertNull(p.shouldAttempt(1999));assertEquals(address,p.shouldAttempt(2000))
        p.connectionFailed(2100);p.background();p.requestSoon(2200);assertNull(p.shouldAttempt(20_000))
        p.foreground(3000);assertNull(p.shouldAttempt(3999));assertEquals(address,p.shouldAttempt(4000))
        p.connectionFailed(4100);p.manualDisconnect();p.requestSoon(10_000);p.foreground(10_000);assertNull(p.shouldAttempt(10_000))
        p.explicitConnect(address,11_000,remembered=true);p.connectionFailed(11_100);p.disable()
        p.requestSoon(20_000);p.foreground(20_000);assertNull(p.shouldAttempt(20_000))
        p.explicitConnect(address,21_000,remembered=true);p.connectionFailed(21_100);p.environmentUnavailable()
        p.requestSoon(24_000);assertNull(p.shouldAttempt(24_000))
        p.environmentAvailable();assertEquals(CoffeeReconnectPolicy.Dispatch.NONE,p.dispatch(24_000,false,true){error("safety gate bypassed")})
    }
    @Test fun explicitRetryRecoversOnlyKnownTargetAndDoesNotFollowOldMachine(){
        val p=CoffeeReconnectPolicy().apply {foreground(0);explicitConnect(address,0,remembered=true)}
        p.connectionFailed(100);assertEquals(address,p.shouldAttempt(2100))
        p.explicitConnect("AA:BB:CC:DD:EE:02",2200);p.connectionFailed(2300)
        assertNull(p.shouldAttempt(30_000))
    }
}
