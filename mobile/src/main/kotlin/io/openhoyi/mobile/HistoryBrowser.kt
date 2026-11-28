package io.openhoyi.mobile

import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

/** Browser-local source tools. The visible count opens the rules for the active source. */
internal class HistoryBrowserSources(
    private val activity: ThemedActivity,
    private val style: TaskProductVisualStyle,
    private val journal: Boolean,
    menu: View,
    navigationTools: View? = null,
    onSource: (journal: Boolean) -> Unit,
) : LinearLayout(activity) {
    private val source = if (journal) R.string.journal_tab else R.string.history_local_tab
    private val count = style.text("", TaskProductVisualStyle.auxiliary, "product/history/count").apply {
        minWidth = style.dp(48)
        minHeight = style.dp(48)
        gravity = Gravity.CENTER
        setTextColor(style.accent)
        setPaddingRelative(style.dp(4), 0, style.dp(4), 0)
        setCompoundDrawablesRelative(null, null, HomeVisualIcon.drawable(activity, "info", style.accent).apply {
            setBounds(0, 0, style.dp(16), style.dp(16))
        }, null)
        compoundDrawablePadding = style.dp(2)
        setOnClickListener { showRules() }
        isFocusable = true
        background = sourceRipple()
    }
    private var records: Int? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isBaselineAligned = false
        tag = "product/history/sources"
        setPadding(style.dp(4), style.dp(4), style.dp(4), style.dp(4))
        protectRoundedBackdropContent(style.surface, style.dp(12), photoFill = wallpaperTint(style.surface, 228))
        val tabs = style.row().apply { gravity = Gravity.CENTER_VERTICAL }
        addView(tabs, LayoutParams(0, -2, 1f))
        val labels = listOf(R.string.history_local_tab, R.string.journal_tab)
        labels.forEach { resource ->
            val isJournal = resource == R.string.journal_tab
            val active = isJournal == journal
            val label = activity.getString(resource)
            val tab = style.text(label, TaskProductVisualStyle.auxiliary, secondary = !active).apply {
                minWidth = style.dp(48)
                minHeight = style.dp(48)
                setPaddingRelative(style.dp(6), 0, if (active) 0 else style.dp(6), 0)
                gravity = Gravity.CENTER
                isSelected = active
                contentDescription = activity.getString(R.string.ui_tab_description, label,
                    if (active) activity.getString(R.string.ui_current_page_suffix) else "")
            }
            if (active) {
                tab.setTextColor(style.accent)
                tabs.addView(style.row().apply {
                    gravity = Gravity.CENTER_VERTICAL
                    background = style.shape(style.selected, radius = 8)
                    addView(tab, LayoutParams(0, -2, 1f))
                    addView(count, LayoutParams(-2, -2))
                }, LayoutParams(0, -2, 3f))
            } else {
                tab.background = sourceRipple()
                tab.setOnClickListener { onSource(isJournal) }
                tab.isFocusable = true
                tabs.addView(tab, LayoutParams(0, -2, 2f))
            }
        }
        addView(menu, LayoutParams(style.dp(48), style.dp(48)))
        if (navigationTools != null) addView(navigationTools, LayoutParams(-2, -2))
        setCount(null)
    }

    private fun sourceRipple() = RippleDrawable(ColorStateList.valueOf(style.selected), null,
        style.shape(Color.WHITE, radius = 8))

    fun setCount(value: Int?) {
        records = value
        val visible = value?.toString() ?: "—"
        count.text = visible
        val sourceLabel = activity.getString(source)
        count.contentDescription = if (value == null)
            activity.getString(R.string.history_browser_source_info_unknown, sourceLabel)
        else activity.getString(R.string.history_browser_source_info, sourceLabel, value)
    }

    private fun showRules() {
        val summary = records?.let { activity.getString(R.string.history_browser_record_count, it) }
            ?: activity.getString(if (journal) R.string.journal_unavailable else R.string.history_unavailable)
        val rule = activity.getString(if (journal) R.string.history_browser_journal_retention else R.string.history_browser_local_retention)
        activity.beanShowDialog(AlertDialog.Builder(activity)
            .setTitle(source).setMessage("$summary\n\n$rule").setPositiveButton(android.R.string.ok, null).create())
    }
}

internal fun historyBrowserCollection(activity: ThemedActivity) =
    TaskCollectionLayout(activity, browserFraction = .30f, browserWidthDp = 280..300)

internal fun historyBrowserList(activity: ThemedActivity, style: TaskProductVisualStyle) = ListView(activity).apply {
    tag = "product/history/list"
    protectRoundedBackdropContent(style.surface, style.dp(12), clipContent = true, photoContentEnd = {
        if (lastVisiblePosition == count - 1 && childCount > 0) getChildAt(childCount - 1) else null
    })
    divider = ColorDrawable(style.border)
    dividerHeight = style.dp(1)
}

internal fun TaskProductVisualStyle.addEmptyHistoryCompare(activity: ThemedActivity, parent: LinearLayout): Button =
    action(parent, activity.getString(R.string.journal_compare), "product/history/empty-compare", outline = true) {
        activity.showHistoryCupPicker()
    }.apply {
        setSingleLine(false)
        maxLines = Int.MAX_VALUE
        ellipsize = null
    }

