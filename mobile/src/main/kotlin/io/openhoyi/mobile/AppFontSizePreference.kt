package io.openhoyi.mobile

/** App-only typography. These are absolute choices, never multiplied by system font scale. */
internal enum class AppFontSize(val key: String, val scale: Float, val label: Int) {
    SMALL("small", .9f, R.string.font_size_small),
    MEDIUM("medium", 1f, R.string.font_size_medium),
    LARGE("large", 1.1f, R.string.font_size_large);
    companion object {
        fun restore(value: String?) = entries.firstOrNull { it.key == value } ?: MEDIUM
    }
}

internal class AppFontSizePreference(private val storage: Storage) {
    interface Storage { fun read(): String?; fun write(key: String): Boolean }
    enum class Selection { UNCHANGED, APPLIED, SAVE_FAILED }
    @Volatile private var selected = AppFontSize.restore(runCatching { storage.read() }.getOrNull())
    val current get() = selected
    @Synchronized fun select(value: AppFontSize): Selection {
        if (value == selected) return Selection.UNCHANGED
        if (!runCatching { storage.write(value.key) }.getOrDefault(false)) return Selection.SAVE_FAILED
        selected = value
        return Selection.APPLIED
    }
}
