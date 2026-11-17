package io.openhoyi.mobile

/** Trusted local official catalog, not the current account's GET /models response. */
internal data class AiModelSpec(val id: String, val label: String, val images: Boolean,
    val contextTokens: Long = 1_048_576, val maxOutputTokens: Long = 393216)
internal object AiModelPolicy {
    const val FLASH = "deepseek-flash"
    const val PRO = "deepseek-v4-pro"
    val models = listOf(AiModelSpec(FLASH, "DeepSeek Flash", true), AiModelSpec(PRO, "DeepSeek V4 Pro", false))
    fun known(id: String) = models.firstOrNull { it.id == id } ?: error("Unsupported model")
    fun accepts(id: String, attachments: List<AiAttachment>) = known(id).images || attachments.none { it.kind == AiAttachmentKind.IMAGE }
}

/** A pure, non-network switch. The owner must atomically save the returned container before showing it. */
internal object AiModelSessions {
    fun switch(current: AiSession, target: String): AiSession {
        AiModelPolicy.known(target)
        if (target == current.modelId) return current
        val restored = current.inactive.firstOrNull { it.modelId == target }
            ?: AiSession(current.epoch, emptyList(), AiProtocolHistory.empty(target), "", AiUsageSnapshot())
        val prior = AiSession(current.epoch, current.lines, current.protocol, current.draft, current.usage, current.draftAttachments, ocr = current.ocr, hoyiScreenshotSourceId = current.hoyiScreenshotSourceId)
        return AiSession(current.epoch, restored.lines, restored.protocol, restored.draft, restored.usage,
            restored.draftAttachments, listOf(prior), restored.ocr, restored.hoyiScreenshotSourceId)
    }
}

/** Fence a late IO usage callback in the same critical section as successful slot replacement. */
internal object AiSessionTransactions {
    fun commit(lock: Any, candidate: AiSession, save: (AiSession) -> Boolean, committed: (AiSession) -> Unit): Boolean = synchronized(lock) {
        if (!save(candidate)) false else { committed(candidate); true }
    }
}
/** Activity-local operation identity; no identity or resumable request is persisted. */
internal class AiOperationFence {
    private var generation = 0L
    @Synchronized fun begin(): Long = ++generation
    @Synchronized fun invalidate() { generation++ }
    @Synchronized fun current(token: Long) = token == generation
}
