package io.openhoyi.mobile

/** UI-only submission state. A pending question is not part of protocol history. */
internal class AiChatPendingDraft {
    var draft: String = ""
    var pending: String? = null
        private set

    fun begin(text: String): Boolean {
        if (pending != null || text.isBlank()) return false
        pending = text
        draft = ""
        return true
    }

    fun committed() { pending = null }

    fun recoverableDraft(nextDraft: String): String = pending?.let { question ->
        if (nextDraft.isEmpty() || nextDraft == question) question else "$question\n\n$nextDraft"
    } ?: nextDraft

    fun remainingDraftCapacity(limit: Int): Int = pending?.let { (limit - it.length - 2).coerceAtLeast(0) } ?: limit

    fun restore(nextDraft: String = ""): String? {
        if (pending == null) return null
        draft = recoverableDraft(nextDraft)
        pending = null
        return draft
    }

    fun clear() { draft = ""; pending = null }
}
