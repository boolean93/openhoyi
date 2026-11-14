package io.openhoyi.mobile

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.EditText
import android.widget.TextView
import java.io.File

/** Disposable CI Mock only. Never installs on a user's tablet or connects to BLE. */
internal class CurveEditorBChecks(private val test: Instrumentation) {
    private val context get() = test.targetContext
    private val app get() = context.applicationContext as MobileApplication
    private val opened = mutableListOf<Activity>()
    private fun main(action: () -> Unit) {
        var error: Throwable? = null
        test.runOnMainSync { try { action() } catch (failure: Throwable) { error = failure } }
        error?.let { throw it }
    }
    private fun await(label: String, condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 15000
        while (true) {
            var ready = false; main { ready = condition() }
            if (ready) return
            check(SystemClock.elapsedRealtime() < end) { "Editor B timeout: $label" }; Thread.sleep(30)
        }
    }
    private fun root(activity: Activity) = activity.findViewById<View>(android.R.id.content)
    private fun tag(activity: Activity, key: String) = requireNotNull(root(activity).findViewWithTag<View>("product/form/curve-editor/$key"))
    private fun field(activity: Activity, key: String) = tag(activity, key) as EditText
    private fun start(id: String? = null): Activity = test.startActivitySync(Intent(context, CurveEditorActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).apply { id?.let { putExtra(CurveEditorActivity.CURVE_ID, it) } }).also {
        opened += it; test.waitForIdleSync(); await("ready") { it.hasWindowFocus() && root(it).isLaidOut }
    }
    private fun preview(activity: Activity) = (root(activity).findViewWithTag<View>("task-form-stage-preview") as CurveEditorPreviewView).model
    private fun screenshot(name: String) {
        test.waitForIdleSync(); Thread.sleep(200)
        val directory = File(context.getExternalFilesDir(null), "editor-b").apply { mkdirs() }
        requireNotNull(test.uiAutomation.takeScreenshot()).let { bitmap ->
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
    }
    fun run(arguments: Bundle) {
        check(BuildConfig.MOCK_MODE && context.packageName == "io.openhoyi.mobile.mock")
        check(arguments.getString("disposableCiEmulator") == "true" && android.os.Build.HARDWARE in setOf("ranchu", "goldfish"))
        val store = app.customCurvesResult.getOrThrow()
        val original = store.list(); val selected = context.getSharedPreferences("curves", Context.MODE_PRIVATE).all.toMap()
        val presets = context.getSharedPreferences("presets", Context.MODE_PRIVATE)
        val oldBinding = presets.getString(PresetSlots.key(1), null)
        val history = app.history.entries
        val size = app.fontSizePreferences.current
        val appearance = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        val dark = appearance.getBoolean("dark", false)
        val ids = mutableSetOf<String>()
        try {
            check(app.fontSizePreferences.select(AppFontSize.restore(arguments.getString("fontSize"))) != AppFontSizePreference.Selection.SAVE_FAILED)
            check(appearance.edit().putBoolean("dark", arguments.getString("dark") == "true").commit())
            val editor = start()
            main {
                check((tag(editor, "save") as TextView).text == editor.getString(R.string.curve_editor_create))
                (tag(editor, "control-mode") as CurveEditorSegments).selectFromUser(true)
                check((tag(editor, "pressure-logic") as CurveEditorSegments).checked)
                root(editor).findViewWithTag<View>("curve-stage-add").performClick()
                listOf("2" to "25", "4.5" to "35", "2.5" to "10").forEachIndexed { index, values ->
                    field(editor, "target-$index").setText(values.first); field(editor, "water-$index").setText(values.second)
                }
                field(editor, "name").setText("Editor B isolated check")
                field(editor, "temperature").setText("92"); field(editor, "cup").setText("0")
                field(editor, "total-water-limit").setText("70"); field(editor, "preinfusion").setText("3")
                field(editor, "first-duration").setText("6"); field(editor, "compensation").setText("0")
                check(!preview(editor).invalid && preview(editor).totalWater == 700)
            }
            screenshot("valid")
            main { (root(editor).findViewWithTag<View>("product/form/curve-editor/content-scroll") as android.widget.ScrollView).fullScroll(View.FOCUS_DOWN) }
            screenshot("valid-lower")
            main { (root(editor).findViewWithTag<View>("product/form/curve-editor/content-scroll") as android.widget.ScrollView).scrollTo(0, 0) }
            main {
                field(editor, "target-2").setText("")
                check(preview(editor).stages[0].target == 20 && preview(editor).stages[1].target == 45)
                check(preview(editor).stages[2].target == null)
                check(root(editor).findViewWithTag<TextView>("curve-editor-error-target-2").text.isNotBlank())
                check(root(editor).findViewWithTag<View>("curve-editor-error-target-2").visibility == View.VISIBLE)
                field(editor, "water-2").setText("9999")
                check(preview(editor).stages[2].water == null)
            }
            main { root(editor).findViewWithTag<View>("curve-editor-stage-2").requestRectangleOnScreen(android.graphics.Rect(0, 0,
                root(editor).findViewWithTag<View>("curve-editor-stage-2").width, root(editor).findViewWithTag<View>("curve-editor-stage-2").height), true) }
            screenshot("invalid")
            main {
                field(editor, "target-2").setText("3.5"); field(editor, "water-2").setText("15")
                check(preview(editor).stages[2].target == 35 && preview(editor).totalWater == 750)
                check(root(editor).findViewWithTag<View>("curve-editor-error-target-2").visibility == View.GONE)
                root(editor).findViewWithTag<View>("curve-stage-remove-2").performClick()
                check(preview(editor).stages.size == 2)
                root(editor).findViewWithTag<View>("curve-stage-add").performClick()
                check(preview(editor).stages.size == 3 && preview(editor).stages[2].target == 35)
                (tag(editor, "first-mode") as CurveEditorSegments).selectFromUser(true)
                check(preview(editor).stages.all { it.flow })
                (tag(editor, "first-mode") as CurveEditorSegments).selectFromUser(false)
                // Change the total without losing the current draft graph.
                field(editor, "total-water-limit").setText("")
                check(preview(editor).stages[2].water == 150)
            }
            main {
                field(editor, "preinfusion").setText(""); field(editor, "first-duration").setText("256")
                field(editor, "compensation").setText("-"); field(editor, "total-water-limit").setText("0")
                for (key in listOf("preinfusion", "first-duration", "compensation", "total-water-limit"))
                    check(root(editor).findViewWithTag<View>("curve-editor-error-$key").visibility == View.VISIBLE)
                field(editor, "preinfusion").setText("3"); field(editor, "first-duration").setText("6")
                field(editor, "compensation").setText("0"); field(editor, "total-water-limit").setText("")
                for (key in listOf("preinfusion", "first-duration", "compensation", "total-water-limit"))
                    check(root(editor).findViewWithTag<View>("curve-editor-error-$key").visibility == View.GONE)
            }
            screenshot("recovered")
            main { tag(editor, "save").performClick(); tag(editor, "save").performClick() }
            await("new saved") { editor.isDestroyed }
            val created = store.list().filter { doc -> original.none { it.id == doc.id } }
            check(created.size == 1); ids += created.single().id
            val originalDoc = created.single()
            val binding = PresetSlots.bind(originalDoc.id)
            check(presets.edit().putString(PresetSlots.key(1), binding).commit())
            val copyEditor = start(originalDoc.id)
            main {
                check((tag(copyEditor, "save") as TextView).text == copyEditor.getString(R.string.curve_editor_save_copy))
                field(copyEditor, "name").setText("Editor B new copy")
                tag(copyEditor, "save").performClick(); tag(copyEditor, "save").performClick()
            }
            await("copy saved") { copyEditor.isDestroyed }
            val copies = store.list().filter { it.id !in ids && original.none { old -> old.id == it.id } }
            check(copies.size == 1 && copies.single().id != originalDoc.id); ids += copies.single().id
            check(store.find(originalDoc.id) == originalDoc)
            check(presets.getString(PresetSlots.key(1), null) == binding)
            check(PresetSlots.curveId(1, binding) == originalDoc.id)
            val cancel = start(originalDoc.id)
            main { field(cancel, "name").setText("Cancelled change"); tag(cancel, "cancel").performClick() }
            await("cancelled") { cancel.isDestroyed }
            check(store.find(originalDoc.id) == originalDoc)
            check(store.list().filter { it.id !in ids } == original)
            check(context.getSharedPreferences("curves", Context.MODE_PRIVATE).all == selected)
            check(app.history.entries == history)
        } finally {
            main { opened.filter { !it.isDestroyed }.forEach { it.finish() } }
            app.fontSizePreferences.select(size); appearance.edit().putBoolean("dark", dark).commit()
            presets.edit().apply { if (oldBinding == null) remove(PresetSlots.key(1)) else putString(PresetSlots.key(1), oldBinding) }.commit()
            // Only this test's own generated documents are recoverably archived.
            ids.forEach { id -> store.find(id)?.let { store.delete(id, emptyMap(), it) } }
        }
    }
}
