package io.openhoyi.mobile

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

/** Imported rows are browse-only and never feed the machine-control curve library. */
class LegacyCurveActivity : ThemedActivity() {
    private val visual by lazy { LegacyVisualStyle(this) }
    private val style get() = visual.palette
    private data class RowViews(val title: TextView, val subtitle: TextView)
    private lateinit var count: TextView
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var list: ListView
    private lateinit var emptyState: LinearLayout
    private lateinit var emptyTitle: TextView
    private lateinit var emptyMessage: TextView
    private var bundle: LegacyCurveBundle? = null
    private var refreshGeneration = 0
    private var listState: android.os.Parcelable? = null
    private lateinit var collection: TaskCollectionLayout
    private lateinit var detail: TextView
    private lateinit var detailContent: LinearLayout
    private var detailSummary = ""
    private var selectedIndex = -1
    private var selectedFingerprint: String? = null
    private var compact = true
    private var backCallback: android.window.OnBackInvokedCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        listState = savedInstanceState?.getParcelable("listState")
        selectedIndex = savedInstanceState?.getInt("selectedIndex", -1) ?: -1
        selectedFingerprint = savedInstanceState?.getString("selectedFingerprint")
        val root = style.page(this, getString(R.string.legacy_curve_title))
        val header = (root.tag as BeanPageRegions).header
        val headerWords = header.getChildAt(1) as LinearLayout
        val headingText = headerWords.getChildAt(0)
        headerWords.removeView(headingText)
        headerWords.addView(LegacyNaturalFlow(this, dp(16), dp(8)).apply {
            addView(headingText); addView(visual.readonly())
        })
        val toolbar = object : LinearLayout(this) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val available = View.MeasureSpec.getSize(widthMeasureSpec)
                getChildAt(0).measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                getChildAt(1).measure(View.MeasureSpec.makeMeasureSpec(available, View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                val wide = getChildAt(0).measuredWidth + getChildAt(1).measuredWidth + dp(16) <= available
                orientation = if (wide) HORIZONTAL else VERTICAL
                (getChildAt(0).layoutParams as LayoutParams).apply {
                    width = if (wide) 0 else -1
                    weight = if (wide) 1f else 0f
                    marginEnd = if (wide) dp(TaskVisualTokens.GROUP_GAP_DP) else 0
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }.apply {
            gravity = android.view.Gravity.CENTER_VERTICAL
            isBaselineAligned = false
        }
        root.addView(toolbar, LinearLayout.LayoutParams(-1, -2))
        count = style.text(getString(R.string.legacy_curve_loading_initial), TaskProductVisualStyle.auxiliary, "legacy/curve/count", true)
        toolbar.addView(count, LinearLayout.LayoutParams(0, -2, 1f))
        HoyiUi.button(this, toolbar, getString(R.string.curve_import_legacy)) {
                @Suppress("DEPRECATION")
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), IMPORT_CURVES)
        }.apply {
            style.button(this, outline = true)
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { gravity = android.view.Gravity.END }
        }
        collection = TaskCollectionLayout(this, .34f)
        root.addView(collection, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(8) })
        val back = style.button(HoyiUi.button(this, collection.detailBody, getString(R.string.curve_back)) { showList() })
        val detailScroll = android.widget.ScrollView(this)
        collection.detailBody.addView(detailScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        detailContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = style.shape(style.surface, outline = true, radius = 16)
        }
        detailScroll.addView(detailContent)
        detail = style.text(getString(R.string.legacy_curve_select_hint), TaskProductVisualStyle.body).apply {
            tag = "legacy-curve-detail"
        }
        collection.onPresentationChanged = { twoPane, detailVisible ->
            compact = !twoPane
            back.visibility = if (compact) View.VISIBLE else View.GONE
            updateBack(compact && detailVisible)
        }
        collection.detailRequested = savedInstanceState?.getBoolean("detailRequested") == true
        collection.browserBody.background = style.shape(style.surface, outline = true, radius = 16)
        val content = FrameLayout(this)
        collection.browserBody.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        list = ListView(this).apply { divider = android.graphics.drawable.ColorDrawable(style.border); dividerHeight = dp(1) }
        adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, mutableListOf()) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val card = convertView as? LinearLayout ?: LinearLayout(this@LegacyCurveActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    isBaselineAligned = false
                    minimumHeight = dp(64)
                    setPadding(dp(16), dp(8), dp(16), dp(8))
                    background = HoyiUi.shape(this@LegacyCurveActivity, R.color.mobile_surface, 0)
                    addView(HomeVisualIcon(this@LegacyCurveActivity, "curve", style.accent),
                        LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(16) })
                    val words = style.column()
                    addView(words, LinearLayout.LayoutParams(0, -2, 1f))
                    val title = style.text("", TaskProductVisualStyle.compactMetric).also { words.addView(it) }
                    val subtitle = style.text("", TaskProductVisualStyle.auxiliary, secondary = true).also { words.addView(it) }
                    addView(HomeVisualIcon(this@LegacyCurveActivity, "chevron", style.muted),
                        LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginStart = dp(8) })
                    subtitle.setPadding(0, dp(8), 0, 0)
                    tag = RowViews(title, subtitle)
                }
                val curve = bundle?.curves?.getOrNull(position)
                card.setBackgroundColor(if (position == selectedIndex) style.selected else style.surface)
                card.isActivated = position == selectedIndex
                val views = card.tag as RowViews
                views.title.text = curve?.name ?: getItem(position)
                val category = curve?.let { bundle?.categoryLabels?.get(it.category) ?: it.category.ifBlank { getString(R.string.legacy_curve_uncategorized) } }
                views.subtitle.text = curve?.let { getString(R.string.legacy_curve_metadata,
                    category ?: getString(R.string.legacy_curve_uncategorized),
                    getString(if (it.factory) R.string.legacy_curve_factory else R.string.legacy_curve_user), it.index + 1) }.orEmpty()
                return card
            }
        }
        list.adapter = adapter
        content.addView(list, FrameLayout.LayoutParams(-1, -1))
        emptyState = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            visibility = View.GONE
        }
        content.addView(emptyState, FrameLayout.LayoutParams(-1, -1))
        val emptyCard = visual.panel(style.column()).also { emptyState.addView(it, LinearLayout.LayoutParams(-1, -2)) }
        emptyTitle = style.text(getString(R.string.legacy_curve_empty_title), TaskProductVisualStyle.section).also { style.add(emptyCard, it) }
        emptyMessage = style.text(getString(R.string.legacy_curve_empty_message), TaskProductVisualStyle.auxiliary, secondary = true).also { style.add(emptyCard, it, 8) }
        list.setOnItemClickListener { _, _, position, _ ->
            val curve = bundle?.curves?.getOrNull(position) ?: return@setOnItemClickListener
            rememberList()
            selectedIndex = position
            selectedFingerprint = fingerprint(curve)
            showSelected()
            collection.detailRequested = true
        }
    }


    private fun fingerprint(curve: LegacyCurve) = java.security.MessageDigest.getInstance("SHA-256")
        .digest(curve.rawJson.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun showSelected() {
        val data = bundle
        val curve = data?.curves?.getOrNull(selectedIndex)
        detailContent.removeAllViews()
        detailContent.addView(detail, LinearLayout.LayoutParams(-1, -2))
        if (curve == null || fingerprint(curve) != selectedFingerprint) {
            detailSummary = ""
            style.apply(detail, TaskProductVisualStyle.body)
            selectedIndex = -1; selectedFingerprint = null
            detail.setText(R.string.legacy_curve_select_hint)
            collection.detailRequested = false
        } else {
            val category = data.categoryLabels[curve.category] ?: curve.category.ifBlank { getString(R.string.legacy_curve_uncategorized) }
            detailSummary = getString(R.string.legacy_curve_detail, curve.name, category, curve.index + 1,
                getString(if (curve.factory) R.string.legacy_curve_factory else R.string.legacy_curve_user),
                curve.temperatureC?.let { "$it °C" } ?: getString(R.string.settings_unknown),
                curve.waterMl?.let { "$it ml" } ?: getString(R.string.settings_unknown),
                curve.weightTenthsGram?.let { String.format(Locale.CHINA, "%.1f g", it / 10.0) } ?: getString(R.string.settings_unknown),
                curve.segments?.toString() ?: getString(R.string.settings_unknown))
            detail.text = curve.name
            style.apply(detail, TaskProductVisualStyle.title)
            style.add(detailContent, style.text(getString(R.string.legacy_curve_metadata, category,
                getString(if (curve.factory) R.string.legacy_curve_factory else R.string.legacy_curve_user),
                curve.index + 1), TaskProductVisualStyle.auxiliary, "legacy/curve/metadata", true), 8)
            style.divider(detailContent)
            data class Metric(val label: Int, val value: String?, val unit: String?)
            val values = listOf(
                Metric(R.string.curve_preview_temperature, curve.temperatureC?.toString(), "°C"),
                Metric(R.string.legacy_curve_water_label, curve.waterMl?.toString(), "ml"),
                Metric(R.string.curve_preview_weight, curve.weightTenthsGram?.let { String.format(Locale.CHINA, "%.1f", it / 10.0) }, "g"),
                Metric(R.string.curve_preview_stages, curve.segments?.toString(), null))
            values.chunked(2).forEachIndexed { rowIndex, pair ->
                val row = style.row()
                detailContent.addView(row, LinearLayout.LayoutParams(-1, -2).apply { if (rowIndex > 0) topMargin = dp(16) })
                pair.forEachIndexed { index, metric ->
                    val cell = visual.panel(style.column())
                    row.addView(cell, LinearLayout.LayoutParams(0, -2, 1f).apply { if (index > 0) marginStart = dp(16) })
                    style.add(cell, style.text(getString(metric.label), TaskProductVisualStyle.auxiliary, secondary = true))
                    style.add(cell, style.quantity(metric.value ?: getString(R.string.settings_unknown),
                        metric.unit.takeIf { metric.value != null }, visual.metric, "legacy/curve/metric/${rowIndex * 2 + index}"), 8)
                }
            }
            style.add(detailContent, visual.warning(getString(R.string.legacy_curve_subtitle), "legacy/curve/notice"), 16)
            HoyiUi.button(this, detailContent, getString(R.string.ui_full_details)) {
                android.app.AlertDialog.Builder(this).setTitle(curve.name).setMessage(detailSummary)
                    .setPositiveButton(android.R.string.ok, null).show()
            }.also { style.button(it) }
        }
        adapter.notifyDataSetChanged()
    }

    private fun showList() { collection.detailRequested = false; updateBack(false) }
    private fun updateBack(active: Boolean) {
        if (Build.VERSION.SDK_INT < 33) return
        if (active && backCallback == null) {
            backCallback = android.window.OnBackInvokedCallback { showList() }.also {
                onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, it)
            }
        } else if (!active) {
            backCallback?.let { onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it) }
            backCallback = null
        }
    }
    @Deprecated("Platform back callback")
    override fun onBackPressed() {
        if (compact && collection.detailRequested) showList() else super.onBackPressed()
    }
    override fun onDestroy() { updateBack(false); super.onDestroy() }

    override fun onStart() { super.onStart(); refresh() }

    private fun rememberList() {
        if (::list.isInitialized && list.isShown && !list.isLayoutRequested && list.childCount > 0)
            listState = list.onSaveInstanceState()
    }

    override fun onStop() { rememberList(); super.onStop() }

    override fun onSaveInstanceState(outState: Bundle) {
        rememberList()
        outState.putParcelable("listState", listState)
        outState.putInt("selectedIndex", selectedIndex)
        outState.putString("selectedFingerprint", selectedFingerprint)
        outState.putBoolean("detailRequested", collection.detailRequested)
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Platform activity results")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != IMPORT_CURVES || resultCode != Activity.RESULT_OK) return
        val uri = data?.data ?: return
        count.text = getString(R.string.legacy_curve_checking)
        Thread({
            val app = application as MobileApplication
            val result = runCatching {
                app.legacyCurves.import(contentResolver.openInputStream(uri) ?: error("No input stream"))
            }
            app.logs.record(if (result.isSuccess) "legacy.curves_import_finished" else "legacy.curves_import_failed",
                mapOf("result" to result.fold({ "${it.count} curves, changed=${it.changed}" },
                    { it.javaClass.simpleName })))
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                val toast = Toast.makeText(this, result.fold(
                    { if (it.changed) getString(R.string.legacy_curve_import_changed, it.count.toString()) else getString(R.string.legacy_curve_import_unchanged, it.count.toString()) },
                    { getString(R.string.legacy_import_error, it.message ?: it.javaClass.simpleName) }),
                    Toast.LENGTH_LONG)
                LegacyImportToastObservation.beforeShow(toast, "legacy-curve-import")
                toast.show()
                if (result.getOrNull()?.changed == true) { selectedIndex = -1; selectedFingerprint = null }
                refresh()
            }
        }, "legacy-curve-import").start()
    }

    private fun refresh() {
        rememberList()
        val generation = ++refreshGeneration
        count.text = getString(R.string.legacy_curve_loading)
        emptyState.visibility = View.GONE
        list.visibility = View.GONE
        Thread({
            val result = runCatching { (application as MobileApplication).legacyCurves.load() }
            runOnUiThread {
                if (isFinishing || isDestroyed || generation != refreshGeneration) return@runOnUiThread
                bundle = result.getOrNull()
                count.text = result.fold({ data ->
                    if (data == null) getString(R.string.legacy_curve_not_imported)
                    else getString(R.string.legacy_curve_count, data.curves.size.toString())
                }, { getString(R.string.legacy_curve_read_error, it.message ?: it.javaClass.simpleName) })
                adapter.clear()
                adapter.addAll(bundle?.curves?.map { curve ->
                    val category = bundle?.categoryLabels?.get(curve.category)
                        ?: curve.category.ifBlank { getString(R.string.legacy_curve_uncategorized) }
                    getString(R.string.legacy_curve_adapter_row, (curve.index + 1).toString(), curve.name, category)
                } ?: emptyList())
                if (selectedIndex < 0) bundle?.curves?.firstOrNull()?.let {
                    selectedIndex = 0; selectedFingerprint = fingerprint(it)
                }
                showSelected()
                emptyTitle.text = if (result.isFailure) getString(R.string.legacy_curve_failed) else getString(R.string.legacy_curve_empty_title)
                emptyMessage.text = if (result.isFailure) getString(R.string.legacy_curve_failed_message)
                    else getString(R.string.legacy_curve_empty_message)
                emptyState.visibility = if (adapter.count == 0) View.VISIBLE else View.GONE
                list.visibility = if (adapter.count == 0) View.GONE else View.VISIBLE
                if (adapter.count > 0) list.post {
                    if (!isFinishing && !isDestroyed && generation == refreshGeneration)
                        listState?.let { list.onRestoreInstanceState(it) }
                }
            }
        }, "legacy-curve-list").start()
    }

    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()
    private companion object { const val IMPORT_CURVES = 43 }
}
