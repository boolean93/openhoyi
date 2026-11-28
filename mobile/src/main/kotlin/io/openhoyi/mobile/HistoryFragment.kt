package io.openhoyi.mobile

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*

/** Native extraction outcomes with separate legacy import actions. */
class HistoryFragment : androidx.fragment.app.Fragment() {
    private var toolsDialog: android.app.AlertDialog? = null
    private val host get() = requireActivity() as MainActivity
    private val RESULT_OK=android.app.Activity.RESULT_OK
    private val application get() = host.application
    private val contentResolver get() = host.contentResolver
    private val pageHost by lazy { android.widget.FrameLayout(host) }
    private val visual by lazy { ExtractionHistoryVisualStyle(host) }
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
    private var requestedSource: Boolean? = null
    internal fun showSource(journal: Boolean) {
        if (view == null) {
            requestedSource = journal
            showJournal = journal
        } else if (showJournal != journal) {
            rememberList()
            showJournal = journal
            restoreDetail = false
            buildScreen()
            render()
        }
    }
    private lateinit var collection: TaskCollectionLayout
    private lateinit var detailContent: LinearLayout
    private val selectedIds = mutableMapOf<Boolean, String>()
    private var portraitDetailBack: Button? = null
    private var restoreDetail = false
    private var compact = true
    private var renderedPreview: HistoryPreview? = null
    private val journalActions = mutableListOf<Button>()

