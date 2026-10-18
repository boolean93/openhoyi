package io.openhoyi.mobile

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

/** Imported rows are browse-only and never feed the machine-control curve library. */
class LegacyCurveActivity : ThemedActivity() {
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
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.mobile_background))
            setOnApplyWindowInsetsListener { view, insets ->
                val bars = if (Build.VERSION.SDK_INT >= 30) {
                    val area = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                    intArrayOf(area.left, area.top, area.right, area.bottom)
                } else {
                    @Suppress("DEPRECATION")
                    intArrayOf(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                        insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                }
                view.setPadding(dp(16) + bars[0], dp(8) + bars[1], dp(16) + bars[2], dp(8) + bars[3])
                insets
            }
        }
        setContentView(root)
        HoyiUi.header(this, root, getString(R.string.legacy_curve_title), getString(R.string.legacy_curve_subtitle), back = true)
        val toolbar = object : LinearLayout(this) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val wide = View.MeasureSpec.getSize(widthMeasureSpec) >= dp(624)
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
        count = label(getString(R.string.legacy_curve_loading_initial), TaskVisualTokens.SUPPORTING_SP)
        toolbar.addView(count, LinearLayout.LayoutParams(0, -2, 1f))
        HoyiUi.button(this, toolbar, getString(R.string.curve_import_legacy)) {
                @Suppress("DEPRECATION")
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), IMPORT_CURVES)
        }.apply {
            asTaskTextAction()
            maxWidth = dp(320)
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { gravity = android.view.Gravity.END }
        }
        collection = TaskCollectionLayout(this)
        root.addView(collection, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(8) })
        val back = HoyiUi.button(this, collection.detailBody, getString(R.string.curve_back)) { showList() }
        val detailScroll = android.widget.ScrollView(this)
        collection.detailBody.addView(detailScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        detail = label(getString(R.string.legacy_curve_select_hint), 17).apply { tag = "legacy-curve-detail" }
        detailScroll.addView(detail)
        collection.onPresentationChanged = { twoPane, detailVisible ->
            compact = !twoPane
            back.visibility = if (compact) View.VISIBLE else View.GONE
            updateBack(compact && detailVisible)
        }
        collection.detailRequested = savedInstanceState?.getBoolean("detailRequested") == true
        val content = FrameLayout(this)
        collection.browserBody.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        list = ListView(this).apply { divider = android.graphics.drawable.ColorDrawable(getColor(R.color.mobile_border)); dividerHeight = dp(1) }
        adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, mutableListOf()) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val card = convertView as? LinearLayout ?: LinearLayout(this@LegacyCurveActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    minimumHeight = dp(64)
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    background = HoyiUi.shape(this@LegacyCurveActivity, R.color.mobile_surface, 0)
                    val title = HoyiUi.label(this@LegacyCurveActivity, this, "", 17, true)
                    val subtitle = HoyiUi.label(this@LegacyCurveActivity, this, "", 14, muted = true)
                    subtitle.setPadding(0, dp(7), 0, 0)
                    tag = RowViews(title, subtitle)
                }
                val curve = bundle?.curves?.getOrNull(position)
                card.setBackgroundColor(getColor(if (position == selectedIndex) R.color.mobile_accent_soft else R.color.mobile_surface))
                card.isActivated = position == selectedIndex
                val views = card.tag as RowViews
                views.title.text = curve?.name ?: getItem(position)
                val category = curve?.let { bundle?.categoryLabels?.get(it.category) ?: it.category.ifBlank { getString(R.string.legacy_curve_uncategorized) } }
                views.subtitle.text = if (curve?.factory == true) getString(R.string.legacy_curve_factory_row, category ?: getString(R.string.legacy_curve_uncategorized)) else getString(R.string.legacy_curve_user_row, category ?: getString(R.string.legacy_curve_uncategorized))
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
        val emptyCard = HoyiUi.card(this, emptyState)
        emptyTitle = HoyiUi.label(this, emptyCard, getString(R.string.legacy_curve_empty_title), 19, true)
        emptyMessage = HoyiUi.label(this, emptyCard, getString(R.string.legacy_curve_empty_message), 15, muted = true)
        emptyMessage.setPadding(0, dp(10), 0, 0)
        HoyiUi.navigation(this, root, CurveActivity::class.java)
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
        if (curve == null || fingerprint(curve) != selectedFingerprint) {
            selectedIndex = -1; selectedFingerprint = null
            detail.setText(R.string.legacy_curve_select_hint)
            collection.detailRequested = false
        } else {
            val category = data.categoryLabels[curve.category] ?: curve.category.ifBlank { getString(R.string.legacy_curve_uncategorized) }
            detail.text = getString(R.string.legacy_curve_detail, curve.name, category, curve.index + 1,
                getString(if (curve.factory) R.string.legacy_curve_factory else R.string.legacy_curve_user),
                curve.temperatureC?.let { "$it °C" } ?: getString(R.string.settings_unknown),
                curve.waterMl?.let { "$it ml" } ?: getString(R.string.settings_unknown),
                curve.weightTenthsGram?.let { String.format(Locale.CHINA, "%.1f g", it / 10.0) } ?: getString(R.string.settings_unknown),
                curve.segments?.toString() ?: getString(R.string.settings_unknown))
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
                Toast.makeText(this, result.fold(
                    { if (it.changed) getString(R.string.legacy_curve_import_changed, it.count.toString()) else getString(R.string.legacy_curve_import_unchanged, it.count.toString()) },
                    { getString(R.string.legacy_import_error, it.message ?: it.javaClass.simpleName) }),
                    Toast.LENGTH_LONG).show()
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

    private fun label(value: String, size: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size.toFloat(); setTextColor(getColor(R.color.mobile_text))
        setPadding(0, dp(5), 0, dp(5))
        if (bold) typeface = TaskTypography.face(context, 700)
    }
    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()
    private companion object { const val IMPORT_CURVES = 43 }
}
