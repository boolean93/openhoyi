package io.openhoyi.mobile

import android.app.Activity
import android.app.AlertDialog
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.SystemClock
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.View
import android.view.ContextThemeWrapper
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.TextView
import androidx.core.graphics.ColorUtils

/** Actual settings/preview window and configuration restoration. Disposable Mock emulator only. */
internal class ThemePaletteRuntimeChecks(private val test: Instrumentation) {
    private val context get() = test.targetContext
    private val app get() = context.applicationContext as MobileApplication
    private val preferences get() = app.appearancePreferences
    private val store get() = context.getSharedPreferences(SharedAppearanceStorage.PREFERENCES, Context.MODE_PRIVATE)
    private fun <T> main(action: () -> T) = BoundedInstrumentationMain.run("theme palette", action = action)

    fun run() {
        check(BuildConfig.MOCK_MODE && context.packageName == "io.openhoyi.mobile.mock" &&
            Build.HARDWARE in setOf("ranchu", "goldfish")) { "Palette fixtures require the disposable Mock emulator" }
        val original = store.all.toMap()
        try {
            verifyStorageAndTokens()
            settingsPreviewAndApply()
            FontSizeRuntimeChecks(test).runPaletteRecreation()
            aiDraftAndBackStack()
        } finally {
            main { restoreMockPreferences(store, original) }
        }
    }

    private fun verifyStorageAndTokens() = main {
        val dark = SharedAppearanceStorage.DARK
        val key = SharedAppearanceStorage.PALETTE
        check(store.edit().putBoolean(dark, true).remove(key).commit())
        check(preferences.current == AppAppearance(dark = true))
        check(store.edit().putString(key, "unknown-palette").commit())
        check(preferences.current == AppAppearance(dark = true))
        var commits = 0
        val failing = AppAppearancePreference(SharedAppearanceStorage(store) { edit ->
            check(edit.commit()) // SharedPreferences memory changed even when commit reports failure.
            if (++commits == 1) {
                check(store.getString(key, null) == AppThemePalette.COFFEE.key)
                false
            } else true
        })
        check(failing.selectPalette(AppThemePalette.COFFEE) == AppAppearancePreference.Selection.SAVE_FAILED)
        check(commits == 2 && store.getString(key, null) == "unknown-palette" && preferences.current == AppAppearance(true))
        check(store.edit().putBoolean(dark, false).remove(key).commit())
        fun contrast(foreground: Int, background: Int, required: Double) {
            check(ColorUtils.calculateContrast(foreground, background) >= required) {
                "Insufficient contrast: ${foreground.toUInt().toString(16)} on ${background.toUInt().toString(16)}"
            }
        }
        for (night in listOf(false, true)) {
            val base = AppLanguageContext.wrap(context, AppLanguage.ENGLISH, night)
            fun styled(palette: AppThemePalette) = ContextThemeWrapper(
                AppAppearanceContext(base, AppAppearance(night, palette)), R.style.MobileTheme
            ).apply { theme.applyStyle(palette.nativeOverlay, true) }
            val baseline = styled(AppThemePalette.CLASSIC)
            val classic = TaskProductVisualStyle(baseline)
            val live = ExtractionHistoryVisualStyle(baseline)
            val seen = mutableSetOf<Int>()
            for (choice in AppThemePalette.entries) {
                val candidate = styled(choice)
                val product = TaskProductVisualStyle(candidate)
                val brand = AppPalette(candidate)
                check(seen.add(brand.accent))
                contrast(product.onPrimary, product.primary, 4.5)
                contrast(product.accent, product.surface, 4.5)
                check(product.onDisabled == classic.onDisabled && product.disabled == classic.disabled)
                if (choice != AppThemePalette.CLASSIC) {
                    // Historical Classic tints are kept; new palettes must meet the text floor.
                    contrast(product.accent, product.selected, 4.5)
                    contrast(product.onPrimary, product.homePrimary, 4.5)
                    contrast(0xffe5eae7.toInt(), brand.switchOn, 3.0)
                }
                check(product.stageColors == classic.stageColors && product.scaleMetric == classic.scaleMetric)
                val extraction = ExtractionHistoryVisualStyle(candidate)
                check(extraction.stop == live.stop && ChartMetric.entries.all { extraction.liveMetric(it) == live.liveMetric(it) })
                for (id in listOf(R.color.mobile_danger, R.color.mobile_stop_button, R.color.mobile_alarm_advisory))
                    check(candidate.appColor(id) == baseline.getColor(id))
                check(nativeAccent(candidate) == brand.accent)
            }
        }
    }

