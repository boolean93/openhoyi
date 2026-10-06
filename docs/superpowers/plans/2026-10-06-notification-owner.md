# Retain notification connection ownership after readiness observers

Root cause: onNotification validates generation at entry only. A Settings (coffee) or first sample (BOOKOO) publishes READY. Its synchronous state observer may reconnect and bring the new connection all the way to READY. The old call then publishes its old Settings/weight through the new connection's product callback.

Design: revalidate active original generation after readiness state publication and before product telemetry/readback handling. Keep decoding, stored current connection readbacks, permitted firmware, timestamps, normal READY notification delivery, and protocol bytes unchanged.

TDD: coffee first READY and repeated READY settings observers reconnect with a distinguishable new readback; BOOKOO first READY observer reconnects with a distinguishable new weight. Only the new notification reaches product callbacks. Disconnect/unfinished replacement must suppress old delivery; ordinary first notification delivers once; old generation and wrong endpoint frames remain ignored.

Validate RED/GREEN, retained session scenarios, full local build/lint and independent review, then exact-source cloud tasks. This only fences synchronous publication reentrancy, not hardware packet reordering that arrives under a newly assigned host timestamp. Outer connect initial disconnect observer reentrancy remains separate.
