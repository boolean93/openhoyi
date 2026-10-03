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
    private val onApplied: () -> Unit,
) {
    val button: Button
    var dialog: AlertDialog? = null
        private set
    init {
        val card = HoyiUi.card(activity, parent, activity.getString(R.string.language_title))
        button = HoyiUi.button(activity, card, currentLabel()) { choose() }
        HoyiUi.label(activity, card, activity.getString(R.string.language_hint), 14, muted = true)
    }
    private fun currentLabel() = activity.getString(R.string.language_current, preferences.current.nativeName)
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
