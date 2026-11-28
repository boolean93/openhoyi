package io.openhoyi.mobile

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*

/** Native extraction outcomes with separate legacy import actions. */
class HistoryActivity : ThemedActivity() {
    private val visual by lazy { ExtractionHistoryVisualStyle(this) }
    private val style get() = visual.palette
    private lateinit var adapter: HistoryBrowserAdapter
    private lateinit var historyList: ListView
    private val listStates = mutableMapOf<Boolean, android.os.Parcelable>()
    private lateinit var sources: HistoryBrowserSources
    private lateinit var emptyTitle: TextView
    private lateinit var emptyMessage: TextView
    private lateinit var emptyAction: Button
    private var rows = emptyList<ShotHistory.Entry>()
    private var showJournal = false
    private lateinit var collection: TaskCollectionLayout
    private lateinit var detailContent: LinearLayout
    private val selectedIds = mutableMapOf<Boolean, String>()
    private var restoreDetail = false
    private var compact = true
    private var renderedPreview: HistoryPreview? = null
    private var backCallback: android.window.OnBackInvokedCallback? = null
    private val journalActions = mutableListOf<Button>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (redirectRoot(TaskDestination.HISTORY)) return
        showJournal = savedInstanceState?.getBoolean("showJournal")
            ?: (intent.getStringExtra(EXTRA_HISTORY_SOURCE) == SOURCE_JOURNAL)
        savedInstanceState?.getParcelable<android.os.Parcelable>("localList")?.let { listStates[false] = it }
        savedInstanceState?.getParcelable<android.os.Parcelable>("journalList")?.let { listStates[true] = it }
        savedInstanceState?.getString("localSelection")?.let { selectedIds[false] = it }
        savedInstanceState?.getString("journalSelection")?.let { selectedIds[true] = it }
        restoreDetail = savedInstanceState?.getBoolean("detailRequested") ?: false
        buildScreen()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        if (redirectedRoot) { super.onSaveInstanceState(outState); return }
        super.onSaveInstanceState(outState)
        rememberList()
        outState.putParcelable("localList", listStates[false])
        outState.putParcelable("journalList", listStates[true])
        outState.putBoolean("showJournal", showJournal)
        outState.putString("localSelection", selectedIds[false])
        outState.putString("journalSelection", selectedIds[true])
        outState.putBoolean("detailRequested", collection.detailRequested)
    }
    private fun buildScreen() {
        updateBack(false)
        renderedPreview = null
        journalActions.clear()
        val body = style.page(this, getString(R.string.home_history), navigation = HistoryActivity::class.java)
        val header = (body.tag as BeanPageRegions).header
        val navigationTools = header.findViewWithTag<View>("task-tools-host")
        header.removeView(navigationTools)
        header.visibility = View.GONE
        val actions = style.column()
        fun action(title: String, click: () -> Unit) {
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            actions.addView(box, LinearLayout.LayoutParams(-1, -2))
            style.button(HoyiUi.button(this, box, title, action = click)).also { if (showJournal) journalActions += it }
        }
        action(getString(if (showJournal) R.string.journal_export else R.string.history_export)) {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType(if (showJournal) "application/json" else "application/zip")
                .putExtra(Intent.EXTRA_TITLE, if (showJournal) "openhoyi-journal-${System.currentTimeMillis()}.json" else "openhoyi-history-${System.currentTimeMillis()}.zip"),
                if (showJournal) EXPORT_JOURNAL else EXPORT_HISTORY)
        }
        action(getString(if (showJournal) R.string.journal_migrate else R.string.history_import)) {
            if (showJournal) migrateRecent()
            else startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), IMPORT_LEGACY)
        }
        style.action(actions, getString(R.string.history_browser_legacy_menu), "product/history/open-legacy") {
            startActivity(Intent(this, LegacyHistoryActivity::class.java))
        }
        val ioMenu = ImageButton(this).apply {
            tag = "product/history/io-menu"; contentDescription = getString(R.string.task_tools)
            setImageDrawable(HomeVisualIcon.drawable(this@HistoryActivity, "more", style.accent))
            setPadding(dp(12), dp(12), dp(12), dp(12)); minimumWidth = dp(48); minimumHeight = dp(48)
            background = android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(style.selected),
                null, style.shape(android.graphics.Color.WHITE))
            setOnClickListener {
                (actions.parent as? android.view.ViewGroup)?.removeView(actions)
                val dialog = android.app.AlertDialog.Builder(this@HistoryActivity).setTitle(R.string.task_tools)
                    .setView(ScrollView(this@HistoryActivity).apply { setPadding(dp(16), dp(16), dp(16), dp(16)); addView(actions) })
                    .setPositiveButton(android.R.string.ok, null).create()
                beanShowDialog(dialog)
            }
        }
        val list = historyBrowserList(this, style)
        historyList = list
        adapter = HistoryBrowserAdapter(this, visual, showJournal) { selectedIds[showJournal] }
        list.adapter = adapter
        collection = historyBrowserCollection(this)
        body.addView(collection, LinearLayout.LayoutParams(-1, 0, 1f))
        sources = HistoryBrowserSources(this, style, showJournal, ioMenu, navigationTools) { journal ->
            rememberList()
            showJournal = journal
            restoreDetail = false
            buildScreen()
            render()
        }
        style.add(collection.browserBody, sources)
        val back = style.button(HoyiUi.button(this, collection.detailBody, getString(R.string.history_back_to_list)) { showList() })
        collection.onPresentationChanged = { split, visible ->
            compact = !split
            back.visibility = if (compact) View.VISIBLE else View.GONE
            updateBack(compact && visible)
        }
        collection.detailRequested = restoreDetail
        detailContent = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        collection.detailBody.addView(detailContent, LinearLayout.LayoutParams(-1, 0, 1f))
        val content = FrameLayout(this)
        collection.browserBody.addView(content, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(8) })
        content.addView(list, FrameLayout.LayoutParams(-1, -1))
        val emptyHolder = style.column().apply { gravity = android.view.Gravity.CENTER }
        val empty = ScrollView(this).apply {
            isFillViewport = true
            addView(emptyHolder, FrameLayout.LayoutParams(-1, -2))
        }
        content.addView(empty, FrameLayout.LayoutParams(-1, -1))
        val emptyCard = HoyiUi.card(this, emptyHolder).apply {
            tag = "product/history/empty-card"
            keepOwnBackdropTreatment()
        }
        if (HoyiUi.wide(this)) (emptyCard.layoutParams as LinearLayout.LayoutParams).apply {
            width = -1
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        }.also { emptyCard.layoutParams = it }
        emptyTitle = HoyiUi.label(this, emptyCard, getString(R.string.history_empty_title), 19, true)
        emptyMessage = HoyiUi.label(this, emptyCard,
            getString(R.string.history_empty_message), 16, muted = true)
        emptyAction = HoyiUi.button(this, emptyCard, getString(R.string.history_select_curve), primary = true) {
            if (showJournal) migrateRecent() else startActivity(Intent(this, CurveActivity::class.java))
        }
        style.addEmptyHistoryCompare(this, emptyCard)
        list.emptyView = empty
        listStates[showJournal]?.let { list.onRestoreInstanceState(it) }
        list.setOnItemClickListener { _, _, position, _ ->
            rememberList()
            selectedIds[showJournal] = rows[position].id
            collection.detailRequested = true
            renderSelection(refresh = true)
            adapter.notifyDataSetChanged()
        }
    }
    override fun onStart() { super.onStart(); if (!redirectedRoot) render() }

    @Deprecated("Platform activity results")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == EXPORT_HISTORY && resultCode == RESULT_OK)
            data?.data?.let { (application as MobileApplication).exportHistory(it) }
        if (requestCode == IMPORT_LEGACY && resultCode == RESULT_OK)
            data?.data?.let { (application as MobileApplication).importLegacyHistory(it) }
        if (requestCode == EXPORT_JOURNAL && resultCode == RESULT_OK) data?.data?.let { uri ->
            val document = runCatching { (application as MobileApplication).journalResult.getOrThrow().exportJson() }
            Thread({
                val result = document.mapCatching { json ->
                    val output = contentResolver.openOutputStream(uri) ?: error("No document output")
                    output.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                }
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) Toast.makeText(this, getString(if (result.isSuccess) R.string.journal_export_success else R.string.journal_export_failed), Toast.LENGTH_LONG).show()
                }
            }, "journal-export").start()
        }
    }
    private fun migrateRecent() {
        val app = application as MobileApplication
        val result = runCatching {
            val recent = app.history.entries.map { it.journalObservation() }
            app.reconcileJournal().getOrThrow().importRecent(recent)
        }
        Toast.makeText(this, result.fold({ getString(R.string.journal_migrated, it) }, { getString(R.string.journal_save_failed) }), Toast.LENGTH_LONG).show()
        render()
    }
    private fun render() {
        // Adapter refreshes must not send a returning reader back to the first record.
        rememberList()
        if (showJournal) {
            val journal = (application as MobileApplication).journalResult.getOrNull()
            rows = journal?.entries()?.map { it.observation.historySummary() }.orEmpty()
            journalActions.forEach { it.isEnabled = journal != null }
            sources.setCount(rows.size.takeIf { journal != null })
            emptyTitle.text = getString(if (journal == null) R.string.journal_unavailable else R.string.journal_empty)
            emptyMessage.text = getString(if (journal == null) R.string.journal_unavailable else R.string.journal_empty_hint)
            emptyAction.text = getString(R.string.journal_migrate)
            emptyAction.visibility = if (journal == null) View.GONE else View.VISIBLE
            adapter.replaceRows(rows)
            listStates[showJournal]?.let { historyList.onRestoreInstanceState(it) }
            renderSelection()
            return
        }
        val history = runCatching { (application as MobileApplication).history }.getOrNull()
        if (history == null) {
            rows = emptyList()
            sources.setCount(null)
            emptyTitle.text = getString(R.string.history_unavailable)
            emptyMessage.text = getString(R.string.history_unavailable_message)
            emptyAction.visibility = View.GONE
        } else {
            rows = history.entries
            sources.setCount(rows.size)
            emptyTitle.text = getString(R.string.history_empty_title)
            emptyMessage.text = getString(R.string.history_empty_message)
            emptyAction.visibility = View.VISIBLE
        }
        adapter.replaceRows(rows)
        listStates[showJournal]?.let { historyList.onRestoreInstanceState(it) }
        renderSelection()
    }
    private fun renderSelection(refresh: Boolean = false) {
        val selected = rows.firstOrNull { it.id == selectedIds[showJournal] }
        val preview = selected?.let { historyPreview(it) }
        val selectionTag = selected?.let { "history-selection-${it.id}" }
        if (!refresh && preview != null && detailContent.tag == selectionTag && detailContent.childCount > 0) {
            val previous = renderedPreview
            if (previous == preview) return
            if (previous != null && previous.hasSamePreviewExceptNotes(preview) &&
                refreshHistoryPreviewNotes(detailContent, preview)) {
                renderedPreview = preview
                return
            }
        }
        renderedPreview = preview
        detailContent.removeAllViews()
        detailContent.tag = selectionTag
        if (preview == null) {
            selectedIds.remove(showJournal)
            collection.detailRequested = false
            HoyiUi.label(this, detailContent, getString(R.string.history_select_record), 18)
            if (rows.isNotEmpty()) style.addEmptyHistoryCompare(this, detailContent)
            return
        }
        renderHistoryPreview(detailContent, preview, recoverTiming = collection.detailRequested, openFullPage = {
            startActivity(Intent(this, HistoryDetailActivity::class.java).putExtra("shotId", preview.entry.id))
        }, visual = visual, onReadFailure = {
            if (renderedPreview === preview) renderedPreview = null
        })
    }
    private fun rememberList() {
        if (::historyList.isInitialized && historyList.isShown && !historyList.isLayoutRequested && historyList.childCount > 0)
            historyList.onSaveInstanceState()?.let { listStates[showJournal] = it }
    }
    private fun showList() {
        collection.detailRequested = false
        updateBack(false)
        historyList.post { listStates[showJournal]?.let { historyList.onRestoreInstanceState(it) } }
    }
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
    override fun onBackPressed() { if (compact && collection.detailRequested) showList() else super.onBackPressed() }
    override fun onStop() { if (!redirectedRoot) rememberList(); super.onStop() }
    override fun onDestroy() { if (!redirectedRoot) updateBack(false); super.onDestroy() }
    private fun dp(value: Int) = HoyiUi.dp(this, value)
    companion object {
        const val EXTRA_HISTORY_SOURCE = "historySource"
        const val SOURCE_JOURNAL = "journal"
        const val SOURCE_LOCAL = "local"
        private const val EXPORT_HISTORY = 41
        private const val IMPORT_LEGACY = 42
        private const val EXPORT_JOURNAL = 43
    }
}
