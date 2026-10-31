package io.openhoyi.mobile

import android.content.Intent
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Imported UniApp records have their own list and never join Alpha control/history state. */
class LegacyHistoryActivity : ThemedActivity() {
    private val visual by lazy { LegacyVisualStyle(this) }
    private val style get() = visual.palette
    private data class RowViews(val day: TextView, val title: TextView, val subtitle: TextView)
    private lateinit var collection: TaskCollectionLayout
    private lateinit var preview: LegacyHistoryContent
    private var selectedId: String? = null
    private var compact = true
    private var rows = emptyList<LegacyShot>()
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var count: TextView
    private lateinit var list: ListView
    private lateinit var emptyState: LinearLayout
    private lateinit var emptyTitle: TextView
    private lateinit var emptyMessage: TextView
    private var refreshGeneration = 0
    private var listState: android.os.Parcelable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        listState = savedInstanceState?.getParcelable("listState")
        selectedId = savedInstanceState?.getString("selectedId")
        val root = style.page(this, getString(R.string.legacy_history_title))
        count = style.text("", TaskProductVisualStyle.auxiliary, "legacy/history/count", true)
        val information = LegacyHistoryInformationRow(this)
        root.addView(information, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        information.addView(count, LinearLayout.LayoutParams(-1, -2))
        HoyiUi.tabs(this, information, listOf(getString(R.string.journal_tab), getString(R.string.history_local_tab),
            getString(R.string.history_legacy_tab)), selected = 2) {
            if (it != 2) {
                rememberList()
                startActivity(Intent(this, HistoryActivity::class.java).apply {
                    putExtra(HistoryActivity.EXTRA_HISTORY_SOURCE,
                        if (it == 0) HistoryActivity.SOURCE_JOURNAL else HistoryActivity.SOURCE_LOCAL)
                })
            }
        }
        visual.styleTabs(information.getChildAt(1))
        collection = TaskCollectionLayout(this, .34f)
        root.addView(collection, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(16) })
        preview = LegacyHistoryContent(this, visual) { openDetails(it) }
        collection.detailBody.addView(preview, LinearLayout.LayoutParams(-1, -1))
        collection.onPresentationChanged = { twoPane, _ -> compact = !twoPane }
        collection.browserBody.background = style.shape(style.surface, outline = true, radius = 16)
        adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, mutableListOf()) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val card = convertView as? LinearLayout ?: LinearLayout(this@LegacyHistoryActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    minimumHeight = dp(64)
                    setPadding(dp(16), dp(8), dp(16), dp(8))
                    background = HoyiUi.shape(this@LegacyHistoryActivity, R.color.mobile_surface, 0)
                    val day = style.text("", TaskProductVisualStyle.auxiliary, "legacy/history/row/day", true).also { addView(it) }
                    val heading = style.row().apply { gravity = android.view.Gravity.CENTER_VERTICAL }
                    addView(heading, LinearLayout.LayoutParams(-1, -2))
                    val title = style.text("", TaskProductVisualStyle.compactMetric, "legacy/history/row/title").also {
                        heading.addView(it, LinearLayout.LayoutParams(0, -2, 1f))
                    }
                    heading.addView(HomeVisualIcon(this@LegacyHistoryActivity, "chevron", style.muted),
                        LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginStart = dp(8) })
                    val subtitle = style.text("", TaskProductVisualStyle.auxiliary, "legacy/history/row/metadata", true).also { addView(it) }
                    subtitle.setPadding(0, dp(8), 0, 0)
                    tag = RowViews(day, title, subtitle)
                }
                val item = rows[position]
                card.setBackgroundColor(if (item.id == selectedId) style.selected else style.surface)
                card.isActivated = item.id == selectedId
                val views = card.tag as RowViews
                val day = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(item.createdAtMs))
                val previousDay = rows.getOrNull(position - 1)?.let { SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(it.createdAtMs)) }
                views.day.visibility = if (day == previousDay) View.GONE else View.VISIBLE
                views.day.text = day
                views.day.setPadding(0, 0, 0, dp(8))
                views.title.text = item.profileName.ifBlank { getString(R.string.legacy_history_unnamed) }
                views.subtitle.text = getString(R.string.legacy_history_row, SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date(item.createdAtMs)), item.durationSec.toString())
                return card
            }
        }
        val content = FrameLayout(this)
        collection.browserBody.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        list = ListView(this).apply {
            adapter = this@LegacyHistoryActivity.adapter
            divider = android.graphics.drawable.ColorDrawable(style.border)
            dividerHeight = dp(1)
            setOnItemClickListener { _, _, position, _ ->
                rememberList()
                selectedId = rows[position].id
                preview.present(rows[position]); this@LegacyHistoryActivity.adapter.notifyDataSetChanged()
                if (compact) openDetails(rows[position])
            }
        }
        content.addView(list, FrameLayout.LayoutParams(-1, -1))
        emptyState = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            visibility = View.GONE
        }
        content.addView(emptyState, FrameLayout.LayoutParams(-1, -1))
        val emptyCard = visual.panel(style.column()).also { emptyState.addView(it, LinearLayout.LayoutParams(-1, -2)) }
        emptyTitle = style.text(getString(R.string.legacy_history_empty_title), TaskProductVisualStyle.section).also { style.add(emptyCard, it) }
        emptyMessage = style.text(getString(R.string.legacy_history_empty_message), TaskProductVisualStyle.auxiliary, secondary = true).also { style.add(emptyCard, it, 8) }
    }

    override fun onStart() {
        super.onStart()
        rememberList()
        val generation = ++refreshGeneration
        count.text = getString(R.string.legacy_history_loading)
        emptyState.visibility = View.GONE
        list.visibility = View.GONE
        Thread({
            val result = runCatching { (application as MobileApplication).legacyHistory.list() }
            runOnUiThread {
                if (isFinishing || isDestroyed || generation != refreshGeneration) return@runOnUiThread
                rows = result.getOrDefault(emptyList())
                count.text = result.fold({ getString(R.string.legacy_history_count, it.size.toString()) },
                    { getString(R.string.legacy_history_read_error, it.message ?: it.javaClass.simpleName) })
                adapter.clear()
                adapter.addAll(rows.map { it.id })
                val selected = rows.firstOrNull { it.id == selectedId } ?: rows.firstOrNull()
                selectedId = selected?.id
                preview.present(selected, if (result.isFailure) getString(R.string.legacy_history_failed_message) else null)
                adapter.notifyDataSetChanged()
                emptyTitle.text = if (result.isFailure) getString(R.string.legacy_history_failed) else getString(R.string.legacy_history_empty_title)
                emptyMessage.text = if (result.isFailure) getString(R.string.legacy_history_failed_message)
                    else getString(R.string.legacy_history_empty_message)
                emptyState.visibility = if (adapter.count == 0) View.VISIBLE else View.GONE
                list.visibility = if (adapter.count == 0) View.GONE else View.VISIBLE
                if (adapter.count > 0) list.post {
                    if (!isFinishing && !isDestroyed && generation == refreshGeneration)
                        listState?.let { list.onRestoreInstanceState(it) }
                }
            }
        }, "legacy-history-list").start()
    }

    private fun rememberList() {
        if (::list.isInitialized && list.isShown && !list.isLayoutRequested && list.childCount > 0)
            listState = list.onSaveInstanceState()
    }

    override fun onStop() { rememberList(); super.onStop() }

    override fun onSaveInstanceState(outState: Bundle) {
        rememberList()
        outState.putParcelable("listState", listState)
        outState.putString("selectedId", selectedId)
        super.onSaveInstanceState(outState)
    }

    private fun openDetails(shot: LegacyShot) {
        rememberList()
        startActivity(Intent(this, LegacyHistoryDetailActivity::class.java).putExtra("legacyId", shot.id))
    }

    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()
}

