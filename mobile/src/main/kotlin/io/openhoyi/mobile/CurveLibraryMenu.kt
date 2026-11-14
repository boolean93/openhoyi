package io.openhoyi.mobile

import android.content.Context
import android.widget.Button
import android.widget.PopupMenu

/** One compact anchor, with independent source filtering and mutually exclusive ordering. */
class CurveLibraryMenu(
    context: Context,
    private var sortMode: CurveSortMode,
    private var customOnly: Boolean,
    private val changed: (CurveSortMode, Boolean) -> Unit,
) : Button(context) {
    init {
        minimumHeight = (52 * resources.displayMetrics.density).toInt()
        tag = "product/curve-library/sort"
        updateLabel()
        setOnClickListener { createPopup().show() }
    }

    fun createPopup(): PopupMenu = PopupMenu(context, this).apply {
        menu.add(1, CUSTOM, 0, R.string.curve_filter_custom).apply {
            isCheckable = true
            isChecked = customOnly
        }
        menu.add(2, RECENT, 1, R.string.curve_sort_recent)
        menu.add(2, MOST_USED, 2, R.string.curve_sort_most_used)
        menu.setGroupCheckable(2, true, true)
        menu.findItem(if (sortMode == CurveSortMode.RECENT) RECENT else MOST_USED).isChecked = true
        if (android.os.Build.VERSION.SDK_INT >= 28) menu.setGroupDividerEnabled(true)
        setOnMenuItemClickListener { item ->
            when (item.itemId) {
                CUSTOM -> customOnly = !customOnly
                RECENT -> sortMode = CurveSortMode.RECENT
                MOST_USED -> sortMode = CurveSortMode.MOST_USED
                else -> return@setOnMenuItemClickListener false
            }
            updateLabel()
            changed(sortMode, customOnly)
            true
        }
    }

    private fun updateLabel() {
        val order = context.getString(if (sortMode == CurveSortMode.RECENT)
            R.string.curve_sort_recent else R.string.curve_sort_most_used)
        val label = if (customOnly) context.getString(R.string.curve_filter_custom_sort, order) else order
        text = "$label ▾"
        contentDescription = label
    }

    companion object {
        const val CUSTOM = 1
        const val RECENT = 2
        const val MOST_USED = 3
    }
}
