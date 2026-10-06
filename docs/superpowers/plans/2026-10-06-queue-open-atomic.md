# Keep queue replacement atomic

Root cause: open calls disconnect before incrementing generation. A detached observer (or driver.close) can call open and submit an operation during that cleanup; the outer open then increments generation again while the nested operation remains in flight. Its actual callback cannot match the queue generation.

Design: guard only the synchronous open replacement call stack. Nested open while replacing throws a clear IllegalStateException before changing generation or transport. The guard resets in finally on normal completion and reporting failure. Explicit opens from ordinary completion, timeout or disconnect observers remain allowed (previous owner-isolation tests must stay green). No retry or payload/timer change.

Tests first: observer nested open with explicit rejection capture; uncaught nested attempt with all old waiters settling; nested attempt from driver.close; cleanup reporting error aborts replacement and resets the guard for later explicit recovery; ordinary replacement matches fresh driver tokens/generation and ignores stale callbacks.

Verify JVM RED/GREEN with existing queue tests, full offline build/lint, independent review and exact-source cloud checks. Keep prior metadata edits and inspect pending Service fixture run.

Boundary: only GattQueue.open atomicity. Broader DeviceSession.connect/initialization observer reentrancy is a separate audit, not claimed solved. No actual BLE or physical-device safety guarantee.
