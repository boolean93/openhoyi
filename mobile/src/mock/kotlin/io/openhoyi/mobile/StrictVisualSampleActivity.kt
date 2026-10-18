package io.openhoyi.mobile

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
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
import kotlin.math.roundToInt

/** Mock-only native candidate. All action-looking views have zero business callbacks. */
class StrictVisualSampleActivity : Activity() {
    internal lateinit var fixture: StrictVisualSampleFixtures.Fixture
    internal lateinit var palette: StrictVisualSampleTokens.Palette
    internal lateinit var sampleRoot: LinearLayout
    internal val charts = mutableListOf<StrictVisualSampleChart>()
    internal val icons = mutableListOf<StrictVisualSampleIcon>()
    internal val actionViews = mutableListOf<View>()
    internal lateinit var themeFontDiagnostics: JSONObject
        private set
    private val requested = linkedMapOf<TextView, StrictVisualSampleTokens.Type>()
    private fun dp(value: Int) = StrictVisualSampleTokens.dp(this, value.toFloat())
    override fun attachBaseContext(base: Context) {
        val config = Configuration(base.resources.configuration).apply { setLocales(LocaleList.forLanguageTags("zh-Hans")) }
        super.attachBaseContext(base.createConfigurationContext(config))
    }
    override fun onCreate(state: Bundle?) {
        val beforeFontFamily = resolvedThemeFontFamily()
        // Plain Activity does not run ThemedActivity's Chinese font-only overlay.
        // Keep the specimen isolated from that class's preference/recreation behavior.
        if (resources.configuration.locales[0].language == "zh") theme.applyStyle(R.style.MobileChineseTypography, true)
        super.onCreate(state)
        check(BuildConfig.MOCK_MODE && packageName == "io.openhoyi.mobile.mock")
        themeFontDiagnostics = defaultThemeFontDiagnostics(beforeFontFamily)
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
    private fun resolvedThemeFontFamily(): JSONObject {
        val value = android.util.TypedValue()
        val resolved = theme.resolveAttribute(android.R.attr.fontFamily, value, true)
        return JSONObject().put("resolved", resolved).put("attribute", "android:fontFamily")
            .put("type", if (resolved) value.type else JSONObject.NULL)
            .put("resourceId", if (resolved) value.resourceId else JSONObject.NULL)
            .put("resourceName", if (resolved && value.resourceId != 0) resources.getResourceName(value.resourceId) else JSONObject.NULL)
            .put("string", if (resolved) value.string?.toString() ?: JSONObject.NULL else JSONObject.NULL)
    }
    private fun defaultThemeFontDiagnostics(beforeFontFamily: JSONObject): JSONObject {
        // Exact same sample and Paint parameters as the unchanged TaskTypographyEvidence gate.
        val sample = "萃取压力温度 0123456789 bar ml g °C"
        val probe = TextView(this)
        val fixed = TaskTypography.face(this)
        val defaultPaint = Paint().apply { textSize = 40f; typeface = probe.typeface }
        val fixedPaint = Paint().apply { textSize = 40f; typeface = fixed }
        val defaultWidth = defaultPaint.measureText(sample)
        val fixedWidth = fixedPaint.measureText(sample)
        return JSONObject().put("status", "diagnostics_only_not_acceptance")
            .put("locale", resources.configuration.locales.toLanguageTags())
            .put("beforeFontFamily", beforeFontFamily).put("afterFontFamily", resolvedThemeFontFamily())
            .put("overlay", "MobileChineseTypography; fontFamily_only; before_super_onCreate")
            .put("expectedFontFamilyResourceId", R.font.task_chinese_family)
            .put("sample", sample).put("paintTextSizePx", 40f)
            .put("defaultMeasuredWidthPx", defaultWidth).put("fixedMeasuredWidthPx", fixedWidth)
            .put("measuredWidthDeltaPx", defaultWidth - fixedWidth)
            .put("defaultTypefaceStyle", probe.typeface.style).put("fixedTypefaceStyle", fixed.style)
            .put("defaultTypefaceWeight", if (Build.VERSION.SDK_INT >= 28) probe.typeface.weight else JSONObject.NULL)
            .put("fixedTypefaceWeight", if (Build.VERSION.SDK_INT >= 28) fixed.weight else JSONObject.NULL)
            .put("defaultFakeBold", defaultPaint.isFakeBoldText).put("fixedFakeBold", fixedPaint.isFakeBoldText)
            .put("typefaceEqualsDiagnosticOnly", probe.typeface == fixed)
            .put("typefaceIdentityDiagnosticOnly", probe.typeface === fixed)
            .put("gate", "unchanged_TaskTypographyEvidence_default_TextView_measureText_and_weight_checks")
    }
    private fun row(id: String) = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; isBaselineAligned = false; tag = id }
    private fun baselineRow(id: String) = row(id).apply { isBaselineAligned = true; gravity = Gravity.BOTTOM }
    private fun icon(kind: String, id: String, color: Int = palette.muted, size: Int = 24) =
        StrictVisualSampleIcon(this, kind, color).apply { tag = id; icons.add(this); layoutParams = LinearLayout.LayoutParams(dp(size), dp(size)) }
    private fun withIcon(content: View, kind: String, id: String, color: Int = palette.muted, size: Int = 24): LinearLayout = row(id).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(icon(kind, "$id/icon", color, size))
        addView(content, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(8) })
    }
    /** Borders are inside existing row bounds: they never consume layout budget. */
    private fun rules(view: View, fill: Int = Color.TRANSPARENT, bottom: Boolean = false, columns: Int = 0, outline: Boolean = false) {
        view.background = object : Drawable() {
            private val ink = Paint().apply { color = palette.border; strokeWidth = dp(1).toFloat() }
            override fun draw(canvas: Canvas) {
                canvas.drawRect(bounds, Paint().apply { color = fill })
                val half = ink.strokeWidth / 2
                if (bottom) canvas.drawLine(bounds.left.toFloat(), bounds.bottom - half, bounds.right.toFloat(), bounds.bottom - half, ink)
                if (columns > 1) for (i in 1 until columns) {
                    val x = bounds.left + bounds.width() * i.toFloat() / columns
                    canvas.drawLine(x, bounds.top.toFloat(), x, bounds.bottom.toFloat(), ink)
                }
                if (outline) { ink.style = Paint.Style.STROKE; canvas.drawRect(bounds.left + half, bounds.top + half, bounds.right - half, bounds.bottom - half, ink); ink.style = Paint.Style.FILL }
            }
            override fun setAlpha(alpha: Int) { ink.alpha = alpha }
            override fun setColorFilter(filter: ColorFilter?) { ink.colorFilter = filter }
            @Deprecated("Drawable opacity") override fun getOpacity() = PixelFormat.TRANSLUCENT
        }
    }
    private fun nameDescription(name: TextView, description: TextView, id: String): LinearLayout = object : LinearLayout(this) {
        init { tag = id; isBaselineAligned = true; gravity = Gravity.BOTTOM; addView(name, LinearLayout.LayoutParams(-2, -2)); addView(description, LinearLayout.LayoutParams(-2, -2)) }
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val available = MeasureSpec.getSize(widthMeasureSpec)
            val needed = name.paint.measureText(name.text.toString()) + description.paint.measureText(description.text.toString()) + dp(16)
            val stacked = MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED && needed > available
            orientation = if (stacked) VERTICAL else HORIZONTAL
            (name.layoutParams as LinearLayout.LayoutParams).width = if (stacked) -1 else -2
            (description.layoutParams as LinearLayout.LayoutParams).apply { width = if (stacked) -1 else -2; leftMargin = if (stacked) 0 else dp(16) }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }
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
    private fun action(value: String, id: String, primary: Boolean = false, disabled: Boolean = false, outline: Boolean = false): LinearLayout {
        val color = when { disabled -> palette.onDisabled; primary -> palette.onPrimary; else -> palette.accent }
        val kind = when (id) {
            "sample/page/back" -> "back"
            "sample/preparation/primary" -> "play"
            "sample/share/primary", "sample/share/fileShareDisabled" -> "share"
            "sample/share/secondary" -> "copy"
            "sample/share/export" -> "export"
            "sample/inventory/primary" -> "coffee"
            "sample/inventory/adjust" -> "cube"
            "sample/inventory/batchDetails" -> "chevron"
            else -> null
        }
        return row(id).apply {
            gravity = Gravity.CENTER; minimumHeight = dp(if (primary) 56 else 48)
            setPadding(dp(8), dp(8), dp(8), dp(8)); isEnabled = !disabled
            background = shape(when { disabled -> palette.disabled; primary -> palette.primary; else -> Color.TRANSPARENT }, outline, 12)
            if (kind != null) addView(icon(kind, "$id/icon", color, if (kind == "chevron") 16 else 24))
            if (id != "sample/page/back") addView(text(value, StrictVisualSampleTokens.action, "$id/label").apply { setTextColor(color); isEnabled = !disabled },
                LinearLayout.LayoutParams(-2, -2).apply { if (kind != null) leftMargin = dp(8) })
            actionViews.add(this)
        }
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
        if (!isShare) {
            val subtitle = baselineRow("sample/page/subtitleRow")
            subtitle.addView(text(if (fixture.id == StrictVisualSampleFixtures.ids[0]) "配方与粉量已确认" else "3 款豆 · 500 g",
                StrictVisualSampleTokens.auxiliary, if (fixture.id == StrictVisualSampleFixtures.ids[2]) "sample/inventory/headerSummary" else "sample/page/subtitle", true))
            if (fixture.id == StrictVisualSampleFixtures.ids[0]) subtitle.addView(text("演示数据", StrictVisualSampleTokens.auxiliary, "sample/preparation/demo", true), LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(16) })
            add(titles, subtitle, 8)
        }
        head.addView(titles, if (isShare) LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(16) }
            else LinearLayout.LayoutParams(dp(694), -2))
        if (!isShare) head.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        if (fixture.id == StrictVisualSampleFixtures.ids[0]) {
            val devices = row("sample/preparation/deviceEntry")
            listOf("咖啡机" to "已就绪", "电子秤" to "实时").forEachIndexed { index, data ->
                val labels = column("sample/preparation/device/$index/labels")
                add(labels, text(data.first, StrictVisualSampleTokens.auxiliary, "sample/preparation/device/$index/title", true))
                add(labels, text(data.second, StrictVisualSampleTokens.auxiliary.copy(weight = 500), "sample/preparation/device/$index/status").apply { setTextColor(palette.accent) })
                val card = withIcon(labels, if (index == 0) "coffeeMachine" else "scale", "sample/preparation/device/$index").apply {
                    minimumHeight = dp(64); setPadding(dp(8), dp(8), dp(8), dp(8)); background = shape(palette.disabled, radius = 12)
                }
                devices.addView(card, LinearLayout.LayoutParams(dp(150), -2).apply { if (index > 0) leftMargin = dp(8) })
            }
            head.addView(devices)
        }
        if (isShare) head.addView(text("设计示意", StrictVisualSampleTokens.auxiliary, "sample/share/designChip", true).apply {
            setPadding(dp(8), dp(4), dp(8), dp(4)); background = shape(palette.disabled, radius = 12)
        })
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
        val result = row(id).apply { gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(36) }
        for ((index, values) in listOf("93" to "°C", "36" to "g", "4" to "个阶段").withIndex()) {
            if (index > 0) {
                val separator = FrameLayout(this)
                separator.addView(View(this).apply { setBackgroundColor(palette.border); tag = "$id/divider$index" }, FrameLayout.LayoutParams(dp(1), dp(24), Gravity.CENTER))
                result.addView(separator, LinearLayout.LayoutParams(dp(24), dp(36)))
            }
            val valuesRow = baselineRow("$id/$index/values")
            if (index == 1) valuesRow.addView(text("目标液重", StrictVisualSampleTokens.auxiliary, "$id/$index/label", true), LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(8) })
            valuesRow.addView(text(values.first, StrictVisualSampleTokens.compactRecipeMetric, "$id/$index/value"))
            valuesRow.addView(text(values.second, StrictVisualSampleTokens.auxiliary, "$id/$index/unit", true), LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
            val group = withIcon(valuesRow, listOf("temperature", "coffee", "stages")[index], "$id/$index")
            result.addView(group, LinearLayout.LayoutParams(if (index < 2) dp(208) else 0, -2, if (index == 2) 1f else 0f))
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
        recipe.addView(nameDescription(text("经典浓缩", StrictVisualSampleTokens.title, "sample/preparation/recipe"),
            text("四阶段压力配方", StrictVisualSampleTokens.auxiliary, "sample/preparation/recipeDescription", true), "sample/preparation/recipeIdentity"), LinearLayout.LayoutParams(0, -2, 1f))
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
            line.addView(nameDescription(text(data.getString("name"), StrictVisualSampleTokens.action, "sample/preparation/recent/row/$index/name"),
                text(listOf("四阶段压力配方", "柔和风味 · 三阶段", "低压逐步释放 · 三阶段")[index], StrictVisualSampleTokens.auxiliary, "sample/preparation/recent/row/$index/description", true),
                "sample/preparation/recent/row/$index/identity"), LinearLayout.LayoutParams(0, -2, 1f))
            val metrics = row("sample/preparation/recent/row/$index/summary").apply { gravity = Gravity.CENTER_VERTICAL }
            listOf("${data.getInt("temperatureC")}°C", "${data.getInt("targetGrams")}g", "${data.getInt("stages")}个阶段").forEachIndexed { metric, value ->
                if (metric > 0) metrics.addView(View(this).apply { setBackgroundColor(palette.border) }, LinearLayout.LayoutParams(dp(1), dp(16)).apply { leftMargin = dp(8); rightMargin = dp(8) })
                metrics.addView(text(value, StrictVisualSampleTokens.auxiliary, "sample/preparation/recent/row/$index/metric$metric", true))
            }
            line.addView(metrics, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(16) })
            rules(line, bottom = true)
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
        val amount = baselineRow("sample/preparation/doseValue")
        amount.addView(text("18.0", StrictVisualSampleTokens.largeValue, "sample/preparation/doseValue/number"))
        amount.addView(text("g", StrictVisualSampleTokens.auxiliary, "sample/preparation/doseValue/unit"), LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
        dose.addView(amount, LinearLayout.LayoutParams(0, -2, 1f))
        dose.addView(action("编辑", "sample/preparation/editDose"), LinearLayout.LayoutParams(dp(64), -2).apply { leftMargin = dp(24) })
        add(body, dose, 8); divider(body, id = "sample/preparation/doseDivider")
        add(body, withIcon(text("咖啡机已就绪", StrictVisualSampleTokens.body, "sample/preparation/machineReady/text"), "statusDot", "sample/preparation/machineReady", palette.accent, 12)); divider(body, id = "sample/preparation/machineDivider")
        add(body, withIcon(text("电子秤数据实时", StrictVisualSampleTokens.body, "sample/preparation/scaleReady/text"), "statusDot", "sample/preparation/scaleReady", palette.accent, 12))
        divider(body, id = "sample/preparation/scaleDivider")
        right.addView(scrolling(body, "sample/preparation/cup/scroll"), LinearLayout.LayoutParams(-1, 0, 1f))
        val dock = column("sample/preparation/dock")
        add(dock, withIcon(text("开始前将再次确认。", StrictVisualSampleTokens.auxiliary, "sample/preparation/confirmation/text", true), "info", "sample/preparation/confirmation"))
        add(dock, action("开始萃取", "sample/preparation/primary", primary = true), 8)
        add(dock, action("查看准备详情", "sample/preparation/details"), 8)
        add(right, dock, 8)
    }
    private fun paneGap(parent: LinearLayout, id: String) {
        val gap = FrameLayout(this)
        if (id == "sample/share/paneDivider") {
            gap.tag = id; parent.addView(gap, LinearLayout.LayoutParams(dp(24), -1)); return
        }
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
        val identity = row("sample/share/identity").apply { gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(36) }
        identity.addView(text("经典浓缩", StrictVisualSampleTokens.title, "sample/share/recipe"))
        identity.addView(text("压力模式", StrictVisualSampleTokens.auxiliary, "sample/share/modeBadge").apply { setTextColor(palette.accent); setPadding(dp(8), 0, dp(8), 0); background = shape(palette.selected, radius = 12) }, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(16) })
        add(left, identity)
        val summaries = row("sample/share/summary")
        for ((index, metric) in listOf("设定温度" to ("93" to "°C"), "目标液重" to ("36.0" to "g")).withIndex()) {
            val card = column(if (index == 0) "sample/share/temperatureCard" else "sample/share/targetCard").apply { minimumHeight = dp(74); gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(8)); background = shape(palette.surface, true, 12) }
            add(card, text(metric.first, StrictVisualSampleTokens.auxiliary, "sample/share/summary/$index/label", true))
            val values = baselineRow("sample/share/summary/$index/values")
            values.addView(text(metric.second.first, StrictVisualSampleTokens.shareParameterMetric, "sample/share/summary/$index/value"), LinearLayout.LayoutParams(0, -2, 1f))
            values.addView(text(metric.second.second, StrictVisualSampleTokens.auxiliary, "sample/share/summary/$index/unit", true))
            add(card, values)
            summaries.addView(card, LinearLayout.LayoutParams(0, -2, 1f).apply { if (index > 0) leftMargin = dp(24) })
        }
        add(left, summaries, 8)
        add(left, text("压力阶段预览", StrictVisualSampleTokens.section, "sample/share/stageTitle"), 8)
        chart(left, "sample/share/stage", 160)
        add(left, text("阶段目标示意，不代表实际时长。", StrictVisualSampleTokens.auxiliary, "sample/share/stageNote", true), 8)
        add(left, text("4 个阶段参数", StrictVisualSampleTokens.auxiliary.copy(weight = 500), "sample/share/tableCaption"), 8)
        val table = column("sample/share/table")
        fun tableRow(values: List<String>, id: String, header: Boolean = false) {
            val row = row(id)
            values.forEachIndexed { index, value ->
                val label = text(value, StrictVisualSampleTokens.auxiliary.copy(weight = if (header) 500 else 400), "$id/$index")
                val cell: View = if (!header && index == 0) withIcon(label, "statusDot", "$id/stage", palette.stageColors[id.takeLast(1).toInt() - 1], 12) else label
                row.addView(cell, LinearLayout.LayoutParams(0, -2, 1f))
            }
            rules(row, fill = if (header) palette.disabled else Color.TRANSPARENT, bottom = true, columns = 3)
            add(table, row)
        }
        tableRow(listOf("阶段", "压力（bar）", "注水量（ml）"), "sample/share/table/header", true)
        fixture.targetsTenthsBar.forEachIndexed { index, value -> tableRow(listOf("阶段 ${index + 1}", "${value / 10.0}", "${fixture.waterMl[index]}.0"), "sample/share/table/row${index + 1}") }
        rules(table, outline = true)
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
            row.addView(icon("bean", "sample/inventory/row${index + 1}/icon"), LinearLayout.LayoutParams(dp(24), dp(24)).apply { rightMargin = dp(12) })
            row.addView(text(bean.name, StrictVisualSampleTokens.body, "sample/inventory/row${index + 1}/name"), LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(text("${bean.remainingGrams} g", StrictVisualSampleTokens.body, "sample/inventory/row${index + 1}/weight"), LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(16) })
            row.addView(icon("chevron", "sample/inventory/row${index + 1}/chevron", size = 16), LinearLayout.LayoutParams(dp(16), dp(16)).apply { leftMargin = dp(8) })
            if (index != 0) rules(row, bottom = true)
            add(left, row, if (index == 0) 14 else 8)
        }
        val body = column("sample/inventory/body")
        val name = row("sample/inventory/nameRow").apply { gravity = Gravity.TOP; minimumHeight = dp(38) }
        name.addView(text("哥伦比亚 水洗", StrictVisualSampleTokens.title, "sample/inventory/beanName"), LinearLayout.LayoutParams(-2, -2))
        name.addView(text("批次 1", StrictVisualSampleTokens.auxiliary, "sample/inventory/batchBadge", true).apply {
            gravity = Gravity.CENTER; setPadding(dp(8), dp(4), dp(8), dp(4))
            minHeight = StrictVisualSampleTokens.sp(this@StrictVisualSampleActivity, 22).toInt() + dp(8)
            background = shape(palette.disabled, radius = 12)
        }, LinearLayout.LayoutParams(dp(80), -2).apply { leftMargin = dp(16) })
        add(body, name)
        add(body, text("剩余重量", StrictVisualSampleTokens.auxiliary, "sample/inventory/weightLabel", true), 6)
        val weight = baselineRow("sample/inventory/weight")
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
        rules(dateGroup, columns = 3)
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
            rules(row, fill = if (index == 0) palette.disabled else Color.TRANSPARENT, bottom = true)
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
        right.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val density = resources.displayMetrics.density
            val appHeight = sampleRoot.height.toFloat() / density
            val gap = ((4f + .8f * (appHeight.coerceIn(618f, 623f) - 618f)) * density).roundToInt()
            val params = actions.layoutParams as LinearLayout.LayoutParams
            if (params.topMargin != gap) { params.topMargin = gap; actions.layoutParams = params }
        }
        add(right, withIcon(text("确认本杯用量后才扣减库存。", StrictVisualSampleTokens.auxiliary, "sample/inventory/confirmation/text", true), "info", "sample/inventory/confirmation"), 8)
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
