package io.openhoyi.mobile

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Native extraction outcomes with separate legacy import actions. */
class HistoryFragment : androidx.fragment.app.Fragment() {
    private var toolsDialog: android.app.AlertDialog? = null
    private val host get() = requireActivity() as MainActivity
    private fun getColor(id:Int)=requireContext().getColor(id)
    private val RESULT_OK=android.app.Activity.RESULT_OK
    private val application get() = host.application
    private val contentResolver get() = host.contentResolver
    private val pageHost by lazy { android.widget.FrameLayout(host) }
    private val visual by lazy { ExtractionHistoryVisualStyle(host) }
    private val style get() = visual.palette
    private data class RowViews(val day: TextView, val title: TextView, val status: TextView,
        val meta: LinearLayout, val chunks: List<TextView>, val taste: TextView)
    private lateinit var adapter: ArrayAdapter<ShotHistory.Entry>
    private lateinit var historyList: ListView
    private val listStates = mutableMapOf<Boolean, android.os.Parcelable>()
    private lateinit var count: TextView
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
    private var restoreDetail = false
    private var compact = true
    private var backCallback: android.window.OnBackInvokedCallback? = null
    private val journalActions = mutableListOf<Button>()

    override fun onCreateView(inflater: android.view.LayoutInflater, container: android.view.ViewGroup?, savedInstanceState: Bundle?): View {
        showJournal = requestedSource ?: savedInstanceState?.getBoolean("showJournal")
            ?: false
        requestedSource = null
        savedInstanceState?.getParcelable<android.os.Parcelable>("localList")?.let { listStates[false] = it }
        savedInstanceState?.getParcelable<android.os.Parcelable>("journalList")?.let { listStates[true] = it }
        savedInstanceState?.getString("localSelection")?.let { selectedIds[false] = it }
        savedInstanceState?.getString("journalSelection")?.let { selectedIds[true] = it }
        restoreDetail = savedInstanceState?.getBoolean("detailRequested") ?: false
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
        updateBack(false)
        journalActions.clear()
        val (root,body) = style.fragmentPage(host, getString(R.string.home_history))
        pageHost.removeAllViews();pageHost.addView(root)
        val header = (body.tag as BeanPageRegions).header
        header.removeViewAt(0)
        header.minimumHeight = dp(48)
        (header.layoutParams as LinearLayout.LayoutParams).bottomMargin = 0
        count = style.text("", TaskProductVisualStyle.auxiliary, "product/history/count", true).also {
            header.addView(it, 0, LinearLayout.LayoutParams(0, -2, 1f))
        }
        val sourcesIndex = body.childCount
        HoyiUi.tabs(host, body, listOf(getString(R.string.history_local_tab), getString(R.string.journal_tab), getString(R.string.history_legacy_tab)), selected = if (showJournal) 1 else 0) {
            if (it == 2) startActivity(Intent(host, LegacyHistoryActivity::class.java))
            else { rememberList(); showJournal = it == 1; restoreDetail = false; buildScreen(); render() }
        }
        val sources = body.getChildAt(sourcesIndex)
        fun styleSources(view: View) {
            if (view is Button) style.button(view)
            else if (view is TextView) {
                style.apply(view, TaskProductVisualStyle.auxiliary)
                view.gravity = android.view.Gravity.CENTER
                view.minHeight = dp(48); view.minimumHeight = dp(48)
                view.setTextColor(if (view.isSelected) style.accent else style.muted)
                view.background = style.shape(if (view.isSelected) style.selected else style.surface)
            }
            if (view is android.view.ViewGroup) (0 until view.childCount).forEach { styleSources(view.getChildAt(it)) }
        }
        styleSources(sources)
        val actions = LinearLayout(host).apply { orientation = LinearLayout.HORIZONTAL }
        actions.orientation = LinearLayout.VERTICAL
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
        if (showJournal) action(getString(R.string.journal_compare)) { startActivity(Intent(host, BrewComparisonActivity::class.java)) }
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
        header.addView(ioMenu, LinearLayout.LayoutParams(dp(48), dp(48)))
        val list = ListView(host).apply {
            divider = android.graphics.drawable.ColorDrawable(style.border)
            dividerHeight = dp(1)
        }
        adapter = object : ArrayAdapter<ShotHistory.Entry>(host, android.R.layout.simple_list_item_1, mutableListOf()) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val card = convertView as? LinearLayout ?: LinearLayout(host).apply {
                    orientation = LinearLayout.VERTICAL
                    minimumHeight = dp(64)
                    setPadding(dp(16), dp(8), dp(16), dp(8))
                    background = style.shape(style.surface, radius = 0)
                    val day = HoyiUi.label(host, this, "", 14, true).apply {
                        setTextColor(getColor(R.color.mobile_accent))
                        setPadding(0, 0, 0, dp(8))
                    }
                    val header = LinearLayout(host).apply {
                        orientation = LinearLayout.HORIZONTAL
                        isBaselineAligned = false
                        gravity = android.view.Gravity.CENTER_VERTICAL
                    }
                    addView(header)
                    val title = TextView(host).apply {
                        textSize = 17f
                        typeface = TaskTypography.face(host, 500)
                        includeFontPadding = false
                        setTextColor(getColor(R.color.mobile_text))
                        maxLines = 2
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    }
                    val identity = style.column()
                    header.addView(identity, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(16) })
                    identity.addView(title, LinearLayout.LayoutParams(-1, -2))
                    val state = TextView(host).apply {
                        textSize = 14f
                        typeface = TaskTypography.face(host, 400)
                        includeFontPadding = false
                    }
                    header.addView(state, LinearLayout.LayoutParams(-2, -2))
                    val meta = style.column().apply {
                        setPadding(0, dp(8), 0, 0)
                    }
                    identity.addView(meta)
                    val flow = HistoryVisualFlow(host, dp(8), dp(8))
                    meta.addView(flow, LinearLayout.LayoutParams(-1, -2))
                    val chunks = (0..3).map {
                        style.text("", TaskProductVisualStyle.auxiliary, secondary = true).also { flow.addView(it) }
                    }
                    val taste = style.text("", TaskProductVisualStyle.auxiliary, secondary = true)
                    meta.addView(taste, LinearLayout.LayoutParams(-1, -2))
                    tag = RowViews(day, title, state, meta, chunks, taste)
                }
                val entry = requireNotNull(getItem(position))
                card.setBackgroundColor(if (selectedIds[showJournal] == entry.id) style.selected else android.graphics.Color.TRANSPARENT)
                val views = card.tag as RowViews
                style.apply(views.day, TaskProductVisualStyle.auxiliary, true)
                style.apply(views.title, TaskProductVisualStyle.action)
                style.apply(views.status, TaskProductVisualStyle.auxiliary)
                (views.chunks + views.taste).forEach { style.apply(it, TaskProductVisualStyle.auxiliary, true) }
                views.title.tag = "product/history/list/${entry.id}/name"
                views.status.tag = "product/history/list/${entry.id}/status"
                views.meta.tag = "product/history/list/${entry.id}/metadata"
                views.chunks.forEachIndexed { index, view -> view.tag = "product/history/list/${entry.id}/metadata/$index" }
                views.taste.tag = "product/history/list/${entry.id}/metadata/taste"
                val day = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(entry.startedAtMs))
                val previousDay = if (position > 0) getItem(position - 1)?.let {
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it.startedAtMs))
                } else null
                views.day.text = day
                views.day.visibility = if (day != previousDay) View.VISIBLE else View.GONE
                val library = runCatching { (application as MobileApplication).curves }.getOrNull()
                views.title.text = if (entry.curveId == "manual") getString(R.string.extraction_manual_label)
                    else library?.find(entry.curveId)?.name ?: entry.curveId
                views.status.text = status(entry.status)
                views.status.setTextColor(if (entry.status == ShotHistory.Status.UNKNOWN) visual.warningInk else style.accent)
                val metadata = listOf(date(entry.startedAtMs),
                    entry.elapsedMs?.let { getString(R.string.history_meta_duration, "%.1f".format(Locale.ROOT, it / 1000.0)) },
                    entry.weightHundredthsGram?.let { getString(R.string.history_meta_scale, "%.2f".format(Locale.ROOT, it / 100.0)) },
                    entry.slot?.takeIf { it in 1..5 }?.let { getString(R.string.extraction_slot_suffix, it.toString()) })
                views.chunks.forEachIndexed { index, view ->
                    // Whole semantic chunks flow between rows; keep Latin numeric units bound together within a chunk.
                    view.text = metadata[index]?.replace(' ', '\u00a0').orEmpty()
                    view.visibility = if (metadata[index] == null) View.GONE else View.VISIBLE
                }
                val taste = if (showJournal) (application as MobileApplication).journalResult.getOrNull()?.find(entry.id)?.notes?.taste?.takeIf { it.isNotBlank() } else null
                views.taste.text = taste?.let { getString(R.string.journal_field_value, getString(R.string.journal_taste), it) }.orEmpty()
                views.taste.visibility = if (taste == null) View.GONE else View.VISIBLE
                return card
            }
        }
        historyList = list
        list.adapter = adapter
        collection = TaskCollectionLayout(host)
        body.addView(collection, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(8) })
        // Source controls belong to the record browser, not above the selected chart.
        listOf(sources, count).forEach { control ->
            body.removeView(control)
            collection.browserBody.addView(control, LinearLayout.LayoutParams(-1, -2))
        }
        val back = style.button(HoyiUi.button(host, collection.detailBody, getString(R.string.history_back_to_list)) { showList() })
        collection.onPresentationChanged = { split, visible ->
            compact = !split
            back.visibility = if (compact) View.VISIBLE else View.GONE
            updateBack(compact && visible)
        }
        collection.detailRequested = restoreDetail
        detailContent = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
        collection.detailBody.addView(detailContent, LinearLayout.LayoutParams(-1, 0, 1f))
        val content = FrameLayout(host)
        collection.browserBody.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        content.addView(list, FrameLayout.LayoutParams(-1, -1))
        val emptyHolder = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
        }
        content.addView(emptyHolder, FrameLayout.LayoutParams(-1, -1))
        val emptyCard = HoyiUi.card(host, emptyHolder)
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
        list.emptyView = emptyHolder
        listStates[showJournal]?.let { list.onRestoreInstanceState(it) }
        list.setOnItemClickListener { _, _, position, _ ->
            rememberList()
            selectedIds[showJournal] = rows[position].id
            collection.detailRequested = true
            renderSelection()
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
        val position = if (historyList.isShown && historyList.childCount > 0) historyList.onSaveInstanceState() else null
        if (showJournal) {
            val journal = (application as MobileApplication).journalResult.getOrNull()
            rows = journal?.entries()?.map { it.observation.historySummary() }.orEmpty()
            journalActions.forEach { it.isEnabled = journal != null }
            count.text = if (journal == null) getString(R.string.journal_unavailable) else getString(R.string.journal_count, rows.size)
            emptyTitle.text = getString(if (journal == null) R.string.journal_unavailable else R.string.journal_empty)
            emptyMessage.text = getString(if (journal == null) R.string.journal_unavailable else R.string.journal_empty_hint)
            emptyAction.text = getString(R.string.journal_migrate)
            emptyAction.visibility = if (journal == null) View.GONE else View.VISIBLE
            adapter.clear(); adapter.addAll(rows)
            (listStates[showJournal] ?: position)?.let { historyList.onRestoreInstanceState(it) }
            renderSelection()
            return
        }
        val history = runCatching { (application as MobileApplication).history }.getOrNull()
        if (history == null) {
            count.text = getString(R.string.history_unavailable); rows = emptyList()
            emptyTitle.text = getString(R.string.history_unavailable)
            emptyMessage.text = getString(R.string.history_unavailable_message)
            emptyAction.visibility = View.GONE
        } else {
            rows = history.entries
            count.text = if (rows.isEmpty()) getString(R.string.history_no_local) else getString(R.string.history_count, rows.size.toString())
            emptyTitle.text = getString(R.string.history_empty_title)
            emptyMessage.text = getString(R.string.history_empty_message)
            emptyAction.visibility = View.VISIBLE
        }
        adapter.clear()
        adapter.addAll(rows)
        (listStates[showJournal] ?: position)?.let { historyList.onRestoreInstanceState(it) }
        renderSelection()
    }
    private fun renderSelection() {
        detailContent.removeAllViews()
        detailContent.tag = null
        val selected = rows.firstOrNull { it.id == selectedIds[showJournal] }
        if (selected == null) {
            selectedIds.remove(showJournal)
            collection.detailRequested = false
            HoyiUi.label(host, detailContent, getString(R.string.history_select_record), 18)
            return
        }
        detailContent.tag = "history-selection-${selected.id}"
        host.renderHistoryDetail(detailContent, selected.id, openFullPage = {
            startActivity(Intent(host, HistoryDetailActivity::class.java).putExtra("shotId", selected.id))
        }, visual = visual)
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
    private fun status(value: ShotHistory.Status) = when (value) {
        ShotHistory.Status.STARTING -> getString(R.string.extraction_state_starting)
        ShotHistory.Status.RUNNING -> getString(R.string.extraction_state_running)
        ShotHistory.Status.STOP_REQUESTED -> getString(R.string.history_status_stop_requested)
        ShotHistory.Status.ENDED -> getString(R.string.history_status_ended)
        ShotHistory.Status.UNKNOWN -> getString(R.string.extraction_state_unknown)
        ShotHistory.Status.NOT_STARTED -> getString(R.string.history_status_not_started)
    }
    private fun date(epochMs: Long) = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date(epochMs))
    private fun dp(value: Int) = HoyiUi.dp(host, value)
    companion object {
        const val EXTRA_HISTORY_SOURCE = "historySource"
        const val SOURCE_JOURNAL = "journal"
        private const val EXPORT_HISTORY = 41
        private const val IMPORT_LEGACY = 42
        private const val EXPORT_JOURNAL = 43
    }
}
