package io.openhoyi.mobile

/** Suggest a fresh machine value only while the edit field still contains our previous suggestion. */
internal object ReadbackPrefill {
    fun next(current: String, previousSuggestion: String?, readback: Int, focused: Boolean): String? {
        if (focused) return null
        val value = readback.toString()
        if (previousSuggestion == null && current.isNotEmpty()) return null
        if (previousSuggestion != null && current != previousSuggestion) return null
        return value
    }
}
