package io.openhoyi.mobile

/** Keep the original dark flag independent of the brand palette, including on downgrade. */
internal data class AppAppearance(val dark: Boolean = false, val palette: AppThemePalette = AppThemePalette.CLASSIC)

internal class AppAppearancePreference(private val storage: Storage) {
    interface Storage {
        fun read(): AppAppearance
        /** Writes the changed fields together; false leaves the old selection visible. */
        fun write(previous: AppAppearance, value: AppAppearance): Boolean
    }

    enum class Selection { UNCHANGED, APPLIED, SAVE_FAILED }

    @get:Synchronized
    val current get() = storage.read()

    fun selectDark(dark: Boolean) = select { it.copy(dark = dark) }
    fun selectPalette(palette: AppThemePalette) = select { it.copy(palette = palette) }

    @Synchronized private fun select(change: (AppAppearance) -> AppAppearance): Selection {
        val previous = current
        val next = change(previous)
        return when {
            previous == next -> Selection.UNCHANGED
            runCatching { storage.write(previous, next) }.getOrDefault(false) -> Selection.APPLIED
            else -> Selection.SAVE_FAILED
        }
    }
}
