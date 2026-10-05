# Actual Service registration failure checks

Scope: isolated Mock instrumentation only. Keep production protocol, Service gates and transport unchanged.

1. Add a detached MobileService fixture with mock=null, synthetic READY/fresh idle/settings/schedule and a validated selected curve. Block Android component/system/BLE access, replace both GATT drivers, isolate preferences and close callbacks/trace storage.
2. Exercise SETTING, CUP_RESET, SLEEP_SCHEDULE, SLEEP_NOW and BREW_WAIT against storage returning false and throwing. For each, call the actual entry twice: require the exact registration-failure resource, write attempt increments, attempted record kind/address, WRITING at the persistence barrier, FAILED (Brew IDLE) after failure, no pending record, no preference/snapshot/baseline mutation, no GATT execution.
3. Add an instrumentation completion marker and require it in the existing lifecycle script. Review independently, build test APK and lint, then push and read actual cloud results when available.

Limits: detached Service, synthetic authentication/readback, inactive queues, fake storage failures. No registered lifecycle, OS crash atomicity or physical machine safety proof. No successful write fixture or hardware control in this task.
