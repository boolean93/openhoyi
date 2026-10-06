# Actual Service retention after transport exceptions

Scope: Android isolated Mock instrumentation. Production code unchanged.

1. Use detached MobileService mock=null with its real synchronous SharedPreferences recovery adapter, isolated UUID preferences/trace directory, synthetic READY/fresh settings/idle/weekly plan, validated selected curve.
2. Open only the coffee fake queue; retain GuardedGattDriver and replace delegate with a faulting fake. Populate DeviceSession's readback gates. Invoke each of five actual control entry methods; require exact permitted opcode bytes at fake dispatch, durable kind/address already committed there, UNKNOWN tracker and exact unknown event after throw, session FAILED and closed queue, no scale sends and exactly one fake execution.
3. Retry all five controls and startShot: each must return the durable warning without sending. Explicit acknowledgement without qualified same-device evidence must retain the record. A newly attached Service instance using the same fixture preferences must load the durable record and block all six entries, rather than relying on the old in-memory tracker.
4. Keep running=false, reject all system/BLE/component access, verify original preference maps unchanged, remove handlers/close fake owners/trace and delete fixture preferences. Add and require a specific instrumentation marker; compile/lint, independent review, then read actual cloud runtime output.

Limits: detached Service/new instance only, not a process restart or registered lifecycle. Synthetic authentication/readbacks, fake dispatch and local commit do not prove physical submission, execution or OS crash atomicity. No real BLE or installation.
