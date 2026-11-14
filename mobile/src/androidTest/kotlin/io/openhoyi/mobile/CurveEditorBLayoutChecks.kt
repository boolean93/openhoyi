package io.openhoyi.mobile

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

/** Read-only recipe/layout acceptance. Tablet execution requires a separately coordinated idle window. */
internal class CurveEditorBLayoutChecks(private val test: Instrumentation) {
    private val context get() = test.targetContext
    private val app get() = context.applicationContext as MobileApplication
    private fun main(action: () -> Unit) {
        var failure: Throwable? = null
        test.runOnMainSync { try { action() } catch (t: Throwable) { failure = t } }
        failure?.let { throw it }
    }
    private fun await(label: String, checkState: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 12000
        while (true) {
            var ok = false; main { ok = checkState() }; if (ok) return
            check(SystemClock.elapsedRealtime() < deadline) { "Editor B layout: $label" }; Thread.sleep(40)
        }
    }
    private fun descendants(v: View): List<View> = listOf(v) + if (v is ViewGroup)
        (0 until v.childCount).flatMap { descendants(v.getChildAt(it)) } else emptyList()
    private fun fullyVisible(v: View) {
        val rect = Rect(); check(v.getGlobalVisibleRect(rect) && rect.width() >= v.width && rect.height() >= v.height) {
            "Clipped ${v.tag}: size=${v.width}x${v.height} visible=$rect"
        }
    }
    private fun shot(name: String) {
        test.waitForIdleSync(); Thread.sleep(260)
        val folder = File(context.getExternalFilesDir(null), "editor-b-revision").apply { mkdirs() }
        requireNotNull(test.uiAutomation.takeScreenshot()).let { bitmap ->
            File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
    }
    fun run(args: Bundle) {
        check(BuildConfig.MOCK_MODE && context.packageName == "io.openhoyi.mobile.mock")
        check(args.getString("coordinatedIdleWindow") == "true") { "Coordinate tablet use before layout acceptance" }
        val originalDocuments = app.customCurvesResult.getOrThrow().list()
        val originalHistory = app.history.entries
        val originalSize = app.fontSizePreferences.current
        val fontPrefs = context.getSharedPreferences("app_typography", Context.MODE_PRIVATE)
        val storedSize = fontPrefs.getString("size", null)
        val appearance = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        val storedDark = if (appearance.contains("dark")) appearance.getBoolean("dark", false) else null
        var activity: Activity? = null
        var launcher: Activity? = null
        try {
            val requestedSize = AppFontSize.restore(args.getString("fontSize"))
            check(app.fontSizePreferences.select(requestedSize) != AppFontSizePreference.Selection.SAVE_FAILED)
            check(appearance.edit().putBoolean("dark", args.getString("dark") == "true").commit())
            // MIUI can defer an instrumentation-originated background Activity launch indefinitely.
            // Enter the exported Mock launcher through the shell first; never loosen security settings.
            val monitor = test.addMonitor(MainActivity::class.java.name, null, false)
            try {
                test.uiAutomation.executeShellCommand("am start -W -n io.openhoyi.mobile.mock/io.openhoyi.mobile.MainActivity").use { descriptor ->
                    android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
                }
                launcher = test.waitForMonitorWithTimeout(monitor, 10000)
                check(launcher != null) { "Mock launcher did not enter foreground" }
            } finally { test.removeMonitor(monitor) }
            await("Mock launcher focus") { launcher?.hasWindowFocus() == true }
            val screen = test.startActivitySync(Intent(context, CurveEditorActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            activity = screen
            check(screen.resources.configuration.fontScale == requestedSize.scale)
            check((screen.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES) == (args.getString("dark") == "true"))
            val root = screen.findViewById<View>(android.R.id.content)
            fun tagged(key: String) = requireNotNull(root.findViewWithTag<View>("product/form/curve-editor/$key"))
            val save = tagged("save"); val cancel = tagged("cancel")
            val scroll = tagged("content-scroll") as ScrollView
            await("initial frame") { screen.hasWindowFocus() && save.isLaidOut && !save.isLayoutRequested }
            main {
                fullyVisible(save); fullyVisible(cancel)
                check(!descendants(scroll).contains(save)) { "Save must not live inside scrolling content" }
                check(root.findViewWithTag<View>("curve-editor-light-toolbar").background == null)
                val control = tagged("control-mode") as CurveEditorSegments
                control.selectFromUser(true)
                root.findViewWithTag<View>("curve-stage-add").performClick()
                listOf("2" to "25", "4.5" to "35", "2.5" to "10").forEachIndexed { index, value ->
                    (tagged("target-$index") as EditText).setText(value.first)
                    (tagged("water-$index") as EditText).setText(value.second)
                }
                (tagged("temperature") as EditText).setText("92")
                (tagged("cup") as EditText).setText("0")
                (tagged("total-water-limit") as EditText).setText("70")
                (tagged("preinfusion") as EditText).setText("3")
                (tagged("first-duration") as EditText).setText("6")
                (tagged("compensation") as EditText).setText("0")
            }
            test.waitForIdleSync()
            fun controls() = descendants(root).filterIsInstance<CurveEditorSegments>().filter { it.isShown }
            main {
                // Every active pair has two individually reachable 48dp-wide options.
                for (segments in controls()) {
                    segments.requestRectangleOnScreen(Rect(0, 0, segments.width, segments.height), true)
                    val options = descendants(segments).filterIsInstance<TextView>().filter { it.isClickable }
                    check(options.size == 2)
                    options.forEach { option ->
                        fullyVisible(option)
                        check(option.width >= HoyiUi.dp(screen, 48) && option.height >= HoyiUi.dp(screen, 48)) {
                            "Undersized segmented option: ${option.text} ${option.width}x${option.height}"
                        }
                    }
                }
                val colors = CurveEditorColors(screen)
                check(root.findViewWithTag<TextView>("curve-stage-mode-1").currentTextColor == colors.flowInk)
                (tagged("first-mode") as CurveEditorSegments).selectFromUser(true)
                val firstMode = tagged("first-mode") as CurveEditorSegments
                val firstPair = descendants(firstMode).filterIsInstance<TextView>().filter { it.isClickable }
                check(firstPair[1].isSelected && firstPair[1].text.toString() == screen.getString(R.string.curve_editor_first_flow))
                check(firstPair.all { it.lineCount == 1 })
                check((tagged("pressure-logic") as CurveEditorSegments).text.toString() == screen.getString(R.string.curve_control_fast))
                // Validate RTL layout of the actual picker, including the selected thumb placement.
                firstMode.layoutDirection = View.LAYOUT_DIRECTION_RTL
                firstMode.requestLayout()
            }
            test.waitForIdleSync()
            main {
                val first = tagged("first-mode") as CurveEditorSegments
                val selected = descendants(first).filterIsInstance<TextView>().single { it.isClickable && it.isSelected }
                val pos = IntArray(2); selected.getLocationOnScreen(pos)
                val thumb = (first as ViewGroup).getChildAt(0)
                val thumbPos = IntArray(2); thumb.getLocationOnScreen(thumbPos)
                check(kotlin.math.abs((pos[0] + selected.width / 2) - (thumbPos[0] + thumb.width / 2)) <= HoyiUi.dp(screen, 3))
                first.layoutDirection = View.LAYOUT_DIRECTION_INHERIT
                first.selectFromUser(false)
                val colors = CurveEditorColors(screen)
                check(((first.getChildAt(0).background as android.graphics.drawable.GradientDrawable).color?.defaultColor) == colors.pressureFill)
                scroll.scrollTo(0, 0)
            }
            test.waitForIdleSync()
            main {
                fun y(view: View): Int { val point = IntArray(2); view.getLocationOnScreen(point); return point[1] }
                (0..2).groupBy { y(root.findViewWithTag<View>("curve-editor-stage-$it")) }.values.forEach { sameRow ->
                    val targetTops = sameRow.map { y(tagged("target-$it")) }
                    check(targetTops.max() - targetTops.min() <= 1) { "Stage target rows do not align: $targetTops" }
                }
            }
            shot(args.getString("fixture", "current") + "-top")
            if (args.getString("semanticStates") == "true") {
                val prefix = args.getString("fixture", "current")
                fun modeAction(action: () -> Unit) { main(action); test.waitForIdleSync(); Thread.sleep(220) }
                shot(prefix + "-mixed-fast")
                modeAction { (tagged("first-mode") as CurveEditorSegments).selectFromUser(true) }
                main {
                    val pair = descendants(tagged("first-mode")).filterIsInstance<TextView>().filter { it.isClickable }
                    check(pair.all { it.lineCount == 1 && !it.text.contains('\n') })
                    check(pair[1].isSelected)
                }
                shot(prefix + "-first-flow-fast")
                modeAction { (tagged("pressure-logic") as CurveEditorSegments).selectFromUser(false) }
                shot(prefix + "-first-flow-slow")
                modeAction { (tagged("first-mode") as CurveEditorSegments).selectFromUser(false) }
                shot(prefix + "-mixed-slow")
                modeAction { (tagged("control-mode") as CurveEditorSegments).selectFromUser(false) }
                main {
                    val colors = CurveEditorColors(screen)
                    check((0..2).all { root.findViewWithTag<TextView>("curve-stage-mode-$it").currentTextColor == colors.pressureInk })
                }
                shot(prefix + "-pressure-constant")
                modeAction { (tagged("pressure-logic") as CurveEditorSegments).selectFromUser(true) }
                shot(prefix + "-pressure-follow")
                modeAction { (tagged("control-mode") as CurveEditorSegments).selectFromUser(true) }
            }
            var originalSave = Rect()
            main { save.getGlobalVisibleRect(originalSave); scroll.fullScroll(View.FOCUS_DOWN) }
            test.waitForIdleSync()
            main {
                fullyVisible(save); fullyVisible(cancel)
                val after = Rect(); save.getGlobalVisibleRect(after); check(after == originalSave) { "Save moved with form scrolling" }
                val last = tagged("compensation"); last.requestRectangleOnScreen(Rect(0, 0, last.width, last.height), true)
            }
            test.waitForIdleSync(); main { fullyVisible(tagged("compensation")) }
            shot(args.getString("fixture", "current") + "-bottom")
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            val imeField = tagged("compensation") as EditText
            main { imeField.requestFocus(); imeField.setSelection(imeField.length()) }
            await("native input connection") { imm.isActive(imeField) }
            main { imm.showSoftInput(imeField, InputMethodManager.SHOW_IMPLICIT) }
            await("IME visible") { root.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime()) == true }
            test.waitForIdleSync(); Thread.sleep(260)
            main { imeField.requestRectangleOnScreen(Rect(0, 0, imeField.width, imeField.height), true) }
            test.waitForIdleSync()
            main {
                fullyVisible(save); fullyVisible(cancel); fullyVisible(imeField)
                val frame = Rect(); root.getWindowVisibleDisplayFrame(frame)
                val button = Rect(); save.getGlobalVisibleRect(button)
                check(button.bottom <= frame.bottom) { "Save overlaps IME: $button vs $frame" }
            }
            shot(args.getString("fixture", "current") + "-ime")
            main { imm.hideSoftInputFromWindow(root.windowToken, 0) }
            check(app.customCurvesResult.getOrThrow().list() == originalDocuments)
            check(app.history.entries == originalHistory)
        } finally {
            main { activity?.finish(); launcher?.finish() }
            app.fontSizePreferences.select(originalSize)
            fontPrefs.edit().apply { if (storedSize == null) remove("size") else putString("size", storedSize) }.commit()
            appearance.edit().apply { if (storedDark == null) remove("dark") else putBoolean("dark", storedDark) }.commit()
        }
    }
}
