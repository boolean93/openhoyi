# Bind initialization continuations to their connection

Observed risk: initialization success callbacks validate generation only on entry. A synchronous stateChanged observer can explicitly reconnect, after which the old continuation captures the new queue generation and enqueues the old connection's stage/authentication, or overwrites the new connection's deadline.

Design: bind every initialization step to its original generation. Set phase deadlines before publishing the phase. After observers return, require the same active generation and expected phase before continuing. This preserves explicit reconnect/disconnect, exact protocol bytes and phase timing. BOOKOO final initialization transition follows the same rule.

TDD: all five coffee initialization state observer phases reconnect with a distinct password; finish the new connection and assert only its operations/authentication are dispatched. Disconnect observers stop continuation. BOOKOO final initialization observer synchronously reconnects through subscription and must retain the new initialization timeout (10 s) rather than the old synchronization timeout (5 s). A separate coffee test retains the 22 s connect timeout. Retain ordinary authentication and BOOKOO initialization scenarios.

Validate focused RED/GREEN, all session checks, full native build/lint and independent review. Terminal disconnect/failure cleanup observer reentrancy and notification delivery ownership remain separate boundaries; do not claim these covered.
