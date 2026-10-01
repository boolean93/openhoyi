package io.openhoyi.mobile

import android.content.Context
import android.content.res.Configuration

/** Isolated configuration contexts; never mutates system resources or Locale.setDefault. */
internal object AppLanguageContext {
    fun configuration(base: Context, language: AppLanguage, dark: Boolean? = null): Configuration =
        Configuration(base.resources.configuration).apply {
            setLocale(language.locale)
            setLayoutDirection(language.locale)
            if (dark != null) uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                (if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
        }

    fun wrap(base: Context, language: AppLanguage, dark: Boolean? = null): Context =
        base.createConfigurationContext(configuration(base, language, dark))
}

/** The base is the raw attached context, never the owner's resource override. */
internal class AppLanguageContextProvider(private val base: Context, private val language: () -> AppLanguage) {
    private var cachedConfiguration: Configuration? = null
    private var cachedContext: Context? = null

    @Synchronized fun context(): Context {
        val desired = AppLanguageContext.configuration(base, language())
        if (cachedContext == null || desired != cachedConfiguration) {
            cachedContext = base.createConfigurationContext(desired)
            cachedConfiguration = Configuration(desired)
        }
        return checkNotNull(cachedContext)
    }
}
