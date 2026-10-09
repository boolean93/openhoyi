package io.openhoyi.mobile

import android.app.Activity
import android.app.AlertDialog
import android.app.Instrumentation
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import io.openhoyi.bean.InventoryEventKind
import java.util.UUID

/** UI writes only to this run's local fixtures. Never clicks machine/BLE, external share or SAF. */
internal class AdvancedBusinessUiChecks(private val test: Instrumentation) {
    data class Result(val paths: List<String>, val inventoryEvents: Int, val newDrafts: Int)
    private val context get() = test.targetContext
    private val opened = mutableListOf<Activity>()
    private fun onMain(action: () -> Unit) {
        var failure: Throwable? = null
        test.runOnMainSync { try { action() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }
    private fun awaitMain(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10000
        while (true) {
            var ready = false
            onMain { ready = condition() }
            if (ready) return
            check(SystemClock.elapsedRealtime() < deadline) { "Advanced business UI condition timed out" }
            Thread.sleep(25)
        }
    }
    private fun views(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun root(activity: Activity): View = activity.findViewById(android.R.id.content)
    private fun click(activity: Activity, label: Int) = click(root(activity), activity.getString(label))
    private fun click(root: View, text: String) {
        onMain {
            val button = views(root).filterIsInstance<TextView>().single { it.isShown && it.isClickable && it.text.toString() == text }
            check(button.isEnabled) { "Disabled UI action: $text" }
            button.requestRectangleOnScreen(android.graphics.Rect(0, 0, button.width, button.height), true)
            check(button.performClick()) { "Action not handled: $text" }
        }
        test.waitForIdleSync()
    }
    private fun fill(root: View, label: String, value: String, index: Int = 0) = onMain {
        val title = views(root).filterIsInstance<TextView>().filter { it.text.toString() == label && it.labelFor != View.NO_ID }[index]
        val input = root.findViewById<EditText>(title.labelFor)
        check(input.isEnabled)
        input.setText(value)
    }
    private fun fill(activity: Activity, label: Int, value: String, index: Int = 0) = fill(root(activity), activity.getString(label), value, index)
    private fun start(type: Class<out Activity>, extras: (Intent) -> Unit = {}): Activity {
        val intent = Intent(context, type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).also(extras)
        val activity = test.startActivitySync(intent).also { opened += it }
        test.waitForIdleSync()
        awaitMain { activity.hasWindowFocus() && root(activity).isLaidOut && !root(activity).isLayoutRequested }
        return activity
    }
    private fun close(activity: Activity) { onMain { if (!activity.isDestroyed) activity.finish() }; awaitMain { activity.isDestroyed }; opened.remove(activity) }
    private fun recreate(activity: Activity): Activity {
        val monitor = test.addMonitor(activity.javaClass.name, null, false)
        try {
            onMain { activity.recreate() }
            val replacement = test.waitForMonitorWithTimeout(monitor, 10000) ?: error("Recreation missing")
            opened.remove(activity); opened += replacement
            test.waitForIdleSync()
            awaitMain { replacement.hasWindowFocus() && root(replacement).isLaidOut && !root(replacement).isLayoutRequested }
            return replacement
        } finally { test.removeMonitor(monitor) }
    }
    private fun dialog(activity: Activity): AlertDialog {
        var result: AlertDialog? = null
        onMain {
            result = when (activity) {
                is BeanBatchActivity -> (BeanBatchActivity::class.java.getDeclaredField("dialogs").apply { isAccessible = true }.get(activity) as List<*>)
                    .filterIsInstance<AlertDialog>().last { it.isShowing }
                is BeanPreparationActivity -> BeanPreparationActivity::class.java.getDeclaredField("dialog").apply { isAccessible = true }.get(activity) as AlertDialog
                else -> error("Unsupported local dialog owner")
            }
        }
        return requireNotNull(result)
    }
    private fun dialogClick(dialog: AlertDialog, which: Int, expected: String) {
        onMain {
            val button = dialog.getButton(which)
            check(button.isEnabled && button.text.toString() == expected)
            check(button.performClick())
        }
        test.waitForIdleSync()
    }
    private fun owner(home: Activity): MobileService? = HomeActivity::class.java.getDeclaredField("service").apply { isAccessible = true }.get(home) as? MobileService

    fun run(): Result {
        check(BuildConfig.MOCK_MODE && context.packageName == "io.openhoyi.mobile.mock")
        val app = context.applicationContext as MobileApplication
        val inventory = app.beanInventoryResult.getOrThrow()
        val preparation = app.beanPreparationResult.getOrThrow()
        val journal = app.journalResult.getOrThrow()
        val custom = app.customCurvesResult.getOrThrow()
        // Warm bounded-history reconciliation without replacing any unresolved preparation.
        app.history
        check(preparation.current() == null) { "Do not replace an existing or unknown preparation; isolated Mock store required" }
        val oldLanguage = app.languagePreferences.current
        val languagePrefs = context.getSharedPreferences("app_language", Context.MODE_PRIVATE)
        val oldLanguageStorage = languagePrefs.all.toMap()
        val appearance = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        val oldAppearance = appearance.all.toMap()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        var oldClip: ClipData? = null
        var clipboardCaptured = false
        val preserved = listOf("devices", "curves", "presets", "shot_safety", "machine_write_safety", "shot_history", "brew_feedback", "scale_tool")
            .associateWith { context.getSharedPreferences(it, Context.MODE_PRIVATE).all.toMap() }
        val initialBatches = inventory.batches()
        val initialEvents = inventory.events()
        val initialJournal = journal.entries()
        val initialCurves = custom.list()
        val token = UUID.randomUUID().toString()
        val beanName = "UI beans $token"
        val shotId = "ui-flow-shot-$token"
        val taste = "UI taste $token"
        val observation = BrewJournal.Observation(shotId, "capture-1", 1700000000000L, 1700000030000L, 30000L, "ENDED", null, 3600)
        val paths = mutableListOf<String>()
        var batchId: String? = null
        val ownCurveIds = mutableSetOf<String>()
        var home: Activity? = null
        var service: MobileService? = null
        var message: SnapshotMessage? = null
        var failure: Throwable? = null
        fun record(name: String) {
            paths += name
            test.sendStatus(0, android.os.Bundle().apply { putString("stream", "ADVANCED_BUSINESS_UI_PATH $name\n") })
        }
        fun balance() = inventory.batches().single { it.batch.id == batchId }.balanceMg
        fun ownEvents() = inventory.events().filter { it.batchId == batchId }
        fun invariants() = onMain {
            check(service!!.running && service!!.snapshot.message === message) { "Local UI changed machine service/event" }
            preserved.forEach { (name, values) -> check(context.getSharedPreferences(name, Context.MODE_PRIVATE).all == values) { "Local UI changed $name" } }
            check(inventory.batches().filter { it.batch.id != batchId } == initialBatches)
            check(inventory.events().filter { it.batchId != batchId } == initialEvents)
            check(journal.entries().filter { it.observation.id != shotId } == initialJournal)
            check(custom.list().filter { it.id !in ownCurveIds } == initialCurves)
            opened.filterIsInstance<BeanPreparationActivity>().filter { !it.isDestroyed && it.hasWindowFocus() }.forEach {
                val bound = ScaleOwnerActivity::class.java.getDeclaredField("owner").apply { isAccessible = true }.get(it)
                check(bound === service) { "Preparation bound another service" }
            }
        }
        try {
            onMain {
                check(app.languagePreferences.select(AppLanguage.CHINESE) != AppLanguagePreference.Selection.SAVE_FAILED)
                check(appearance.edit().putBoolean("dark", false).commit())
            }
            home = start(HomeActivity::class.java)
            awaitMain { owner(requireNotNull(home))?.running == true }
            onMain {
                service = owner(requireNotNull(home)); message = service!!.snapshot.message
                oldClip = clipboard.primaryClip; clipboardCaptured = true
                journal.observe(observation)
            }
            var entry = start(BeanEntryActivity::class.java)
            fill(entry, R.string.beans_name, beanName)
            for (invalid in listOf("0", "-1", "18.0001", "9223372036854776")) {
                fill(entry, R.string.beans_initial_input, invalid)
                click(entry, R.string.beans_save)
                check(!entry.isFinishing && inventory.batches() == initialBatches && inventory.events() == initialEvents)
            }
            record("entry-invalid-four")
            fill(entry, R.string.beans_initial_input, "100")
            click(entry, R.string.beans_save)
            awaitMain { entry.isDestroyed }
            opened.remove(entry)
            batchId = inventory.batches().single { it.batch.bean.name == beanName }.batch.id
            check(balance() == 100000L && ownEvents().single().kind == InventoryEventKind.ADD)
            record("entry-save")
            val batch = start(BeanBatchActivity::class.java) { it.putExtra(BeanBatchActivity.BATCH_ID, batchId) }
            click(batch, R.string.beans_adjust)
            var form = dialog(batch)
            fill(form.window!!.decorView, batch.getString(R.string.beans_target), "90")
            fill(form.window!!.decorView, batch.getString(R.string.beans_reason), "UI recount")
            dialogClick(form, AlertDialog.BUTTON_NEGATIVE, batch.getString(R.string.beans_cancel))
            check(balance() == 100000L && ownEvents().size == 1)
            record("adjust-cancel")
            click(batch, R.string.beans_adjust); form = dialog(batch)
            fill(form.window!!.decorView, batch.getString(R.string.beans_target), "90")
            fill(form.window!!.decorView, batch.getString(R.string.beans_reason), "UI recount")
            dialogClick(form, AlertDialog.BUTTON_POSITIVE, batch.getString(R.string.beans_preview))
            val preview = dialog(batch)
            onMain { check(views(preview.window!!.decorView).filterIsInstance<TextView>().any { it.text.toString().contains("100") && it.text.toString().contains("90") }) }
            dialogClick(preview, AlertDialog.BUTTON_POSITIVE, batch.getString(R.string.beans_confirm_adjust))
            check(balance() == 90000L && ownEvents().size == 2 && ownEvents().last().let { it.kind == InventoryEventKind.ADJUST && it.deltaMg == -10000L && it.reason == "UI recount" })
            record("adjust-confirm-preview"); close(batch)
            var prep = start(BeanPreparationActivity::class.java)
            awaitMain { ScaleOwnerActivity::class.java.getDeclaredField("owner").apply { isAccessible = true }.get(prep) === service }
            onMain {
                val selector = views(root(prep)).filterIsInstance<Spinner>().single()
                selector.setSelection(inventory.batches().indexOfFirst { it.batch.id == batchId })
            }
            test.waitForIdleSync()
            fill(prep, R.string.dose_amount, "18.0001")
            click(prep, R.string.dose_save_selection)
            check(preparation.current() == null && balance() == 90000L && ownEvents().size == 2)
            record("dose-invalid-no-write")
            fill(prep, R.string.dose_amount, "18")
            click(prep, R.string.dose_save_selection)
            val selected = requireNotNull(preparation.current())
            check(selected.batchId == batchId && selected.amountMg == 18000L && !selected.confirmed && balance() == 90000L && ownEvents().size == 2)
            record("dose-select-no-deduction")
            // Hold the real button before it is detached by rendering; repeated dispatch must remain idempotent.
            var confirm: TextView? = null
            onMain { confirm = views(root(prep)).filterIsInstance<TextView>().single { it.isClickable && it.text.toString() == prep.getString(R.string.dose_confirm) } }
            click(prep, R.string.dose_confirm)
            onMain { confirm!!.performClick() }
            prep = recreate(prep)
            check(preparation.current()?.eventId == selected.eventId && preparation.current()?.confirmed == true)
            check(balance() == 72000L && ownEvents().count { it.eventId == selected.eventId && it.kind == InventoryEventKind.CONSUME } == 1 && ownEvents().size == 3)
            record("dose-confirm-repeat-recreate")
            click(prep, R.string.dose_discard)
            dialogClick(dialog(prep), AlertDialog.BUTTON_POSITIVE, prep.getString(R.string.dose_discard_confirm))
            check(preparation.current() == null && balance() == 72000L && ownEvents().size == 3)
            record("dose-discard-no-refund"); close(prep)
            var review = start(BrewReviewActivity::class.java) { it.putExtra(BrewReviewActivity.SHOT_ID, shotId) }
            onMain {
                val selector = views(root(review)).filterIsInstance<Spinner>().single()
                selector.setSelection(inventory.batches().map { it.batch.bean }.distinctBy { it.id }.indexOfFirst { it.name == beanName } + 1)
            }
            test.waitForIdleSync()
            fill(review, R.string.journal_dose, "18")
            fill(review, R.string.journal_grind, "UI fine")
            fill(review, R.string.journal_taste, taste)
            fill(review, R.string.journal_next, "UI coarser")
            click(review, R.string.journal_save); awaitMain { review.isDestroyed }; opened.remove(review)
            check(journal.find(shotId)?.observation == observation)
            check(journal.find(shotId)?.notes == BrewJournal.Notes(
                inventory.batches().single { it.batch.id == batchId }.batch.bean.id, beanName, 18000,
                "UI fine", "", taste, "UI coarser"))
            review = start(BrewReviewActivity::class.java) { it.putExtra(BrewReviewActivity.SHOT_ID, shotId) }
            review = recreate(review)
            onMain { check(views(root(review)).filterIsInstance<EditText>().any { it.text.toString() == taste }) }
            record("journal-edit-reopen-recreate"); close(review)
            val editor = start(CurveEditorActivity::class.java)
            val recipeName = "UI recipe $token"
            fill(editor, R.string.profiles_name, recipeName)
            fill(editor, R.string.profiles_temperature, "200")
            click(editor, R.string.profiles_save)
            check(!editor.isFinishing && custom.list() == initialCurves)
            record("curve-editor-invalid")
            fill(editor, R.string.profiles_temperature, "93")
            click(editor, R.string.profiles_save); awaitMain { editor.isDestroyed }; opened.remove(editor)
            val draft = custom.list().single { it.name == recipeName }; ownCurveIds += draft.id
            check(draft.temperatureC == 93 && !app.curves.canStart(requireNotNull(app.curves.find(draft.id))))
            record("curve-editor-save-readonly")
            val share = start(CurveShareActivity::class.java) { it.putExtra(CurveShareActivity.CURVE_ID, draft.id) }
            click(share, R.string.profiles_copy)
            var copied = ""
            onMain { copied = clipboard.primaryClip!!.getItemAt(0).coerceToText(share).toString() }
            check(CustomCurveDocument.decode(copied) == draft && !copied.contains(beanName) && !copied.contains(taste) && !copied.contains(shotId))
            check(org.json.JSONObject(copied).keys().asSequence().toSet() == setOf("schema", "kind", "id", "name", "temperatureC", "targetHundredthsGram", "controlMode", "stages"))
            record("curve-copy-parameters-only"); close(share)
            val imported = draft.copy(id = CustomCurveDocument.newId(), name = "UI imported $token")
            fun importPreview(document: String): Pair<Activity, Activity> {
                val beforePreview = custom.list()
                val input = start(CurveImportActivity::class.java)
                onMain { clipboard.setPrimaryClip(ClipData.newPlainText("UI fixture", document)) }
                click(input, R.string.profiles_paste)
                onMain { check(views(root(input)).filterIsInstance<EditText>().single().text.toString() == document) }
                val monitor = test.addMonitor(CurveImportPreviewActivity::class.java.name, null, false)
                val result: Activity
                try {
                    click(input, R.string.profiles_import_preview)
                    result = test.waitForMonitorWithTimeout(monitor, 10000) ?: error("Import preview missing")
                    opened += result
                    test.waitForIdleSync(); awaitMain { result.hasWindowFocus() && root(result).isLaidOut }
                } finally { test.removeMonitor(monitor) }
                check(custom.list() == beforePreview) { "Preview changed a draft before explicit save" }
                return input to result
            }
            var importPages = importPreview(imported.encode())
            click(importPages.second, R.string.profiles_import_save)
            awaitMain { importPages.second.isDestroyed }; opened.remove(importPages.second)
            ownCurveIds += imported.id
            check(custom.find(imported.id) == imported)
            record("curve-paste-preview-save"); close(importPages.first)
            val beforeRepeat = custom.list()
            importPages = importPreview(imported.encode())
            var repeatButton: TextView? = null
            onMain { repeatButton = views(root(importPages.second)).filterIsInstance<TextView>().single { it.isClickable && it.text.toString() == importPages.second.getString(R.string.profiles_import_save) } }
            click(importPages.second, R.string.profiles_import_save)
            onMain { repeatButton!!.performClick() }
            awaitMain { importPages.second.isDestroyed }; opened.remove(importPages.second)
            check(custom.list() == beforeRepeat)
            record("curve-import-repeat-idempotent"); close(importPages.first)
            val conflict = imported.copy(name = "UI conflict $token")
            importPages = importPreview(conflict.encode())
            click(importPages.second, R.string.profiles_keep_existing)
            awaitMain { importPages.second.isDestroyed }; opened.remove(importPages.second)
            check(custom.list() == beforeRepeat)
            record("curve-conflict-keep-existing"); close(importPages.first)
            importPages = importPreview(conflict.encode())
            click(importPages.second, R.string.profiles_save_copy)
            awaitMain { importPages.second.isDestroyed }; opened.remove(importPages.second)
            val savedCopy = custom.list().single { it.name == conflict.name }
            ownCurveIds += savedCopy.id
            check(savedCopy.id != imported.id && custom.find(imported.id) == imported && savedCopy.copy(id = conflict.id) == conflict)
            record("curve-conflict-save-copy"); close(importPages.first)
            val invalidImport = start(CurveImportActivity::class.java)
            val beforeInvalid = custom.list()
            for (invalid in listOf("{bad", imported.encode().replace("\"schema\":1", "\"schema\":99"), imported.copy(temperatureC = 200).toJson().toString())) {
                check(runCatching { CustomCurveDocument.decode(invalid) }.isFailure) { "Negative import fixture is valid" }
                fill(invalidImport, R.string.profiles_json, invalid)
                click(invalidImport, R.string.profiles_import_preview)
                check(!invalidImport.isFinishing && custom.list() == beforeInvalid)
                onMain {
                    check(invalidImport.hasWindowFocus()) { "Invalid import navigated to another page" }
                    check(views(root(invalidImport)).filterIsInstance<TextView>().any { it.text.toString() == invalidImport.getString(R.string.profiles_import_invalid) })
                }
            }
            record("curve-import-invalid-three"); close(invalidImport)
            check(preparation.current() == null && ownEvents().size == 3 && ownCurveIds.size == 3 && paths.size == 17)
            ownCurveIds.forEach { check(!app.curves.canStart(requireNotNull(app.curves.find(it)))) }
            invariants()
        } catch (error: Throwable) { failure = error }
        finally {
            fun cleanup(action: () -> Unit) { try { action() } catch (error: Throwable) { if (failure == null) failure = error else failure!!.addSuppressed(error) } }
            cleanup { opened.toList().asReversed().filter { it !== home }.forEach(::close) }
            cleanup { onMain { if (clipboardCaptured) { if (oldClip == null) clipboard.clearPrimaryClip() else clipboard.setPrimaryClip(requireNotNull(oldClip)) } } }
            cleanup { onMain { check(app.languagePreferences.select(oldLanguage) != AppLanguagePreference.Selection.SAVE_FAILED); restoreMockPreferences(languagePrefs, oldLanguageStorage); restoreMockPreferences(appearance, oldAppearance) } }
            cleanup {
                home?.let { original -> home = recreate(original); awaitMain { owner(requireNotNull(home)) === service }; invariants() }
            }
        }
        failure?.let { throw it }
        return Result(paths.toList(), ownEvents().size, ownCurveIds.size)
    }
}
