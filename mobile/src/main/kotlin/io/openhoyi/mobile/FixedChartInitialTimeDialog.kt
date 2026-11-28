package io.openhoyi.mobile

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import androidx.fragment.app.DialogFragment

/** An app preference editor; opening or cancelling never changes the selected time. */
internal class FixedChartInitialTimeDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val preferences = context.getSharedPreferences(FixedChartAxes.PREFERENCES, Context.MODE_PRIVATE)
        val visual = SettingsVisualStyle(context)
        val style = visual.palette
        val input = EditText(context).apply {
            id = R.id.fixed_chart_initial_time_input
            tag = "product/settings/chart/initial-time/input"
            contentDescription = getString(R.string.fixed_chart_initial_time)
            setSingleLine(true)
            // Keep the numeric keyboard without stripping signs or decimals from pasted input.
            setRawInputType(InputType.TYPE_CLASS_NUMBER)
            setText(FixedChartAxes.readInitialTimeSeconds(preferences).toString())
            setSelectAllOnFocus(true)
        }
        visual.input(input)
        val content = style.column().apply {
            setPadding(style.dp(24), style.dp(8), style.dp(24), style.dp(8))
            style.add(this, style.text(getString(R.string.fixed_chart_initial_time_positive_integer),
                TaskProductVisualStyle.auxiliary, secondary = true))
            style.add(this, input, 8)
        }
        val viewport = SettingsDialogViewport(context).apply { addView(content) }
        return AlertDialog.Builder(context)
            .setTitle(R.string.fixed_chart_initial_time)
            .setView(viewport)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.fixed_chart_initial_time_save, null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    visual.dialog(dialog, viewport)
                    AppDialog.styleShown(dialog)
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val seconds = input.text.toString().trim().toIntOrNull()
                        if (seconds == null || seconds <= 0) {
                            input.error = getString(R.string.fixed_chart_initial_time_positive_integer)
                            return@setOnClickListener
                        }
                        preferences.edit().putInt(FixedChartAxes.INITIAL_TIME_SECONDS_KEY, seconds).apply()
                        parentFragmentManager.setFragmentResult(TAG, Bundle.EMPTY)
                        dismiss()
                    }
                }
            }
    }

    companion object { const val TAG = "fixed-chart-initial-time" }
}
