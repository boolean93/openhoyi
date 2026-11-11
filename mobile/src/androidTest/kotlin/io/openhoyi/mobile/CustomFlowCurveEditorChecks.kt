package io.openhoyi.mobile

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView

/** Opt-in disposable Mock UI regression. It never selects a curve or starts an extraction. */
internal class CustomFlowCurveEditorChecks(private val test: Instrumentation) {
    private val context get() = test.targetContext
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
            check(SystemClock.elapsedRealtime() < end) { "Flow editor timeout: $label" }
            Thread.sleep(25)
        }
    }
    private fun root(activity: Activity): View = activity.findViewById(android.R.id.content)
    private fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun tag(activity: Activity, key: String): View = requireNotNull(root(activity).findViewWithTag<View>("product/form/curve-editor/$key"))
    private fun input(activity: Activity, key: String) = tag(activity, key) as EditText
    private fun source(activity: Activity) = CurveEditorActivity::class.java.getDeclaredField("sourceDocument")
        .apply { isAccessible = true }.get(activity) as CustomCurveDocument
    private fun start(type: Class<out Activity>, configure: Intent.() -> Unit = {}): Activity =
        test.startActivitySync(Intent(context, type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).apply(configure)).also {
            opened += it; test.waitForIdleSync(); await("activity ready") { it.hasWindowFocus() && root(it).isLaidOut }
        }
    private fun close(activity: Activity) { main { activity.finish() }; await("closed") { activity.isDestroyed } }
    private fun visible(activity: Activity, key: String) {
        main { val view = tag(activity, key); check(view.isShown); view.requestRectangleOnScreen(Rect(0, 0, view.width, view.height), true) }
        await("fully reachable $key") {
            val view = tag(activity, key); val rect = Rect()
            view.getGlobalVisibleRect(rect) && rect.width() >= view.width && rect.height() >= view.height
        }
    }
    private fun save(activity: Activity) {
        visible(activity, "save")
        main { check(tag(activity, "save").isEnabled); check(tag(activity, "save").performClick()) }
        test.waitForIdleSync()
    }
    private fun recreate(activity: Activity, rotate: Boolean = false): Activity {
        val monitor = test.addMonitor(CurveEditorActivity::class.java.name, null, false)
        return try {
            main {
                if (rotate) activity.requestedOrientation = if (activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE)
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                else activity.recreate()
            }
            requireNotNull(test.waitForMonitorWithTimeout(monitor, 10000)).also {
                opened += it; test.waitForIdleSync(); await("recreated") { it !== activity && it.hasWindowFocus() && root(it).isLaidOut }
            }
        } finally { test.removeMonitor(monitor) }
    }

    fun run(disposable: Boolean) {
        check(disposable && BuildConfig.MOCK_MODE && context.packageName == "io.openhoyi.mobile.mock")
        check(android.os.Build.HARDWARE in setOf("ranchu", "goldfish")) { "Physical devices are forbidden" }
        val qemu = test.uiAutomation.executeShellCommand("getprop ro.kernel.qemu").use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText().trim() }
        }
        check(qemu == "1") { "Disposable emulator required" }
        val app = context.applicationContext as MobileApplication
        val store = app.customCurvesResult.getOrThrow()
        val originalDrafts = store.list()
        val originalHistory = app.history.entries
        check(originalHistory.none { it.status in setOf(ShotHistory.Status.STARTING, ShotHistory.Status.RUNNING,
            ShotHistory.Status.STOP_REQUESTED, ShotHistory.Status.UNKNOWN) })
        val prefs = context.getSharedPreferences("curves", android.content.Context.MODE_PRIVATE)
        val selected = prefs.getString("selected", null)
        val factory = app.curves.items.filter { it.factoryCurve?.variableFlowLogic == true }
        val representatives = listOf(factory.first(), factory.first { it.factoryCurve!!.segmentCount != factory.first().factoryCurve!!.segmentCount })
        val created = mutableSetOf<String>()
        try {
            val newEditor = start(CurveEditorActivity::class.java)
            main {
                check((tag(newEditor, "control-mode") as Spinner).selectedItemPosition == 0)
                check(tag(newEditor, "first-mode").visibility == View.GONE)
                check(tag(newEditor, "pressure-logic").visibility == View.GONE)
                (tag(newEditor, "control-mode") as Spinner).setSelection(1)
            }
            await("flow-only controls visible") { tag(newEditor, "first-mode").visibility == View.VISIBLE && tag(newEditor, "pressure-logic").visibility == View.VISIBLE }
            main {
                check((tag(newEditor, "pressure-logic") as Switch).isChecked)
                (tag(newEditor, "control-mode") as Spinner).setSelection(0)
            }
            await("pressure controls restored") {
                tag(newEditor, "first-mode").visibility == View.GONE && tag(newEditor, "pressure-logic").visibility == View.GONE &&
                    !(tag(newEditor, "pressure-logic") as Switch).isChecked
            }
            var newPressureId = ""
            main {
                newPressureId = source(newEditor).id
                check(source(newEditor).configuration == null)
                check(requireNotNull(root(newEditor).findViewWithTag<View>("curve-stage-count-1")).performClick())
                input(newEditor, "target-0").setText("0")
            }
            created += newPressureId
            save(newEditor)
            await("legacy single-stage pressure creation") {
                val saved = store.find(newPressureId)
                newEditor.isDestroyed && saved?.stages == listOf(CustomCurveDocument.Stage(0, 150)) &&
                    saved.configuration == null && CustomPressureCurveAdapter.profile(saved, false) != null
            }
            for ((index, item) in representatives.withIndex()) {
                var editor = start(CurveEditorActivity::class.java) { putExtra(CurveEditorActivity.CURVE_ID, item.id) }
                lateinit var copied: CustomCurveDocument
                main {
                    copied = source(editor)
                    check(copied == CustomCurveDocument.fromLibraryItem(item, copied.id))
                    check((root(editor).findViewWithTag<View>("task-form-stage-preview") as CurveStageView).preview?.mixed == true)
                }
                created += copied.id
                // A true no-edit save must not erase timers, flags or inactive slots.
                save(editor); await("copy saved") { editor.isDestroyed && store.find(copied.id) == copied }
                editor = start(CurveEditorActivity::class.java) { putExtra(CurveEditorActivity.CURVE_ID, copied.id) }
                main {
                    (tag(editor, "first-mode") as Spinner).setSelection(1)
                    (tag(editor, "pressure-logic") as Switch).isChecked = index == 0
                    input(editor, "target-0").setText("2.5")
                    input(editor, "cup").setText("36")
                    input(editor, "preinfusion").setText("4")
                    input(editor, "first-duration").setText("8")
                    input(editor, "compensation").setText("0.2")
                }
                await("first flow preview") {
                    val preview = (root(editor).findViewWithTag<View>("task-form-stage-preview") as CurveStageView).preview
                    preview?.hasFlow == true && !preview.mixed
                }
                for (key in listOf("control-mode", "first-mode", "pressure-logic", "target-0", "water-0", "preinfusion", "first-duration", "compensation")) visible(editor, key)
                editor = recreate(editor, rotate = index == 0)
                main {
                    check((tag(editor, "control-mode") as Spinner).selectedItemPosition == 1)
                    check((tag(editor, "first-mode") as Spinner).selectedItemPosition == 1)
                    check((tag(editor, "pressure-logic") as Switch).isChecked == (index == 0))
                    mapOf("target-0" to "2.5", "cup" to "36", "preinfusion" to "4", "first-duration" to "8", "compensation" to "0.2")
                        .forEach { (key, value) -> check(input(editor, key).text.toString() == value) }
                }
                val expected = copied.copy(targetHundredthsGram = 3600,
                    stages = copied.stages.mapIndexed { at, stage -> if (at == 0) stage.copy(target = 25) else stage },
                    configuration = copied.configuration!!.copy(firstSegmentFlowMode = true, pressureLogic = index == 0,
                        preinfusionSeconds = 4, firstDurationSeconds = 8, compensationHundredthsGram = 20))
                save(editor); await("edited saved") { editor.isDestroyed && store.find(copied.id) == expected }
                editor = start(CurveEditorActivity::class.java) { putExtra(CurveEditorActivity.CURVE_ID, copied.id) }
                main { check(source(editor) == expected); input(editor, "water-0").setText("0"); input(editor, "first-duration").setText("0") }
                save(editor)
                main {
                    check(!editor.isFinishing && !editor.isDestroyed)
                    check(input(editor, "first-duration").error != null)
                    check(store.find(copied.id) == expected)
                }
                close(editor)
                val share = start(CurveShareActivity::class.java) { putExtra(CurveShareActivity.CURVE_ID, copied.id) }
                lateinit var shared: CustomCurveDocument
                main {
                    shared = CurveShareActivity::class.java.getDeclaredField("document").apply { isAccessible = true }.get(share) as CustomCurveDocument
                    check(shared == expected)
                    check(views(root(share)).filterIsInstance<CurveStageView>().any { it.preview?.hasFlow == true })
                }
                // Inspect actual share composition, but never invoke an external share or clipboard action.
                close(share)
                val recipient = CustomCurveDocument.decode(shared.encode()).copy(id = CustomCurveDocument.newId())
                val import = start(CurveImportPreviewActivity::class.java) { putExtra(CurveImportPreviewActivity.DOCUMENT, recipient.encode()) }
                main {
                    check(views(root(import)).filterIsInstance<CurveStageView>().any { it.preview?.hasFlow == true })
                    val action = views(root(import)).filterIsInstance<TextView>().single {
                        it.isClickable && it.text.toString() == import.getString(R.string.profiles_import_save)
                    }
                    check(action.performClick())
                }
                created += recipient.id
                await("imported") { import.isDestroyed && store.find(recipient.id) == recipient }
            }
            // Explicitly reducing old documents upgrades only that edited recipe and keeps hidden slots.
            for (limit in listOf(null, 70)) {
                val old = CustomCurveDocument(CustomCurveDocument.newId(), "Legacy pressure tail", 93, 3600,
                    CustomCurveDocument.ControlMode.PRESSURE,
                    listOf(30, 90, 80, 60).map { CustomCurveDocument.Stage(it, 150) }, limit)
                store.save(old); created += old.id
                var editor = start(CurveEditorActivity::class.java) { putExtra(CurveEditorActivity.CURVE_ID, old.id) }
                main { check(requireNotNull(root(editor).findViewWithTag<View>("curve-stage-count-2")).performClick()) }
                save(editor); await("old reduced saved") { editor.isDestroyed && store.find(old.id)?.stages?.size == 2 }
                main {
                    val reduced = requireNotNull(store.find(old.id))
                    check(reduced.configuration!!.inactiveStages == old.stages.drop(2))
                    check(reduced.totalWaterLimitMl == old.totalWaterLimitMl)
                }
                editor = start(CurveEditorActivity::class.java) { putExtra(CurveEditorActivity.CURVE_ID, old.id) }
                main {
                    check(requireNotNull(root(editor).findViewWithTag<View>("curve-stage-count-4")).performClick())
                    old.stages.forEachIndexed { index, stage ->
                        check(input(editor, "target-$index").text.toString() == CurveDraftNumber.format(stage.target, 1))
                        check(input(editor, "water-$index").text.toString() == CurveDraftNumber.format(stage.waterTenthsMl, 1))
                    }
                }
                save(editor); await("hidden stages restored") { editor.isDestroyed && store.find(old.id)?.stages == old.stages }
            }
            val raw = CustomCurveDocument(CustomCurveDocument.newId(), "Legacy raw editor guard", 93, 0,
                CustomCurveDocument.ControlMode.FLOW_RAW, listOf(CustomCurveDocument.Stage(25, 150), CustomCurveDocument.Stage(35, 400)))
            store.save(raw); created += raw.id
            val readonly = start(CurveEditorActivity::class.java) { putExtra(CurveEditorActivity.CURVE_ID, raw.id) }
            main {
                check(views(root(readonly)).none { it is EditText })
                check(root(readonly).findViewWithTag<View>("product/form/curve-editor/save") == null)
                check(views(root(readonly)).filterIsInstance<TextView>().any { it.text.toString().contains(readonly.getString(R.string.flow_curve_missing_modes_short)) })
                check(store.find(raw.id) == raw)
            }
            check(store.list().filter { it.id !in created } == originalDrafts)
            check(prefs.getString("selected", null) == selected && app.history.entries == originalHistory)
        } finally {
            main { opened.asReversed().filter { !it.isDestroyed }.forEach { it.finish() } }
        }
    }
}