    private fun settingsPreviewAndApply() {
        var screen = MainPageTestHost.launch(test, TaskDestination.SETTINGS, timeoutMs = 10_000).activity
        fun fragment() = MainPageTestHost(test, screen).active("root:SETTINGS") as AppSettingsFragment
        fun paletteButton() = fragment().requireView().findViewWithTag<Button>("product/settings/app/palette/current")
        val service = waitFor("settings owner") { screen.currentService }
        fun open(): AlertDialog {
            main { fragment().openSection("appearance") }
            waitFor("settings palette control is reachable") {
                val button = paletteButton()
                val visible = Rect()
                Unit.takeIf { button.getGlobalVisibleRect(visible) && visible.height() == button.height &&
                    visible.width() == button.width && button.height >= HoyiUi.dp(screen, 48) }
            }
            val dialog = main {
                check(paletteButton().performClick())
                checkNotNull(fragment().appearanceCard?.dialog)
            }
            waitFor("dialog actions laid out") {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.takeIf { it.isLaidOut && it.isShown && !it.isLayoutRequested }
            }
            return dialog
        }
        fun click(dialog: AlertDialog, choice: AppThemePalette) = main {
            val button = dialog.window!!.decorView.findViewWithTag<RadioButton>("product/settings/app/palette/${choice.key}")
            button.performClick()
            check(button.isChecked)
            val sample = dialog.window!!.decorView.findViewWithTag<Button>("product/settings/app/palette/preview/action")
            val switch = dialog.window!!.decorView.findViewWithTag<HoyiSwitch>("product/settings/app/palette/preview/switch")
            check(sample.context.themePalette() == choice && switch.context.themePalette() == choice && switch.isChecked)
            val primary = ((sample.background.current as RippleDrawable).getDrawable(0) as GradientDrawable).color!!.defaultColor
            check(primary == AppPalette(sample.context).productPrimary)
            check(nativeAccent(sample.context) == AppPalette(sample.context).accent)
            check(nativeAccent(screen) == AppPalette(screen).accent)
        }
        try {
            val initial = AppAppearance()
            val cancelled = open()
            click(cancelled, AppThemePalette.SEA_SALT)
            main {
                val apply = cancelled.getButton(AlertDialog.BUTTON_POSITIVE)
                val visible = Rect()
                check(apply.getGlobalVisibleRect(visible) && visible.height() == apply.height &&
                    visible.width() == apply.width && apply.height >= HoyiUi.dp(screen, 48))
                check(preferences.current == initial && screen.themePalette() == initial.palette)
                cancelled.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            }
            waitFor("Cancel dismisses without applying") { Unit.takeUnless { cancelled.isShowing } }

            val unchanged = open()
            main { unchanged.getButton(AlertDialog.BUTTON_POSITIVE).performClick() }
            waitFor("Unchanged dismisses without applying") { Unit.takeUnless { unchanged.isShowing } }
            main { check(!screen.isDestroyed && preferences.current == initial) }

            val picker = open()
            click(picker, AppThemePalette.GRAPHITE)
            main {
                val card = checkNotNull(fragment().appearanceCard)
                val writer = card.writePalette
                try {
                    card.writePalette = { AppAppearancePreference.Selection.SAVE_FAILED }
                    picker.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                    val error = picker.window!!.decorView.findViewWithTag<TextView>("product/settings/app/palette/error")
                    check(error.visibility == View.VISIBLE && picker.isShowing && !screen.isDestroyed && preferences.current == initial)
                } finally { card.writePalette = writer }
            }
            screen = activityAfter(MainActivity::class.java, screen) { picker.getButton(AlertDialog.BUTTON_POSITIVE).performClick() }
            waitFor("palette settings rebuilt with its original owner") {
                Unit.takeIf { screen.themePalette() == AppThemePalette.GRAPHITE && screen.currentService === service && screen.hasWindowFocus() }
            }
            main {
                check(preferences.current == AppAppearance(false, AppThemePalette.GRAPHITE))
                check(paletteButton().text == screen.getString(AppThemePalette.GRAPHITE.label))
                check(!fragment().themeCheckBox.isChecked)
            }
            val previous = screen
            screen = activityAfter(MainActivity::class.java, previous) { fragment().themeCheckBox.performClick() }
            waitFor("dark mode rebuilt without changing palette") {
                Unit.takeIf { HoyiUi.dark(screen) && screen.currentService === service && screen.hasWindowFocus() }
            }
            main {
                check(preferences.current == AppAppearance(true, AppThemePalette.GRAPHITE))
                check(screen.themePalette() == AppThemePalette.GRAPHITE && fragment().themeCheckBox.isChecked)
                screen.select(TaskDestination.BREW)
            }
            waitFor("live Home flow legend") {
                val group = screen.supportFragmentManager.findFragmentByTag("root:BREW")?.view
                val label = group?.findViewWithTag<TextView>("product/home/preview-legend")
                val text = label?.text as? Spanned
                if (text != null) {
                    val colors = text.getSpans(0, text.length, ForegroundColorSpan::class.java).map { it.foregroundColor }
                    val style = TaskProductVisualStyle(screen)
                    check(colors.size == 2 && colors.toSet() == setOf(style.pressure, style.flowMetric))
                    Unit
                } else null
            }
        } finally {
            main { screen.finish() }
            waitFor("settings fixture teardown") { Unit.takeIf { screen.isDestroyed } }
        }
    }

