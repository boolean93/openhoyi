package io.openhoyi.mobile

import android.app.Dialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.RadioButton
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.fragment.app.DialogFragment
import java.util.Locale

internal fun ThemedActivity.showHistoryCupPicker(firstId: String? = null) {
    val fragments = supportFragmentManager
    if (isFinishing || fragments.isStateSaved || fragments.findFragmentByTag(HistoryCupPicker.TAG) != null) return
    HistoryCupPicker().apply {
        arguments = bundleOf(
            BrewComparisonActivity.FIRST_SHOT_ID to firstId,
            HistoryCupPicker.REPLACE to (this@showHistoryCupPicker is BrewComparisonActivity),
        )
    }.showNow(fragments, HistoryCupPicker.TAG)
}

/** The draft belongs to the dialog; Back and Cancel cannot change the host's comparison. */
class HistoryCupPicker : DialogFragment() {
    companion object {
        internal const val TAG = "history-cup-picker"
        internal const val REPLACE = "replaceSecond"
        private const val LIST_POSITION = "listPosition"
        private const val LIST_TOP = "listTop"
    }

    private val host get() = requireActivity() as ThemedActivity
    private val journal get() = (host.application as MobileApplication).journalResult.getOrNull()
    private lateinit var style: TaskProductVisualStyle
    private lateinit var title: TextView
    private lateinit var first: TextView
    private lateinit var listTitle: TextView
    private lateinit var list: ListView
    private lateinit var prompt: TextView
    private lateinit var confirm: Button
    private lateinit var adapter: CandidateAdapter
    private var draft = HistoryCupSelection()
    private var candidates = emptyList<BrewJournal.Observation>()
    private var previousId: String? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val firstId = if (savedInstanceState?.containsKey(BrewComparisonActivity.FIRST_SHOT_ID) == true)
            savedInstanceState.getString(BrewComparisonActivity.FIRST_SHOT_ID)
        else arguments?.getString(BrewComparisonActivity.FIRST_SHOT_ID)
        draft = HistoryCupSelection(firstId, savedInstanceState?.getString(BrewComparisonActivity.SECOND_SHOT_ID))
        style = TaskProductVisualStyle(host)
        val content = style.column(TAG).apply {
            val inset = style.dp(16)
            setPadding(inset, style.dp(12), inset, inset)
            background = style.shape(style.surface, outline = true)
        }
        title = style.text("", TaskProductVisualStyle.title).also {
            ViewCompat.setAccessibilityHeading(it, true)
        }
        val header = style.row().apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(style.button(Button(host)).apply {
            text = getString(android.R.string.cancel)
            tag = "history-cup-cancel"
            setOnClickListener { dismiss() }
        }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = style.dp(8) })
        style.add(content, header)
        first = style.text("", TaskProductVisualStyle.body, "history-cup-first").apply {
            background = style.shape(style.page, outline = true)
            setPadding(style.dp(12), style.dp(8), style.dp(12), style.dp(8))
        }
        style.add(content, first, top = 8)
        listTitle = style.text("", TaskProductVisualStyle.auxiliary, secondary = true)
        style.add(content, listTitle, top = 4)
        list = ListView(host).apply {
            tag = "history-cup-options"
            divider = ColorDrawable(style.border)
            dividerHeight = style.dp(1)
            isVerticalScrollBarEnabled = true
        }
        adapter = CandidateAdapter()
        list.adapter = adapter
        content.addView(list, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = style.dp(8) })

        prompt = style.text("", TaskProductVisualStyle.auxiliary, "history-cup-selection", secondary = true)
        ViewCompat.setAccessibilityLiveRegion(prompt, View.ACCESSIBILITY_LIVE_REGION_POLITE)
        val actions = style.row().apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }
        confirm = style.button(Button(host), primary = true).apply {
            tag = "history-cup-confirm"
            text = getString(if (arguments?.getBoolean(REPLACE) == true) R.string.history_cup_apply_second else R.string.history_cup_start)
            setOnClickListener { confirmPair() }
        }
        actions.addView(prompt, LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(confirm, LinearLayout.LayoutParams(-2, -2).apply { marginStart = style.dp(8) })
        style.add(content, actions, top = 8)
        render(observations())
        if (savedInstanceState != null) {
            list.setSelectionFromTop(savedInstanceState.getInt(LIST_POSITION), savedInstanceState.getInt(LIST_TOP))
        }
        return Dialog(host).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(content)
            setCanceledOnTouchOutside(true)
            window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                setDimAmount(.38f)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val config = host.resources.configuration
        val decor = host.findViewById<View>(android.R.id.content)
        val width = decor?.width?.takeIf { it > 0 } ?: style.dp(config.screenWidthDp)
        val height = decor?.height?.takeIf { it > 0 } ?: style.dp(config.screenHeightDp)
        dialog?.window?.setLayout(
            minOf(style.dp(700), width - style.dp(24)).coerceAtLeast(1),
            minOf(style.dp(570), height - style.dp(24)).coerceAtLeast(1),
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(BrewComparisonActivity.FIRST_SHOT_ID, draft.firstId)
        outState.putString(BrewComparisonActivity.SECOND_SHOT_ID, draft.secondId)
        if (::list.isInitialized) {
            outState.putInt(LIST_POSITION, list.firstVisiblePosition)
            outState.putInt(LIST_TOP, list.getChildAt(0)?.top ?: 0)
        }
    }

    private fun observations() = journal?.entries()?.map { it.observation }.orEmpty()

    private fun render(records: List<BrewJournal.Observation>) {
        draft = draft.reconcile(records)
        val base = records.firstOrNull { it.id == draft.firstId }
        val selected = records.firstOrNull { it.id == draft.secondId }
        previousId = draft.previousSameCurve(records)?.id
        candidates = if (records.size >= 2) draft.candidates(records) else emptyList()
        title.text = getString(if (base == null) R.string.history_cup_choose_first else R.string.history_cup_choose_second_title)
        first.text = when {
            journal == null -> getString(R.string.journal_unavailable)
            records.size < 2 -> getString(R.string.journal_compare_need_two)
            base == null && arguments?.getString(BrewComparisonActivity.FIRST_SHOT_ID) != null ->
                getString(R.string.history_cup_first_unavailable)
            base == null -> ""
            else -> listOfNotNull(
                getString(R.string.history_cup_first_mark) + " · " + host.journalCurve(base.curveId),
                host.journalDate(base.startedAtMs),
                host.historyCupReadings(base).takeUnless { resources.configuration.screenHeightDp < 440 },
            ).joinToString("\n")
        }
        first.visibility = if (first.text.isEmpty()) View.GONE else View.VISIBLE
        listTitle.text = getString(if (base == null) R.string.history_cup_from_journal else R.string.history_cup_choose_second)
        listTitle.visibility = if (records.size >= 2) View.VISIBLE else View.GONE
        prompt.text = when {
            selected != null -> getString(R.string.history_cup_chosen_second, host.journalDate(selected.startedAtMs))
            base != null && records.size >= 2 -> getString(R.string.history_cup_second_pending)
            else -> ""
        }
        prompt.visibility = if (prompt.text.isEmpty()) View.INVISIBLE else View.VISIBLE
        confirm.isEnabled = draft.confirmedPair(records) != null
        adapter.notifyDataSetChanged()
    }

    private fun select(id: String) {
        val records = observations()
        val oldFirst = draft.firstId
        draft = draft.choose(id, records)
        render(records)
        if (oldFirst != draft.firstId) list.setSelection(0)
    }

    private fun confirmPair() {
        val records = observations()
        val pair = draft.confirmedPair(records)
        if (pair == null) {
            render(records)
            return
        }
        confirm.isEnabled = false
        if (arguments?.getBoolean(REPLACE) == true && host is BrewComparisonActivity) {
            (host as BrewComparisonActivity).setHistoryCupPair(pair.first, pair.second)
        } else {
            host.startActivity(Intent(host, BrewComparisonActivity::class.java)
                .putExtra(BrewComparisonActivity.FIRST_SHOT_ID, pair.first)
                .putExtra(BrewComparisonActivity.SECOND_SHOT_ID, pair.second))
        }
        dismiss()
    }

    private inner class CandidateAdapter : BaseAdapter() {
        override fun getCount() = candidates.size
        override fun getItem(position: Int) = candidates[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = convertView as? CandidateRow ?: CandidateRow()
            row.bind(getItem(position))
            return row
        }
    }

    private inner class CandidateRow : LinearLayout(host) {
        private val radio = RadioButton(host).apply {
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            buttonTintList = ColorStateList.valueOf(style.accent)
        }
        private val name = style.text("", TaskProductVisualStyle.body.copy(weight = 500))
        private val date = style.text("", TaskProductVisualStyle.auxiliary, secondary = true)
        private val readings = style.text("", TaskProductVisualStyle.auxiliary, secondary = true)
        private val recommendation = style.text(getString(R.string.history_cup_previous), TaskProductVisualStyle.auxiliary).apply {
            setTextColor(style.accent)
        }
        init {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = style.dp(64)
            setPadding(style.dp(4), style.dp(6), style.dp(8), style.dp(6))
            addView(radio, LayoutParams(-2, -2))
            val metadata = HistoryToolbarRow(host, style.dp(12), style.dp(2)) {
                maxOf(
                    android.text.Layout.getDesiredWidth(name.text, name.paint),
                    android.text.Layout.getDesiredWidth(readings.text, readings.paint),
                ).toInt()
            }
            metadata.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            metadata.addView(style.column().apply {
                style.add(this, name)
                style.add(this, readings)
            })
            metadata.addView(style.column().apply {
                gravity = Gravity.END
                addView(date, LayoutParams(-2, -2))
                addView(recommendation, LayoutParams(-2, -2))
            })
            addView(metadata, LayoutParams(0, -2, 1f))
            isFocusable = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            background = RippleDrawable(ColorStateList.valueOf(style.selected), StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_selected), style.shape(style.selected))
                addState(intArrayOf(android.R.attr.state_focused), style.shape(style.page, outline = true))
                addState(intArrayOf(), style.shape(style.surface))
            }, null)
        }

        fun bind(shot: BrewJournal.Observation) {
            tag = "history-cup-option-${shot.id}"
            name.text = host.journalCurve(shot.curveId)
            date.text = host.journalDate(shot.startedAtMs)
            readings.text = host.historyCupReadings(shot)
            recommendation.visibility = if (shot.id == previousId) View.VISIBLE else View.GONE
            isSelected = shot.id == draft.secondId
            radio.isChecked = isSelected
            contentDescription = listOfNotNull(name.text, date.text, readings.text,
                recommendation.text.takeIf { recommendation.visibility == View.VISIBLE }).joinToString(" · ")
            setOnClickListener { select(shot.id) }
        }

        override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(info)
            info.className = RadioButton::class.java.name
            info.isCheckable = true
            info.isChecked = isSelected
        }
    }
}

internal fun ThemedActivity.historyCupTimingLabel(shot: BrewJournal.Observation?) =
    if (shot?.machineTimingInterrupted == true) R.string.history_pre_interruption_duration else R.string.history_duration

internal fun ThemedActivity.historyCupSeconds(seconds: Long?, signed: Boolean = false): String =
    seconds?.let { getString(R.string.history_duration_value, (if (signed && it > 0) "+" else "") + it) } ?: "—"

internal fun ThemedActivity.historyCupGrams(hundredths: Long?, signed: Boolean = false): String =
    hundredths?.let {
        getString(R.string.journal_grams, (if (signed && it > 0) "+" else "") + "%.2f".format(Locale.ROOT, it / 100.0))
    } ?: "—"

private fun ThemedActivity.historyCupReadings(shot: BrewJournal.Observation) = listOf(
    getString(historyCupTimingLabel(shot)) + " " + historyCupSeconds(shot.observedMachineElapsedSeconds?.toLong()),
    getString(R.string.history_end_scale) + " " + historyCupGrams(shot.weightHundredthsGram?.toLong()),
    journalStatus(shot.status).takeUnless { shot.status == "ENDED" },
).filterNotNull().joinToString(" · ")