    override fun onCreateView(inflater: android.view.LayoutInflater, container: android.view.ViewGroup?, savedInstanceState: Bundle?): View {
        showJournal = requestedSource ?: savedInstanceState?.getBoolean("showJournal")
            ?: false
        requestedSource = null
        savedInstanceState?.getParcelable<android.os.Parcelable>("localList")?.let { listStates[false] = it }
        savedInstanceState?.getParcelable<android.os.Parcelable>("journalList")?.let { listStates[true] = it }
        savedInstanceState?.getString("localSelection")?.let { selectedIds[false] = it }
        savedInstanceState?.getString("journalSelection")?.let { selectedIds[true] = it }
        restoreDetail = savedInstanceState?.let {
            it.getBoolean("showJournal") == showJournal && it.getBoolean("detailRequested")
        } ?: false
        buildScreen()
        return pageHost
    }
    override fun onSaveInstanceState(outState: Bundle) {
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
        compact = true
        rows = emptyList()
        renderedPreview = null
        updateBack(false)
        journalActions.clear()
        val (root,body) = style.fragmentPage(host, getString(R.string.home_history))
        pageHost.removeAllViews();pageHost.addView(root)
        val header = (body.tag as BeanPageRegions).header
        header.visibility = View.GONE
        val actions = style.column()
        fun action(title: String, click: () -> Unit) {
            val box = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
            actions.addView(box, LinearLayout.LayoutParams(-1, -2))
            style.button(HoyiUi.button(host, box, title, action = click)).also { if (showJournal) journalActions += it }
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
            toolsDialog?.dismiss()
            startActivity(Intent(host, LegacyHistoryActivity::class.java))
        }
        val ioMenu = ImageButton(host).apply {
            tag = "product/history/io-menu"; contentDescription = getString(R.string.task_tools)
            setImageDrawable(HomeVisualIcon.drawable(host, "more", style.accent))
            setPadding(dp(12), dp(12), dp(12), dp(12)); minimumWidth = dp(48); minimumHeight = dp(48)
            background = android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(style.selected),
                null, style.shape(android.graphics.Color.WHITE))
            setOnClickListener {
                (actions.parent as? android.view.ViewGroup)?.removeView(actions)
                val dialog = android.app.AlertDialog.Builder(host).setTitle(R.string.task_tools)
                    .setView(ScrollView(host).apply { setPadding(dp(16), dp(16), dp(16), dp(16)); addView(actions) })
                    .setPositiveButton(android.R.string.ok, null).create()
                toolsDialog = AppDialog.show(dialog)
            }
        }
        val list = historyBrowserList(host, style)
        historyList = list
        adapter = HistoryBrowserAdapter(host, visual, showJournal) { selectedIds[showJournal] }
        list.adapter = adapter
        collection = historyBrowserCollection(host)
        body.addView(collection, LinearLayout.LayoutParams(-1, 0, 1f))
        sources = HistoryBrowserSources(host, style, showJournal, ioMenu, onSource = ::showSource)
        style.add(collection.browserBody, sources)
        val back = style.button(HoyiUi.button(host, collection.detailBody, getString(R.string.history_back_to_list)) { showList() }).also { portraitDetailBack = it }
        val screen = collection
        screen.onPresentationChanged = { split, visible ->
            val enteringSplit = compact && split
            compact = !split
            back.visibility = if (compact) View.VISIBLE else View.GONE
            updateBack(compact && visible)
            if (enteringSplit) screen.post {
                if (collection === screen && screen.isAttachedToWindow && !compact) renderSelection()
            }
        }
        collection.detailRequested = restoreDetail
        detailContent = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
        collection.detailBody.addView(detailContent, LinearLayout.LayoutParams(-1, 0, 1f))
        val content = FrameLayout(host)
        collection.browserBody.addView(content, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(8) })
        content.addView(list, FrameLayout.LayoutParams(-1, -1))
        val emptyHolder = style.column().apply { gravity = android.view.Gravity.CENTER }
        val empty = ScrollView(host).apply {
            isFillViewport = true
            addView(emptyHolder, FrameLayout.LayoutParams(-1, -2))
        }
        content.addView(empty, FrameLayout.LayoutParams(-1, -1))
        val emptyCard = HoyiUi.card(host, emptyHolder).apply {
            tag = "product/history/empty-card"
            keepOwnBackdropTreatment()
        }
        if (HoyiUi.wide(host)) (emptyCard.layoutParams as LinearLayout.LayoutParams).apply {
            width = -1
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        }.also { emptyCard.layoutParams = it }
        emptyTitle = HoyiUi.label(host, emptyCard, getString(R.string.history_empty_title), 19, true)
        emptyMessage = HoyiUi.label(host, emptyCard,
            getString(R.string.history_empty_message), 16, muted = true)
        emptyAction = HoyiUi.button(host, emptyCard, getString(R.string.history_select_curve), primary = true) {
            if (showJournal) migrateRecent() else host.select(TaskDestination.CURVES)
        }
        style.addEmptyHistoryCompare(host, emptyCard)
        list.emptyView = empty
        list.setOnItemClickListener { _, _, position, _ ->
            rememberList()
            selectedIds[showJournal] = rows[position].id
            collection.detailRequested = true
            renderSelection(refresh = true)
            adapter.notifyDataSetChanged()
        }
    }
    override fun onResume() { super.onResume(); render() }

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
                host.runOnUiThread {
                    if (!host.isFinishing && !host.isDestroyed) Toast.makeText(host, getString(if (result.isSuccess) R.string.journal_export_success else R.string.journal_export_failed), Toast.LENGTH_LONG).show()
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
        Toast.makeText(host, result.fold({ getString(R.string.journal_migrated, it) }, { getString(R.string.journal_save_failed) }), Toast.LENGTH_LONG).show()
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
        val previousId = selectedIds[showJournal]
        val previousSelection = rows.firstOrNull { it.id == previousId }
        if (previousSelection == null) collection.detailRequested = false
        val selected = previousSelection ?: if (compact) null else rows.firstOrNull()
        if (selected == null) selectedIds.remove(showJournal) else selectedIds[showJournal] = selected.id
        if (selected?.id != previousId) adapter.notifyDataSetChanged()

        val preview = selected?.let { host.historyPreview(it) }
        val selectionTag = selected?.let { "history-selection-${it.id}" }
        if (!refresh && detailContent.tag == selectionTag && detailContent.childCount > 0) {
            val previous = renderedPreview
            if (previous == preview) return
            if (previous != null && preview != null && previous.hasSamePreviewExceptNotes(preview) &&
                host.refreshHistoryPreviewNotes(detailContent, preview)) {
                renderedPreview = preview
                return
            }
        }
        renderedPreview = preview
        detailContent.removeAllViews()
        detailContent.tag = selectionTag
        if (preview == null) {
            HoyiUi.label(host, detailContent, getString(R.string.history_select_record), 18)
            if (rows.isNotEmpty()) style.addEmptyHistoryCompare(host, detailContent)
            return
        }
        host.renderHistoryPreview(detailContent, preview, recoverTiming = collection.detailRequested, openFullPage = {
            startActivity(Intent(host, HistoryDetailActivity::class.java).putExtra("shotId", preview.entry.id))
        }, visual = visual, onReadFailure = {
            if (renderedPreview === preview) renderedPreview = null
        })
        if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT) {
            val title = detailContent.findViewWithTag<LinearLayout>("product/history/title-row")
            val back = portraitDetailBack
            if (title != null && back != null) {
                (back.parent as? android.view.ViewGroup)?.removeView(back)
                back.text = ""; back.contentDescription = getString(R.string.history_back_to_list)
                HomeVisualIcon.buttonIcon(back, "back")
                title.addView(back, 0, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(8) })
            }
        }
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
        // MainActivity checks consumeBack only for the visible tab.
    }
    fun consumeBack():Boolean { if (compact && collection.detailRequested) {showList();return true};return false }
    override fun onPause() { toolsDialog?.dismiss(); toolsDialog = null; rememberList(); super.onPause() }
    override fun onDestroy() { updateBack(false); super.onDestroy() }
    private fun dp(value: Int) = HoyiUi.dp(host, value)
    companion object {
        const val EXTRA_HISTORY_SOURCE = "historySource"
        const val SOURCE_JOURNAL = "journal"
        private const val EXPORT_HISTORY = 41
        private const val IMPORT_LEGACY = 42
        private const val EXPORT_JOURNAL = 43
    }
}
