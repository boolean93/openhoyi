# Keep terminal cleanup on its original connection

Root cause: disconnect/fail publish terminal state before closing the queue. A state observer may explicitly reconnect. The outer cleanup then cancels the new sleep transaction, closes the new queue, or resets the new BOOKOO initBusy flag.

Design: capture original generation and detach its sleep transaction and initialization flag before external observers. Deliver the detached sleep result once even if observers throw. Close the queue only if it still belongs to the original generation. Keep terminal state delivery and exception cleanup guarantees; do not retry any operation or alter bytes/timers.

Tests first: both roles and both terminal states reconnect and retain the new queue; reconnect-and-throw still cleans old ownership without closing the new one; new two-frame sleep transaction survives old terminal cleanup while old callback gets one Unknown; old running tare callback reconnects and begins BOOKOO initialization without resetting its initBusy and duplicating the command. Preserve four existing throwing terminal observer tests.

Boundary: terminal cleanup ownership only. An outer connect whose initial disconnect observer requests another connect, plus notification publication ownership, remain separate audits. No real Bluetooth or physical safety proof.
