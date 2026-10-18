package io.openhoyi.mobile

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject

/** Mock-only native candidate. All action-looking views have zero business callbacks. */
class StrictVisualSampleActivity : Activity() {
    internal lateinit var fixture: StrictVisualSampleFixtures.Fixture
    internal lateinit var palette: StrictVisualSampleTokens.Palette
    internal lateinit var sampleRoot: LinearLayout
    internal val charts = mutableListOf<StrictVisualSampleChart>()
    internal val icons = mutableListOf<StrictVisualSampleIcon>()
    internal val actionViews = mutableListOf<TextView>()
    private val requested = linkedMapOf<TextView, StrictVisualSampleTokens.Type>()
    private fun dp(value: Int) = StrictVisualSampleTokens.dp(this, value.toFloat())
    override fun attachBaseContext(base: Context) {
        val config = Configuration(base.resources.configuration).apply { setLocales(LocaleList.forLanguageTags("zh-Hans")) }
        super.attachBaseContext(base.createConfigurationContext(config))
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        check(BuildConfig.MOCK_MODE && packageName == "io.openhoyi.mobile.mock")
        fixture = StrictVisualSampleFixtures.get(requireNotNull(intent.getStringExtra("fixtureId")),
            requireNotNull(intent.getStringExtra("candidateVersion")))
        palette = StrictVisualSampleTokens.Palette(resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)
        window.statusBarColor = palette.page; window.navigationBarColor = palette.surface
        window.decorView.systemUiVisibility = if (palette.dark) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(true)
        sampleRoot = row("sample/root").apply { setBackgroundColor(palette.page) }
        setContentView(sampleRoot)
        navigation()
        val isShare = fixture.id == StrictVisualSampleFixtures.ids[1]
        val page = column("sample/page").apply { setPadding(dp(16), dp(if (isShare) 8 else 16), dp(16), dp(16)) }
        sampleRoot.addView(page, LinearLayout.LayoutParams(0, -1, 1f))
        header(page)
        val content = row("sample/page/content")
        page.addView(content, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(if (isShare || fixture.id == StrictVisualSampleFixtures.ids[0]) 8 else 14) })
        when (fixture.id) {
            StrictVisualSampleFixtures.ids[0] -> preparation(content)
            StrictVisualSampleFixtures.ids[1] -> share(content)
            StrictVisualSampleFixtures.ids[2] -> inventory(content)
        }
        if (fixture.id == StrictVisualSampleFixtures.ids[0]) sampleRoot.addOnLayoutChangeListener { root, _, _, _, _, _, _, _, _ ->
            val deltaPx = root.height - dp(623)
            val chart = charts.single()
            val height = (dp(166) + deltaPx).coerceAtLeast(dp(160))
            if (chart.layoutParams.height != height) { chart.layoutParams.height = height; chart.requestLayout() }
        }
    }
    private fun row(id: String) = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; isBaselineAligned = false; tag = id }
    private fun column(id: String) = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; tag = id }
    private fun text(value: CharSequence, role: StrictVisualSampleTokens.Type, id: String, muted: Boolean = false): TextView = TextView(this).apply {
        tag = id; text = value; setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, role.sizeSp.toFloat())
        typeface = TaskTypography.face(this@StrictVisualSampleActivity, role.weight)
        includeFontPadding = false; letterSpacing = 0f; setTextColor(if (muted) palette.muted else palette.text)
        gravity = Gravity.CENTER_VERTICAL
        val requestedLine = StrictVisualSampleTokens.sp(this@StrictVisualSampleActivity, role.lineSp).toInt()
        if (Build.VERSION.SDK_INT >= 28) setLineHeight(requestedLine)
        else setLineSpacing((requestedLine - paint.fontMetricsInt.descent + paint.fontMetricsInt.ascent).coerceAtLeast(0).toFloat(), 1f)
        minHeight = requestedLine
        requested[this] = role
    }
    private fun add(parent: LinearLayout, view: View, top: Int = 0, height: Int = -2) {
        parent.addView(view, LinearLayout.LayoutParams(-1, if (height >= 0) dp(height) else height).apply { topMargin = dp(top) })
    }
    private fun shape(fill: Int, stroke: Boolean = false, radius: Int = 0) = GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(radius).toFloat(); if (stroke) setStroke(dp(1), palette.border)
    }
    private fun action(value: String, id: String, primary: Boolean = false, disabled: Boolean = false, outline: Boolean = false): TextView =
        text(value, StrictVisualSampleTokens.action, id).apply {
            gravity = Gravity.CENTER; minHeight = dp(if (primary) 56 else 48)
            setPadding(dp(8), dp(8), dp(8), dp(8)); isEnabled = !disabled
            setTextColor(when { disabled -> palette.onDisabled; primary -> palette.onPrimary; else -> palette.accent })
            background = shape(when { disabled -> palette.disabled; primary -> palette.primary; else -> Color.TRANSPARENT }, outline, 12)
            actionViews.add(this)
            // Deliberately no onClickListener/onTouchListener; never delegates to a product action.
        }
    private fun divider(parent: LinearLayout, top: Int = 8, bottom: Int = 7, id: String? = null) {
        parent.addView(View(this).apply { tag = id; setBackgroundColor(palette.border) }, LinearLayout.LayoutParams(-1, dp(1)).apply {
            topMargin = dp(top); bottomMargin = dp(bottom)
        })
    }
    private fun scrolling(content: View, id: String) = ScrollView(this).apply {
        tag = id; isFillViewport = false; addView(content, android.widget.FrameLayout.LayoutParams(-1, -2))
    }
    private fun navigation() {
        val nav = column("sample/nav/rail").apply { setBackgroundColor(palette.surface); setPadding(dp(8), dp(16), dp(8), dp(8)) }
        sampleRoot.addView(nav, LinearLayout.LayoutParams(dp(80), -1))
        add(nav, text("HOYI", StrictVisualSampleTokens.action, "sample/nav/brand").apply { gravity = Gravity.CENTER; minHeight = dp(32) })
        for ((index, label) in listOf("冲煮", "曲线", "记录", "豆子").withIndex()) {
            val selected = index == when (fixture.id) { StrictVisualSampleFixtures.ids[0] -> 0; StrictVisualSampleFixtures.ids[1] -> 1; else -> 3 }
            add(nav, navItem(label, listOf("coffee", "curve", "history", "bean")[index],
                if (selected) "sample/nav/selected" else "sample/nav/item/$index", selected), if (index == 0) 16 else 8)
        }
        nav.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        val tools = column("sample/nav/tools")
        add(tools, navItem("电子秤", "scale", "sample/nav/scale", false)); add(tools, navItem("设置", "settings", "sample/nav/settings", false), 8)
        add(nav, tools)
    }
    private fun navItem(label: String, kind: String, id: String, selected: Boolean): View {
        val item = column(id).apply { minimumHeight = dp(64); gravity = Gravity.CENTER; isSelected = selected
            if (selected) background = shape(palette.selected, radius = 12) }
        val color = if (selected) palette.accent else palette.muted
        val icon = StrictVisualSampleIcon(this, kind, color).apply { tag = "$id/icon" }
        icons.add(icon); item.addView(icon, LinearLayout.LayoutParams(dp(24), dp(24)))
        item.addView(text(label, StrictVisualSampleTokens.auxiliary, "$id/label").apply { setTextColor(color); gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        return item
    }
    private fun header(page: LinearLayout) {
        val isShare = fixture.id == StrictVisualSampleFixtures.ids[1]
        val head = row("sample/page/headerRow").apply { minimumHeight = dp(if (isShare) 48 else 80); gravity = if (isShare) Gravity.CENTER_VERTICAL else Gravity.TOP }
        if (isShare) head.addView(action("‹", "sample/page/back"), LinearLayout.LayoutParams(dp(48), -1))
        val titles = column("sample/page/titles")
        add(titles, text(fixture.title, StrictVisualSampleTokens.title, "sample/page/title"))
        if (fixture.id != StrictVisualSampleFixtures.ids[1]) add(titles,
            text(if (fixture.id == StrictVisualSampleFixtures.ids[0]) "配方与粉量已确认" else "3 款豆 · 500 g",
                StrictVisualSampleTokens.auxiliary, if (fixture.id == StrictVisualSampleFixtures.ids[2]) "sample/inventory/headerSummary" else "sample/page/subtitle", true), 8)
        head.addView(titles, if (isShare) LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(16) }
            else LinearLayout.LayoutParams(dp(694), -2))
        if (!isShare) head.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        if (fixture.id == StrictVisualSampleFixtures.ids[0]) head.addView(action("连接设备", "sample/preparation/deviceEntry"), LinearLayout.LayoutParams(dp(144), -2))
        if (fixture.id == StrictVisualSampleFixtures.ids[2]) head.addView(action("＋ 添加咖啡豆", "sample/inventory/add"), LinearLayout.LayoutParams(dp(192), -2))
        val frame = FrameLayout(this).apply { tag = "sample/page/header" }
        frame.addView(head, FrameLayout.LayoutParams(-1, -2))
        if (!isShare) frame.addView(View(this).apply { tag = "sample/page/headerDivider"; setBackgroundColor(palette.border) }, FrameLayout.LayoutParams(-1, dp(1), Gravity.BOTTOM).apply { bottomMargin = dp(1) })
        add(page, frame)
    }
    private fun chart(parent: LinearLayout, id: String, height: Int) {
        val chart = StrictVisualSampleChart(this, fixture, palette).apply { tag = id }
        charts.add(chart); add(parent, chart, 8, height)
    }
    private fun summary(id: String): View {
        val result = row(id).apply { gravity = Gravity.CENTER_VERTICAL }
        for ((index, values) in listOf("93" to "°C", "36" to "g", "4" to "个阶段").withIndex()) {
            val group = row("$id/$index").apply { gravity = Gravity.CENTER_VERTICAL }
            group.addView(text(values.first, StrictVisualSampleTokens.recipeValue, "$id/$index/value"))
            group.addView(text(values.second, StrictVisualSampleTokens.auxiliary, "$id/$index/unit", true), LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
            result.addView(group, LinearLayout.LayoutParams(0, -2, 1f))
        }
        return result
    }
    private fun preparation(parent: LinearLayout) {
        val left = column("sample/preparation/mainPane")
        parent.addView(left, LinearLayout.LayoutParams(0, -1, 1f))
        paneGap(parent, "sample/preparation/paneDivider")
        val right = column("sample/preparation/cup")
        parent.addView(right, LinearLayout.LayoutParams(dp(308), -1))
        val recipe = row("sample/preparation/recipeRow").apply { gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(48) }
        recipe.addView(text("经典浓缩", StrictVisualSampleTokens.title, "sample/preparation/recipe"), LinearLayout.LayoutParams(0, -2, 1f))
        recipe.addView(action("更换曲线 ›", "sample/preparation/changeRecipe"), LinearLayout.LayoutParams(dp(144), -2).apply { leftMargin = dp(16) })
        add(left, recipe)
        add(left, summary("sample/preparation/summary"), 8)
        val summaryGap = FrameLayout(this).apply { addView(View(this@StrictVisualSampleActivity).apply { tag = "sample/preparation/summaryDivider"; setBackgroundColor(palette.border) }, FrameLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(8) }) }
        add(left, summaryGap, height = 16)
        add(left, text("阶段目标", StrictVisualSampleTokens.section, "sample/preparation/stageTitle"))
        chart(left, "sample/preparation/stage", 166)
        add(left, text("阶段目标示意，不代表实际时间走势。", StrictVisualSampleTokens.auxiliary, "sample/preparation/stageNote", true), 8)
        val recentHead = row("sample/preparation/recentHeaderRow").apply { gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(48) }
        recentHead.addView(text("最近使用", StrictVisualSampleTokens.section, "sample/preparation/recentTitle"), LinearLayout.LayoutParams(0, -2, 1f))
        recentHead.addView(action("更多配方 ›", "sample/preparation/recentMore"), LinearLayout.LayoutParams(dp(144), -2).apply { leftMargin = dp(16) })
        add(left, recentHead, 8)
        val recent = column("sample/preparation/recent/rows")
        val inputs = fixture.json().getJSONArray("recentRecipes")
        for (index in 0 until inputs.length()) {
            val data = inputs.getJSONObject(index)
            val line = row("sample/preparation/recent/row${index + 1}").apply { gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(48) }
            line.addView(text(data.getString("name"), StrictVisualSampleTokens.action, "sample/preparation/recent/row/$index/name"), LinearLayout.LayoutParams(0, -2, 1f))
            line.addView(text("${data.getInt("temperatureC")}°C  /  ${data.getInt("targetGrams")}g  /  ${data.getInt("stages")}个阶段",
                StrictVisualSampleTokens.auxiliary, "sample/preparation/recent/row/$index/summary", true))
            add(recent, line)
        }
        left.addView(scrolling(recent, "sample/preparation/recent"), LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(8); bottomMargin = dp(1) })
        val body = column("sample/preparation/cup/body")
        val cupTitle = row("sample/preparation/cupTitleRow").apply { minimumHeight = dp(42); gravity = Gravity.CENTER_VERTICAL }
        cupTitle.addView(text("本杯准备", StrictVisualSampleTokens.section, "sample/preparation/cupTitle"), LinearLayout.LayoutParams(-1, -2))
        add(body, cupTitle)
        divider(body, top = 2, bottom = 7, id = "sample/preparation/cupTitleDivider")
        add(body, text("咖啡豆", StrictVisualSampleTokens.auxiliary, "sample/preparation/beanLabel", true))
        val bean = row("sample/preparation/cup/bean").apply { gravity = Gravity.CENTER_VERTICAL }
        bean.addView(text("哥伦比亚 水洗", StrictVisualSampleTokens.title, "sample/preparation/beanName"), LinearLayout.LayoutParams(0, -2, 1f))
        bean.addView(action("更换", "sample/preparation/changeBean"), LinearLayout.LayoutParams(dp(64), -2).apply { leftMargin = dp(8) })
        add(body, bean); divider(body, id = "sample/preparation/beanDivider")
        add(body, text("已确认粉量", StrictVisualSampleTokens.auxiliary, "sample/preparation/doseLabel", true))
        val dose = row("sample/preparation/cup/dose").apply { gravity = Gravity.CENTER_VERTICAL }
        val amount = row("sample/preparation/doseValue").apply { gravity = Gravity.CENTER_VERTICAL }
        amount.addView(text("18.0", StrictVisualSampleTokens.largeValue, "sample/preparation/doseValue/number"))
        amount.addView(text("g", StrictVisualSampleTokens.auxiliary, "sample/preparation/doseValue/unit"), LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
        dose.addView(amount, LinearLayout.LayoutParams(0, -2, 1f))
        dose.addView(action("编辑", "sample/preparation/editDose"), LinearLayout.LayoutParams(dp(64), -2).apply { leftMargin = dp(24) })
        add(body, dose, 8); divider(body, id = "sample/preparation/doseDivider")
        add(body, text("●  咖啡机已就绪", StrictVisualSampleTokens.body, "sample/preparation/machineReady")); divider(body, id = "sample/preparation/machineDivider")
        add(body, text("●  电子秤数据实时", StrictVisualSampleTokens.body, "sample/preparation/scaleReady")); divider(body, bottom = 15, id = "sample/preparation/scaleDivider")
        add(body, text("开始前将再次确认。", StrictVisualSampleTokens.auxiliary, "sample/preparation/confirmation", true).apply { minLines = 2 })
        right.addView(scrolling(body, "sample/preparation/cup/scroll"), LinearLayout.LayoutParams(-1, 0, 1f))
        add(right, action("开始萃取", "sample/preparation/primary", primary = true), 8)
        add(right, action("查看准备详情", "sample/preparation/details"), 8)
    }
    private fun paneGap(parent: LinearLayout, id: String) {
        val gap = FrameLayout(this)
        gap.addView(View(this).apply { tag = id; setBackgroundColor(palette.border) }, FrameLayout.LayoutParams(dp(1), -1, Gravity.CENTER_HORIZONTAL).apply {
            if (id == "sample/preparation/paneDivider") topMargin = dp(6)
        })
        parent.addView(gap, LinearLayout.LayoutParams(dp(24), -1))
    }
    private fun share(parent: LinearLayout) {
        val left = column("sample/share/recipeBody").apply { setPadding(dp(16), dp(16), dp(16), dp(16)) }
        val leftScroll = scrolling(left, "sample/share/recipePane").apply { background = shape(palette.surface, true, 16) }
        parent.addView(leftScroll, LinearLayout.LayoutParams(dp(608), -1))
        paneGap(parent, "sample/share/paneDivider")
        val right = column("sample/share/actionsPane").apply { setPadding(dp(16), dp(16), dp(16), dp(16)); background = shape(palette.surface, true, 16) }
        parent.addView(right, LinearLayout.LayoutParams(0, -1, 1f))
        add(left, text("经典浓缩 · 压力模式", StrictVisualSampleTokens.title, "sample/share/recipe"))
        val summaries = row("sample/share/summary")
        for ((index, metric) in listOf("设定温度" to ("93" to "°C"), "目标液重" to ("36.0" to "g")).withIndex()) {
            val card = column(if (index == 0) "sample/share/temperatureCard" else "sample/share/targetCard").apply { setPadding(dp(16), dp(8), dp(16), dp(8)); background = shape(palette.surface, true, 12) }
            add(card, text(metric.first, StrictVisualSampleTokens.auxiliary, "sample/share/summary/$index/label", true))
            val values = row("sample/share/summary/$index/values").apply { gravity = Gravity.CENTER_VERTICAL }
            values.addView(text(metric.second.first, StrictVisualSampleTokens.recipeValue, "sample/share/summary/$index/value"), LinearLayout.LayoutParams(0, -2, 1f))
            values.addView(text(metric.second.second, StrictVisualSampleTokens.auxiliary, "sample/share/summary/$index/unit", true))
            add(card, values)
            summaries.addView(card, LinearLayout.LayoutParams(0, -2, 1f).apply { if (index > 0) leftMargin = dp(16) })
        }
        add(left, summaries, 8)
        add(left, text("压力阶段预览", StrictVisualSampleTokens.section, "sample/share/stageTitle"), 8)
        chart(left, "sample/share/stage", 160)
        add(left, text("阶段目标示意，不代表实际时长。", StrictVisualSampleTokens.auxiliary, "sample/share/stageNote", true), 8)
        add(left, text("4 个阶段参数", StrictVisualSampleTokens.auxiliary.copy(weight = 500), "sample/share/tableCaption"), 8)
        val table = column("sample/share/table")
        fun tableRow(values: List<String>, id: String, header: Boolean = false) {
            val row = row(id)
            values.forEachIndexed { index, value -> row.addView(text(value,
                StrictVisualSampleTokens.auxiliary.copy(weight = if (header) 500 else 400), "$id/$index"), LinearLayout.LayoutParams(0, -2, 1f)) }
            add(table, row)
        }
        tableRow(listOf("阶段", "压力（bar）", "注水量（ml）"), "sample/share/table/header", true)
        fixture.targetsTenthsBar.forEachIndexed { index, value -> tableRow(listOf("阶段 ${index + 1}", "${value / 10.0}", "${fixture.waterMl[index]}.0"), "sample/share/table/row${index + 1}") }
        add(left, table, 8)
        add(right, text("分享方式", StrictVisualSampleTokens.section, "sample/share/actionsTitle"))
        add(right, action("分享文本", "sample/share/primary", primary = true), 16)
        add(right, action("复制曲线数据", "sample/share/secondary", outline = true), 8)
        divider(right, top = 16, bottom = 15, id = "sample/share/actionsDivider")
        val exports = column("sample/share/exportGroup")
        add(exports, action("导出 JSON 文件", "sample/share/export"))
        add(exports, action("分享已导出的文件", "sample/share/fileShareDisabled", primary = true, disabled = true), 8)
        add(exports, text("导出成功后可分享文件。", StrictVisualSampleTokens.auxiliary, "sample/share/exportHint", true), 8)
        add(right, exports)
        val support = column("sample/share/support")
        add(support, text(getString(R.string.profiles_pressure_compatible), StrictVisualSampleTokens.auxiliary, "sample/share/compatibility", true).apply { minLines = 2 }, 16)
        add(support, action("查看完整参数", "sample/share/details"), 8)
        support.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        add(support, text(getString(R.string.profiles_text_hint), StrictVisualSampleTokens.auxiliary, "sample/share/privacy", true).apply { minLines = 2 })
        right.addView(scrolling(support, "sample/share/supportScroll").apply { isFillViewport = true }, LinearLayout.LayoutParams(-1, 0, 1f))
    }
    private fun inventory(parent: LinearLayout) {
        val left = column("sample/inventory/list")
        parent.addView(left, LinearLayout.LayoutParams(dp(400), -1))
        paneGap(parent, "sample/inventory/paneDivider")
        val right = column("sample/inventory/detail")
        parent.addView(right, LinearLayout.LayoutParams(0, -1, 1f))
        left.addView(text("在库咖啡豆", StrictVisualSampleTokens.section, "sample/inventory/listTitle"),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2); leftMargin = dp(16); rightMargin = dp(16) })
        StrictVisualSampleFixtures.beans.forEachIndexed { index, bean ->
            val row = row(if (index == 0) "sample/inventory/selected" else "sample/inventory/listRow${index + 1}").apply {
                minimumHeight = dp(64); gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(8))
                isSelected = index == 0; if (index == 0) background = shape(palette.selected, radius = 12)
            }
            row.addView(text(bean.name, StrictVisualSampleTokens.body, "sample/inventory/row${index + 1}/name"), LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(text("${bean.remainingGrams} g", StrictVisualSampleTokens.body, "sample/inventory/row${index + 1}/weight"))
            add(left, row, if (index == 0) 14 else 8)
        }
        val body = column("sample/inventory/body")
        val name = row("sample/inventory/nameRow").apply { gravity = Gravity.TOP; minimumHeight = dp(38) }
        name.addView(text("哥伦比亚 水洗", StrictVisualSampleTokens.title, "sample/inventory/beanName"), LinearLayout.LayoutParams(0, -2, 1f))
        name.addView(text("批次 1", StrictVisualSampleTokens.auxiliary, "sample/inventory/batchBadge", true).apply {
            gravity = Gravity.CENTER; setPadding(dp(8), dp(8), dp(8), dp(8))
            minHeight = StrictVisualSampleTokens.sp(this@StrictVisualSampleActivity, 22).toInt() + dp(16)
            background = shape(palette.disabled, radius = 12)
        }, LinearLayout.LayoutParams(dp(80), -2).apply { leftMargin = dp(16) })
        add(body, name)
        add(body, text("剩余重量", StrictVisualSampleTokens.auxiliary, "sample/inventory/weightLabel", true), 6)
        val weight = row("sample/inventory/weight").apply { gravity = Gravity.CENTER_VERTICAL }
        weight.addView(text("90", StrictVisualSampleTokens.largeValue, "sample/inventory/weight/value"))
        weight.addView(text("g", StrictVisualSampleTokens.auxiliary, "sample/inventory/weight/unit"), LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
        add(body, weight, 8)
        add(body, text("初始 100 g", StrictVisualSampleTokens.body, "sample/inventory/initialWeight"))
        val progress = row("sample/inventory/progressRow").apply { gravity = Gravity.CENTER_VERTICAL }
        val track = LinearLayout(this).apply { tag = "sample/inventory/progress"; background = shape(palette.disabled, radius = 12) }
        track.addView(View(this).apply { background = shape(palette.primary, radius = 12) }, LinearLayout.LayoutParams(0, -1, 9f))
        track.addView(View(this), LinearLayout.LayoutParams(0, -1, 1f))
        progress.addView(track, LinearLayout.LayoutParams(0, dp(8), 1f))
        progress.addView(text("90%", StrictVisualSampleTokens.auxiliary, "sample/inventory/progressPercent", true).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(dp(52), -2).apply { leftMargin = dp(16) })
        add(body, progress, 2); divider(body, top = 8, bottom = 15, id = "sample/inventory/weightDivider")
        val dateGroup = column("sample/inventory/dates")
        val labels = row("sample/inventory/dateLabels")
        val values = row("sample/inventory/dateValues")
        for ((index, pair) in listOf("购入日期" to "2026-10-02", "烘焙日期" to "2026-09-28", "开封日期" to "2026-10-03").withIndex()) {
            labels.addView(text(pair.first, StrictVisualSampleTokens.auxiliary, "sample/inventory/date/$index/label", true), LinearLayout.LayoutParams(0, -2, 1f))
            values.addView(text(pair.second, StrictVisualSampleTokens.body, "sample/inventory/date/$index/value"), LinearLayout.LayoutParams(0, -2, 1f))
        }
        add(dateGroup, labels); add(dateGroup, values, 8)
        add(body, dateGroup); divider(body, top = 8, bottom = 7, id = "sample/inventory/datesDivider")
        val ledgerHead = row("sample/inventory/ledgerHeaderRow").apply { gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(48) }
        ledgerHead.addView(text("最近流水", StrictVisualSampleTokens.section, "sample/inventory/ledgerTitle"), LinearLayout.LayoutParams(0, -2, 1f))
        ledgerHead.addView(action("查看全部 ›", "sample/inventory/ledgerMore"), LinearLayout.LayoutParams(dp(136), -2).apply { leftMargin = dp(8) })
        add(body, ledgerHead)
        val ledger = column("sample/inventory/ledger")
        for ((index, values) in listOf(listOf("类型", "变更", "原因"), listOf("调整", "-10 g", "盘点修正"), listOf("入库", "+100 g", "—")).withIndex()) {
            val row = row(if (index == 0) "sample/inventory/ledgerColumns" else "sample/inventory/ledgerRow$index")
            values.forEachIndexed { column, value -> row.addView(text(value, if (index == 0) StrictVisualSampleTokens.auxiliary else StrictVisualSampleTokens.body,
                "sample/inventory/ledger/$index/$column", index == 0), LinearLayout.LayoutParams(0, -2, 1f)) }
            add(ledger, row)
        }
        add(body, ledger, 8)
        right.addView(scrolling(body, "sample/inventory/bodyScroll"), LinearLayout.LayoutParams(-1, 0, 1f))
        val actions = row("sample/inventory/actions").apply { gravity = Gravity.CENTER_VERTICAL }
        for ((index, pair) in listOf("使用这款豆" to 216, "调整库存" to 176, "查看批次详情" to 194).withIndex()) {
            actions.addView(action(pair.first, listOf("sample/inventory/primary", "sample/inventory/adjust", "sample/inventory/batchDetails")[index], primary = index == 0, outline = index == 1),
                LinearLayout.LayoutParams(0, -2, pair.second.toFloat()).apply { if (index > 0) leftMargin = dp(8) })
        }
        add(right, actions, 8)
        add(right, text("确认本杯用量后才扣减库存。", StrictVisualSampleTokens.auxiliary, "sample/inventory/confirmation", true), 8)
    }
    internal fun requestedRoles() = JSONArray(requested.map { (view, type) -> JSONObject().put("semanticId", view.tag)
        .put("requestedSizeSp", type.sizeSp).put("requestedLineSp", type.lineSp).put("requestedWeight", type.weight)
        .put("actualTextSizePx", view.textSize).put("actualLineHeightPx", view.lineHeight)
        .put("fontFeatureSettings", view.fontFeatureSettings ?: JSONObject.NULL).put("typefaceStyle", view.typeface.style)
        .put("fakeBold", view.paint.isFakeBoldText).put("requestedStatus", "proposed")
        .put("paintFontAscentPx", view.paint.fontMetricsInt.ascent).put("paintFontDescentPx", view.paint.fontMetricsInt.descent)
        .put("actualBaselinePx", view.baseline).apply {
            val fm = view.paint.fontMetricsInt
            val centered = view.paddingTop + (view.height - view.paddingTop - view.paddingBottom - (fm.descent - fm.ascent)) / 2 - fm.ascent
            put("singleLineCenteredBaselineExpectedPx", if (view.lineCount == 1) centered else JSONObject.NULL)
            put("singleLineBaselineDeltaPx", if (view.lineCount == 1) view.baseline - centered else JSONObject.NULL)
            put("baselineSemantics", "integer_centered_actual_Paint_metrics; multiline_uses_native_layout_not_single_line_formula")
        } })
}
