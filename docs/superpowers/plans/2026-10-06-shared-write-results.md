# Shared machine write transport result handling

Continue the approved native architecture separation after registration/ack/activity extraction. MobileService currently updates five trackers and branches on their concrete enums separately. Move this transport-result application/normalization into a pure session helper. Service retains resource messages, Handler deadlines, latest telemetry serial acquisition, durable recovery clear decisions, and physical transport calls. No bytes, delays, unknown-write retry, or ACK gate changes.

Typed requests carry each existing tracker and the exact current serial/readback arguments. apply(token,result,request) returns IGNORED/WAITING/CONFIRMED/FAILED/UNKNOWN. Stale or duplicate callbacks are ignored without mutation. Successful transport waits for fresh post-write evidence; no automatic recovery clear is allowed in the helper. Keep the current schedule CONFIRMED Service branch for parity even though written currently always waits for fresh readbacks.

Test first: five kinds across successful/failed/cancelled/unknown/stale/repeated callbacks, retained durable intent, and fresh serial boundaries. Compile stub for actual RED, then implement; rewire exactly the five real callbacks (not mock/preheat cancellation), preserving all Service branches/timeouts/resources. Verify session check, actual detached Service fixtures through existing cloud lifecycle workflow, full native build/lint and independent review.

Boundary: normalization is only part of business coordination. Timers, readback sampling, trace/history, OS lifecycle and device acceptance remain separate. Existing physical-device gaps and high-risk control restrictions remain unchanged.
