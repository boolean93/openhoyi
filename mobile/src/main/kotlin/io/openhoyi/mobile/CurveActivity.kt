package io.openhoyi.mobile

import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowInsets
import android.widget.*

/** Library browsing and selection are local only; no BLE command is sent here. */
class CurveActivity : ThemedActivity() {
    private data class RowViews(val title: TextView, val subtitle: TextView, val preview: CurveStageView)
    private lateinit var detailTitle: TextView
    private lateinit var detailCategory: TextView
    private lateinit var details: TextView
    private lateinit var availability: TextView
    private lateinit var select: Button
    private lateinit var assignPreset: Button
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var search: EditText
    private lateinit var resultCount: TextView
    private lateinit var stageChart: CurveStageView
    private lateinit var stageCaption: TextView
    private lateinit var browserPane: LinearLayout
    private lateinit var detailPanel: LinearLayout
    private lateinit var detailScroll: ScrollView
    private var compact = false
    private var category = CurveCategoryFilter.ALL
    private var visibleItems = emptyList<CurveLibraryItem>()
    private var selected: CurveLibraryItem? = null
    private var detailBackCallback: android.window.OnBackInvokedCallback? = null
    private val library get() = (application as MobileApplication).curves

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        category = CurveCategoryFilter.restore(savedInstanceState?.getString("category"))
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.mobile_background))
            setOnApplyWindowInsetsListener { view, insets ->
                val bars = if (Build.VERSION.SDK_INT >= 30) {
                    val a = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                    intArrayOf(a.left, a.top, a.right, a.bottom)
                } else {
                    @Suppress("DEPRECATION")
                    intArrayOf(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                        insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                }
                view.setPadding(bars[0], bars[1], bars[2], bars[3])
                insets
            }
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(12))
        }
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        HoyiUi.navigation(this, root, CurveActivity::class.java)
        setContentView(root)
        HoyiUi.header(this, body, getString(R.string.curve_title), getString(R.string.curve_subtitle))
        val work = LinearLayout(this).apply { orientation = if (HoyiUi.wide(this@CurveActivity)) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL }
        body.addView(work, LinearLayout.LayoutParams(-1, 0, 1f))
        compact = !HoyiUi.wide(this)
        val browser = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        browserPane = browser
        detailPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val detailPane = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        detailScroll = ScrollView(this).apply { isFillViewport = true; addView(detailPane) }
        if (compact) HoyiUi.button(this, detailPanel, getString(R.string.curve_back)) { showBrowser() }
        detailPanel.addView(detailScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        if (!compact) {
            work.addView(browser, LinearLayout.LayoutParams(0, -1, .95f))
            work.addView(detailPanel, LinearLayout.LayoutParams(0, -1, 1.05f).apply { marginStart = dp(16) })
        } else {
            work.addView(browser, LinearLayout.LayoutParams(-1, -1))
            work.addView(detailPanel, LinearLayout.LayoutParams(-1, -1))
            detailPanel.visibility = View.GONE
        }
        search = EditText(this).apply {
            hint = getString(R.string.curve_search_hint)
            setSingleLine(true)
            textSize = 16f
            setPadding(dp(16), 0, dp(16), 0)
            background = HoyiUi.shape(this@CurveActivity, R.color.mobile_surface, 12, R.color.mobile_border)
        }
        browser.addView(search, LinearLayout.LayoutParams(-1, dp(52)))
        val categories = CurveCategoryFilter.entries
        val filter = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val filterRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        filter.addView(filterRow)
        browser.addView(filter, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) })
        val filterChips = mutableListOf<TextView>()
        fun updateFilterChips() {
            filterChips.forEachIndexed { index, chip ->
                val chosen = categories[index] == category
                chip.setTextColor(getColor(if (chosen) R.color.mobile_accent else R.color.mobile_muted))
                chip.background = HoyiUi.shape(this, if (chosen) R.color.mobile_accent_soft else R.color.mobile_surface,
                    10, R.color.mobile_border)
                chip.contentDescription = getString(R.string.curve_filter_description, categories[index].label(this), if (chosen) getString(R.string.curve_filter_selected_suffix) else "")
            }
        }
        categories.forEach { name ->
            filterChips += TextView(this).apply {
                text = name.label(this@CurveActivity)
                textSize = 15f
                gravity = android.view.Gravity.CENTER
                minHeight = dp(44)
                setPadding(dp(16), dp(8), dp(16), dp(8))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    category = name
                    updateFilterChips()
                    refreshList()
                }
                filterRow.addView(this, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8) })
            }
        }
        updateFilterChips()
        val listHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        browser.addView(listHeader, LinearLayout.LayoutParams(-1, dp(48)))
        resultCount = HoyiUi.label(this, listHeader, "", 13, muted = true).apply {
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            setSingleLine(true)
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        listHeader.addView(TextView(this).apply {
            text = getString(R.string.curve_legacy)
            textSize = 14f
            gravity = android.view.Gravity.CENTER
            minHeight = dp(48)
            isClickable = true
            isFocusable = true
            contentDescription = getString(R.string.curve_legacy_description)
            setTextColor(getColor(R.color.mobile_accent))
            setOnClickListener { startActivity(Intent(this@CurveActivity, LegacyCurveActivity::class.java)) }
        }, LinearLayout.LayoutParams(-2, -1).apply { marginStart = dp(8) })
        val list = ListView(this).apply {
            divider = null
            dividerHeight = dp(6)
            selector = HoyiUi.shape(this@CurveActivity, R.color.mobile_accent_soft, 12)
        }
        adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, mutableListOf()) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val row = convertView as? LinearLayout ?: LinearLayout(this@CurveActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    minimumHeight = dp(76)
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    val thumbnail = CurveStageView(this@CurveActivity).apply {
                        compact = true
                        background = HoyiUi.shape(this@CurveActivity, R.color.mobile_accent_soft, 10)
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    }
                    addView(thumbnail, LinearLayout.LayoutParams(dp(78), dp(52)).apply { marginEnd = dp(12) })
                    val words = LinearLayout(this@CurveActivity).apply { orientation = LinearLayout.VERTICAL }
                    addView(words, LinearLayout.LayoutParams(0, -2, 1f))
                    val title = HoyiUi.label(this@CurveActivity, words, "", 16, true).apply {
                        maxLines = 2
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    }
                    val subtitle = HoyiUi.label(this@CurveActivity, words, "", 13, muted = true).apply {
                        setPadding(0, dp(5), 0, 0)
                    }
                    tag = RowViews(title, subtitle, thumbnail)
                }
                val item = visibleItems[position]
                val views = row.tag as RowViews
                views.title.text = item.name
                views.subtitle.text = getString(R.string.curve_row_summary, CurveCategoryFilter.display(this@CurveActivity, item.category), if (library.canStart(item)) getString(R.string.home_curve_startable) else getString(R.string.curve_browse_only))
                views.preview.targets = stageTargets(item)
                row.background = HoyiUi.shape(this@CurveActivity,
                    if (item.id == selected?.id) R.color.mobile_accent_soft else R.color.mobile_surface,
                    12, R.color.mobile_border)
                return row
            }
        }
        list.adapter = adapter
        val listHolder = FrameLayout(this)
        browser.addView(listHolder, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(8) })
        listHolder.addView(list, FrameLayout.LayoutParams(-1, -1))
        val emptyState = TextView(this).apply {
            text = getString(R.string.curve_empty)
            textSize = 16f
            gravity = android.view.Gravity.CENTER
            setTextColor(getColor(R.color.mobile_muted))
        }
        listHolder.addView(emptyState, FrameLayout.LayoutParams(-1, -1))
        list.emptyView = emptyState
        list.setOnItemClickListener { _, _, position, _ -> show(visibleItems[position]) }

        val preview = HoyiUi.card(this, detailPane, getString(R.string.curve_detail_title))
        detailTitle = HoyiUi.label(this, preview, getString(R.string.curve_detail_initial), 21, true)
        detailCategory = HoyiUi.label(this, preview, getString(R.string.curve_detail_hint), 14, muted = true)
        stageChart = CurveStageView(this).apply { visibility = View.GONE }
        preview.addView(stageChart, LinearLayout.LayoutParams(-1, dp(150)).apply { topMargin = dp(14) })
        stageCaption = HoyiUi.label(this, preview, getString(R.string.curve_stage_caption), 12, muted = true)
            .apply { visibility = View.GONE }
        details = HoyiUi.label(this, preview, "", 15)
        availability = HoyiUi.label(this, preview, "", 15, true).apply { visibility = View.GONE }
        val actions = if (compact) detailPanel else LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            detailPanel.addView(this)
        }
        select = HoyiUi.button(this, actions, getString(R.string.curve_use), primary = true) {
            selected?.let { item ->
                getSharedPreferences("curves", MODE_PRIVATE).edit().putString("selected", item.id).apply()
                finish()
            }
        }.apply { isEnabled = false; visibility = View.GONE }
        assignPreset = HoyiUi.button(this, actions, getString(R.string.curve_assign_slot)) {
            val item = selected?.takeIf { it.factoryCurve != null && library.canStart(it) } ?: return@button
            AlertDialog.Builder(this).setTitle(getString(R.string.curve_choose_slot))
                .setItems((1..5).map { getString(R.string.home_slot, it.toString()) }.toTypedArray()) { _, index ->
                    val slot = index + 1
                    getSharedPreferences("presets", MODE_PRIVATE).edit()
                        .putString(PresetSlots.key(slot), item.id).apply()
                    Toast.makeText(this, getString(R.string.curve_slot_assigned, item.name, slot.toString()), Toast.LENGTH_SHORT).show()
                }.show()
        }.apply { isEnabled = false; visibility = View.GONE }
        if (!compact) {
            select.layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply {
                topMargin = dp(10); marginEnd = dp(8)
            }
            assignPreset.layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { topMargin = dp(10) }
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = refreshList()
            override fun afterTextChanged(s: Editable?) = Unit
        })
        search.setText(savedInstanceState?.getString("query").orEmpty())
        refreshList()
        val initialId = savedInstanceState?.getString("selected")
            ?: getSharedPreferences("curves", MODE_PRIVATE).getString("selected", null)
        initialId?.let(library::find)?.let { item ->
            if (!compact || savedInstanceState?.getBoolean("detailVisible") == true) show(item)
            else { selected = item; adapter.notifyDataSetChanged() }
        }
    }

    private fun showBrowser() {
        detailPanel.visibility = View.GONE
        browserPane.visibility = View.VISIBLE
        unregisterDetailBack()
    }

    private fun registerDetailBack() {
        if (Build.VERSION.SDK_INT < 33 || detailBackCallback != null) return
        val callback = android.window.OnBackInvokedCallback { showBrowser() }
        onBackInvokedDispatcher.registerOnBackInvokedCallback(
            android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback)
        detailBackCallback = callback
    }

    private fun unregisterDetailBack() {
        if (Build.VERSION.SDK_INT < 33) return
        detailBackCallback?.let { onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it) }
        detailBackCallback = null
    }

    @Deprecated("Platform back callback")
    override fun onBackPressed() {
        if (compact && detailPanel.visibility == View.VISIBLE) showBrowser()
        else super.onBackPressed()
    }

    override fun onDestroy() {
        unregisterDetailBack()
        super.onDestroy()
    }

    private fun show(item: CurveLibraryItem) {
        selected = item
        stageChart.targets = stageTargets(item)
        stageChart.visibility = View.VISIBLE
        stageCaption.visibility = View.VISIBLE
        if (compact) {
            browserPane.visibility = View.GONE
            detailPanel.visibility = View.VISIBLE
            detailScroll.scrollTo(0, 0)
            registerDetailBack()
        }
        val canStart = library.canStart(item)
        detailTitle.text = item.name
        detailCategory.text = CurveCategoryFilter.display(this, item.category)
        details.text = item.details
        availability.text = if (canStart) getString(R.string.curve_verified) else getString(R.string.curve_unavailable)
        availability.setTextColor(getColor(if (canStart) R.color.mobile_accent else R.color.mobile_muted))
        availability.visibility = View.VISIBLE
        select.isEnabled = canStart
        select.visibility = View.VISIBLE
        select.text = if (canStart) getString(R.string.curve_use) else getString(R.string.curve_cannot_start)
        assignPreset.visibility = if (item.factoryCurve != null) View.VISIBLE else View.GONE
        assignPreset.isEnabled = item.factoryCurve != null && canStart
        adapter.notifyDataSetChanged()
    }

    private fun refreshList() {
        if (!::adapter.isInitialized || !::search.isInitialized) return
        visibleItems = CurveSearch.filter(library.items, category, search.text.toString())
        resultCount.text = getString(R.string.curve_result_count, visibleItems.size.toString())
        adapter.clear()
        adapter.addAll(visibleItems.map { it.id })
        if (selected != null && selected !in visibleItems) {
            selected = null
            stageChart.targets = emptyList()
            stageChart.visibility = View.GONE
            stageCaption.visibility = View.GONE
            detailTitle.text = getString(R.string.curve_detail_initial)
            detailCategory.text = getString(R.string.curve_detail_hint)
            details.text = ""
            availability.visibility = View.GONE
            select.isEnabled = false
            select.visibility = View.GONE
            assignPreset.isEnabled = false
            assignPreset.visibility = View.GONE
        }
    }
    private fun stageTargets(item: CurveLibraryItem): List<Int> =
        item.factoryCurve?.targets?.take(item.factoryCurve.segmentCount)
            ?: item.controlProfile?.parameters?.let {
                listOf(it.target1, it.target2, it.target3, it.target4).take(it.segmentCount)
            } ?: emptyList()
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("selected", selected?.id)
        outState.putString("category", category.name)
        outState.putString("query", search.text.toString())
        outState.putBoolean("detailVisible", compact && detailPanel.visibility == View.VISIBLE)
        super.onSaveInstanceState(outState)
    }
    private fun dp(value: Int) = HoyiUi.dp(this, value)
}
