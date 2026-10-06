# Preserve the latest explicit connection request

Root cause: connect calls public disconnect, whose state/transport/operation observers may synchronously issue a newer connect or explicit disconnect. The outer connect resumes, clears the new state and opens its originally requested device. Queue generation detects replacement, but cannot detect a newer disconnect without a new queue owner.

Design: assign a session-local request serial after validation and BOOKOO same-address no-op. Connect uses private terminal cleanup and proceeds only if its request serial is still current after all cleanup callbacks. Public disconnect and session failure invalidate outstanding requests. Keep credential validation before disturbance, same-address BOOKOO no-op, exact bytes, generation checks and no automatic retry.

TDD: both roles newer-connect terminal observers win; explicit disconnect without generation change cancels the outer connect; driver.close and old tare Unknown callbacks may issue newer connection/cancellation; invalid nested credentials do not invalidate valid outer work; same-address BOOKOO no-op and ordinary coffee reauthentication remain unchanged.

Verify focused RED/GREEN, all existing session checks, full native build/lint and independent review. This is host synchronous request ordering, not hardware validation or a complete proof of all lifecycle behavior.
