package io.openhoyi.mobile

/** DeepSeek thinking-mode contract shared by Flash and V4 Pro. Never infer unsupported levels. */
internal enum class AiReasoningEffort(val apiValue: String, val label: Int) {
    LOW("low", R.string.ai_reasoning_low),
    HIGH("high", R.string.ai_reasoning_high),
    MAX("max", R.string.ai_reasoning_max);
    companion object {
        fun fromStored(value: String?) = entries.firstOrNull { it.apiValue == value } ?: LOW
    }
}

internal object AiReasoningPreference {
    private const val FILE = "ai_reasoning"
    private const val KEY = "effort"
    fun read(context: android.content.Context) = AiReasoningEffort.fromStored(
        context.getSharedPreferences(FILE, android.content.Context.MODE_PRIVATE).getString(KEY, null))
    fun save(context: android.content.Context, effort: AiReasoningEffort): Boolean {
        val prefs = context.getSharedPreferences(FILE, android.content.Context.MODE_PRIVATE)
        val previous = prefs.getString(KEY, null)
        if (prefs.edit().putString(KEY, effort.apiValue).commit()) return true
        // Failed commits can still update SharedPreferences' in-memory map. Restore the selection.
        prefs.edit().putString(KEY, previous).commit()
        return false
    }
}
