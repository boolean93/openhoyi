# Ordinary write recovery ownership

Extend existing instance/revision ownership to four ordinary controls. Capture immediately after successful registration; check at actual dispatch, clear with captured owner on known-unsent failure and immediate schedule confirmation, retain owner for readback confirmation/retry. Keep callback tracker correlation, UNKNOWN/reconciliation gates, exact bytes and ordered CONFIRMED then CLEAR_FAILED feedback.

Add explicit beforeClear callback overloads to OrdinaryWriteReadback and CupResetReadback; compatible callers retain original entry. Production Service passes captured ownership, so repeated confirmed readback cannot clear a later same-kind/same-address record. All four tracker's owner fields are set at registration, not on incoming readback; closure captures original local owner to avoid newer request fields.

Tests four kinds × allow, same-record rearm, missing owner, failed storage retry. Stub callback overload ignores callback for RED; real readback returns confirmation plus clear failure without touching replacement storage. Full build/review; existing detached Android control fixtures must still run. Add actual Service replacement fixture separately if time permits; no device actions.
