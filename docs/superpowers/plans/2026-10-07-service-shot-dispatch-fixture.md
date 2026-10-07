# Detached Service shot dispatch fixture

Exercise real MobileService.startShot -> NativeDeviceHub -> ExtractionController -> DeviceSession -> GattQueue. Mock APK only; per-case isolated preferences/cache; detached Service; synthetic READY/context, guarded fake drivers, no system/component/permission/BLE access.

Three windows TARE, WEIGHT_START, FLOW_START. Each has ALLOW plus manual activity, curve selection change, Hub replacement, product not-ready, own shot record cleared/replaced, machine record introduced, product studio temperature change and settings tracker activity. Each also has owned BREW_WAIT allowed, cleared and replaced cases. Assert exact wire bytes/endpoints, durable record before every fake write, no late-success resend, own pending record retained at immediate completion, reload both record values. Honest scope: synthetic readiness, no physical tare or hardware proof. Ticker auto-clear ownership is a separate next audit, not exercised by immediate completion fixture.

Build test APK and lint locally, review independently, push isolated cloud emulator workflow, inspect real marker/results before claiming execution verified.
