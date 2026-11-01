package io.openhoyi.mobile

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Explicit M7 presentation input. The stores and all other page defaults are untouched. */
internal class LegacyVisualStyle(val context: Context) {
    val palette = TaskProductVisualStyle(context)
    private val history = ExtractionHistoryVisualStyle(context)
    val water get() = history.water
    val warningSurface get() = history.warningSurface
    val warningInk get() = history.warningInk
    val metric = TaskProductVisualStyle.Type(24, 700, 36)
    fun panel(body: LinearLayout) = body.apply {
        background = palette.shape(palette.surface, outline = true, radius = 16)
        setPadding(palette.dp(16), palette.dp(16), palette.dp(16), palette.dp(16))
    }
    fun readonly() = palette.text(context.getString(R.string.legacy_visual_readonly), TaskProductVisualStyle.auxiliary,
        "legacy/readonly").apply {
        setTextColor(warningInk); background = palette.shape(warningSurface)
        setPadding(palette.dp(12), palette.dp(4), palette.dp(12), palette.dp(4))
    }
    fun warning(text: String, id: String) = palette.text(text, TaskProductVisualStyle.auxiliary, id).apply {
        setTextColor(warningInk); background = palette.shape(warningSurface)
        setPadding(palette.dp(12), palette.dp(8), palette.dp(12), palette.dp(8))
    }
    fun styleTabs(view: View) {
        if (view is TextView) {
            palette.apply(view, TaskProductVisualStyle.auxiliary)
            view.gravity = android.view.Gravity.CENTER
            view.minimumHeight = palette.dp(48); view.minHeight = palette.dp(48)
            view.setTextColor(if (view.isSelected) palette.accent else palette.muted)
            view.background = palette.shape(if (view.isSelected) palette.selected else palette.surface)
        }
        if (view is android.view.ViewGroup) (0 until view.childCount).forEach { styleTabs(view.getChildAt(it)) }
    }
}
