package io.openhoyi.mobile

/** Ephemeral display copy only. Never used as protocol, tool arguments, logs or stored history. */
internal class AiStreamPreview {
    enum class Phase { WAITING, REASONING, CONTENT, TOOL, COMPLETE, INTERRUPTED }
    data class Snapshot(val reasoning: String = "", val content: String = "", val toolStarted: Boolean = false, val contentTruncated: Boolean = false, val phase: Phase = Phase.WAITING) {
        override fun toString() = "AiStreamPreview.Snapshot(redacted, phase=$phase)"
    }
    private var value = Snapshot()
    private var terminal = false
    private val scheduled = java.util.concurrent.atomic.AtomicBoolean(false)
    fun scheduleUpdate() = scheduled.compareAndSet(false, true)
    fun updateDelivered() { scheduled.set(false) }
    @Synchronized fun append(delta: AiStreamDelta) {
        if (terminal) return
        value = when (delta) {
            is AiStreamDelta.Reasoning -> value.copy(reasoning = bounded(value.reasoning, delta.text), toolStarted = false, phase = Phase.REASONING)
            is AiStreamDelta.Content -> {
                val available = (CONTENT_CHARS - value.content.length).coerceAtLeast(0)
                val prefix = value.content + delta.text.take(available)
                value.copy(content = prefix, contentTruncated = value.contentTruncated || delta.text.length > available,
                    toolStarted = false, phase = Phase.CONTENT)
            }
        }
    }
    @Synchronized fun toolStarted() { if (terminal) return; value = value.copy(toolStarted = true, phase = Phase.TOOL) }
    @Synchronized fun snapshot() = if (value.content.lastOrNull()?.isHighSurrogate() == true)
        value.copy(content = value.content.dropLast(1)) else value
    @Synchronized fun finish() { terminal = true; value = value.copy(content = "", toolStarted = false, phase = Phase.COMPLETE) }
    /** Display only: never turn failed content into a completed protocol message or tool proposal. */
    @Synchronized fun interrupt() {
        terminal = true
        val content = value.content.let { if (it.lastOrNull()?.isHighSurrogate() == true) it.dropLast(1) else it }
        value = value.copy(reasoning = "", content = content, toolStarted = false, phase = Phase.INTERRUPTED)
    }
    @Synchronized fun clear() { terminal = true; value = Snapshot() }
    private fun bounded(before: String, delta: String): String {
        val result = (before + delta.takeLast(MAX_CHARS)).takeLast(MAX_CHARS)
        return result.let { if (it.firstOrNull()?.isLowSurrogate() == true) it.drop(1) else it }
            .let { if (it.lastOrNull()?.isHighSurrogate() == true) it.dropLast(1) else it }
    }
    companion object { const val MAX_CHARS = 12_000; const val CONTENT_CHARS = AiChatPolicy.OUTPUT_CHARS }
}
