package io.openhoyi.mobile

import android.app.Activity
import android.app.AlertDialog
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** Actual settings/preview window and configuration restoration. Disposable Mock emulator only. */
internal class ThemePaletteRuntimeChecks(private val test: Instrumentation) {
    private val context get() = test.targetContext
    private val app get() = context.applicationContext as MobileApplication
    private val preferences get() = app.appearancePreferences
    private val evidence get() = File(context.filesDir, "theme-palette-evidence")
    private val store get() = context.getSharedPreferences(SharedAppearanceStorage.PREFERENCES, Context.MODE_PRIVATE)
    private fun <T> main(action: () -> T) = BoundedInstrumentationMain.run("theme palette", action = action)

    fun run() {
        check(BuildConfig.MOCK_MODE && context.packageName == "io.openhoyi.mobile.mock" &&
            Build.HARDWARE in setOf("ranchu", "goldfish")) { "Palette fixtures require the disposable Mock emulator" }
        check(evidence.mkdir()) { "Fresh palette evidence directory required" }
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
                verifySwitchInk(candidate, product.surface)
            }
        }
    }

    /** Exercise all eight palettes/modes through the actual Switch painter, not just token values. */
    private fun verifySwitchInk(context: Context, surface: Int) {
        val toggle = HoyiSwitch(context)
        val style = TaskProductVisualStyle(context)
        val width = style.dp(120)
        val height = style.dp(56)
        toggle.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        toggle.layout(0, 0, width, height)
        fun ink(checked: Boolean, enabled: Boolean): Pair<Double, Double> {
            toggle.isEnabled = enabled
            toggle.isChecked = checked
            toggle.jumpDrawablesToCurrentState()
            val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                image.eraseColor(surface)
                toggle.draw(Canvas(image))
                val track = toggle.trackDrawable.bounds
                check(track.width() > 0 && track.height() > 0)
                val first = track.left + track.width() / 4
                val last = track.right - track.width() / 4
                return ColorUtils.calculateLuminance(image.getPixel(first, track.centerY())) to
                    ColorUtils.calculateLuminance(image.getPixel(last, track.centerY()))
            } finally { image.recycle() }
        }
        val off = ink(checked = false, enabled = true)
        val on = ink(checked = true, enabled = true)
        val disabled = ink(checked = true, enabled = false)
        // Positional contrast still distinguishes checked/unchecked without hue perception.
        check(off.first - off.second > .14 && on.second - on.first > .14)
        check(abs(disabled.first - on.first) > .025 || abs(disabled.second - on.second) > .025)
    }

    private fun awaitSwitchFrame(decor: View, toggle: HoyiSwitch) {
        check(Build.VERSION.SDK_INT >= 29)
        val committed = CountDownLatch(1)
        val callback = Runnable { committed.countDown() }
        val tree = main {
            check(decor.isAttachedToWindow && decor.isHardwareAccelerated)
            decor.viewTreeObserver.also { it.registerFrameCommitCallback(callback) }
                .also { toggle.invalidate(); decor.invalidate() }
        }
        if (!committed.await(5, TimeUnit.SECONDS)) {
            main { if (tree.isAlive) tree.unregisterFrameCommitCallback(callback) }
            error("The palette dialog did not submit a switch frame")
        }
    }

    private fun captureSwitchStates(dialog: AlertDialog, sample: String) {
        val decor = dialog.window!!.decorView
        val toggle = waitFor("preview switch laid out") {
            decor.findViewWithTag<HoyiSwitch>("product/settings/app/palette/preview/switch")
                ?.takeIf { it.isLaidOut && it.width > 0 && !it.isLayoutRequested }
        }
        val palette = AppPalette(toggle.context)
        val captured = mutableMapOf<String, Pair<Double, Double>>()
        for ((name, checked, enabled) in listOf(
            Triple("on", true, true), Triple("off", false, true), Triple("disabled", true, false)
        )) {
            main {
                toggle.isChecked = checked
                toggle.isEnabled = enabled
                // Finish the demo animation at its endpoint; production tap/drag is untouched.
                toggle.jumpDrawablesToCurrentState()
                toggle.requestRectangleOnScreen(Rect(0, 0, toggle.width, toggle.height), true)
            }
            waitFor("$sample/$name switch visible and laid out") {
                val visible = Rect()
                Unit.takeIf { !toggle.isLayoutRequested && !decor.isLayoutRequested &&
                    toggle.getGlobalVisibleRect(visible) && visible.height() == toggle.height && visible.width() == toggle.width }
            }
            var lastInk: Pair<Double, Double>? = null
            // SurfaceFlinger can still present the previous buffer immediately after RenderThread
            // commits. Retry actual window frames, and sample the track pixels rather than the status bar.
            for (attempt in 1..5) {
                awaitSwitchFrame(decor, toggle)
                val (first, last) = main {
                    check(toggle.isChecked == checked && toggle.isEnabled == enabled)
                    val origin = IntArray(2)
                    toggle.getLocationOnScreen(origin)
                    val track = toggle.trackDrawable.bounds
                    check(track.width() > 0 && track.height() > 0)
                    val y = origin[1] + track.centerY()
                    val left = origin[0] + track.left + track.width() / 4
                    val right = origin[0] + track.right - track.width() / 4
                    val ltr = toggle.layoutDirection != View.LAYOUT_DIRECTION_RTL
                    (if (ltr) left to y else right to y) to (if (ltr) right to y else left to y)
                }
                val image = checkNotNull(test.uiAutomation.takeScreenshot())
                try {
                    val left = image.getPixel(first.first, first.second)
                    val right = image.getPixel(last.first, last.second)
                    val ink = ColorUtils.calculateLuminance(left) to ColorUtils.calculateLuminance(right)
                    lastInk = ink
                    fun differsFrom(state: String): Boolean {
                        val previous = captured.getValue(state)
                        return abs(ink.first - previous.first) > .025 || abs(ink.second - previous.second) > .025
                    }
                    fun trackMatches(actual: Int, expected: Int): Boolean =
                        abs(Color.red(actual) - Color.red(expected)) <= 8 &&
                            abs(Color.green(actual) - Color.green(expected)) <= 8 &&
                            abs(Color.blue(actual) - Color.blue(expected)) <= 8
                    val correct = when (name) {
                        "on" -> ink.second - ink.first > .14 && trackMatches(left, palette.switchOn)
                        "off" -> ink.first - ink.second > .14 && trackMatches(right, palette.switchOff)
                        else -> ink.second > ink.first && differsFrom("on") && differsFrom("off")
                    }
                    if (!correct) continue
                    File(evidence, "$sample-$name.png").outputStream().use {
                        check(image.compress(Bitmap.CompressFormat.PNG, 100, it))
                    }
                    captured[name] = ink
                    break
                } finally { image.recycle() }
            }
            check(name in captured) { "$sample/$name never appeared in the dialog: captured track luminance=$lastInk" }
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
            for (choice in AppThemePalette.entries) {
                click(cancelled, choice)
                captureSwitchStates(cancelled, "light-${choice.key}")
            }
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
            }
            val darkPreview = open()
            for (choice in AppThemePalette.entries) {
                click(darkPreview, choice)
                captureSwitchStates(darkPreview, "dark-${choice.key}")
            }
            main { darkPreview.getButton(AlertDialog.BUTTON_NEGATIVE).performClick() }
            waitFor("dark preview dismissed") { Unit.takeUnless { darkPreview.isShowing } }
            main { screen.select(TaskDestination.BREW) }
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
