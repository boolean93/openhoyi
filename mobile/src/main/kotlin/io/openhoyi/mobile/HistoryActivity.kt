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
class HistoryActivity : ThemedActivity() {
    private data class RowViews(val day: TextView, val title: TextView, val status: TextView, val meta: TextView)
    private lateinit var adapter: ArrayAdapter<ShotHistory.Entry>
    private lateinit var historyList: ListView
    private val listStates = mutableMapOf<Boolean, android.os.Parcelable>()
    private lateinit var count: TextView
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
    private var backCallback: android.window.OnBackInvokedCallback? = null
    private val journalActions = mutableListOf<Button>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showJournal = savedInstanceState?.getBoolean("showJournal") ?: false
        savedInstanceState?.getParcelable<android.os.Parcelable>("localList")?.let { listStates[false] = it }
        savedInstanceState?.getParcelable<android.os.Parcelable>("journalList")?.let { listStates[true] = it }
        savedInstanceState?.getString("localSelection")?.let { selectedIds[false] = it }
        savedInstanceState?.getString("journalSelection")?.let { selectedIds[true] = it }
        restoreDetail = savedInstanceState?.getBoolean("detailRequested") ?: false
        buildScreen()
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
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.mobile_background))
            setOnApplyWindowInsetsListener { view, insets ->
                val area = if (Build.VERSION.SDK_INT >= 30) {
                    val x = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                    intArrayOf(x.left, x.top, x.right, x.bottom)
                } else {
                    @Suppress("DEPRECATION")
                    intArrayOf(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                        insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                }
                view.setPadding(area[0], area[1], area[2], area[3]); insets
            }
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        HoyiUi.navigation(this, root, HistoryActivity::class.java)
        setContentView(root)
        HoyiUi.header(this, body, getString(R.string.home_history))
        count = HoyiUi.label(this, body, "", 14, muted = true)
        HoyiUi.tabs(this, body, listOf(getString(R.string.history_local_tab), getString(R.string.journal_tab), getString(R.string.history_legacy_tab)), selected = if (showJournal) 1 else 0) {
            if (it == 2) startActivity(Intent(this, LegacyHistoryActivity::class.java))
            else { rememberList(); showJournal = it == 1; restoreDetail = false; buildScreen(); render() }
        }
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        body.addView(actions)
        fun action(title: String, click: () -> Unit) {
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            actions.addView(box, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) })
            HoyiUi.button(this, box, title, action = click).also { if (showJournal) journalActions += it }
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
        if (showJournal) action(getString(R.string.journal_compare)) { startActivity(Intent(this, BrewComparisonActivity::class.java)) }
        val list = ListView(this).apply {
            divider = android.graphics.drawable.ColorDrawable(getColor(R.color.mobile_border))
            dividerHeight = dp(1)
        }
        adapter = object : ArrayAdapter<ShotHistory.Entry>(this, android.R.layout.simple_list_item_1, mutableListOf()) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val card = convertView as? LinearLayout ?: LinearLayout(this@HistoryActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    minimumHeight = dp(64)
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    background = HoyiUi.shape(this@HistoryActivity, R.color.mobile_surface, 0)
                    val day = HoyiUi.label(this@HistoryActivity, this, "", 14, true).apply {
                        setTextColor(getColor(R.color.mobile_accent))
                        setPadding(0, dp(6), 0, dp(10))
                    }
                    val header = LinearLayout(this@HistoryActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                    }
                    addView(header)
                    val title = TextView(this@HistoryActivity).apply {
                        textSize = 17f
                        setTextColor(getColor(R.color.mobile_text))
                        maxLines = 2
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    }
                    header.addView(title, LinearLayout.LayoutParams(-1, -2))
                    val state = TextView(this@HistoryActivity).apply { textSize = 14f }
                    header.addView(state, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
                    val meta = TextView(this@HistoryActivity).apply {
                        textSize = 14f
                        setTextColor(getColor(R.color.mobile_muted))
                        setPadding(0, dp(8), 0, 0)
                    }
                    addView(meta)
                    tag = RowViews(day, title, state, meta)
                }
                val entry = requireNotNull(getItem(position))
                card.setBackgroundColor(getColor(if (selectedIds[showJournal] == entry.id) R.color.mobile_accent_soft else R.color.mobile_surface))
                val views = card.tag as RowViews
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
                views.status.setTextColor(getColor(if (entry.status == ShotHistory.Status.UNKNOWN)
                    R.color.mobile_danger else R.color.mobile_accent))
                views.meta.text = buildString {
                    append(date(entry.startedAtMs))
                    entry.elapsedMs?.let { append(getString(R.string.history_meta_duration, "%.1f".format(Locale.ROOT, it / 1000.0))) }
                    entry.weightHundredthsGram?.let { append(getString(R.string.history_meta_scale, "%.2f".format(Locale.ROOT, it / 100.0))) }
                    entry.slot?.takeIf { it in 1..5 }?.let { append(getString(R.string.extraction_slot_suffix, it.toString())) }
                }
                if (showJournal) (application as MobileApplication).journalResult.getOrNull()?.find(entry.id)?.notes?.taste?.takeIf { it.isNotBlank() }?.let {
                    views.meta.append("\n" + getString(R.string.journal_field_value, getString(R.string.journal_taste), it))
                }
                return card
            }
        }
        historyList = list
        list.adapter = adapter
        collection = TaskCollectionLayout(this)
        body.addView(collection, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(12) })
        val back = HoyiUi.button(this, collection.detailBody, getString(R.string.history_back_to_list)) { showList() }
        collection.onPresentationChanged = { split, visible ->
            compact = !split
            back.visibility = if (compact) View.VISIBLE else View.GONE
            updateBack(compact && visible)
        }
        collection.detailRequested = restoreDetail
        detailContent = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        collection.detailBody.addView(detailContent, LinearLayout.LayoutParams(-1, 0, 1f))
        val content = FrameLayout(this)
        collection.browserBody.addView(content, LinearLayout.LayoutParams(-1, -1))
        content.addView(list, FrameLayout.LayoutParams(-1, -1))
        val emptyHolder = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
        }
        content.addView(emptyHolder, FrameLayout.LayoutParams(-1, -1))
        val emptyCard = HoyiUi.card(this, emptyHolder)
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
    override fun onStart() { super.onStart(); render() }

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
            HoyiUi.label(this, detailContent, getString(R.string.history_select_record), 18)
            return
        }
        detailContent.tag = "history-selection-${selected.id}"
        HoyiUi.button(this, detailContent, getString(R.string.ui_full_details)) {
            startActivity(Intent(this, HistoryDetailActivity::class.java).putExtra("shotId", selected.id))
        }
        renderHistoryDetail(detailContent, selected.id)
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
    override fun onStop() { rememberList(); super.onStop() }
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
    private fun dp(value: Int) = HoyiUi.dp(this, value)
    private companion object { const val EXPORT_HISTORY = 41; const val IMPORT_LEGACY = 42; const val EXPORT_JOURNAL = 43 }
}
