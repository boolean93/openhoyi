package io.openhoyi.mobile

import android.app.Dialog
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.DialogFragment

internal data class ManualLiveSamples(val points: List<ShotPoint>, val unavailable: Set<ChartMetric>)

/** Opaque current-window layer. Neither the underlying form nor device commands are accessible here. */
class ManualLiveDialog : DialogFragment() {
    internal var observationId = ""
        private set
    private var chart: ExtractionOverlayView? = null
    private var status: TextView? = null
    private var selector: ChartSeriesSelector? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        observationId = savedInstanceState?.getString(OBSERVATION) ?: requireArguments().getString(OBSERVATION).orEmpty()
        // Android may restore a Fragment after process death; the process-local signal does not
        // survive that boundary. Do not briefly restore a stale or empty foreground dialog.
        if (savedInstanceState != null &&
            (requireActivity().application as MobileApplication).manualShotPresentation.activeId != observationId)
            showsDialog = false
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        Dialog(requireContext(), android.R.style.Theme_Material_NoActionBar).apply {
            setCanceledOnTouchOutside(false)
            window?.apply {
                addFlags(WindowManager.LayoutParams.FLAG_SECURE or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
                setBackgroundDrawable(ColorDrawable(TaskProductVisualStyle(requireContext()).page))
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        if (!showsDialog) return null
        val context = requireContext()
        val visual = ExtractionHistoryVisualStyle(context)
        val style = visual.palette
        val body = LinearLayout(context).apply {
            tag = "product/manual-live"
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(style.page)
        }
        val padding = style.dp(12)
        ViewCompat.setOnApplyWindowInsetsListener(body) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(padding + bars.left, padding + bars.top, padding + bars.right, padding + bars.bottom)
            insets
        }
        body.setPadding(padding, padding, padding, padding)
        val heading = LinearLayout(context).apply {
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val titles = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val title = style.text(getString(R.string.extraction_manual_label), TaskProductVisualStyle.section)
        ViewCompat.setAccessibilityHeading(title, true)
        titles.addView(title, LinearLayout.LayoutParams(-1, -2))
        status = style.text(getString(R.string.extraction_live_data), TaskProductVisualStyle.auxiliary).also {
            titles.addView(it, LinearLayout.LayoutParams(-1, -2))
        }
        heading.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        heading.addView(Button(context).apply {
            tag = "product/manual-live/close"
            text = getString(R.string.chart_series_close)
            style.button(this, outline = true)
            minimumWidth = style.dp(48)
            minHeight = style.dp(48)
            setOnClickListener { dismiss() }
        }, LinearLayout.LayoutParams(-2, -2))
        body.addView(heading, LinearLayout.LayoutParams(-1, -2))
        chart = ExtractionOverlayView(context, visual, allowSampleInspection = false).apply {
            tag = "product/manual-live/chart"
            liveCompactChrome = true
            compactRunningExplanation = true
            setBackgroundColor(style.surface)
        }.also { liveChart ->
            selector = ChartSeriesSelector(liveChart, secure = true).also {
                heading.addView(it.button, 1, LinearLayout.LayoutParams(-2, -2))
            }
            body.addView(liveChart, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        return body
    }

    override fun onStart() {
        super.onStart()
        if (!showsDialog) { dismissAllowingStateLoss(); return }
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    internal fun render(id: String, frame: ManualLiveSamples) {
        observationId = id
        setStatus(R.string.extraction_state_running)
        chart?.apply {
            traceIdentity = id // Keep the axes stable when the service rebinds to a history ID.
            unavailableLiveMetrics = frame.unavailable
            val aligned = ExtractionReference.align(frame.points).points
            if (points != aligned) points = aligned
        }
        selector?.refreshReadings()
    }

    internal fun finishObservation(terminal: ManualShotPresentationGate.Terminal?) {
        setStatus(if (terminal?.outcome == ManualShotPresentationGate.Outcome.ENDED)
            R.string.extraction_state_ended else R.string.extraction_state_unknown)
        chart?.apply {
            unavailableLiveMetrics = ChartMetric.entries.toSet()
            terminal?.points?.takeIf { it.isNotEmpty() }?.let {
                val aligned = ExtractionReference.align(it).points
                if (points != aligned) points = aligned
            }
        }
        selector?.refreshReadings()
    }

    private fun setStatus(res: Int) {
        val label = getString(res)
        status?.takeUnless { it.text == label }?.text = label
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(OBSERVATION, observationId)
        super.onSaveInstanceState(outState)
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        (activity as? ThemedActivity)?.manualLiveDialogClosed()
    }

    override fun onStop() {
        selector?.close()
        super.onStop()
    }

    override fun onDestroyView() {
        selector?.close()
        selector = null
        chart = null
        status = null
        super.onDestroyView()
    }

    companion object {
        internal const val TAG = "manual-live"
        private const val OBSERVATION = "manualObservation"

        internal fun open(id: String) = ManualLiveDialog().apply {
            arguments = Bundle().apply { putString(OBSERVATION, id) }
        }
    }
}
