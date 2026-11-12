package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class ReleaseInstallLeaseTest {
    private var clock = 0L
    private val owner = Any(); private val page = Any()
    private fun token(generation: Long = 1, epoch: Long = 0) = ReleaseInstallLease.Token(generation, "target", owner, page, epoch)
    private fun matches(lease: ReleaseInstallLease, generation: Long = 1, epoch: Long = 0) = lease.matches(generation, "target", owner, page, epoch)
    @Test fun onlyTheExactOwnerPageTargetGenerationAndSafetyEpochCanConsumeOnce() {
        val lease = ReleaseInstallLease { clock }; lease.begin(token())
        assertTrue(matches(lease)); assertFalse(matches(lease,2)); assertFalse(matches(lease,epoch=1))
        assertFalse(lease.matches(1,"other",owner,page,0)); assertFalse(lease.matches(1,"target",Any(),page,0))
        assertTrue(lease.consume()); assertFalse(lease.consume())
    }
    @Test fun ordinaryPauseNavigationRecreationAndAnyBusyPermanentlyInvalidate() {
        val lease = ReleaseInstallLease { clock }
        lease.begin(token()); lease.paused(); assertFalse(lease.active)
        lease.ownerResumed(owner,page); assertFalse(lease.active)
        lease.begin(token()); lease.pageLeft(page); assertFalse(lease.active)
        lease.begin(token()); lease.ownerResumed(Any(),page); assertFalse(lease.active)
        lease.begin(token()); lease.revoke(); assertFalse(matches(lease))
        lease.ownerResumed(owner,page); assertFalse(lease.active)
    }
    @Test fun onlyMatchedPermissionResultMayPreserveIntentAndItStillCannotInstallWhileAwaiting() {
        val lease = ReleaseInstallLease { clock }; lease.begin(token())
        assertTrue(lease.requestPermission(11)); lease.paused(); lease.pageLeft(page)
        assertTrue(lease.active); assertFalse(lease.consume()); assertFalse(lease.requestPermission(12))
        assertTrue(lease.permissionMatches(11)); assertTrue(lease.permissionResult(11))
        assertTrue(matches(lease)); assertTrue(lease.consume()); assertFalse(lease.permissionResult(11))
    }
    @Test fun stalePermissionCallbackNeverCancelsNewFlowAndExpiredOrDestroyedFlowCannotResume() {
        val lease = ReleaseInstallLease { clock }; lease.begin(token()); lease.requestPermission(1)
        lease.revoke(); lease.begin(token(2)); lease.requestPermission(2)
        assertFalse(lease.permissionResult(1)); assertTrue(lease.active); assertTrue(lease.permissionMatches(2))
        lease.pageLeft(page, destroyed = true); assertFalse(lease.permissionResult(2)); assertFalse(lease.active)
        lease.begin(token()); lease.requestPermission(3); clock = 120001
        assertFalse(lease.permissionResult(3)); assertFalse(lease.active)
    }
    @Test fun permissionResultPostedBeforeANewIntentCannotContinueOrRevokeThatNewIntent() {
        val lease = ReleaseInstallLease { clock }; lease.begin(token()); lease.requestPermission(10)
        val expected = lease.snapshot()!!
        assertTrue(lease.permissionResult(10)); assertTrue(lease.owns(expected))
        lease.begin(token(2))
        // This is the posted callback's first guard. It must do nothing when ownership changed.
        assertFalse(lease.owns(expected)); assertTrue(matches(lease,2))
        val replacement = lease.snapshot()!!
        lease.begin(token(2)); assertFalse(lease.owns(replacement))
    }
    @Test fun restartStartsWithNoIntentAndPermissionBusyCannotResurrect() {
        val old = ReleaseInstallLease { clock }; old.begin(token()); old.requestPermission(1); old.revoke()
        assertFalse(old.permissionResult(1))
        val fresh = ReleaseInstallLease { clock }; assertFalse(fresh.active); assertFalse(fresh.consume())
    }
}
