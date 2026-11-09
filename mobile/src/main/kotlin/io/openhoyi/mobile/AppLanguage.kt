package io.openhoyi.mobile

import java.util.Locale

/** Language identity is independent of translated labels and machine/session preferences. */
internal enum class AppLanguage(val tag: String, val legacyIndex: Int) {
    CHINESE("zh-Hans", 0),
    ENGLISH("en", 1);

    val nativeName: String get() = when (this) {
        CHINESE -> "简体中文"
        ENGLISH -> "English"

    }

    val locale: Locale get() = Locale.forLanguageTag(tag)

    companion object {
        /** Stored values are canonical tags. This does not import another app's preferences. */
        fun restore(stored: String?): AppLanguage = entries.firstOrNull { it.tag == stored } ?: CHINESE
    }
}
