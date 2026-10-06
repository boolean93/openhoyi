# Machine write disconnection coordination

Move the five existing tracker disconnected calls from the coffee state observer into an owner-thread pure Kotlin coordinator. Keep the exact state != READY eligibility, original call order, original six counters and all snapshot/persistence/notification behavior. Preheat is special: every non-IDLE state becomes UNKNOWN and its token is invalidated; ordinary confirmed/failed states remain unchanged. No transport, durable clearing, retry or reconnection policy is added.

Parameterized tests first verify writing/waiting invalidation, late callback suppression, distinct live serial baselines, confirmed/failed differences and retained durable intent. RED via no-op implementation, then GREEN, exact Service wiring, full build and independent review. Existing detached Service tests are not registered Service state-observer proof; track latest cloud source explicitly. Hardware remains unavailable.
