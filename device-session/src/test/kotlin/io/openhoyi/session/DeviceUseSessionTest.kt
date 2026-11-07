package io.openhoyi.session

import org.junit.Assert.*
import org.junit.Test

class DeviceUseSessionTest {
    private fun stamp(t:Long,boot:Int=1,wall:Long=100_000+t)=DeviceUseSession.Stamp(t,wall,boot)
    @Test fun shortRecreationKeepsPauseButLongAbsenceStartsNewSession() {
        val session=DeviceUseSession()
        assertTrue(session.visibility(true,stamp(0)))
        session.pause(DeviceRole.BOOKOO,true)
        session.visibility(false,stamp(100))
        val restarted=DeviceUseSession(session.saved)
        assertFalse(restarted.visibility(true,stamp(1_000)))
        assertTrue(restarted.saved!!.scalePaused)
        restarted.visibility(false,stamp(2_000))
        assertTrue(restarted.visibility(true,stamp(2_000+AutoConnectTiming.NEW_SESSION_MS)))
        assertFalse(restarted.saved!!.scalePaused)
    }
    @Test fun continuousUseDoesNotExpireAndRepeatedBackgroundDoesNotExtendAbsence() {
        val session=DeviceUseSession()
        session.visibility(true,stamp(0));session.pause(DeviceRole.COFFEE,true)
        session.visibility(true,stamp(4_000_000))
        assertTrue(session.saved!!.coffeePaused)
        session.visibility(false,stamp(4_000_100))
        session.visibility(false,stamp(5_000_000))
        assertTrue(session.visibility(true,stamp(5_800_100)))
        assertFalse(session.saved!!.coffeePaused)
    }
    @Test fun rebootOrElapsedResetStartsFreshButWallClockRollbackDoesNotErasePause() {
        val session=DeviceUseSession()
        session.visibility(true,stamp(5_000));session.pause(DeviceRole.BOOKOO,true)
        val restored=DeviceUseSession(session.saved)
        assertFalse(restored.visibility(true,stamp(6_000,wall=0)))
        assertTrue(restored.saved!!.scalePaused)
        assertTrue(DeviceUseSession(restored.saved).visibility(true,stamp(7_000,boot=2)))
        assertTrue(DeviceUseSession(restored.saved).visibility(true,stamp(1)))
    }
    @Test fun scaleWaitsPastTenMinutesAndRequestsCoalesceWithoutBreakingScanThrottle() {
        val policy=ReconnectPolicy();policy.foreground(0,true)
        assertTrue(policy.shouldAttempt(0,false));policy.attemptFinished(5_000)
        policy.requestSoon(5_001)
        assertFalse(policy.shouldAttempt(9_999,false))
        assertTrue(policy.shouldAttempt(10_000,false));policy.attemptFinished(15_000)
        assertTrue(policy.shouldAttempt(700_000,false));policy.attemptFinished(705_000)
        assertFalse(policy.shouldAttempt(729_999,false))
        assertTrue(policy.shouldAttempt(730_000,false))
    }
    @Test fun automaticScaleCallbackCannotBypassManualShotOrUnknownRecovery() {
        fun allowed(role:DeviceRole=DeviceRole.BOOKOO,manual:Boolean=false,paused:Boolean=false,
            enabled:Boolean=true,selecting:Boolean=false,shot:ExtractionState=ExtractionState.IDLE,
            pending:Boolean=false,settings:Boolean=false)=AutomaticConnectionGate.allows(role,enabled,paused,true,
                selecting,manual,shot,pending,settings,false)
        assertTrue(allowed())
        assertFalse(allowed(manual=true));assertFalse(allowed(paused=true));assertFalse(allowed(enabled=false))
        assertFalse(allowed(selecting=true));assertFalse(allowed(pending=true));assertFalse(allowed(settings=true))
        assertFalse(allowed(shot=ExtractionState.RUNNING))
        assertFalse(allowed(shot=ExtractionState.OUTCOME_UNKNOWN))
        // Preserve same-machine read-only recovery; the host still checks target matching.
        assertTrue(allowed(role=DeviceRole.COFFEE,shot=ExtractionState.OUTCOME_UNKNOWN,pending=true))
        assertFalse(allowed(role=DeviceRole.COFFEE,manual=true))
    }
    @Test fun targetRemovalAndUnsupportedAreNotUserPauses() {
        val policy=ReconnectPolicy();policy.foreground(0,true)
        policy.observeScaleState(1,DeviceState.UNSUPPORTED);policy.setPaused(false)
        assertFalse(policy.shouldAttempt(20_000,false))
        policy.targetChanged(false,30_000)
        assertFalse(policy.shouldAttempt(40_000,false))
        policy.setPaused(true);policy.targetChanged(true,50_000)
        assertFalse(policy.shouldAttempt(60_000,false))
        policy.setPaused(false)
        assertTrue(policy.shouldAttempt(60_000,false))
    }
    @Test fun newSessionDoesNotBypassCoffeeCredentialBlock() {
        val policy=CoffeeReconnectPolicy();policy.foreground(0);policy.ensureKnown("AA:BB:CC:DD:EE:01",0)
        policy.disable();policy.setPaused(true);policy.background();policy.setPaused(false);policy.foreground(1_000_000)
        assertNull(policy.shouldAttempt(1_000_000))
    }
}
