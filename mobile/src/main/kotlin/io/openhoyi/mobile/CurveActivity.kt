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
    private lateinit var previewFallback: TextView
    private lateinit var previewFallbackScroll: ScrollView
    private lateinit var previewSpacer: Space
    private var shortPreview = false
    private val rawReasonMeasure by lazy { TextView(this).apply {
        textSize = 14f
        text = getString(R.string.task_curve_library_no_pressure)
    } }
    private var compact = false
    private var category = CurveCategoryFilter.ALL
    private var sortMode = CurveSortMode.RECENT
    private var visibleItems = emptyList<CurveLibraryItem>()
    private var selected: CurveLibraryItem? = null
    private var detailBackCallback: android.window.OnBackInvokedCallback? = null
    private val curveDetailsText by lazy { CurveDetailsText(this) }
    private val library get() = (application as MobileApplication).curves

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        category = CurveCategoryFilter.restore(savedInstanceState?.getString("category"))
        sortMode = CurveSortMode.restore(getSharedPreferences("curves", MODE_PRIVATE).getString("sort", null))
        // Initializes migration of existing positively observed shots, not selection events.
        runCatching { (application as MobileApplication).history }
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
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        HoyiUi.navigation(this, root, CurveActivity::class.java)
        setContentView(root)
        HoyiUi.header(this, body, getString(R.string.curve_title))
        collection = TaskCollectionLayout(this)
        body.addView(collection, LinearLayout.LayoutParams(-1, 0, 1f))
        compact = resources.configuration.screenWidthDp < 840
        browserPane = collection.browserBody
        detailPanel = collection.detailBody
        val browser = browserPane
        val detailPane = detailPanel
        detailToolbar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        detailPanel.addView(detailToolbar)
        detailBack = HoyiUi.button(this, detailToolbar, getString(R.string.curve_back)) { showBrowser() }.apply {
            setPadding(dp(6), dp(4), dp(6), dp(4))
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) }
        }
        collection.onPresentationChanged = { twoPane, detailVisible ->
            compact = !twoPane
            detailBack.visibility = if (compact) View.VISIBLE else View.GONE
            if (compact && detailVisible) registerDetailBack() else unregisterDetailBack()
        }
        val searchTools = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        browser.addView(searchTools, LinearLayout.LayoutParams(-1, -2))
        search = EditText(this).apply {
            hint = getString(R.string.curve_search_hint)
            setSingleLine(true)
            textSize = 16f
            setPadding(dp(16), dp(8), dp(16), dp(8))
            minimumHeight = dp(48)
            background = HoyiUi.shape(this@CurveActivity, R.color.mobile_surface, 12, R.color.mobile_border)
        }
        searchTools.addView(search, LinearLayout.LayoutParams(0, -2, 1f))
        val customAvailable = (application as MobileApplication).customCurvesResult.isSuccess
        val creation = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val newCurve = HoyiUi.button(this, creation, getString(R.string.profiles_new)) {
            startActivity(Intent(this, CurveEditorActivity::class.java))
        }.apply { isEnabled = customAvailable; layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6); topMargin = dp(6) } }
        val importCurve = HoyiUi.button(this, creation, getString(R.string.profiles_import)) {
            startActivity(Intent(this, CurveImportActivity::class.java))
        }.apply { isEnabled = customAvailable; layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { topMargin = dp(6) } }
        if (!customAvailable) HoyiUi.label(this, browser, getString(R.string.profiles_unavailable), 14, muted = true)
        val sort = Spinner(this).apply { minimumHeight = dp(48) }
        sort.adapter = HoyiUi.spinnerAdapter(this,
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
        browser.addView(sort, LinearLayout.LayoutParams(-1, -2))
        if ((application as MobileApplication).curveUsageResult.isFailure)
            HoyiUi.label(this, browser, getString(R.string.curve_usage_unavailable), 13, muted = true)
        val categories = CurveCategoryFilter.entries
        val filter = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val filterRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        filter.addView(filterRow)
        browser.addView(filter, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
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
                minHeight = dp(48)
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
        }.apply { contentDescription = getString(R.string.curve_legacy_description) }
        HoyiUi.button(this, searchTools, getString(R.string.task_curve_library_manage)) {
            showActions(searchTools.findViewWithTag("curve-library-management"), listOf(newCurve, importCurve, legacy))
        }.apply {
            tag = "curve-library-management"
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(6) }
        }
        resultCount = HoyiUi.label(this, browser, "", 13, muted = true)
        val list = ListView(this).apply {
            divider = android.graphics.drawable.ColorDrawable(getColor(R.color.mobile_border))
            dividerHeight = dp(1)
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
                        background = HoyiUi.shape(this@CurveActivity, R.color.mobile_surface, 8, R.color.mobile_border)
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    }
                    addView(thumbnail, LinearLayout.LayoutParams(dp(78), dp(48)).apply { marginEnd = dp(12) })
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
                views.subtitle.text = getString(R.string.curve_row_summary, if (item.customDocument != null) getString(R.string.profiles_category) else CurveCategoryFilter.display(this@CurveActivity, item.category), if (library.canStart(item)) getString(R.string.home_curve_startable) else getString(R.string.curve_browse_only))
                (application as MobileApplication).curveUsageResult.getOrNull()?.let { ledger ->
                    views.subtitle.append(getString(R.string.curve_usage_count, ledger.stats(item.id).count))
                }
                views.preview.targets = stageTargets(item)
                row.isSelected = item.id == selected?.id
                row.background = if (row.isSelected) HoyiUi.shape(this@CurveActivity,
                    R.color.mobile_accent_soft, 0, R.color.mobile_accent) else
                    android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
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
            textSize = 16f
            gravity = android.view.Gravity.CENTER
            setTextColor(getColor(R.color.mobile_muted))
        }
        listHolder.addView(emptyState, FrameLayout.LayoutParams(-1, -1))
        list.emptyView = emptyState
        list.setOnItemClickListener { _, _, position, _ -> show(visibleItems[position]) }

        // The continuous list and genuinely short preview fallback scroll locally, never the whole page.
        detailTitle = HoyiUi.label(this, detailPane, getString(R.string.curve_detail_initial), 19, true).apply {
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            tag = "full-text-details"
        }
        detailCategory = HoyiUi.label(this, detailPane, getString(R.string.curve_detail_hint), 13, muted = true)
        availability = HoyiUi.label(this, detailPane, "", 13, true).apply { visibility = View.GONE }
        stageChart = CurveStageView(this).apply { visibility = View.GONE }
        // Kept as a bounded, non-overflowing viewport for existing layout selectors.
        detailScroll = ScrollView(this).apply {
            isFillViewport = true
            visibility = View.GONE
            isVerticalScrollBarEnabled = false
            addView(stageChart, FrameLayout.LayoutParams(-1, -1))
            background = HoyiUi.shape(this@CurveActivity, R.color.mobile_surface, 12, R.color.mobile_border)
        }
        detailPane.addView(detailScroll, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(6) })
        previewFallback = TextView(this).apply { textSize = 14f; setTextColor(getColor(R.color.mobile_muted)) }
        previewFallbackScroll = ScrollView(this).apply {
            tag = "curve-preview-fallback"
            visibility = View.GONE
            addView(previewFallback)
        }
        detailPane.addView(previewFallbackScroll, LinearLayout.LayoutParams(-1, -2))
        previewSpacer = Space(this).apply { visibility = View.GONE }
        detailPane.addView(previewSpacer, LinearLayout.LayoutParams(-1, 0, 1f))
        stageCaption = HoyiUi.label(this, detailPane, getString(R.string.curve_stage_caption), 12, muted = true)
            .apply { visibility = View.GONE }
        // Full metadata is retained, never truncated; the dedicated dialog owns its local scrolling.
        details = TextView(this).apply { textSize = 15f; setTextColor(getColor(R.color.mobile_text)) }
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
        detailActions = HoyiUi.button(this, detailToolbar, getString(R.string.task_curve_library_actions)) {
            showActions(detailActions, listOf(parameterDetails, shareParameters, editDraft, assignPreset))
        }.apply {
            tag = "curve-detail-actions"
            visibility = View.GONE
            setPadding(dp(6), dp(4), dp(6), dp(4))
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        select = HoyiUi.button(this, detailPane, getString(R.string.curve_use), primary = true) {
            selected?.let { item ->
                getSharedPreferences("curves", MODE_PRIVATE).edit().putString("selected", item.id).apply()
                finish()
            }
        }.apply {
            isEnabled = false; visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) }
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
        availability.setTextColor(getColor(if (canStart) R.color.mobile_accent else R.color.mobile_muted))
        availability.visibility = View.VISIBLE
        detailActions.visibility = View.VISIBLE
        select.isEnabled = canStart
        select.visibility = View.VISIBLE
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
        val exactWidth = View.MeasureSpec.makeMeasureSpec(width.coerceAtLeast(0), View.MeasureSpec.EXACTLY)
        val naturalHeight = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        fun naturalHeightOf(child: View): Int {
            child.measure(exactWidth, naturalHeight)
            val params = child.layoutParams as LinearLayout.LayoutParams
            return child.measuredHeight + params.topMargin + params.bottomMargin
        }
        // Measure the full normal presentation, even when its metadata is currently in fallback.
        // Otherwise hiding metadata would make the next measure flip back into a too-short plot.
        val actionHeight = naturalHeightOf(detailToolbar) + naturalHeightOf(select)
        val metadataHeight = listOf(detailTitle, detailCategory, availability).sumOf(::naturalHeightOf) +
            if (hasPressure) naturalHeightOf(stageCaption) else 0
        val contentMinimum = if (hasPressure) (dp(96) * resources.configuration.fontScale.coerceAtLeast(1f)).toInt() + dp(6)
            else {
                rawReasonMeasure.measure(exactWidth, naturalHeight)
                rawReasonMeasure.measuredHeight
            }
        shortPreview = height - detailPanel.paddingTop - detailPanel.paddingBottom - actionHeight - metadataHeight < contentMinimum
        listOf(detailTitle, detailCategory, availability).forEach {
            it.visibility = if (shortPreview) View.GONE else View.VISIBLE
        }
        stageCaption.visibility = if (hasPressure && !shortPreview) View.VISIBLE else View.GONE
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
            item.name, detailCategory.text.toString(), availability.text.toString(),
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
            detailCategory.visibility = View.VISIBLE
            detailCategory.text = getString(R.string.curve_detail_hint)
            details.text = ""
            availability.visibility = View.GONE
            select.isEnabled = false
            select.visibility = View.GONE
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
