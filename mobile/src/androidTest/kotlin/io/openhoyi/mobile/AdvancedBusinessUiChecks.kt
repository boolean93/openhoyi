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
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import io.openhoyi.bean.InventoryEventKind
import io.openhoyi.bean.BeanInventory
import io.openhoyi.bean.InventoryStorage
import java.io.File
import java.time.LocalDate
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
    /** Capture the already exercised state, not a new fixture or a replacement for assertions. */
    private fun captureState(activity: Activity, name: String, requiredActions: List<Int>) {
        test.waitForIdleSync()
        awaitMain { activity.hasWindowFocus() && root(activity).isLaidOut && !root(activity).isLayoutRequested }
        onMain {
            val labels = views(root(activity)).filterIsInstance<TextView>()
            requiredActions.forEach { id ->
                check(labels.any { it.isShown && it.isClickable && it.text.toString() == activity.getString(id) }) {
                    "$name missing expected action ${activity.getString(id)}"
                }
            }
        }
        awaitUnobscuredContent(activity, name)
        val directory = File(requireNotNull(context.getExternalFilesDir(null)), "advanced-page-screenshots/business")
        check(directory.isDirectory || directory.mkdirs())
        val bitmap = requireNotNull(test.uiAutomation.takeScreenshot()) { "$name screenshot missing" }
        try {
            File(directory, "$name.png").outputStream().use {
                check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { bitmap.recycle() }
    }
    /** Read-only: let transient clipboard controls expire; never dismiss UI or alter clipboard data. */
    private fun awaitUnobscuredContent(activity: Activity, name: String) {
        val automation = test.uiAutomation
        val info = automation.serviceInfo
        val originalFlags = info.flags
        try {
            info.flags = originalFlags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            automation.serviceInfo = info
            val deadline = SystemClock.elapsedRealtime() + 15000
            while (true) {
                val contentBounds = android.graphics.Rect()
                onMain { check(root(activity).getGlobalVisibleRect(contentBounds)) }
                val overlaps = mutableListOf<String>()
                fun inspect(node: android.view.accessibility.AccessibilityNodeInfo) {
                    try {
                        if (node.isVisibleToUser && node.isClickable) {
                            val bounds = android.graphics.Rect()
                            node.getBoundsInScreen(bounds)
                            if (android.graphics.Rect.intersects(bounds, contentBounds))
                                overlaps += "${node.packageName}/${node.className} bounds=$bounds"
                        }
                        for (index in 0 until node.childCount) node.getChild(index)?.let(::inspect)
                    } finally { node.recycle() }
                }
                val windows = automation.windows
                var appWindowObserved = false
                try {
                    for (window in windows) {
                        val node = window.root ?: continue
                        if (node.packageName?.toString() == activity.packageName) {
                            appWindowObserved = true
                            node.recycle()
                        } else inspect(node)
                    }
                } finally { windows.forEach { it.recycle() } }
                if (appWindowObserved && overlaps.isEmpty()) return
                check(SystemClock.elapsedRealtime() < deadline) {
                    "$name screenshot not ready: appWindowObserved=$appWindowObserved systemControls=${overlaps.joinToString()}"
                }
                Thread.sleep(100)
            }
        } finally {
            info.flags = originalFlags
            automation.serviceInfo = info
        }
    }
    private fun click(activity: Activity, label: Int) = click(root(activity), activity.getString(label))
    private fun click(root: View, text: String) {
        if (CurveMenuChecks.clickIfNeeded(test, root, text)) return
        onMain {
            val button = views(root).filterIsInstance<TextView>().single { it.isShown && it.isClickable && it.text.toString() == text }
            check(button.isEnabled) { "Disabled UI action: $text" }
            button.requestRectangleOnScreen(android.graphics.Rect(0, 0, button.width, button.height), true)
            check(button.performClick()) { "Action not handled: $text" }
        }
        test.waitForIdleSync()
    }

    /** Resolve every field by its actual label, and select its live visible section before access. */
    private fun visibleInput(root: View, label: String, index: Int = 0): EditText {
        lateinit var input: EditText
        var sections: Spinner? = null
        var shown = false
        var sectionCount = 0
        onMain {
            val accessibleInput = views(root).filterIsInstance<EditText>()
                .filter { it.contentDescription?.toString() == label }.getOrNull(index)
            input = accessibleInput ?: run {
                val title = views(root).filterIsInstance<TextView>()
                    .filter { it.text.toString() == label && it.labelFor != View.NO_ID }
                    .getOrNull(index) ?: error("Input label not found: $label index=$index")
                root.findViewById(title.labelFor)
            }
            sections = views(root).filterIsInstance<Spinner>().singleOrNull { it.tag == "bean-sections" }
            shown = input.isShown
            sectionCount = sections?.count ?: 0
        }
        if (!shown && sections != null) for (position in 0 until sectionCount) {
            onMain { sections!!.setSelection(position) }
            test.waitForIdleSync()
            awaitMain { sections!!.selectedItemPosition == position && !root.isLayoutRequested }
            onMain { shown = input.isShown }
            if (shown) break
        }
        onMain {
            input.requestRectangleOnScreen(android.graphics.Rect(0, 0, input.width, input.height), true)
        }
        test.waitForIdleSync()
        awaitMain { !root.isLayoutRequested }
        onMain {
            val rect = android.graphics.Rect()
            check(input.isShown && input.isEnabled && input.getGlobalVisibleRect(rect) &&
                rect.width() == input.width && rect.height() == input.height && input.width > 0 && input.height > 0) {
                "Input section is not fully visible/enabled: $label index=$index"
            }
        }
        return input
    }
    private fun fill(root: View, label: String, value: String, index: Int = 0) {
        val input = visibleInput(root, label, index)
        onMain { input.setText(value); check(input.text.toString() == value) { "Input rejected fixture: $label" } }
    }
    private fun fill(activity: Activity, label: Int, value: String, index: Int = 0) = fill(root(activity), activity.getString(label), value, index)
    private fun assertInput(activity: Activity, label: Int, expected: String, index: Int = 0) {
        val input = visibleInput(root(activity), activity.getString(label), index)
        onMain { check(input.text.toString() == expected) { "Restored field differs: ${activity.getString(label)} index=$index expected=$expected actual=${input.text}" } }
    }
    private fun assertReviewFields(activity: Activity, notes: BrewJournal.Notes) {
        assertInput(activity, R.string.journal_dose, BeanQuantity.formatGrams(requireNotNull(notes.doseMg)))
        onMain {
            val bean = views(root(activity)).filterIsInstance<Spinner>().single { it.contentDescription == activity.getString(R.string.journal_bean) }
            check(bean.isShown && bean.selectedItem.toString() == notes.beanName) { "Restored journal bean selection differs" }
        }
        assertInput(activity, R.string.journal_grind, notes.grind)
        assertInput(activity, R.string.journal_expectation, notes.curveExpectation)
        assertInput(activity, R.string.journal_taste, notes.taste)
        assertInput(activity, R.string.journal_next, notes.nextAdjustment)
    }
    private fun assertCurveFields(activity: Activity, name: String, temperature: Int, cup: Int, stages: List<CustomCurveDocument.Stage>) {
        assertInput(activity, R.string.profiles_name, name)
        assertInput(activity, R.string.profiles_temperature, temperature.toString())
        assertInput(activity, R.string.profiles_cup, CurveDraftNumber.format(cup, 2))
        onMain {
            val count = views(root(activity)).filterIsInstance<CurveStageCountTabs>().single()
            check(count.isShown && count.selectedCount == stages.size) { "Restored curve stage count differs" }
        }
        stages.forEachIndexed { index, stage ->
            assertInput(activity, R.string.profiles_pressure, CurveDraftNumber.format(stage.target, 1), index)
            assertInput(activity, R.string.profiles_water, CurveDraftNumber.format(stage.waterTenthsMl, 1), index)
        }
    }
    private fun persistedBytes(name: String): ByteArray = requireNotNull(
        AtomicDocumentStorage(File(context.filesDir, name).toPath()).read()) { "Saved document missing on disk: $name" }
    private fun persistedInventory(): BeanInventory {
        val bytes = persistedBytes("bean_inventory_v1.bin")
        return BeanInventory(object : InventoryStorage {
            override fun read(): ByteArray = bytes.copyOf()
            override fun write(bytes: ByteArray) { error("Read-only inventory verification cannot write") }
        })
    }
    private fun persistedJournal(): BrewJournal {
        val document = persistedBytes("brew_journal_v1.json").toString(Charsets.UTF_8)
        return BrewJournal(object : BrewJournal.Storage {
            override fun read(): String = document
            override fun write(value: String) { error("Read-only journal verification cannot write") }
        })
    }
    private fun persistedCurves(): CustomCurveStore {
        val document = persistedBytes("custom_curves_v1.json").toString(Charsets.UTF_8)
        return CustomCurveStore(object : CustomCurveStore.Storage {
            override fun read(): String = document
            override fun write(value: String) { error("Read-only curve verification cannot write") }
        })
    }
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
        val grind = "UI fine $token"
        val expectation = "UI expectation $token"
        val nextAdjustment = "UI coarser $token"
        val purchased = LocalDate.of(2026, 10, 2)
        val roasted = LocalDate.of(2026, 9, 28)
        val openedDate = LocalDate.of(2026, 10, 3)
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
        fun assertLockedBatchContext(page: Activity, expectedBalance: String) = onMain {
            val row = root(page).findViewWithTag<TextView>("dose-batch-summary-$batchId")
            check(row != null && row.isSelected && !row.isClickable && !row.isLongClickable) {
                "Saved preparation lost its read-only selected batch context"
            }
            check(row.text.toString() == page.getString(R.string.dose_batch_choice, beanName, expectedBalance,
                page.getString(R.string.beans_batch_id, (page as BeanPreparationActivity).beanBatchNumber(inventory, requireNotNull(batchId))))) {
                "Saved preparation batch context showed stale inventory"
            }
        }
        fun captureHomeDose(name: String, state: Int) {
            val beforeDose = preparation.current()
            val beforeEvents = inventory.events()
            val beforeBalance = balance()
            val page = start(HomeActivity::class.java)
            try {
                awaitMain { owner(page) === service && views(root(page)).filterIsInstance<TextView>().any {
                    it.isShown && it.text.toString() == page.getString(state)
                } }
                onMain {
                    val quantity = root(page).findViewWithTag<TextView>("home-dose-quantity")
                    check(quantity.isShown && quantity.text.toString() == "18 g") {
                        "Home did not display the selected dose"
                    }
                    check(views(root(page)).filterIsInstance<TextView>().any {
                        it.isShown && it.text.toString().contains(beanName)
                    }) { "Home dose lost its bean identity" }
                }
                captureState(page, name, emptyList())
                check(preparation.current() == beforeDose && inventory.events() == beforeEvents && balance() == beforeBalance) {
                    "Viewing the home dose changed preparation or inventory"
                }
            } finally { close(page) }
        }
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
            fill(entry, R.string.beans_purchased, purchased.toString())
            fun purchasePicker(): android.app.DatePickerDialog {
                var picker: android.app.DatePickerDialog? = null
                onMain {
                    check(root(entry).findViewWithTag<View>("bean-date-picker-purchased").performClick())
                    picker = BeanEntryActivity::class.java.getDeclaredField("dateDialog").apply { isAccessible = true }
                        .get(entry) as android.app.DatePickerDialog
                    check(requireNotNull(picker).isShowing)
                }
                test.waitForIdleSync()
                return requireNotNull(picker)
            }
            val cancelledDate = purchasePicker()
            onMain {
                check(cancelledDate.datePicker.year == purchased.year && cancelledDate.datePicker.month == purchased.monthValue - 1 &&
                    cancelledDate.datePicker.dayOfMonth == purchased.dayOfMonth)
                cancelledDate.datePicker.updateDate(2026, 9, 3)
                cancelledDate.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            }
            assertInput(entry, R.string.beans_purchased, purchased.toString())
            val confirmedDate = purchasePicker()
            onMain {
                confirmedDate.datePicker.updateDate(2026, 9, 3)
                confirmedDate.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            }
            assertInput(entry, R.string.beans_purchased, "2026-10-03")
            fill(entry, R.string.beans_purchased, purchased.toString())
            fill(entry, R.string.beans_roasted, roasted.toString())
            fill(entry, R.string.beans_opened, openedDate.toString())
            entry = recreate(entry)
            assertInput(entry, R.string.beans_name, beanName)
            assertInput(entry, R.string.beans_initial_input, "100")
            assertInput(entry, R.string.beans_purchased, purchased.toString())
            assertInput(entry, R.string.beans_roasted, roasted.toString())
            assertInput(entry, R.string.beans_opened, openedDate.toString())
            click(entry, R.string.beans_save)
            awaitMain { entry.isDestroyed }
            opened.remove(entry)
            batchId = inventory.batches().single { it.batch.bean.name == beanName }.batch.id
            check(balance() == 100000L && ownEvents().single().kind == InventoryEventKind.ADD)
            val savedBatch = inventory.batches().single { it.batch.id == batchId }.batch
            check(savedBatch.initialMg == 100000L && savedBatch.bean.name == beanName &&
                savedBatch.purchasedOn == purchased && savedBatch.roastedOn == roasted && savedBatch.openedOn == openedDate) { "Saved bean batch lost a date/field" }
            check(persistedInventory().batches().single { it.batch.id == batchId }.batch == savedBatch) { "Bean batch differs after disk reload" }
            record("entry-save")
            var modeEntry = start(BeanEntryActivity::class.java)
            fill(modeEntry, R.string.beans_name, "Unsaved new bean")
            fun selectIdentityMode(existing: Boolean) {
                onMain {
                    check(root(modeEntry).findViewWithTag<View>(if (existing) "bean-mode-existing" else "bean-mode-new").performClick())
                }
                test.waitForIdleSync()
            }
            fun assertExistingIdentity() {
                onMain {
                    val choice = root(modeEntry).findViewWithTag<android.widget.Spinner>("bean-existing-choice")
                    check(choice.isShown && choice.selectedItem.toString() == beanName)
                    val selected = BeanEntryActivity::class.java.getDeclaredField("selectedBeanId").apply { isAccessible = true }
                    check(selected.get(modeEntry) == savedBatch.bean.id)
                    val nameLabel = views(root(modeEntry)).filterIsInstance<TextView>().single {
                        it.text.toString() == modeEntry.getString(R.string.beans_name) && it.labelFor != View.NO_ID
                    }
                    val nameField = root(modeEntry).findViewById<EditText>(nameLabel.labelFor)
                    check(nameField.isShown && !nameField.isEnabled && nameField.text.toString() == beanName)
                }
            }
            fill(modeEntry, R.string.beans_initial_input, "250")
            selectIdentityMode(true)
            onMain {
                val choice = root(modeEntry).findViewWithTag<android.widget.Spinner>("bean-existing-choice")
                val position = (0 until choice.count).single { choice.getItemAtPosition(it).toString() == beanName }
                choice.setSelection(position)
            }
            test.waitForIdleSync()
            modeEntry = recreate(modeEntry)
            assertExistingIdentity()
            captureState(modeEntry, "bean-entry-existing-mode", listOf(R.string.beans_save))
            selectIdentityMode(false)
            assertInput(modeEntry, R.string.beans_name, "Unsaved new bean")
            captureState(modeEntry, "bean-entry-new-mode", listOf(R.string.beans_save))
            modeEntry = recreate(modeEntry)
            selectIdentityMode(true)
            assertExistingIdentity()
            onMain { modeEntry.finish() }
            awaitMain { modeEntry.isDestroyed }
            opened.remove(modeEntry)
            check(balance() == 100000L && ownEvents().size == 1)
            record("entry-mode-draft-recreate-cancel")
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
            captureState(batch, "batch-after-adjustment", listOf(R.string.beans_adjust, R.string.beans_ledger))
            record("adjust-confirm-preview"); close(batch)
            val inventoryPage = start(BeanInventoryActivity::class.java)
            onMain {
                val row = root(inventoryPage).findViewWithTag<android.widget.Button>("inventory-batch-$batchId")
                check(row.isShown && row.isEnabled)
                row.requestRectangleOnScreen(android.graphics.Rect(0, 0, row.width, row.height), true)
                check(row.performClick())
            }
            test.waitForIdleSync()
            val prepMonitor = test.addMonitor(BeanPreparationActivity::class.java.name, null, false)
            var prep: Activity
            try {
                click(inventoryPage, R.string.dose_title)
                prep = test.waitForMonitorWithTimeout(prepMonitor, 10000) ?: error("Inventory preparation entry missing")
                opened += prep
                test.waitForIdleSync()
                awaitMain { prep.hasWindowFocus() && root(prep).isLaidOut && !root(prep).isLayoutRequested }
            } finally { test.removeMonitor(prepMonitor) }
            check(prep.intent.getStringExtra(BeanPreparationActivity.EXTRA_BATCH_ID) == batchId)
            check(preparation.current() == null && balance() == 90000L && ownEvents().size == 2) { "Inventory navigation consumed or saved a dose" }
            awaitMain { ScaleOwnerActivity::class.java.getDeclaredField("owner").apply { isAccessible = true }.get(prep) === service }
            onMain {
                val choice = root(prep).findViewWithTag<Button>("dose-batch-$batchId")
                check(choice.isShown && choice.isSelected && choice.isEnabled) { "Inventory entry lost preselected batch" }
                check(choice.performClick())
            }
            test.waitForIdleSync()
            fill(prep, R.string.dose_amount, "18.0001")
            click(prep, R.string.dose_save_selection)
            check(preparation.current() == null && balance() == 90000L && ownEvents().size == 2)
            record("dose-invalid-no-write")
            fun assertDoseError(visible: Boolean) = onMain {
                val shown = views(root(prep)).filterIsInstance<TextView>().any {
                    it.isShown && it.text.toString() == prep.getString(R.string.dose_bad_amount)
                }
                check(shown == visible) { "Dose validation feedback disagrees with corrected input" }
            }
            assertDoseError(true)
            fill(prep, R.string.dose_amount, "0")
            assertDoseError(true)
            fill(prep, R.string.dose_amount, "18")
            assertDoseError(false)
            onMain {
                val preview = root(prep).findViewWithTag<TextView>("dose-balance-preview")
                check(preview.text.toString() == prep.getString(R.string.dose_balance_preview, "90", "72"))
            }
            check(balance() == 90000L && ownEvents().size == 2)
            captureState(prep, "dose-selected-draft", listOf(R.string.dose_save_selection))
            click(prep, R.string.dose_save_selection)
            val selected = requireNotNull(preparation.current())
            check(selected.batchId == batchId && selected.amountMg == 18000L && !selected.confirmed && balance() == 90000L && ownEvents().size == 2)
            onMain {
                val preview = root(prep).findViewWithTag<TextView>("dose-balance-preview")
                check(preview.text.toString() == prep.getString(R.string.dose_balance_preview, "90", "72"))
                check(root(prep).findViewWithTag<TextView>("dose-balance-receipt") == null)
            }
            captureState(prep, "dose-pending-confirmation", listOf(R.string.dose_confirm))
            assertLockedBatchContext(prep, "90")
            captureHomeDose("home-dose-selected", R.string.task_brew_dose_selected)
            record("dose-select-no-deduction")
            // Hold the real button before it is detached by rendering; repeated dispatch must remain idempotent.
            var confirm: TextView? = null
            onMain { confirm = views(root(prep)).filterIsInstance<TextView>().single { it.isClickable && it.text.toString() == prep.getString(R.string.dose_confirm) } }
            click(prep, R.string.dose_confirm)
            onMain { confirm!!.performClick() }
            prep = recreate(prep)
            check(preparation.current()?.eventId == selected.eventId && preparation.current()?.confirmed == true)
            check(balance() == 72000L && ownEvents().count { it.eventId == selected.eventId && it.kind == InventoryEventKind.CONSUME } == 1 && ownEvents().size == 3)
            onMain {
                val receipt = root(prep).findViewWithTag<TextView>("dose-balance-receipt")
                check(receipt.text.toString() == prep.getString(R.string.dose_balance_receipt, "90", "72"))
            }
            captureState(prep, "dose-confirmed-receipt", listOf(R.string.dose_discard))
            assertLockedBatchContext(prep, "72")
            captureHomeDose("home-dose-confirmed", R.string.task_brew_dose_confirmed)
            record("dose-confirm-repeat-recreate")
            click(prep, R.string.dose_discard)
            dialogClick(dialog(prep), AlertDialog.BUTTON_POSITIVE, prep.getString(R.string.dose_discard_confirm))
            check(preparation.current() == null && balance() == 72000L && ownEvents().size == 3)
            record("dose-discard-no-refund"); close(prep)
            var review = start(BrewReviewActivity::class.java) { it.putExtra(BrewReviewActivity.SHOT_ID, shotId) }
            onMain {
                val selector = views(root(review)).filterIsInstance<Spinner>().single { it.contentDescription == review.getString(R.string.journal_bean) }
                selector.setSelection(inventory.batches().map { it.batch.bean }.distinctBy { it.id }.indexOfFirst { it.name == beanName } + 1)
            }
            test.waitForIdleSync()
            fill(review, R.string.journal_dose, "18")
            fill(review, R.string.journal_grind, grind)
            fill(review, R.string.journal_expectation, expectation)
            fill(review, R.string.journal_taste, taste)
            fill(review, R.string.journal_next, nextAdjustment)
            val expectedNotes = BrewJournal.Notes(savedBatch.bean.id, beanName, 18000,
                grind, expectation, taste, nextAdjustment)
            review = recreate(review)
            assertReviewFields(review, expectedNotes)
            click(review, R.string.journal_save); awaitMain { review.isDestroyed }; opened.remove(review)
            check(journal.find(shotId)?.observation == observation)
            check(journal.find(shotId)?.notes == expectedNotes) { "Journal save lost a notes field" }
            check(persistedJournal().find(shotId) == BrewJournal.Entry(observation, expectedNotes)) { "Journal differs after disk reload" }
            review = start(BrewReviewActivity::class.java) { it.putExtra(BrewReviewActivity.SHOT_ID, shotId) }
            captureState(review, "review-saved-notes", listOf(R.string.journal_save))
            assertReviewFields(review, expectedNotes)
            review = recreate(review)
            assertReviewFields(review, expectedNotes)
            check(journal.find(shotId)?.notes == expectedNotes)
            record("journal-edit-reopen-recreate"); close(review)
            var editor = start(CurveEditorActivity::class.java)
            val recipeName = "UI recipe $token"
            fill(editor, R.string.profiles_name, recipeName)
            fill(editor, R.string.profiles_temperature, "200")
            click(editor, R.string.profiles_save)
            check(!editor.isFinishing && custom.list() == initialCurves)
            record("curve-editor-invalid")
            val temperature = 94
            val cup = 4170
            val stages = listOf(CustomCurveDocument.Stage(24, 120), CustomCurveDocument.Stage(57, 230),
                CustomCurveDocument.Stage(83, 340), CustomCurveDocument.Stage(69, 450))
            fill(editor, R.string.profiles_temperature, temperature.toString())
            fill(editor, R.string.profiles_cup, CurveDraftNumber.format(cup, 2))
            onMain {
                val selector = views(root(editor)).filterIsInstance<CurveStageCountTabs>().single()
                check(selector.isShown && selector.isEnabled) { "Stage count selector is not visible" }
                check(selector.findViewWithTag<Button>("curve-stage-count-4").performClick())
            }
            test.waitForIdleSync()
            awaitMain {
                (0..3).all { index -> views(root(editor)).single { it.tag == "curve-editor-stage-$index" }.isShown }
            }
            stages.forEachIndexed { index, stage ->
                fill(editor, R.string.profiles_pressure, CurveDraftNumber.format(stage.target, 1), index)
                fill(editor, R.string.profiles_water, CurveDraftNumber.format(stage.waterTenthsMl, 1), index)
            }
            onMain {
                val selector = views(root(editor)).filterIsInstance<CurveStageCountTabs>().single()
                check(selector.findViewWithTag<Button>("curve-stage-count-2").performClick())
                check(!views(root(editor)).single { it.tag == "curve-editor-stage-3" }.isShown)
                check(selector.findViewWithTag<Button>("curve-stage-count-4").performClick())
            }
            test.waitForIdleSync()
            assertCurveFields(editor, recipeName, temperature, cup, stages)
            editor = recreate(editor)
            assertCurveFields(editor, recipeName, temperature, cup, stages)
            onMain { views(root(editor)).filterIsInstance<android.widget.ScrollView>().forEach { it.scrollTo(0, 0) } }
            captureState(editor, "curve-editor-four-stages", listOf(R.string.profiles_save))
            click(editor, R.string.profiles_save); awaitMain { editor.isDestroyed }; opened.remove(editor)
            val draft = custom.list().single { it.name == recipeName }; ownCurveIds += draft.id
            val expectedDraft = CustomCurveDocument(draft.id, recipeName, temperature, cup, CustomCurveDocument.ControlMode.PRESSURE, stages)
            check(draft == expectedDraft && persistedCurves().find(draft.id) == expectedDraft) { "Curve document lost a saved field/stage or differs on disk" }
            check(app.curves.canStart(requireNotNull(app.curves.find(draft.id))))
            var reopenedEditor = start(CurveEditorActivity::class.java) { it.putExtra(CurveEditorActivity.CURVE_ID, draft.id) }
            assertCurveFields(reopenedEditor, recipeName, temperature, cup, stages)
            reopenedEditor = recreate(reopenedEditor)
            assertCurveFields(reopenedEditor, recipeName, temperature, cup, stages)
            close(reopenedEditor)
            record("curve-editor-save-compatible-no-auto-start")
            val share = start(CurveShareActivity::class.java) { it.putExtra(CurveShareActivity.CURVE_ID, draft.id) }
            onMain {
                val table = root(share).findViewWithTag<android.widget.LinearLayout>("share-stage-table")
                check(table.isShown && table.childCount == stages.size + 1) { "Four-stage sharing table missing" }
                (0 until table.childCount).forEach { index ->
                    val row = table.getChildAt(index)
                    val bounds = android.graphics.Rect()
                    check(row.getGlobalVisibleRect(bounds) && bounds.width() == row.width && bounds.height() == row.height && row.height > 0) {
                        "Sharing table row $index is clipped: $bounds / ${row.width}x${row.height}"
                    }
                }
                val fileAction = views(root(share)).filterIsInstance<android.widget.Button>().single {
                    it.text.toString() == share.getString(R.string.profiles_share_file)
                }
                check(!fileAction.isEnabled) { "Unexported file must not be shareable" }
            }
            captureState(share, "curve-share-four-stages", listOf(R.string.profiles_copy, R.string.profiles_share_text))
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
            captureState(importPages.second, "import-new", listOf(R.string.profiles_import_save))
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
            captureState(importPages.second, "import-conflict", listOf(R.string.profiles_existing,
                R.string.profiles_keep_existing, R.string.profiles_save_copy))
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
            var invalidImport = start(CurveImportActivity::class.java)
            val beforeInvalid = custom.list()
            val rawImportDraft = "  {未完成的原文\n  保留空格}  "
            fill(invalidImport, R.string.profiles_json, rawImportDraft)
            onMain {
                val field = views(root(invalidImport)).filterIsInstance<EditText>().single()
                check(field.showSoftInputOnFocus && field.isFocusableInTouchMode) { "Import raw text must edit inline" }
                check(field.requestFocus())
                field.setSelection(3, 8)
            }
            invalidImport = recreate(invalidImport)
            onMain {
                val field = views(root(invalidImport)).filterIsInstance<EditText>().single()
                check(field.text.toString() == rawImportDraft && field.hasFocus() &&
                    field.selectionStart == 3 && field.selectionEnd == 8) { "Import recreation changed raw draft/focus/caret" }
                check(custom.list() == beforeInvalid) { "Import editing or recreation wrote a curve" }
                // Return to a non-IME geometry before the unchanged invalid-preview scenarios.
                check((root(invalidImport) as ViewGroup).getChildAt(0).requestFocus())
                (invalidImport.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                    .hideSoftInputFromWindow(field.windowToken, 0)
            }
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
            check(preparation.current() == null && ownEvents().size == 3 && ownCurveIds.size == 3 && paths.size == 18)
            ownCurveIds.forEach {
                val item=requireNotNull(app.curves.find(it))
                check(app.curves.canStart(item) == (CustomPressureCurveAdapter.profile(requireNotNull(item.customDocument),false)!=null))
            }
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
        test.sendStatus(0, android.os.Bundle().apply { putString("stream",
            "ADVANCED_BUSINESS_UI_FULL_FIELDS_PASSED beanDates=3 notesFields=7 curveFields=12 stages=4 durableStores=3 unsavedRecreation=true reopenedRecreation=true paths=18\n") })
        return Result(paths.toList(), ownEvents().size, ownCurveIds.size)
    }
}
