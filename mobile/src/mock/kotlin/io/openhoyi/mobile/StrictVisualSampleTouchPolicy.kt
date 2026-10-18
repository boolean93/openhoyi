package io.openhoyi.mobile

/** Touch-looking specimen containers, never decorative descendants or product controls. */
internal object StrictVisualSampleTouchPolicy {
    enum class Kind { NONE, ACTION, NAVIGATION }
    private val navigationItem = Regex("sample/nav/item/[0-3]")
    private val navigationTools = setOf("sample/nav/selected", "sample/nav/scale", "sample/nav/settings")

    fun kind(semanticId: String, registeredAction: Boolean): Kind = when {
        semanticId in navigationTools || navigationItem.matches(semanticId) -> Kind.NAVIGATION
        registeredAction -> Kind.ACTION
        else -> Kind.NONE
    }

    fun violation(semanticId: String, registeredAction: Boolean, container: Boolean,
        widthPx: Int, heightPx: Int, minimumPx: Int): String? {
        require(widthPx >= 0 && heightPx >= 0 && minimumPx > 0)
        val kind = kind(semanticId, registeredAction)
        if (kind == Kind.NONE) return null
        if (kind == Kind.NAVIGATION && !container) return "Navigation touch semantic is not a container: $semanticId"
        return if (widthPx < minimumPx || heightPx < minimumPx)
            "Touch specimen below 48dp: $semanticId size=${widthPx}x$heightPx minimum=${minimumPx}px"
        else null
    }
}
