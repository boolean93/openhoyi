package io.openhoyi.mobile

import android.content.Context
import android.content.SharedPreferences

internal class ShortcutStartPreferences(
    private val readValue: () -> ShortcutStartPolicy.Preference,
    private val writeValue: (ShortcutStartPolicy.Preference) -> Boolean,
    private val safety: ShortcutPreferenceSafety = processSafety,
    private val observeValue: ((() -> Unit) -> (() -> Unit)) = { {} },
) {
    companion object { private val processSafety = ShortcutPreferenceSafety() }
    constructor(context: Context) : this(
        readValue = {
            context.getSharedPreferences(ShortcutStartPolicy.PREFERENCES, Context.MODE_PRIVATE).let { prefs ->
                // Missing key uses the new default; an explicitly saved false stays false.
                ShortcutStartPolicy.Preference(prefs.getBoolean(ShortcutStartPolicy.DIRECT_START, ShortcutStartPolicy.Preference().directStart))
            }
        },
        writeValue = { value ->
            context.getSharedPreferences(ShortcutStartPolicy.PREFERENCES, Context.MODE_PRIVATE).edit()
                .putBoolean(ShortcutStartPolicy.DIRECT_START, value.directStart).commit()
        },
        observeValue = { observer ->
            val prefs = context.getSharedPreferences(ShortcutStartPolicy.PREFERENCES, Context.MODE_PRIVATE)
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == null || key == ShortcutStartPolicy.DIRECT_START) observer()
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            val stop: () -> Unit = { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
            stop
        })
    val suppressed get() = safety.suppressed
    val revision get() = safety.revision
    fun read() = if (safety.suppressed) ShortcutStartPolicy.Preference(false) else readValue()
    fun observe(observer: () -> Unit): () -> Unit {
        val stopValue = observeValue(observer)
        val stopSafety = safety.observe(observer)
        return { stopValue(); stopSafety() }
    }
    fun choose(directStart: Boolean): Boolean {
        safety.changing()
        val choice = ShortcutStartPolicy.Preference(directStart)
        if (runCatching { writeValue(choice) }.getOrDefault(false)) {
            safety.saved()
            return true
        }
        // Shared across all preference instances. A failed disable must never leave an old
        // enabled value effective, even if both disk writes fail. Only an explicit successful
        // choice clears this process-local suppression; restart uses the last persisted choice.
        safety.failed()
        runCatching { writeValue(ShortcutStartPolicy.Preference(false)) }
        safety.failed()
        return false
    }
}
