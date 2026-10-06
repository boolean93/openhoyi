# Shared ordinary write readback watchdog

Continue approved native business separation. Setting/cup reset/weekly schedule/sleep-now each schedule a Handler timeout, then apply a token-guarded tracker timeout with the live serials at expiry. Move their duration and serial selection into a pure MachineWriteWatchdog. Handler remains a scheduler adapter; Service retains warning resources and durable recovery baselines/clear rules.

Design: typed requests contain one existing tracker, no frozen serials. An injected scheduler receives the unchanged 6000/12000/8000/12000ms delay. Its callback obtains the current immutable serial snapshot exactly when it fires and invokes tracker.timeout. Only a valid active token transition invokes onExpired with that exact snapshot. No storage, transport, retry or optimistic acknowledgement. Host Handler removal on service cleanup remains unchanged.

BrewWait is deliberately excluded: its 600000ms timeout checks activity and attempts guarded cancellation, so it is not the same protocol lifecycle.

Tests first: four kinds verify exact deadline, live rather than scheduled-time serial baseline, stale/new token suppression, completed transactions and duplicates, and durable warning retention. Compile stub for RED, implement GREEN, rewire exactly four real Service WAITING branches. Preserve event order and preheat flow. Full build/lint and independent review; actual detached Service cloud fixtures checked on latest source.
