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

    fun restore(): String? = pending?.also { draft = it; pending = null }

    fun clear() { draft = ""; pending = null }
}
