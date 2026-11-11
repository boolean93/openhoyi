package io.openhoyi.mobile

/** A protected start remains tappable for an explanation, never as an override. */
internal object ExtractionStartAction {
    fun permit(block: String?, explain: (String) -> Unit): Boolean {
        if (block == null) return true
        explain(block)
        return false
    }
}
