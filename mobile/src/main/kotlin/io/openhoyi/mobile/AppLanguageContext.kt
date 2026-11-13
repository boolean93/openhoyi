package io.openhoyi.mobile

import android.content.Context
import android.content.res.Configuration

/** Isolated app language, theme and text-size configuration; never mutates system resources. */
internal object AppLanguageContext {
    fun configuration(base: Context, language: AppLanguage, dark: Boolean? = null, fontSize: AppFontSize = AppFontSize.MEDIUM): Configuration =
        Configuration(base.resources.configuration).apply {
            fontScale = fontSize.scale
            setLocale(language.locale)
            setLayoutDirection(language.locale)
            if (dark != null) uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                (if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
        }

    fun wrap(base: Context, language: AppLanguage, dark: Boolean? = null, fontSize: AppFontSize = AppFontSize.MEDIUM): Context =
        base.createConfigurationContext(configuration(base, language, dark, fontSize))
}

/** The base is the raw attached context, never the owner's resource override. */
internal class AppLanguageContextProvider(private val base: Context, private val fontSize: () -> AppFontSize, private val language: () -> AppLanguage) {
    private var cachedConfiguration: Configuration? = null
    private var cachedContext: Context? = null

    @Synchronized fun context(): Context {
        val desired = AppLanguageContext.configuration(base, language(), fontSize = fontSize())
        if (cachedContext == null || desired != cachedConfiguration) {
            cachedContext = base.createConfigurationContext(desired)
            cachedConfiguration = Configuration(desired)
        }
        return checkNotNull(cachedContext)
    }
}