/** Measures the existing count and three tabs at natural width before choosing one or two rows. */
private class LegacyHistoryInformationRow(context: Context) : LinearLayout(context) {
    init { orientation = VERTICAL; isBaselineAligned = false; gravity = android.view.Gravity.CENTER_VERTICAL }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val count = getChildAt(0)
        val tabs = getChildAt(1) as LinearLayout
        val natural = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        count.measure(natural, natural)
        val minTab = (0 until tabs.childCount).maxOf { index ->
            tabs.getChildAt(index).measure(natural, natural)
            maxOf(tabs.getChildAt(index).measuredWidth, (48 * resources.displayMetrics.density).toInt())
        }
        val density = resources.displayMetrics.density
        val gap = (24 * density).toInt()
        val tabGap = (8 * density).toInt()
        val available = (View.MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight).coerceAtLeast(0)
        val inline = View.MeasureSpec.getMode(widthMeasureSpec) != View.MeasureSpec.UNSPECIFIED &&
            count.measuredWidth + gap + minTab * tabs.childCount + tabGap * (tabs.childCount - 1) <= available
        orientation = if (inline) HORIZONTAL else VERTICAL
        (count.layoutParams as LayoutParams).apply { width = if (inline) -2 else -1; height = -2; weight = 0f }
        (tabs.layoutParams as LayoutParams).apply {
            width = if (inline) 0 else -1; height = -2; weight = if (inline) 1f else 0f
            marginStart = if (inline) gap else 0
            topMargin = if (inline) 0 else (12 * density).toInt()
            resolveLayoutDirection(this@LegacyHistoryInformationRow.layoutDirection)
        }
        count.forceLayout(); tabs.forceLayout()
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
