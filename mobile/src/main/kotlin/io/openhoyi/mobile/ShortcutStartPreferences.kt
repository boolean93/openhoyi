package io.openhoyi.mobile

import android.content.Context

internal class ShortcutStartPreferences(
    private val readValue: () -> ShortcutStartPolicy.Preference,
    private val writeValue: (ShortcutStartPolicy.Preference) -> Boolean,
    private val safety: ShortcutPreferenceSafety = processSafety,
) {
    companion object { private val processSafety = ShortcutPreferenceSafety() }
    constructor(context: Context) : this(
        readValue = {
            context.getSharedPreferences(ShortcutStartPolicy.PREFERENCES, Context.MODE_PRIVATE).let { prefs ->
                ShortcutStartPolicy.Preference(prefs.getBoolean(ShortcutStartPolicy.CONFIGURED, false),
                    prefs.getBoolean(ShortcutStartPolicy.DIRECT_START, false))
            }
        },
        writeValue = { value ->
            context.getSharedPreferences(ShortcutStartPolicy.PREFERENCES, Context.MODE_PRIVATE).edit()
                .putBoolean(ShortcutStartPolicy.CONFIGURED, value.configured)
                .putBoolean(ShortcutStartPolicy.DIRECT_START, value.directStart).commit()
        })
    val suppressed get() = safety.suppressed
    fun read() = if (safety.suppressed) ShortcutStartPolicy.Preference(true, false) else readValue()
    fun choose(directStart: Boolean): Boolean {
        val choice = ShortcutStartPolicy.Preference(true, directStart)
        if (runCatching { writeValue(choice) }.getOrDefault(false)) {
            safety.saved()
            return true
        }
        // Shared across all preference instances. A failed disable must never leave an old
        // enabled value effective, even if both disk writes fail. Only an explicit successful
        // choice clears this process-local suppression; restart uses the last persisted choice.
        safety.failed()
        runCatching { writeValue(ShortcutStartPolicy.Preference(true, false)) }
        return false
    }
}
