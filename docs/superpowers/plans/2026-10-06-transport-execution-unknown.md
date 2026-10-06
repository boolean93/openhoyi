# Treat transport execution exceptions as unknown

Root cause: GattQueue.pump catches every execute Exception and converts it to false/Failed, although the driver may have already submitted the operation before throwing. This can clear durable machine intent and dispatch later commands over an uncertain transport.

Plan:
1. Add failing fake transport tests: throw after recording a write request; require running Unknown, pending Cancelled, close/invalidation, no automatic subsequent sends, ignore late callback and permit only explicit new generation. Distinguish explicit false and pre-dispatch guard errors (still Failed). Cover close exceptions and synchronous completion before throw without double callback.
2. Replace execute exception->false with disconnect + guaranteed host invalidation, then return. Preserve a new generation established by a synchronous callback: an old execution exception must not close its new owner. Keep explicit false behavior, guards, tokens, generations, payloads and no-retry policy unchanged.
3. Exercise real DeviceSession's authenticated synthetic READY write path with faulting driver: tracker UNKNOWN, session FAILED and subsequent writes rejected; actual Service durable recovery retention requires separate evidence. No actual BLE.
4. Run RED/GREEN, full build/lint, independent review and automatic exact-source cloud verification; record evidence and boundaries.

No claim about physical submission from fake execute or native platform exceptions caught internally. Unknown is conservative when the queue cannot know whether execution began. No hardware installation or commands.

Review repair: initial fix closed a callback-created new generation. Added sixth test: first fix 1FAIL/6, captured execution generation and skipped stale/inactive owner invalidation. No more dispatch after exception observation on the same owner; synchronous completion may already have pumped before the exception becomes observable.

Second reentrant repair: Unknown observer may open a new generation while disconnect settles detached waiters. Seventh test was 1FAIL/7 with new callback prematurely settled; finally now checks generation before host invalidation, preserving new owner. Existing detached old waiters still receive Cancelled.
