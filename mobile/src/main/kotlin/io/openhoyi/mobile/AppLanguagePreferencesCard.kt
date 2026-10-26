package io.openhoyi.mobile

import android.app.Activity
import android.app.AlertDialog
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.view.View

/** Local language storage and display only; the Activity owns its notification/rebuild callback. */
internal class AppLanguagePreferencesCard(
    private val activity: Activity,
    parent: LinearLayout,
    private val preferences: AppLanguagePreference = (activity.application as MobileApplication).languagePreferences,
    private val embedded: Boolean = false,
    private val visual: SettingsVisualStyle? = null,
    private val onApplied: () -> Unit,
) {
    val button: Button
    var dialog: AlertDialog? = null
        private set
    internal var dialogError: TextView? = null
        private set
    init {
        val card = if (embedded) parent else HoyiUi.card(activity, parent, activity.getString(R.string.language_title))
        if (visual != null) {
            val style=visual.palette
            val words=style.column()
            style.add(words,style.text(activity.getString(R.string.language_title),TaskProductVisualStyle.body))
            style.add(words,style.text(activity.getString(R.string.language_hint),TaskProductVisualStyle.auxiliary,secondary=true),8)
            button=visual.action(android.widget.Button(activity).apply {text=currentLabel();setOnClickListener {choose()}},icon="chevron",atEnd=true)
                .apply {tag="product/settings/app/language/current";contentDescription=activity.getString(R.string.language_current,preferences.current.nativeName)}
            style.add(card,visual.pair(words,button,"product/settings/app/language/row"))
        } else {
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
    }
    private fun currentLabel() = if (embedded) preferences.current.nativeName
        else activity.getString(R.string.language_current, preferences.current.nativeName)
    private fun choose() {
        close()
        val choices = AppLanguage.entries
        val error = visual?.palette?.let { style ->
            style.text("",TaskProductVisualStyle.auxiliary,"product/settings/app/language/error").apply {
                setTextColor(activity.getColor(R.color.mobile_danger))
                visibility=View.GONE
                accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE
            }
        }
        val errorContainer=visual?.palette?.column()?.apply {visibility=View.GONE}
        val selected=android.content.DialogInterface.OnClickListener { value, index ->
                when (preferences.select(choices[index])) {
                    AppLanguagePreference.Selection.APPLIED -> {
                        error?.apply {text="";visibility=View.GONE};errorContainer?.visibility=View.GONE
                        value.dismiss()
                        button.text = currentLabel()
                        button.contentDescription = activity.getString(R.string.language_current, preferences.current.nativeName)
                        onApplied()
                    }
                    AppLanguagePreference.Selection.UNCHANGED -> {
                        error?.apply {text="";visibility=View.GONE};errorContainer?.visibility=View.GONE
                        value.dismiss()
                    }
                    AppLanguagePreference.Selection.SAVE_FAILED -> {
                        (value as AlertDialog).listView.setItemChecked(choices.indexOf(preferences.current), true)
                        if(error==null)Toast.makeText(activity, R.string.language_save_failed, Toast.LENGTH_LONG).show()
                        else {error.text=activity.getString(R.string.language_save_failed);error.visibility=View.VISIBLE;errorContainer?.visibility=View.VISIBLE}
                    }
                }
            }
        val builder=AlertDialog.Builder(activity).setTitle(R.string.language_title)
        if(visual==null)builder.setSingleChoiceItems(choices.map {it.nativeName}.toTypedArray(),choices.indexOf(preferences.current),selected)
        else {
            val adapter=object:android.widget.ArrayAdapter<String>(activity,android.R.layout.simple_list_item_single_choice,choices.map {it.nativeName}) {
                override fun getView(position:Int,convertView:android.view.View?,parent:android.view.ViewGroup):android.view.View =
                    super.getView(position,convertView,parent).also {view->
                        visual.palette.apply(view as android.widget.TextView,TaskProductVisualStyle.body)
                        view.minHeight=visual.palette.dp(52);view.minimumHeight=view.minHeight
                        view.setPadding(visual.palette.dp(16),visual.palette.dp(8),visual.palette.dp(16),visual.palette.dp(8))
                        view.layoutParams=(view.layoutParams ?: android.view.ViewGroup.LayoutParams(-1,-2)).apply {height=-2}
                        (view as? android.widget.CheckedTextView)?.checkMarkTintList=android.content.res.ColorStateList.valueOf(visual.palette.accent)
                    }
            }
            builder.setSingleChoiceItems(adapter,choices.indexOf(preferences.current),selected)
            visual.palette.add(requireNotNull(errorContainer),requireNotNull(error),8)
            builder.setView(errorContainer)
        }
        val chooser=builder.setNegativeButton(R.string.machine_settings_cancel,null).create()
        chooser.setOnDismissListener { if (dialog === chooser) {dialog = null;dialogError=null};error?.apply {text="";visibility=View.GONE};errorContainer?.visibility=View.GONE }
        dialog = chooser
        dialogError=error
        AppDialog.show(chooser)
        visual?.dialog(chooser)
        AppDialog.styleShown(chooser)
    }
    fun close() { dialog?.dismiss(); dialog = null;dialogError=null }
}
