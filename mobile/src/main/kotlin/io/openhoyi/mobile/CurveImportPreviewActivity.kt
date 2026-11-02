package io.openhoyi.mobile

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView

class CurveImportPreviewActivity : ThemedActivity() {
    companion object { const val DOCUMENT = "curveDocument" }
    private var document: CustomCurveDocument? = null
    private var finishedSave = false
    val productLayoutEvidence = org.json.JSONObject()

    /** Permanent metric children. The measured viewport alone may select label/value inline. */
    private inner class MetricCard(private val style: TaskProductVisualStyle, title: String, value: String, unit: String?, index: Int) : LinearLayout(this@CurveImportPreviewActivity) {
        private val label = style.text(title, TaskProductVisualStyle.auxiliary, "product/import-preview/metric-$index/label", true)
        private val quantity = style.quantity(value, unit, TaskProductVisualStyle.shareMetric, "product/import-preview/metric-$index")
        var inline = false
            set(value) { if (field != value) { field = value; forceLayout() } }
        init {
            isBaselineAligned = false; setPadding(style.dp(16), style.dp(16), style.dp(16), style.dp(16))
            background = style.shape(style.surface, true)
            addView(label, LayoutParams(-1, -2)); addView(quantity, LayoutParams(-1, -2))
        }
        fun inlineFits(): Boolean {
            val free = android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
            label.measure(free, free); quantity.measure(free, free)
            return label.measuredWidth + style.dp(8) + quantity.measuredWidth <= measuredWidth - paddingLeft - paddingRight
        }
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            orientation = if (inline) HORIZONTAL else VERTICAL; gravity = android.view.Gravity.CENTER_VERTICAL
            (label.layoutParams as LayoutParams).apply { width = if (inline) -2 else -1; weight = 0f }
            (quantity.layoutParams as LayoutParams).apply {
                width = if (inline) 0 else -1; weight = if (inline) 1f else 0f
                marginStart = if (inline) style.dp(8) else 0; marginEnd = 0
                topMargin = if (inline) 0 else style.dp(8); resolveLayoutDirection(this@MetricCard.layoutDirection)
            }
            quantity.gravity = android.view.Gravity.BOTTOM or if (inline) android.view.Gravity.END else android.view.Gravity.START
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finishedSave = savedInstanceState?.getBoolean("finishedSave") ?: false
        if (finishedSave) { finish(); return }
        document = runCatching { CustomCurveDocument.decode(intent.getStringExtra(DOCUMENT).orEmpty()) }.getOrNull()
        render()
    }
    private fun render(conflicting: CustomCurveDocument? = null) {
        val style = TaskProductVisualStyle(this)
        val body = style.page(this, getString(R.string.profiles_import_preview), getString(R.string.profiles_compatibility))
        (body.tag as BeanPageRegions).header.apply {
            tag = "product/import-preview/header"
            (layoutParams as LinearLayout.LayoutParams).bottomMargin = style.dp(8)
        }
        val doc = document
        if (doc == null) { style.add(body, style.text(getString(R.string.profiles_import_invalid), TaskProductVisualStyle.body)); return }
        val store = profileStore(body) ?: return
        val existing = conflicting ?: store.find(doc.id)?.takeIf { it != doc }
        val workspace = TaskWorkspaceLayout(this, supportingWidthFraction = .35f, supportingMaxWidthDp = 400,
            boundedSecondaryViewport = true)
        body.addView(workspace, LinearLayout.LayoutParams(-1, 0, 1f))
        listOf(workspace.primaryBody, workspace.secondaryBody).forEach { pane ->
            pane.setPadding(style.dp(16), style.dp(16), style.dp(16), style.dp(16)); pane.background = style.shape(style.surface, true)
        }
        val preview = style.column("product/import-preview/content")
        val previewScroll = ScrollView(this).apply {
            tag = "import-preview-scroll"; isFillViewport = false
            addView(preview, android.widget.FrameLayout.LayoutParams(-1, -2))
        }
        workspace.primaryBody.addView(previewScroll, LinearLayout.LayoutParams(-1, -1))
        val decisions = workspace.secondaryBody.apply { tag = "import-preview-actions" }
        val information = style.column("product/import-preview/information")
        decisions.addView(ScrollView(this).apply { tag = "product/import-preview/information-scroll"; addView(information) },
            LinearLayout.LayoutParams(-1, 0, 1f))
        val name = style.text(doc.name, TaskProductVisualStyle.title, "full-text-details").apply {
            maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
        }
        val mode = style.text(getString(if (doc.controlMode == CustomCurveDocument.ControlMode.PRESSURE)
            R.string.task_chart_pressure else R.string.home_lever_flow), TaskProductVisualStyle.auxiliary, "product/import-preview/mode").apply {
            setTextColor(style.accent); setPadding(style.dp(8), 0, style.dp(8), 0)
            background = style.shape(style.selected); contentDescription = doc.controlMode.name
        }
        val identity = object : LinearLayout(this) {
            init { isBaselineAligned = false; gravity = android.view.Gravity.CENTER_VERTICAL; addView(name); addView(mode, LayoutParams(-2, -2).apply { marginStart = style.dp(16) }) }
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val natural = android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
                name.measure(natural, natural); mode.measure(natural, natural)
                val fits = name.measuredWidth + mode.measuredWidth + style.dp(16) <= android.view.View.MeasureSpec.getSize(widthMeasureSpec)
                (name.layoutParams as LayoutParams).apply { width = if (fits) -2 else 0; weight = if (fits) 0f else 1f }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }
        style.add(preview, identity)
        val metrics = style.row("product/import-preview/metrics")
        val metricCards = listOf(
            MetricCard(style, getString(R.string.curve_preview_temperature), doc.temperatureC.toString(), "°C", 0),
            MetricCard(style, getString(R.string.curve_preview_weight), if (doc.targetHundredthsGram == 0) getString(R.string.curve_details_no_weight)
                else CurveDraftNumber.format(doc.targetHundredthsGram, 2), if (doc.targetHundredthsGram == 0) null else "g", 1)
        )
        metricCards.forEachIndexed { index, card -> metrics.addView(card, LinearLayout.LayoutParams(0, -2, 1f).apply { if (index > 0) marginStart = style.dp(16) }) }
        style.add(preview, metrics, 8)
        var stageChart: CurveStageView? = null
        var pressureCaption: android.widget.TextView? = null
        if (doc.controlMode == CustomCurveDocument.ControlMode.PRESSURE) {
            style.add(preview, style.text(getString(R.string.curve_visual_preview_parameters, doc.stages.size), TaskProductVisualStyle.section,
                "product/import-preview/stage-heading"), 8)
            stageChart = CurveStageView(this).apply { targets = doc.stages.map { it.target }; productVisualStyle = style; tag = "product/import-preview/chart" }
            preview.addView(stageChart, LinearLayout.LayoutParams(-1, style.dp(160)))
            // Keep the complete stage-vs-time explanation visible/reachable without duplicating a left title block.
            pressureCaption = style.text(getString(R.string.profiles_stage_caption), TaskProductVisualStyle.auxiliary,
                "product/import-preview/stage-caption", true)
        } else {
            style.add(preview, style.text(getString(R.string.profiles_stage_summary, doc.stages.size), TaskProductVisualStyle.auxiliary,
                "product/import-preview/stage-count"), 8)
            style.add(preview, style.text(getString(R.string.profiles_flow_raw), TaskProductVisualStyle.body, secondary = true), 8)
        }
        val table = if (stageChart != null) stageTable(doc).also { style.add(preview, it, 8) } else null
        if (table != null) workspace.onPrimarySpaceAvailable = { width, height ->
            val contentWidth = (width - workspace.primaryBody.paddingLeft - workspace.primaryBody.paddingRight).coerceAtLeast(0)
            val available = (height - workspace.primaryBody.paddingTop - workspace.primaryBody.paddingBottom).coerceAtLeast(0)
            val exactWidth = android.view.View.MeasureSpec.makeMeasureSpec(contentWidth, android.view.View.MeasureSpec.EXACTLY)
            val unspecified = android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
            fun metricsMode(inline: Boolean) { metricCards.forEach { it.inline = inline; it.forceLayout() }; metrics.forceLayout() }
            fun fixedHeight(): Int {
                var fixed = 0
                for (index in 0 until preview.childCount) {
                    val child = preview.getChildAt(index)
                    if (child !== stageChart && child !== table) {
                        child.measure(exactWidth, unspecified)
                        val params = child.layoutParams as LinearLayout.LayoutParams
                        fixed += child.measuredHeight + params.topMargin + params.bottomMargin
                    }
                }
                return fixed
            }
            metricsMode(false)
            val fixedVertical = fixedHeight()
            table.measure(exactWidth, unspecified)
            val tableParams = table.layoutParams as LinearLayout.LayoutParams
            val tableNatural = table.measuredHeight + tableParams.topMargin + tableParams.bottomMargin
            val inlineFits = metricCards.map { it.inlineFits() }.all { it }
            val useInline = fixedVertical + tableNatural + style.dp(160) > available && inlineFits
            metricsMode(useInline)
            val fixed = fixedHeight()
            val allocation = CurveImportPreviewLayoutPolicy.allocate(available, fixed, tableNatural, style.dp(160))
            val chartParams = requireNotNull(stageChart).layoutParams as LinearLayout.LayoutParams
            chartParams.height = allocation.chartHeight; chartParams.weight = 0f
            productLayoutEvidence.put("availableContentPx", available).put("contentWidthPx", contentWidth)
                .put("verticalFixedNaturalPx", fixedVertical).put("selectedFixedNaturalPx", fixed)
                .put("metricsInlineFits", inlineFits).put("metricsInlineSelected", useInline)
                .put("tableNaturalPx", tableNatural).put("chartHeightPx", allocation.chartHeight)
                .put("contentNaturalPx", allocation.contentHeight)
            allocation.contentHeight + workspace.primaryBody.paddingTop + workspace.primaryBody.paddingBottom
        }
        val dock = TaskActionDock(this, edgeInsetDp = 0, fullWidthContent = true).apply { tag = "product/import-preview/dock"; setPadding(0, 0, 0, 0) }
        decisions.addView(dock, LinearLayout.LayoutParams(-1, -2).apply { topMargin = style.dp(8) })
        val error = TaskProductFormStyle(this).error(beanError(body))
        if (existing != null) {
            HoyiUi.label(this, information, getString(R.string.profiles_conflict), TaskVisualTokens.SECTION_SP, true).apply {
                style.apply(this, TaskProductVisualStyle.section)
                tag = "import-conflict-notice"
                val inset = HoyiUi.dp(this@CurveImportPreviewActivity, TaskVisualTokens.CARD_PADDING_DP)
                setPadding(inset, inset, inset, inset)
                background = HoyiUi.shape(this@CurveImportPreviewActivity, R.color.mobile_warning_surface,
                    12, R.color.mobile_warning_border)
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = 0
                }
            }
            HoyiUi.button(this, dock, getString(R.string.profiles_existing)) { profilePreviewDialog(existing) }.apply {
                style.button(this); layoutParams = LinearLayout.LayoutParams(-1, -2)
            }
            val copy = HoyiUi.button(this, dock, getString(R.string.profiles_save_copy), primary = true) {}.apply {
                style.button(this, primary = true); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = style.dp(8) }
            }
            copy.setOnClickListener {
                if (finishedSave) return@setOnClickListener
                copy.isEnabled = false
                if (runCatching { store.saveCopy(doc) }.isSuccess) { finishedSave = true; finish() }
                else { error.setText(R.string.profiles_save_failed); copy.isEnabled = true }
            }
            HoyiUi.button(this, dock, getString(R.string.profiles_keep_existing)) { finish() }.apply {
                style.button(this); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = style.dp(8) }
            }
        } else {
            val save = HoyiUi.button(this, dock, getString(R.string.profiles_import_save), primary = true) {}.apply {
                style.button(this, primary = true); layoutParams = LinearLayout.LayoutParams(-1, -2)
            }
            save.setOnClickListener {
                if (finishedSave) return@setOnClickListener
                save.isEnabled = false
                val result = runCatching { store.importDocument(doc) }
                result.onSuccess {
                    if (it.status == CustomCurveStore.ImportStatus.CONFLICT) render(it.document)
                    else { finishedSave = true; finish() }
                }.onFailure { error.setText(R.string.profiles_save_failed); save.isEnabled = true }
            }
        }
        pressureCaption?.let { style.add(information, it, 16) }
        style.add(information, style.text(getString(R.string.profiles_compatibility), TaskProductVisualStyle.auxiliary, "product/import-preview/compatibility", true), 16)
        style.add(information, style.text(getString(if (CustomPressureCurveAdapter.profile(doc, false) != null)
            R.string.profiles_pressure_compatible else R.string.profiles_read_only), TaskProductVisualStyle.auxiliary, "product/import-preview/ability"), 16)
        HoyiUi.button(this, information, getString(R.string.ui_full_details)) {
            beanDetails(doc.name, profileSummary(doc) + "\n\n" + doc.encode())
        }.apply {
            style.button(this); tag = "product/import-preview/full-details"
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = style.dp(8) }
        }
        style.add(information, style.text(getString(R.string.curve_visual_library_scope), TaskProductVisualStyle.auxiliary,
            "product/import-preview/scope", true), 16)
    }
    private fun stageTable(doc: CustomCurveDocument) = LinearLayout(this).apply {
        val style = TaskProductVisualStyle(this@CurveImportPreviewActivity)
        orientation = LinearLayout.VERTICAL; tag = "import-stage-table"
        fun row(values: List<String>, heading: Boolean, stageIndex: Int? = null) {
            val line = style.row(if (heading) "product/import-preview/table/header" else "product/import-preview/table/row${requireNotNull(stageIndex) + 1}")
            addView(line, LinearLayout.LayoutParams(-1, -2))
            values.forEachIndexed { column, value ->
                val label = style.text(value, TaskProductVisualStyle.auxiliary.copy(weight = if (heading) 500 else 400))
                val cell: android.view.View = if (column == 0 && stageIndex != null) style.row().apply {
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    addView(android.view.View(this@CurveImportPreviewActivity).apply {
                        tag = "product/import-preview/table/row${stageIndex + 1}/dot"
                        background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(style.stageColors[stageIndex]) }
                    }, LinearLayout.LayoutParams(style.dp(12), style.dp(12)))
                    addView(label, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = style.dp(8) })
                } else label
                cell.setPaddingRelative(style.dp(8), style.dp(4), style.dp(8), style.dp(4))
                cell.minimumHeight = style.sp(22).toInt() + style.dp(8)
                cell.background = style.shape(if (heading) style.disabled else style.surface, true, 0)
                line.addView(cell, LinearLayout.LayoutParams(0, -2, 1f))
            }
        }
        row(listOf(R.string.import_table_stage, R.string.import_table_pressure, R.string.import_table_water).map(::getString), true)
        doc.stages.forEachIndexed { index, stage ->
            row(listOf(getString(R.string.profiles_stage, index + 1), CurveDraftNumber.format(stage.target, 1),
                CurveDraftNumber.format(stage.waterTenthsMl, 1)), false, index)
        }
    }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); outState.putBoolean("finishedSave", finishedSave) }
}
