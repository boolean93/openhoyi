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
    private lateinit var productStyle: TaskProductVisualStyle
    private lateinit var detailIdentity: LinearLayout
    val productLayoutEvidence = org.json.JSONObject()
    private lateinit var detailTitle: TextView
    private lateinit var previewMetrics: CurvePreviewMetrics
    private lateinit var detailCategory: TextView
    private lateinit var details: TextView
    private lateinit var availability: TextView
    private lateinit var select: Button
    private lateinit var parameterShortcut: Button
    private lateinit var assignPreset: Button
    private lateinit var editDraft: Button
    private lateinit var shareParameters: Button
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var curveList: ListView
    private lateinit var search: EditText
    private lateinit var resultCount: TextView
    private lateinit var stageChart: CurveStageView
    private lateinit var stageCaption: TextView
    private lateinit var browserPane: LinearLayout
    private lateinit var detailPanel: LinearLayout
    private lateinit var detailScroll: ScrollView
    private lateinit var collection: TaskCollectionLayout
    private lateinit var detailBack: Button
    private lateinit var detailToolbar: LinearLayout
    private lateinit var detailActions: Button
    private lateinit var directEdit: Button
    private lateinit var directShare: Button
    private lateinit var stageHeading: TextView
    private lateinit var previewFallback: TextView
    private lateinit var previewFallbackScroll: ScrollView
    private lateinit var previewSpacer: Space
    private var shortPreview = false
    private val rawReasonMeasure by lazy { TextView(this).apply {
        productStyle.apply(this, TaskProductVisualStyle.auxiliary, true)
        text = getString(R.string.task_curve_library_no_pressure)
    } }
    private var compact = false
    private var category = CurveCategoryFilter.ALL
    private var sortMode = CurveSortMode.RECENT
    private var visibleItems = emptyList<CurveLibraryItem>()
    private var selected: CurveLibraryItem? = null
    private lateinit var selectionDock: TaskActionDock
    private var detailBackCallback: android.window.OnBackInvokedCallback? = null
    private val curveDetailsText by lazy { CurveDetailsText(this) }
    private val library get() = (application as MobileApplication).curves

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        productStyle = TaskProductVisualStyle(this)
        category = CurveCategoryFilter.restore(savedInstanceState?.getString("category"))
        sortMode = CurveSortMode.restore(getSharedPreferences("curves", MODE_PRIVATE).getString("sort", null))
        // Initializes migration of existing positively observed shots, not selection events.
        runCatching { (application as MobileApplication).history }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            tag = "product/curve-library/root"
            setBackgroundColor(productStyle.page)
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
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        TaskPageFrame.install(this, root, productStyle)
        window.statusBarColor = productStyle.page; window.navigationBarColor = productStyle.surface
        setContentView(root)
        val pageHeader = HoyiUi.header(this, body, getString(R.string.curve_title))
        pageHeader.tag = "product/curve-library/header"
        productStyle.styleHeader(pageHeader)
        (pageHeader.layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(16)
        pageHeader.foreground = object : android.graphics.drawable.Drawable() {
            private val paint = android.graphics.Paint().apply { color = productStyle.border; strokeWidth = resources.displayMetrics.density }
            override fun draw(canvas: android.graphics.Canvas) { canvas.drawLine(0f, bounds.bottom - paint.strokeWidth / 2, bounds.right.toFloat(), bounds.bottom - paint.strokeWidth / 2, paint) }
            override fun setAlpha(alpha: Int) { paint.alpha = alpha }
            override fun setColorFilter(filter: android.graphics.ColorFilter?) { paint.colorFilter = filter }
            @Deprecated("Drawable opacity") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
        }
        collection = TaskCollectionLayout(this, browserFraction = .34f)
        body.addView(collection, LinearLayout.LayoutParams(-1, 0, 1f))
        compact = resources.configuration.screenWidthDp < 840
        browserPane = collection.browserBody
        detailPanel = collection.detailBody
        browserPane.tag = "product/curve-library/browser"; detailPanel.tag = "product/curve-library/detail"
        collection.foreground = object : android.graphics.drawable.Drawable() {
            private val paint = android.graphics.Paint().apply { color = productStyle.border; strokeWidth = resources.displayMetrics.density }
            override fun draw(canvas: android.graphics.Canvas) {
                if (browserPane.visibility == View.VISIBLE && detailPanel.visibility == View.VISIBLE) {
                    val x = if (layoutDirection == View.LAYOUT_DIRECTION_RTL) detailPanel.right + dp(12) else detailPanel.left - dp(12)
                    canvas.drawLine(x.toFloat(), 0f, x.toFloat(), bounds.bottom.toFloat(), paint)
                }
            }
            override fun setAlpha(alpha: Int) { paint.alpha = alpha }
            override fun setColorFilter(filter: android.graphics.ColorFilter?) { paint.colorFilter = filter }
            @Deprecated("Drawable opacity") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
        }
        val browser = browserPane
        val detailPane = detailPanel
        detailToolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            isBaselineAligned = false
        }
        detailPanel.addView(detailToolbar)
        detailBack = HoyiUi.button(this, detailToolbar, getString(R.string.curve_back)) { showBrowser() }.apply {
            productStyle.button(this)
            HomeVisualIcon.buttonIcon(this, "back")
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8) }
        }
        collection.onPresentationChanged = { twoPane, detailVisible ->
            compact = !twoPane
            detailBack.visibility = if (compact) View.VISIBLE else View.GONE
            if (compact && detailVisible) registerDetailBack() else unregisterDetailBack()
        }
        val searchTools = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; isBaselineAligned = false; gravity = android.view.Gravity.CENTER_VERTICAL
        }
        browser.addView(searchTools, LinearLayout.LayoutParams(-1, -2))
        search = EditText(this).apply {
            hint = getString(R.string.curve_search_hint)
            setSingleLine(true)
            TaskProductFormStyle(this@CurveActivity).input(this, "curve-library", "search")
        }
        searchTools.addView(search, LinearLayout.LayoutParams(0, -2, 1f))
        val customAvailable = (application as MobileApplication).customCurvesResult.isSuccess
        val creation = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val newCurve = HoyiUi.button(this, pageHeader, getString(R.string.profiles_new)) {
            startActivity(Intent(this, CurveEditorActivity::class.java))
        }.apply {
            isEnabled = customAvailable; productStyle.button(this); maxWidth = dp(200)
            tag = "product/curve-library/new"
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(16) }
        }
        val importCurve = HoyiUi.button(this, creation, getString(R.string.profiles_import)) {
            startActivity(Intent(this, CurveImportActivity::class.java))
        }.apply { isEnabled = customAvailable; productStyle.button(this); layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { topMargin = dp(8) } }
        if (!customAvailable) HoyiUi.label(this, browser, getString(R.string.profiles_unavailable), 14, muted = true)
            .apply { productStyle.apply(this, TaskProductVisualStyle.auxiliary, true) }
        val sort = Spinner(this).apply { minimumHeight = dp(52); tag = "product/curve-library/sort" }
        sort.adapter = TaskProductFormStyle(this).choiceAdapter(
            listOf(getString(R.string.curve_sort_recent), getString(R.string.curve_sort_most_used)))
        sort.setSelection(sortMode.ordinal)
        sort.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val chosen = CurveSortMode.entries[position]
                if (sortMode == chosen) return
                sortMode = chosen
                getSharedPreferences("curves", MODE_PRIVATE).edit().putString("sort", sortMode.name).apply()
                refreshList()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        browser.addView(sort, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        if ((application as MobileApplication).curveUsageResult.isFailure)
            HoyiUi.label(this, browser, getString(R.string.curve_usage_unavailable), 14, muted = true)
                .apply { productStyle.apply(this, TaskProductVisualStyle.auxiliary, true) }
        val categories = CurveCategoryFilter.entries
        val filter = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val filterRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        filter.addView(filterRow)
        browser.addView(filter, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        val filterChips = mutableListOf<TextView>()
        fun updateFilterChips() {
            filterChips.forEachIndexed { index, chip ->
                val chosen = categories[index] == category
                chip.isSelected = chosen
                chip.setTextColor(if (chosen) productStyle.accent else productStyle.muted)
                chip.background = productStyle.shape(if (chosen) productStyle.selected else productStyle.surface, true, 10)
                chip.contentDescription = getString(R.string.curve_filter_description, categories[index].label(this), if (chosen) getString(R.string.curve_filter_selected_suffix) else "")
            }
        }
        categories.forEach { name ->
            filterChips += TextView(this).apply {
                text = name.label(this@CurveActivity)
                productStyle.apply(this, TaskProductVisualStyle.auxiliary)
                gravity = android.view.Gravity.CENTER
                minHeight = dp(48); minimumHeight = dp(48); minimumWidth = dp(48)
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
        val legacy = HoyiUi.button(this, creation, getString(R.string.curve_legacy)) {
            startActivity(Intent(this, LegacyCurveActivity::class.java))
        }.apply { productStyle.button(this); contentDescription = getString(R.string.curve_legacy_description) }
        HoyiUi.button(this, searchTools, getString(R.string.task_curve_library_manage)) {
            showActions(searchTools.findViewWithTag("curve-library-management"), listOf(newCurve, importCurve, legacy))
        }.apply {
            tag = "curve-library-management"
            productStyle.button(this)
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) }
        }
        resultCount = HoyiUi.label(this, browser, "", 14, muted = true).apply {
            productStyle.apply(this, TaskProductVisualStyle.auxiliary, true)
            tag = "product/curve-library/count"; setPadding(0, dp(8), 0, 0)
        }
        val list = ListView(this).apply {
            divider = android.graphics.drawable.ColorDrawable(productStyle.border)
            dividerHeight = dp(1)
            selector = productStyle.shape(productStyle.selected)
        }
        adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, mutableListOf()) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val row = convertView as? LinearLayout ?: LinearLayout(this@CurveActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    minimumHeight = dp(76)
                    setPadding(dp(16), dp(8), dp(16), dp(8))
                    val thumbnail = CurveStageView(this@CurveActivity).apply {
                        compact = true
                        pressureColor = true
                        background = productStyle.shape(productStyle.surface, true, 8)
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    }
                    addView(thumbnail, LinearLayout.LayoutParams(dp(78), dp(48)).apply { marginEnd = dp(8) })
                    val words = LinearLayout(this@CurveActivity).apply { orientation = LinearLayout.VERTICAL }
                    addView(words, LinearLayout.LayoutParams(0, -2, 1f))
                    val title = HoyiUi.label(this@CurveActivity, words, "", 16, true).apply {
                        productStyle.apply(this, TaskProductVisualStyle.action)
                        maxLines = 2
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    }
                    val subtitle = HoyiUi.label(this@CurveActivity, words, "", 14, muted = true).apply {
                        productStyle.apply(this, TaskProductVisualStyle.auxiliary, true)
                        setPadding(0, dp(8), 0, 0)
                    }
                    addView(HomeVisualIcon(this@CurveActivity, "chevron", productStyle.muted),
                        LinearLayout.LayoutParams(dp(16), dp(16)).apply { marginStart = dp(8) })
                    tag = RowViews(title, subtitle, thumbnail)
                }
                val item = visibleItems[position]
                val views = row.tag as RowViews
                views.title.text = item.name
                views.title.tag = "product/curve-library/list/${item.id}/name"
                views.subtitle.tag = "product/curve-library/list/${item.id}/summary"
                views.subtitle.text = getString(R.string.curve_row_summary, if (item.customDocument != null) getString(R.string.profiles_category) else CurveCategoryFilter.display(this@CurveActivity, item.category), if (library.canStart(item)) getString(R.string.home_curve_startable) else getString(R.string.curve_browse_only))
                (application as MobileApplication).curveUsageResult.getOrNull()?.let { ledger ->
                    views.subtitle.append(getString(R.string.curve_usage_count, ledger.stats(item.id).count))
                }
                views.preview.targets = stageTargets(item)
                row.isSelected = item.id == selected?.id
                row.background = android.graphics.drawable.ColorDrawable(if (row.isSelected) productStyle.selected else android.graphics.Color.TRANSPARENT)
                row.foreground = if (!row.isSelected) null else object : android.graphics.drawable.Drawable() {
                    private val paint = android.graphics.Paint().apply { color = productStyle.accent }
                    override fun draw(canvas: android.graphics.Canvas) {
                        val x = if (layoutDirection == View.LAYOUT_DIRECTION_RTL) bounds.right - dp(4) else 0
                        canvas.drawRect(x.toFloat(), 0f, (x + dp(4)).toFloat(), bounds.bottom.toFloat(), paint)
                    }
                    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
                    override fun setColorFilter(filter: android.graphics.ColorFilter?) { paint.colorFilter = filter }
                    @Deprecated("Drawable opacity") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
                }
                return row
            }
        }
        curveList = list
        list.adapter = adapter
        val listHolder = FrameLayout(this)
        browser.addView(listHolder, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(8) })
        listHolder.addView(list, FrameLayout.LayoutParams(-1, -1))
        val emptyState = TextView(this).apply {
            text = getString(R.string.curve_empty)
            productStyle.apply(this, TaskProductVisualStyle.body, true)
            gravity = android.view.Gravity.CENTER
        }
        listHolder.addView(emptyState, FrameLayout.LayoutParams(-1, -1))
        list.emptyView = emptyState
        list.setOnItemClickListener { _, _, position, _ -> show(visibleItems[position]) }

        // The continuous list and genuinely short preview fallback scroll locally, never the whole page.
        detailTitle = HoyiUi.label(this, detailToolbar, getString(R.string.curve_detail_initial), TaskVisualTokens.TITLE_SP, true).apply {
            productStyle.apply(this, TaskProductVisualStyle.title)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(16) }
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            tag = "full-text-details"
        }
        detailIdentity = object : LinearLayout(this) {
            init { isBaselineAligned = false; tag = "product/curve-library/identity" }
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val natural = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
                detailCategory.measure(natural, natural); availability.measure(natural, natural)
                val inline = availability.visibility == View.GONE || detailCategory.measuredWidth + availability.measuredWidth + dp(16) <= MeasureSpec.getSize(widthMeasureSpec)
                orientation = if (inline) HORIZONTAL else VERTICAL
                val direction = this.layoutDirection
                (detailCategory.layoutParams as LayoutParams).width = if (inline) -2 else -1
                (availability.layoutParams as LayoutParams).apply {
                    width = if (inline) -2 else -1; marginStart = if (inline) dp(16) else 0
                    marginEnd = 0; topMargin = if (inline) 0 else dp(8); resolveLayoutDirection(direction)
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }
        detailPane.addView(detailIdentity, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        detailCategory = HoyiUi.label(this, detailIdentity, getString(R.string.curve_detail_hint), 14, muted = true)
            .apply { productStyle.apply(this, TaskProductVisualStyle.auxiliary, true) }
        availability = HoyiUi.label(this, detailIdentity, "", 14, true).apply {
            productStyle.apply(this, TaskProductVisualStyle.auxiliary)
            tag = "product/curve-library/availability"; visibility = View.GONE
        }
        previewMetrics = CurvePreviewMetrics(this, productStyle).apply { visibility=View.GONE; tag="curve-preview-metrics" }
        detailPane.addView(previewMetrics,LinearLayout.LayoutParams(-1,-2))
        stageHeading = HoyiUi.label(this, detailPane, getString(R.string.curve_library_stage_heading),
            TaskVisualTokens.SECTION_SP, true).apply {
            productStyle.apply(this, TaskProductVisualStyle.section)
            visibility = View.GONE
            setPadding(0, dp(8), 0, 0)
        }
        stageChart = CurveStageView(this).apply {
            productVisualStyle = productStyle; pressureColor = true; visibility = View.GONE
            tag = "product/curve-library/chart"
        }
        // Kept as a bounded, non-overflowing viewport for existing layout selectors.
        detailScroll = ScrollView(this).apply {
            isFillViewport = true
            visibility = View.GONE
            isVerticalScrollBarEnabled = false
            addView(stageChart, FrameLayout.LayoutParams(-1, -1))
            background = productStyle.shape(productStyle.surface, true)
        }
        detailPane.addView(detailScroll, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(8) })
        previewFallback = productStyle.text("", TaskProductVisualStyle.auxiliary, "product/curve-library/fallback", true)
        previewFallbackScroll = ScrollView(this).apply {
            tag = "curve-preview-fallback"
            visibility = View.GONE
            addView(previewFallback)
        }
        detailPane.addView(previewFallbackScroll, LinearLayout.LayoutParams(-1, -2))
        previewSpacer = Space(this).apply { visibility = View.GONE }
        detailPane.addView(previewSpacer, LinearLayout.LayoutParams(-1, 0, 1f))
        stageCaption = HoyiUi.label(this, detailPane, getString(R.string.curve_stage_caption), 14, muted = true)
            .apply { productStyle.apply(this, TaskProductVisualStyle.auxiliary, true); setPadding(0, dp(8), 0, 0); visibility = View.GONE }
        // Full metadata is retained, never truncated; the dedicated dialog owns its local scrolling.
        details = productStyle.text("", TaskProductVisualStyle.body)
        val secondary = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        shareParameters = HoyiUi.button(this, secondary, getString(R.string.profiles_share)) {
            selected?.let { startActivity(Intent(this, CurveShareActivity::class.java).putExtra(CurveShareActivity.CURVE_ID, it.id)) }
        }.apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { topMargin = dp(6) }
        }
        val editing = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        editDraft = HoyiUi.button(this, secondary, getString(R.string.profiles_edit_copy)) {
            selected?.let { startActivity(Intent(this, CurveEditorActivity::class.java).putExtra(CurveEditorActivity.CURVE_ID, it.id)) }
        }.apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(6); topMargin = dp(6) }
        }
        assignPreset = HoyiUi.button(this, editing, getString(R.string.curve_assign_slot)) {
            val item = selected?.takeIf { it.factoryCurve != null && library.canStart(it) } ?: return@button
            AlertDialog.Builder(this).setTitle(getString(R.string.curve_choose_slot))
                .setItems((1..5).map { getString(R.string.home_slot, it.toString()) }.toTypedArray()) { _, index ->
                    val slot = index + 1
                    getSharedPreferences("presets", MODE_PRIVATE).edit()
                        .putString(PresetSlots.key(slot), item.id).apply()
                    Toast.makeText(this, getString(R.string.curve_slot_assigned, item.name, slot.toString()), Toast.LENGTH_SHORT).show()
                }.show()
        }.apply {
            isEnabled = false; visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { topMargin = dp(6) }
        }
        val parameterDetails = HoyiUi.button(this, secondary, getString(R.string.curve_detail_title)) { showParameterDetails() }
        directEdit = HoyiUi.button(this, detailToolbar, getString(R.string.profiles_edit)) { editDraft.performClick() }.apply {
            productStyle.button(this); visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(-2, -2)
        }
        directShare = HoyiUi.button(this, detailToolbar, getString(R.string.profiles_share)) { shareParameters.performClick() }.apply {
            productStyle.button(this); visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(-2, -2)
        }
        detailActions = HoyiUi.button(this, detailToolbar, getString(R.string.task_curve_library_actions)) {
            showActions(detailActions, listOf(parameterDetails, shareParameters, editDraft, assignPreset))
        }.apply {
            productStyle.button(this)
            tag = "curve-detail-actions"
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(-2, -2)
        }
        selectionDock = TaskActionDock(this, edgeInsetDp = 0, fullWidthContent = true).apply {
            tag = "product/curve-library/dock"; setPadding(0, 0, 0, 0)
        }
        detailPane.addView(selectionDock, LinearLayout.LayoutParams(-1, -2))
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            isBaselineAligned = false
        }
        // Space belongs to the whole row: CENTER_VERTICAL adds a child's asymmetric
        // margin after centering, which can place its bounds outside this row.
        selectionDock.addView(footer, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        secondary.removeView(parameterDetails)
        parameterShortcut = parameterDetails.apply {
            productStyle.button(this)
            tag = "product/curve-library/full-parameters"
            gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
            visibility = View.GONE
        }
        footer.addView(parameterShortcut, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) })
        select = HoyiUi.button(this, footer, getString(R.string.curve_use), primary = true) {
            selected?.let { item ->
                getSharedPreferences("curves", MODE_PRIVATE).edit().putString("selected", item.id).apply()
                finish()
            }
        }.apply {
            productStyle.button(this, primary = true)
            tag = "product/curve-library/use"
            isEnabled = false; visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        collection.onDetailSpaceAvailable = { width, height -> updatePreviewSpace(width, height) }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = refreshList()
            override fun afterTextChanged(s: Editable?) = Unit
        })
        search.setText(savedInstanceState?.getString("query").orEmpty())
        refreshList()
        savedInstanceState?.getParcelable<android.os.Parcelable>("listState")?.let { curveList.onRestoreInstanceState(it) }
        val initialId = savedInstanceState?.getString("selected")
            ?: getSharedPreferences("curves", MODE_PRIVATE).getString("selected", null)
        initialId?.let(library::find)?.takeIf { it in visibleItems }?.let { item ->
            show(item)
            if (compact && savedInstanceState?.getBoolean("detailVisible") != true) showBrowser()
        }
    }

    override fun onStart() {
        super.onStart()
        if (!::adapter.isInitialized) return
        val previousId = selected?.id
        val wasVisible = !compact || detailPanel.visibility == View.VISIBLE
        refreshList()
        previousId?.let(library::find)?.takeIf { it in visibleItems }?.let {
            if (wasVisible) show(it) else { selected = it; adapter.notifyDataSetChanged() }
        }
    }

    private fun showBrowser() {
        collection.detailRequested = false
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
        // Changing the preview must not move the independent library viewport.
        val viewport = curveList.onSaveInstanceState()
        selected = item
        stageChart.targets = stageTargets(item)
        val draft = item.customDocument
        previewMetrics.present(item)
        previewMetrics.visibility=View.VISIBLE
        val hasPressureSchematic = stageChart.targets.isNotEmpty()
        shortPreview = false
        stageChart.visibility = if (hasPressureSchematic) View.VISIBLE else View.GONE
        detailScroll.visibility = if (hasPressureSchematic) View.VISIBLE else View.GONE
        previewFallbackScroll.visibility = if (hasPressureSchematic) View.GONE else View.VISIBLE
        previewFallbackScroll.layoutParams = LinearLayout.LayoutParams(-1, -2)
        previewFallback.text = getString(R.string.task_curve_library_no_pressure)
        previewSpacer.visibility = if (hasPressureSchematic) View.GONE else View.VISIBLE
        stageCaption.visibility = if (hasPressureSchematic) View.VISIBLE else View.GONE
        stageCaption.text = getString(R.string.profiles_stage_caption)
        collection.detailRequested = true
        detailScroll.scrollTo(0, 0)
        val canStart = library.canStart(item)
        detailTitle.text = item.name
        detailCategory.text = if (draft != null) getString(R.string.profiles_category) else CurveCategoryFilter.display(this, item.category)
        details.text = if (draft != null) profileSummary(draft) else curveDetailsText.render(item, canStart)
        availability.text = if (draft != null) getString(if (canStart) R.string.profiles_pressure_compatible else R.string.profiles_read_only) else if (canStart) getString(R.string.curve_verified) else getString(R.string.curve_unavailable)
        val copyable = runCatching { CustomCurveDocument.fromLibraryItem(item) }.getOrNull()
        editDraft.text = getString(if (draft != null) R.string.profiles_edit else R.string.profiles_edit_copy)
        editDraft.visibility = View.VISIBLE
        editDraft.isEnabled = (application as MobileApplication).customCurvesResult.isSuccess && copyable?.controlMode == CustomCurveDocument.ControlMode.PRESSURE
        shareParameters.visibility = View.VISIBLE
        shareParameters.isEnabled = copyable != null
        directEdit.text = editDraft.text
        directEdit.isEnabled = editDraft.isEnabled
        directShare.isEnabled = shareParameters.isEnabled
        availability.setTextColor(if (canStart) productStyle.accent else productStyle.muted)
        availability.visibility = View.VISIBLE
        detailActions.visibility = View.VISIBLE
        select.isEnabled = canStart
        select.visibility = View.VISIBLE
        parameterShortcut.visibility = View.VISIBLE
        select.text = if (canStart) getString(R.string.curve_use) else getString(R.string.curve_cannot_start)
        assignPreset.visibility = if (item.factoryCurve != null) View.VISIBLE else View.GONE
        assignPreset.isEnabled = item.factoryCurve != null && canStart
        adapter.notifyDataSetChanged()
        viewport?.let { curveList.onRestoreInstanceState(it) }
    }

    /** Original actions and capability gates remain on stable buttons; menus only expose them. */
    private fun showActions(anchor: View, actions: List<Button>) {
        PopupMenu(this, anchor).apply {
            actions.filter { it.visibility != View.GONE }.forEachIndexed { index, action ->
                menu.add(0, index, index, action.text).isEnabled = action.isEnabled
            }
            setOnMenuItemClickListener { item ->
                actions.filter { it.visibility != View.GONE }.getOrNull(item.itemId)?.performClick() == true
            }
            show()
        }
    }

    private fun updatePreviewSpace(width: Int, height: Int) {
        val item = selected ?: return
        if (!::stageChart.isInitialized) return
        val hasPressure = stageChart.targets.isNotEmpty()
        val natural = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        val normalToolbar = listOf(detailTitle, directEdit, directShare, detailActions) + if (compact) listOf(detailBack) else emptyList()
        val directWidth = normalToolbar.sumOf { child ->
            child.measure(natural, natural)
            val params = child.layoutParams as LinearLayout.LayoutParams
            child.measuredWidth + params.marginStart + params.marginEnd
        }
        val direct = directWidth <= width
        directEdit.visibility = if (direct) View.VISIBLE else View.GONE
        directShare.visibility = if (direct) View.VISIBLE else View.GONE
        val exactWidth = View.MeasureSpec.makeMeasureSpec(width.coerceAtLeast(0), View.MeasureSpec.EXACTLY)
        val naturalHeight = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        fun naturalHeightOf(child: View): Int {
            child.measure(exactWidth, naturalHeight)
            val params = child.layoutParams as LinearLayout.LayoutParams
            return child.measuredHeight + params.topMargin + params.bottomMargin
        }
        // Measure the full normal presentation, even when its metadata is currently in fallback.
        // Otherwise hiding metadata would make the next measure flip back into a too-short plot.
        val actionHeight = naturalHeightOf(detailToolbar) + naturalHeightOf(selectionDock)
        val metadataHeight = listOf(detailIdentity, previewMetrics).sumOf(::naturalHeightOf) +
            if (hasPressure) naturalHeightOf(stageCaption) + naturalHeightOf(stageHeading) else 0
        val contentMinimum = if (hasPressure) dp(160) + dp(8)
            else {
                rawReasonMeasure.measure(exactWidth, naturalHeight)
                rawReasonMeasure.measuredHeight
            }
        shortPreview = height - detailPanel.paddingTop - detailPanel.paddingBottom - actionHeight - metadataHeight < contentMinimum
        productLayoutEvidence.put("availableWidthPx", width).put("availableHeightPx", height)
            .put("toolbarNaturalWidthPx", directWidth).put("directActions", direct)
            .put("actionNaturalHeightPx", actionHeight).put("metadataNaturalHeightPx", metadataHeight)
            .put("contentMinimumPx", contentMinimum).put("shortPreview", shortPreview)
        listOf(detailIdentity, previewMetrics).forEach {
            it.visibility = if (shortPreview) View.GONE else View.VISIBLE
        }
        stageCaption.visibility = if (hasPressure && !shortPreview) View.VISIBLE else View.GONE
        stageHeading.visibility = if (hasPressure && !shortPreview) View.VISIBLE else View.GONE
        detailScroll.visibility = if (hasPressure && !shortPreview) View.VISIBLE else View.GONE
        stageChart.visibility = if (hasPressure && !shortPreview) View.VISIBLE else View.GONE
        val fallback = shortPreview || !hasPressure
        previewFallbackScroll.visibility = if (fallback) View.VISIBLE else View.GONE
        val fallbackHeight = if (shortPreview) 0 else -2
        val fallbackWeight = if (shortPreview) 1f else 0f
        val currentParams = previewFallbackScroll.layoutParams as LinearLayout.LayoutParams
        if (currentParams.height != fallbackHeight || currentParams.weight != fallbackWeight)
            previewFallbackScroll.layoutParams = LinearLayout.LayoutParams(-1, fallbackHeight, fallbackWeight)
        previewSpacer.visibility = if (!hasPressure && !shortPreview) View.VISIBLE else View.GONE
        val fallbackText = if (shortPreview) listOf(
            item.name, detailCategory.text.toString(), previewMetrics.contentDescription.toString(), availability.text.toString(),
            if (hasPressure) getString(R.string.profiles_stage_caption) else getString(R.string.task_curve_library_no_pressure),
            if (hasPressure) getString(R.string.task_curve_library_short_preview,
                stageChart.targets.mapIndexed { index, value -> getString(R.string.reference_stage,
                    (index + 1).toString(), String.format(java.util.Locale.ROOT, "%.1f", value / 10.0)) }.joinToString("; ")) else "",
        ).filter { it.isNotBlank() }.joinToString("\n\n") else getString(R.string.task_curve_library_no_pressure)
        if (previewFallback.text.toString() != fallbackText) previewFallback.text = fallbackText
    }

    private fun showParameterDetails() {
        val item = selected ?: return
        AlertDialog.Builder(this).setTitle(item.name)
            .setMessage(item.name + "\n\n" + details.text.toString() + "\n\n" + availability.text.toString())
            .setPositiveButton(android.R.string.ok, null).show()
    }

    private fun refreshList() {
        if (!::adapter.isInitialized || !::search.isInitialized) return
        val viewport = curveList.onSaveInstanceState()
        val filtered = CurveSearch.filter(library.items, category, search.text.toString())
        val ledger = (application as MobileApplication).curveUsageResult.getOrNull()
        visibleItems = if (ledger == null) filtered else CurveOrdering.sort(filtered, sortMode, ledger)
        resultCount.text = getString(R.string.curve_result_count, visibleItems.size.toString())
        adapter.clear()
        adapter.addAll(visibleItems.map { it.id })
        viewport?.let { curveList.onRestoreInstanceState(it) }
        if (selected != null && selected !in visibleItems) {
            selected = null
            collection.detailRequested = false
            detailActions.visibility = View.GONE
            detailScroll.visibility = View.GONE
            previewFallbackScroll.visibility = View.GONE
            previewSpacer.visibility = View.GONE
            stageChart.targets = emptyList()
            stageChart.visibility = View.GONE
            stageCaption.visibility = View.GONE
            detailTitle.text = getString(R.string.curve_detail_initial)
            detailTitle.visibility = View.VISIBLE
            detailIdentity.visibility = View.VISIBLE
            detailCategory.visibility = View.VISIBLE
            detailCategory.text = getString(R.string.curve_detail_hint)
            previewMetrics.visibility = View.GONE
            details.text = ""
            availability.visibility = View.GONE
            select.isEnabled = false
            select.visibility = View.GONE
            parameterShortcut.visibility = View.GONE
            assignPreset.isEnabled = false
            assignPreset.visibility = View.GONE
            editDraft.visibility = View.GONE
            shareParameters.visibility = View.GONE
        }
    }
    private fun stageTargets(item: CurveLibraryItem): List<Int> = CurveStageParameters.pressureTargets(item)
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putParcelable("listState", curveList.onSaveInstanceState())
        outState.putString("selected", selected?.id)
        outState.putString("category", category.name)
        outState.putString("query", search.text.toString())
        outState.putBoolean("detailVisible", compact && detailPanel.visibility == View.VISIBLE)
        super.onSaveInstanceState(outState)
    }
    private fun dp(value: Int) = HoyiUi.dp(this, value)
}
