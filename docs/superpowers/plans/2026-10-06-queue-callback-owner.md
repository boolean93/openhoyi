# Bind callback and timeout cleanup to their original queue owner

Root causes: deliver catches an observer exception and disconnects the queue's current generation, even if that observer or an earlier detached waiter explicitly opened a new owner. tick disconnects at a timeout and then unconditionally invalidates the current host after Unknown/Cancelled observers may have reconnected.

1. Add failing tests for Success-observer open+throw, timeout Unknown-observer reconnect, old detached Cancelled observer failure after a new owner opens, and reconnect during callback-failure cleanup. Preserve same-owner callback failure/timeout shutdown, no automatic retry, old waiter settlement and exactly-once results.
2. Capture generation in Pending at enqueue. Consolidate owner-bound invalidation: disconnect only matching generation, notify invalidated only if still that generation after callbacks, retain first reporting exception and suppress distinct later failures. Apply to execute exception, observer exception and timeout. Preserve current explicit-false, guard rejection and no-owner enqueue paths.
3. Run actual RED/GREEN and old cleanup/execute tests, full production build/lint, independent review, automatic exact-source cloud checks. Keep prior local metadata edits.

Limits: host queue reentrancy with fake drivers, not real callbacks/hardware. No frame or timing threshold changes. Reentrant open during open's own replacement cleanup, and broader DeviceSession.connect callback reentrancy are separate audit boundaries; not claimed solved by this change.
