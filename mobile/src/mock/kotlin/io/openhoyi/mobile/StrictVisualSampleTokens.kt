package io.openhoyi.mobile

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import org.json.JSONObject

/** Proposed values transcribed from Astra's strict visual draft, not product defaults. */
internal object StrictVisualSampleTokens {
    const val FONT_SHA = "a3041811a78c361b1de50f953c805e0244951c21c5bd412f7232ef0d899af0da"
    data class Type(val sizeSp: Int, val weight: Int, val lineSp: Int)
    val body = Type(16, 400, 24)
    val action = Type(16, 500, 24)
    val auxiliary = Type(14, 400, 22)
    val section = Type(20, 500, 30)
    val title = Type(24, 500, 36)
    val recipeValue = Type(24, 700, 36)
    val largeValue = Type(40, 700, 60)
    data class Palette(val dark: Boolean) {
        private fun pick(light: String, night: String) = Color.parseColor(if (dark) night else light)
        val page = pick("#F9FAF9", "#11191A")
        val surface = pick("#FFFFFF", "#1B2526")
        val text = pick("#101820", "#EDF3F2")
        val muted = pick("#657183", "#A9B9B8")
        val border = pick("#DDE3E7", "#3B4B4C")
        val primary = Color.parseColor("#087A78")
        val onPrimary = Color.WHITE
        val accent = pick("#087A78", "#83D6D0")
        val selected = pick("#E2F0ED", "#203D3B")
        val disabled = pick("#ECEFF1", "#263234")
        val onDisabled = pick("#8A95A3", "#7D908F")
        val pressure = pick("#176BD1", "#8CB8FF")
        val stageColors = listOf(accent, pressure, pick("#A46500", "#F2C16D"), pick("#7941A5", "#C7A3EB"))
        fun json(): JSONObject = JSONObject().put("status", "proposed").put("dark", dark).apply {
            for ((key, value) in mapOf("page" to page, "surface" to surface, "text" to text, "muted" to muted,
                "border" to border, "primary" to primary, "onPrimary" to onPrimary, "accent" to accent,
                "selected" to selected, "disabled" to disabled, "onDisabled" to onDisabled, "pressure" to pressure))
                put(key, String.format(java.util.Locale.ROOT, "#%08X", value))
            put("shareStages", org.json.JSONArray(stageColors.map { String.format(java.util.Locale.ROOT, "#%08X", it) }))
        }
    }
    fun dp(context: Context, value: Float) = (value * context.resources.displayMetrics.density).toInt()
    fun sp(context: Context, value: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value.toFloat(), context.resources.displayMetrics)
    fun json(palette: Palette): JSONObject = JSONObject().put("version", StrictVisualSampleFixtures.VERSION)
        .put("status", "proposed").put("fontSha256", FONT_SHA).put("palette", palette.json())
        .put("spacingDp", JSONObject().put("related", 8).put("group", 16).put("columns", 24)
            .put("page", 16).put("rail", 80).put("stroke", 1).put("touchMin", 48).put("primaryMin", 56))
        .put("types", JSONObject().apply {
            for ((name, type) in mapOf("body" to body, "action" to action, "auxiliary" to auxiliary,
                "section" to section, "title" to title, "recipeValue" to recipeValue, "largeValue" to largeValue))
                put(name, JSONObject().put("sizeSp", type.sizeSp).put("weight", type.weight).put("lineSp", type.lineSp))
        })
}
