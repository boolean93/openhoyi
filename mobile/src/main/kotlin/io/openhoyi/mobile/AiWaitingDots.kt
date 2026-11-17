package io.openhoyi.mobile

/** Decorative wait rhythm only; no elapsed-time or model-progress estimate. */
internal object AiWaitingDots {
    fun frame(status: String, index: Int): String {
        val first = status.substringBefore('\n').trimEnd('.', '…')
        val tail = status.indexOf('\n').let { if (it >= 0) status.substring(it) else "" }
        return first + ".".repeat(index.mod(3) + 1) + tail
    }
}
