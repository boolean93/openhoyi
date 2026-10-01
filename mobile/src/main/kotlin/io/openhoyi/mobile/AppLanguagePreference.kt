package io.openhoyi.mobile

/** Process authority changes only after a successful disk write; it never rereads a failed cache. */
internal class AppLanguagePreference(private val storage: Storage) {
    interface Storage {
        fun read(): String?
        fun write(tag: String): Boolean
    }
    enum class Selection { UNCHANGED, APPLIED, SAVE_FAILED }
    @Volatile private var selected = AppLanguage.restore(try { storage.read() } catch (_: RuntimeException) { null })
    val current: AppLanguage get() = selected

    @Synchronized fun select(language: AppLanguage): Selection {
        if (language == selected) return Selection.UNCHANGED
        val saved = try { storage.write(language.tag) } catch (_: RuntimeException) { false }
        if (!saved) return Selection.SAVE_FAILED
        selected = language
        return Selection.APPLIED
    }
}
