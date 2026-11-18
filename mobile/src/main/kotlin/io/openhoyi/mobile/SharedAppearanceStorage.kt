package io.openhoyi.mobile

import android.content.Context
import android.content.SharedPreferences

/** SharedPreferences.commit() updates memory before returning a disk failure. Restore both on failure. */
internal class SharedAppearanceStorage(
    private val prefs: SharedPreferences,
    private val commit: (SharedPreferences.Editor) -> Boolean = SharedPreferences.Editor::commit,
) : AppAppearancePreference.Storage {
    constructor(context: Context) : this(context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE))

    override fun read(): AppAppearance {
        val stored = prefs.all
        return AppAppearance(stored[DARK] as? Boolean ?: false, AppThemePalette.restore(stored[PALETTE] as? String))
    }

    override fun write(previous: AppAppearance, value: AppAppearance): Boolean {
        val stored = prefs.all
        val changes = prefs.edit()
        if (previous.dark != value.dark) changes.putBoolean(DARK, value.dark)
        if (previous.palette != value.palette) changes.putString(PALETTE, value.palette.key)
        if (runCatching { commit(changes) }.getOrDefault(false)) return true
        val rollback = prefs.edit()
        if (previous.dark != value.dark) rollback.restore(DARK, stored[DARK])
        if (previous.palette != value.palette) rollback.restore(PALETTE, stored[PALETTE])
        runCatching { commit(rollback) }
        return false
    }

    private fun SharedPreferences.Editor.restore(key: String, value: Any?) {
        when (value) {
            is String -> putString(key, value)
            is Boolean -> putBoolean(key, value)
            is Int -> putInt(key, value)
            is Long -> putLong(key, value)
            is Float -> putFloat(key, value)
            is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
            else -> remove(key)
        }
    }

    companion object {
        const val PREFERENCES = "appearance"
        const val DARK = "dark"
        const val PALETTE = "palette"
    }
}
