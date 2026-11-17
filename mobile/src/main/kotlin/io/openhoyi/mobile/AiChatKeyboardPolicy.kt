package io.openhoyi.mobile

/** Keyboard choices are display-only; the host still owns consent, limits and requests. */
internal object AiChatKeyboardPolicy {
    enum class Decision { PASS, CONSUME, SEND }
    fun ime(sendAction: Boolean, hasText: Boolean, busy: Boolean, composing: Boolean): Decision = when {
        !sendAction || composing -> Decision.PASS
        busy || !hasText -> Decision.CONSUME
        else -> Decision.SEND
    }
    fun hardware(enter: Boolean, down: Boolean, up: Boolean, shift: Boolean,
        repeated: Boolean, hasText: Boolean, busy: Boolean, composing: Boolean): Decision = when {
        !enter || shift || composing || (!down && !up) -> Decision.PASS
        up || repeated || busy || !hasText -> Decision.CONSUME
        else -> Decision.SEND
    }
}
