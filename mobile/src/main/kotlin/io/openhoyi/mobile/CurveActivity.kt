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
    private lateinit var detailScroll: ScrollView
    private var compact = false
    private var category = "全部"
    private var visibleItems = emptyList<CurveLibraryItem>()
    private var selected: CurveLibraryItem? = null
    private val library get() = (application as MobileApplication).curves

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        HoyiUi.header(this, body, "曲线库", "搜索、预览并选用曲线")
        val work = LinearLayout(this).apply { orientation = if (HoyiUi.wide(this@CurveActivity)) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL }
        body.addView(work, LinearLayout.LayoutParams(-1, 0, 1f))
        compact = !HoyiUi.wide(this)
        val browser = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        browserPane = browser
        val detailPane = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        detailScroll = ScrollView(this).apply { addView(detailPane) }
        if (!compact) {
            work.addView(browser, LinearLayout.LayoutParams(0, -1, .95f))
            work.addView(detailScroll, LinearLayout.LayoutParams(0, -1, 1.05f).apply { marginStart = dp(16) })
        } else {
            work.addView(browser, LinearLayout.LayoutParams(-1, -1))
            work.addView(detailScroll, LinearLayout.LayoutParams(-1, -1))
            detailScroll.visibility = View.GONE
        }
        search = EditText(this).apply {
            hint = "搜索曲线名称"
            setSingleLine(true)
            textSize = 16f
            setPadding(dp(16), 0, dp(16), 0)
            background = HoyiUi.shape(this@CurveActivity, R.color.mobile_surface, 12, R.color.mobile_border)
        }
        browser.addView(search, LinearLayout.LayoutParams(-1, dp(52)))
        val categories = listOf("全部", "已采集验证", "深烘", "中烘", "浅烘", "超萃")
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
                chip.contentDescription = "${categories[index]}分类${if (chosen) "，已选中" else ""}"
            }
        }
        categories.forEach { name ->
            filterChips += TextView(this).apply {
                text = name
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
        resultCount = HoyiUi.label(this, browser, "", 13, muted = true)
        HoyiUi.button(this, browser, "查看旧版导入曲线") {
            startActivity(Intent(this, LegacyCurveActivity::class.java))
        }
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
                views.subtitle.text = "${item.category} · ${if (library.canStart(item)) "可萃取" else "仅浏览"}"
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
            text = "没有找到匹配的曲线\n试试其他名称或切换分类"
            textSize = 16f
            gravity = android.view.Gravity.CENTER
            setTextColor(getColor(R.color.mobile_muted))
        }
        listHolder.addView(emptyState, FrameLayout.LayoutParams(-1, -1))
        list.emptyView = emptyState
        list.setOnItemClickListener { _, _, position, _ -> show(visibleItems[position]) }

        if (compact) HoyiUi.button(this, detailPane, "返回曲线列表") {
            detailScroll.visibility = View.GONE
            browserPane.visibility = View.VISIBLE
        }
        val preview = HoyiUi.card(this, detailPane, "曲线详情")
        detailTitle = HoyiUi.label(this, preview, "点选左侧曲线", 21, true)
        detailCategory = HoyiUi.label(this, preview, "查看参数与可用状态", 14, muted = true)
        stageChart = CurveStageView(this).apply { visibility = View.GONE }
        preview.addView(stageChart, LinearLayout.LayoutParams(-1, dp(150)).apply { topMargin = dp(14) })
        stageCaption = HoyiUi.label(this, preview, "分段目标示意 · 不是实际萃取曲线", 12, muted = true)
            .apply { visibility = View.GONE }
        details = HoyiUi.label(this, preview, "", 15)
        availability = HoyiUi.label(this, preview, "", 15, true).apply { visibility = View.GONE }
        val spacer = Space(this)
        if (HoyiUi.wide(this)) detailPane.addView(spacer, LinearLayout.LayoutParams(1, 0, 1f))
        select = HoyiUi.button(this, detailPane, "使用此曲线", primary = true) {
            selected?.let { item ->
                getSharedPreferences("curves", MODE_PRIVATE).edit().putString("selected", item.id).apply()
                finish()
            }
        }.apply { isEnabled = false; visibility = View.GONE }
        assignPreset = HoyiUi.button(this, detailPane, "放入快捷槽位") {
            val item = selected?.takeIf { it.factoryCurve != null && library.canStart(it) } ?: return@button
            AlertDialog.Builder(this).setTitle("选择快捷槽位")
                .setItems(arrayOf("槽位 1", "槽位 2", "槽位 3", "槽位 4", "槽位 5")) { _, index ->
                    val slot = index + 1
                    getSharedPreferences("presets", MODE_PRIVATE).edit()
                        .putString(PresetSlots.key(slot), item.id).apply()
                    Toast.makeText(this, "已将${item.name}放入槽位 $slot", Toast.LENGTH_SHORT).show()
                }.show()
        }.apply { isEnabled = false }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = refreshList()
            override fun afterTextChanged(s: Editable?) = Unit
        })
        refreshList()
        val initialId = savedInstanceState?.getString("selected")
            ?: getSharedPreferences("curves", MODE_PRIVATE).getString("selected", null)
        initialId?.let(library::find)?.let(::show)
    }

    private fun show(item: CurveLibraryItem) {
        selected = item
        stageChart.targets = stageTargets(item)
        stageChart.visibility = View.VISIBLE
        stageCaption.visibility = View.VISIBLE
        if (compact) {
            browserPane.visibility = View.GONE
            detailScroll.visibility = View.VISIBLE
            detailScroll.scrollTo(0, 0)
        }
        val canStart = library.canStart(item)
        detailTitle.text = item.name
        detailCategory.text = item.category
        details.text = item.details
        availability.text = if (canStart) "✓ 已通过报文校验，可用于萃取" else "仅可浏览 · 启动报文校验不可用"
        availability.setTextColor(getColor(if (canStart) R.color.mobile_accent else R.color.mobile_muted))
        availability.visibility = View.VISIBLE
        select.isEnabled = canStart
        select.visibility = View.VISIBLE
        select.text = if (canStart) "使用此曲线" else "此曲线不可萃取"
        assignPreset.visibility = if (item.factoryCurve != null) View.VISIBLE else View.GONE
        assignPreset.isEnabled = item.factoryCurve != null && canStart
        adapter.notifyDataSetChanged()
    }

    private fun refreshList() {
        if (!::adapter.isInitialized || !::search.isInitialized) return
        visibleItems = CurveSearch.filter(library.items, category, search.text.toString())
        resultCount.text = "找到 ${visibleItems.size} 条曲线"
        adapter.clear()
        adapter.addAll(visibleItems.map { it.id })
        if (selected != null && selected !in visibleItems) {
            selected = null
            stageChart.targets = emptyList()
            stageChart.visibility = View.GONE
            stageCaption.visibility = View.GONE
            detailTitle.text = "点选左侧曲线"
            detailCategory.text = "查看参数与可用状态"
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
        super.onSaveInstanceState(outState)
    }
    private fun dp(value: Int) = HoyiUi.dp(this, value)
}
