package io.openhoyi.session

/** Why the current coffee session ended in FAILED; display-only, never a control permit. */
enum class CoffeeSessionFailure { CONNECT, AUTH, PROTOCOL_TIMEOUT, OTHER }
