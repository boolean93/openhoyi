# Ordinary write readback coordination

Goal: move setting/sleep-now/weekly-plan readback correlation and conditional durable clear out of Android Service, without changing protocol or safety behavior.

Architecture: OrdinaryWriteReadback has three typed entry points using existing trackers and MachineWriteRecoveryState. A typed feedback callback preserves existing confirmed/reconciled -> clear-failed ordering, including confirmation before storage clear. Boolean return denotes the exact old notification refresh condition (CONFIRMED and same pending kind, regardless of clear success). Android retains serial increments, sleep-fragment freshness gate, snapshot mutation and resource/tag mapping. CupResetReadback remains separate because its notification ordering differs. Do not flatten the three trackers' distinct UNKNOWN semantics: weekly plans only reconcile and keep pending; matching settings/asleep can confirm.

1. Parameterized tests across three kinds: fresh confirmation, pre-write/old/mismatched reads, UNKNOWN reconciliation/confirmation, false/throw clear, same-kind-only clear, local storage retry and exact callback/clear ordering. Run empty reference implementation RED.
2. Extract exact existing logic; GREEN device-session check including 92 scenarios.
3. Replace three Service blocks, mechanically check unchanged serials/freshness/snapshot/resource mapping. No byte encoder, queue, timers, retry or control gate change.
4. Independent review, full local checks including both app variants/test APK/lint. Commit/push; new source Android runtime evidence remains required.
