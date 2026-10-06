# Preheat watchdog coordination

Extract the existing600000ms expiry branch into device-session BrewPreparationWatchdog. Host retains Handler scheduling, actual cancellation gate/transport and ResourceMessage mapping. Pure helper checks original token WAITING_TEMP/READY before invoking host cancel exactly once; null cancel block and non-UNKNOWN emits requested; otherwise only successful original-token timedOut emits blocked and requests notification refresh. Preserve synchronous cancel result callbacks' ownership and avoid a second transition after callback/disconnect/newer request. Do not treat requested/cancel-write as device confirmation or clear recovery intent.

1. Parameterized tests for WAITING_TEMP and READY: blocked expiry, async cancellation, sync success/unknown, disconnect during cancellation, newer request during blocked cancellation, stale/consumed timer, already cancelling/cancel-written, writing timer. Empty reference RED then exact logic GREEN.
2. Wire Service with same600000ms constant/resource/tag/notification placement. No encoding/storage/control gate changes.
3. Independent review, full local build/session tests/mobile unit tests/dual lint/test APK. Cloud runtime required for actual Service cancellation fixture; this helper tests callable expiry logic, not Android wall clock.