    private fun aiDraftAndBackStack() {
        val host = MainPageTestHost.launch(test, timeoutMs = 10_000).activity
        var chat: AiChatActivity? = null
        var cover: CurveEditorActivity? = null
        val inputField = AiChatActivity::class.java.getDeclaredField("input").apply { isAccessible = true }
        fun input(screen: AiChatActivity) = inputField.get(screen) as EditText
        try {
            check(AiKeyStore(context).status() == AiKeyStatus.MISSING) { "Palette draft fixture must not have a model credential" }
            val before = activityAfter(AiChatActivity::class.java) { host.startActivity(Intent(host, AiChatActivity::class.java)) }
            chat = before
            waitFor("AI draft window focus") { Unit.takeIf { before.hasWindowFocus() } }
            main { input(before).apply { setText("测试 draft  12."); check(requestFocus()); setSelection(2, 8) } }
            val overlay = activityAfter(CurveEditorActivity::class.java) { before.startActivity(Intent(before, CurveEditorActivity::class.java)) }
            cover = overlay
            chat = activityAfter(AiChatActivity::class.java, before) {
                check(preferences.selectPalette(AppThemePalette.COFFEE) == AppAppearancePreference.Selection.APPLIED)
                overlay.finish()
            }
            val updated = checkNotNull(chat)
            waitFor("returning AI window rebuild retains input") {
                val editor = input(updated)
                Unit.takeIf { updated.hasWindowFocus() && updated.themePalette() == AppThemePalette.COFFEE &&
                    editor.text.toString() == "测试 draft  12." && editor.hasFocus() && editor.selectionStart == 2 && editor.selectionEnd == 8 }
            }
        } finally { main { cover?.finish(); chat?.finish(); host.finish() } }
    }

    private fun nativeAccent(context: Context): Int {
        val value = TypedValue()
        check(context.theme.resolveAttribute(android.R.attr.colorAccent, value, true))
        return if (value.resourceId != 0) context.getColor(value.resourceId) else value.data
    }

    private fun <T : Any> waitFor(label: String, value: () -> T?): T {
        val end = SystemClock.elapsedRealtime() + 10_000
        do {
            main(value)?.let { return it }
            SystemClock.sleep(30)
        } while (SystemClock.elapsedRealtime() < end)
        error("Timed out: $label")
    }

    private fun <T : Activity> activityAfter(type: Class<T>, previous: T? = null, action: () -> Unit): T {
        val monitor = test.addMonitor(type.name, null, false)
        val end = SystemClock.elapsedRealtime() + 10_000
        try {
            main(action)
            while (SystemClock.elapsedRealtime() < end) {
                val activity = monitor.waitForActivityWithTimeout((end - SystemClock.elapsedRealtime()).coerceAtLeast(1))
                if (activity != null && activity !== previous) return type.cast(activity)!!
            }
            error("Missing replacement ${type.simpleName}")
        } finally { test.removeMonitor(monitor) }
    }
}
