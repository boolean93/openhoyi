package io.openhoyi.mobile

import android.app.Activity
import android.app.AlertDialog
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast

/** Local language storage and display only; the Activity owns its notification/rebuild callback. */
internal class AppLanguagePreferencesCard(
    private val activity: Activity,
    parent: LinearLayout,
    private val preferences: AppLanguagePreference = (activity.application as MobileApplication).languagePreferences,
    private val embedded: Boolean = false,
    private val onApplied: () -> Unit,
) {
    val button: Button
    var dialog: AlertDialog? = null
        private set
    init {
        val card = if (embedded) parent else HoyiUi.card(activity, parent, activity.getString(R.string.language_title))
        val row = if (embedded) LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL
            isBaselineAligned = false
            card.addView(this, LinearLayout.LayoutParams(-1, -2))
            HoyiUi.label(activity, this, activity.getString(R.string.language_title), TaskVisualTokens.BODY_SP).apply {
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }
        } else card
        button = HoyiUi.button(activity, row, currentLabel()) { choose() }.apply {
            if (embedded) {
                asTaskTextAction()
                maxWidth = HoyiUi.dp(activity, 240)
                layoutParams = LinearLayout.LayoutParams(-2, -2)
            }
            contentDescription = activity.getString(R.string.language_current, preferences.current.nativeName)
        }
        HoyiUi.label(activity, card, activity.getString(R.string.language_hint), 14, muted = true)
    }
    private fun currentLabel() = if (embedded) preferences.current.nativeName
        else activity.getString(R.string.language_current, preferences.current.nativeName)
    private fun choose() {
        close()
        val choices = AppLanguage.entries
        val chooser = AlertDialog.Builder(activity)
            .setTitle(R.string.language_title)
            .setSingleChoiceItems(choices.map { it.nativeName }.toTypedArray(), choices.indexOf(preferences.current)) { value, index ->
                when (preferences.select(choices[index])) {
                    AppLanguagePreference.Selection.APPLIED -> {
                        value.dismiss()
                        button.text = currentLabel()
                        button.contentDescription = activity.getString(R.string.language_current, preferences.current.nativeName)
                        onApplied()
                    }
                    AppLanguagePreference.Selection.UNCHANGED -> value.dismiss()
                    AppLanguagePreference.Selection.SAVE_FAILED -> {
                        (value as AlertDialog).listView.setItemChecked(choices.indexOf(preferences.current), true)
                        Toast.makeText(activity, R.string.language_save_failed, Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton(R.string.machine_settings_cancel, null).create()
        chooser.setOnDismissListener { if (dialog === chooser) dialog = null }
        dialog = chooser
        chooser.show()
    }
    fun close() { dialog?.dismiss(); dialog = null }
}
