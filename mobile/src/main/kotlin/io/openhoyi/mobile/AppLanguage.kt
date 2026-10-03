package io.openhoyi.mobile

import java.util.Locale

/** Language identity is independent of translated labels and machine/session preferences. */
internal enum class AppLanguage(val tag: String, val legacyIndex: Int, val rightToLeft: Boolean = false) {
    CHINESE("zh-Hans", 0),
    ENGLISH("en", 1),
    RUSSIAN("ru", 2),
    THAI("th", 3),
    ARABIC("ar", 4, true),
    JAPANESE("ja", 5),
    KOREAN("ko", 6),
    SPANISH("es", 7);

    val nativeName: String get() = when (this) {
        CHINESE -> "简体中文"
        ENGLISH -> "English"
        RUSSIAN -> "Русский"
        THAI -> "ไทย"
        ARABIC -> "العربية"
        JAPANESE -> "日本語"
        KOREAN -> "한국어"
        SPANISH -> "Español"
    }

    val locale: Locale get() = Locale.forLanguageTag(tag)

    companion object {
        /** Stored values are canonical tags. This does not import another app's preferences. */
        fun restore(stored: String?): AppLanguage = entries.firstOrNull { it.tag == stored } ?: CHINESE
    }
}