/** Both native history entry points bind the same compact row from the real recorded fields. */
internal class HistoryBrowserAdapter(
    private val activity: ThemedActivity,
    private val visual: ExtractionHistoryVisualStyle,
    private val journal: Boolean,
    private val selectedId: () -> String?,
) : ArrayAdapter<ShotHistory.Entry>(activity, android.R.layout.simple_list_item_1, mutableListOf()) {
    private val style = visual.palette
    private val app = activity.application as MobileApplication
    private val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
    private var currentYear = ""
    private data class RowViews(val title: TextView, val time: TextView, val metadata: HistoryVisualFlow,
        val timing: TextView, val scale: TextView, val slot: TextView, val status: TextView, val taste: TextView)

    fun replaceRows(rows: List<ShotHistory.Entry>) {
        currentYear = timestamp.format(Date()).substringBefore('-')
        setNotifyOnChange(false)
        clear()
        addAll(rows)
        notifyDataSetChanged()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val row = convertView ?: createRow()
        val views = row.tag as RowViews
        val entry = requireNotNull(getItem(position))
        val prefix = "product/history/list/${entry.id}"
        row.isActivated = selectedId() == entry.id

        views.title.tag = "$prefix/name"
        views.title.text = if (entry.curveId == "manual") activity.getString(R.string.extraction_manual_label)
            else app.historicalCurveName(entry.curveId) ?: CurveCatalog.find(entry.curveId)?.name ?: entry.curveId
        val started = timestamp.format(Date(entry.startedAtMs))
        views.time.tag = "$prefix/time"
        views.time.text = if (journal) started else started.removePrefix("$currentYear-")

        views.metadata.tag = "$prefix/metadata"
        views.timing.tag = "$prefix/metadata/timing"
        views.timing.text = activity.shotTimingSummary(entry).replace(UNIT_SPACE, "\u00a0")
        views.scale.tag = "$prefix/metadata/scale"
        val reading = entry.weightHundredthsGram?.let { "%.2f\u00a0g".format(Locale.ROOT, it / 100.0) } ?: "—"
        views.scale.text = activity.getString(R.string.history_browser_scale_reading, reading)
        views.slot.tag = "$prefix/metadata/slot"
        views.slot.showText(entry.slot?.takeIf { it in 1..5 }
            ?.let { activity.getString(R.string.extraction_slot_suffix, it.toString()) })
        views.status.tag = "$prefix/status"
        views.status.showText(exceptionStatus(entry.status)?.let { activity.getString(it) })
        views.status.setTextColor(if (entry.status == ShotHistory.Status.UNKNOWN) visual.warningInk else style.accent)
        views.taste.tag = "$prefix/metadata/taste"
        val taste = if (journal) app.journalResult.getOrNull()?.find(entry.id)?.notes?.taste?.takeIf { it.isNotBlank() } else null
        views.taste.showText(taste?.let { activity.getString(R.string.journal_field_value, activity.getString(R.string.journal_taste), it) })
        return row
    }

    private fun createRow() = style.column().apply {
        background = StateListDrawable().apply {
            val active = LayerDrawable(arrayOf(ColorDrawable(style.selected), style.shape(style.accent, radius = 2))).apply {
                setLayerWidth(1, style.dp(3))
                setLayerGravity(1, Gravity.START or Gravity.FILL_VERTICAL)
                setLayerInset(1, 0, style.dp(8), 0, style.dp(8))
            }
            addState(intArrayOf(android.R.attr.state_activated), active)
            addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
        }
        minimumHeight = style.dp(64)
        setPadding(style.dp(12), style.dp(8), style.dp(12), style.dp(8))
        val title = style.text("", TaskProductVisualStyle.action)
        val time = style.text("", TaskProductVisualStyle.auxiliary, secondary = true).apply {
            fontFeatureSettings = "'tnum'"
        }
        val identity = HistoryToolbarRow(activity, style.dp(8), style.dp(2)) {
            // Keep room for the name; stack instead of compressing it in narrow or large-text windows.
            minOf(style.dp(104), ceil(title.paint.measureText(title.text.toString())).toInt())
        }.apply { addView(title); addView(time) }
        style.add(this, identity)
        val metadata = HistoryVisualFlow(activity, style.dp(12), style.dp(2))
        val timing = style.text("", TaskProductVisualStyle.auxiliary, secondary = true)
        val scale = style.text("", TaskProductVisualStyle.auxiliary, secondary = true)
        val slot = style.text("", TaskProductVisualStyle.auxiliary, secondary = true)
        val status = style.text("", TaskProductVisualStyle.auxiliary)
        listOf(timing, scale, slot, status).forEach { metadata.addView(it) }
        style.add(this, metadata, 2)
        val taste = style.text("", TaskProductVisualStyle.auxiliary, secondary = true)
        style.add(this, taste, 2)
        tag = RowViews(title, time, metadata, timing, scale, slot, status, taste)
    }

    private fun TextView.showText(value: String?) {
        text = value.orEmpty()
        visibility = if (value == null) View.GONE else View.VISIBLE
    }

    private fun exceptionStatus(status: ShotHistory.Status): Int? = when (status) {
        ShotHistory.Status.ENDED -> null
        ShotHistory.Status.STARTING -> R.string.extraction_state_starting
        ShotHistory.Status.RUNNING -> R.string.extraction_state_running
        ShotHistory.Status.STOP_REQUESTED -> R.string.history_status_stop_requested
        ShotHistory.Status.UNKNOWN -> R.string.extraction_state_unknown
        ShotHistory.Status.NOT_STARTED -> R.string.history_status_not_started
    }

    private companion object {
        val UNIT_SPACE = Regex(" (?=s$)")
    }
}

internal fun HistoryPreview.hasSamePreviewExceptNotes(other: HistoryPreview): Boolean =
    permanent != null && other.permanent != null && permanent.observation == other.permanent.observation &&
        entry == other.entry && journalFailed == other.journalFailed
