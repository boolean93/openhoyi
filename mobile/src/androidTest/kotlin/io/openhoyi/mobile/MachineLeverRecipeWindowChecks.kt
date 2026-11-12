package io.openhoyi.mobile

import android.app.Instrumentation
import android.app.Service
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import java.io.File

/** Actual Mock windows only. The production journal's storage is temporarily replaced in memory;
 * neither the real journal nor user recipe files are written. No start/stop/BLE actions are used.
 */
internal class MachineLeverRecipeWindowChecks(private val test: Instrumentation) {
    private fun main(action: () -> Unit) = BoundedInstrumentationMain.run("lever recipe window", 5000, action)
    private fun await(stage: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10000
        while (true) {
            var ready = false; main { ready = condition() }
            if (ready) return
            check(SystemClock.elapsedRealtime() < deadline) { "Lever window timeout: $stage" }
            SystemClock.sleep(30)
        }
    }
    private fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun windowText(text: String, click: Boolean = false) {
        val deadline = SystemClock.elapsedRealtime() + 10000
        while (true) {
            val root = test.uiAutomation.rootInActiveWindow
            check(root == null || root.packageName?.toString() == test.targetContext.packageName) { "Unexpected foreground window" }
            val node = root?.findAccessibilityNodeInfosByText(text)?.firstOrNull { it.text?.toString() == text && it.isEnabled }
            if (node != null) {
                if (!click) return
                var target: AccessibilityNodeInfo? = node
                while (target != null && !target.isClickable) target = target.parent
                if (target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return
            }
            check(SystemClock.elapsedRealtime() < deadline) { "Missing lever window text: $text" }
            SystemClock.sleep(40)
        }
    }
    fun run() {
        val context = test.targetContext
        check(BuildConfig.MOCK_MODE && context.packageName == "io.openhoyi.mobile.mock")
        val app = context.applicationContext as MobileApplication
        val lever = app.leverRecipes
        val records = lever.javaClass.getDeclaredField("records").apply { isAccessible = true }
        val usable = lever.javaClass.getDeclaredField("usable").apply { isAccessible = true }
        val storage = lever.javaClass.getDeclaredField("storage").apply { isAccessible = true }
        val originalRecords = records.get(lever)
        val originalUsable = usable.getBoolean(lever)
        val originalStorage = storage.get(lever)
        val journalPrefs = context.getSharedPreferences("machine_lever_recipes", Context.MODE_PRIVATE)
        val originalJournal = journalPrefs.all.toMap()
        val curvePrefs = context.getSharedPreferences("curves", Context.MODE_PRIVATE)
        val slotPrefs = context.getSharedPreferences("presets", Context.MODE_PRIVATE)
        val originalSelection = curvePrefs.getString("selected", null)
        val originalSlots = slotPrefs.all.toMap()
        val custom = app.customCurvesResult.getOrThrow()
        val docs = custom.javaClass.getDeclaredField("documents").apply { isAccessible = true }
        val originalDocs = docs.get(custom)
        val curveFile = File(context.filesDir, "custom_curves_v1.json")
        val originalCurveBytes = if (curveFile.exists()) curveFile.readBytes() else null
        val oldHistory = app.history.entries
        val memory = object : MachineLeverRecipeStore.Storage {
            var bytes: String? = null
            var writes = 0
            override fun read() = bytes
            override fun write(value: String): Boolean { bytes = value; writes++; return true }
        }
        val document = CustomCurveDocument(CustomCurveDocument.newId(), "Lever QA", 92, 3600,
            CustomCurveDocument.ControlMode.PRESSURE,
            listOf(CustomCurveDocument.Stage(30, 150), CustomCurveDocument.Stage(90, 400)))
        var activity: MainActivity? = null
        val directory = File(context.getExternalFilesDir(null), "machine-lever-recipe-checks").apply { mkdirs() }
        fun capture(name: String) {
            SystemClock.sleep(450)
            val bitmap = checkNotNull(test.uiAutomation.takeScreenshot())
            try { File(directory, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
            finally { bitmap.recycle() }
        }
        try {
            main {
                storage.set(lever, memory); records.set(lever, emptyList<MachineLeverRecipeStore.Record>()); usable.setBoolean(lever, true)
                // Match upgrade's detached Service boundary: attach context, never onCreate/BLE.
                val detached = MobileService()
                val wrapped = ContextWrapper(app)
                ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
                    .apply { isAccessible = true }.invoke(detached, wrapped)
                check(detached.application == null)
                val journalGetter = MobileService::class.java.getDeclaredMethod("getLeverStore")
                    .apply { isAccessible = true }
                check(journalGetter.invoke(detached) === lever) { "Detached Service must use the process journal" }
                // Match lifecycle's attached owner with an isolated applicationContext wrapper.
                val attached = MobileService()
                val isolated = object : ContextWrapper(app) {
                    override fun getApplicationContext(): Context = this
                }
                ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
                    .apply { isAccessible = true }.invoke(attached, isolated)
                Service::class.java.getDeclaredField("mApplication").apply { isAccessible = true }.set(attached, app)
                check(attached.applicationContext === isolated && attached.application === app)
                check(journalGetter.invoke(attached) === lever) { "Attached Service must prefer its framework owner" }
                usable.setBoolean(lever, false)
                try {
                    for (subject in listOf(detached, attached)) {
                        check(subject.machineControlSafetyMessage == subject.getString(R.string.lever_unresolved))
                        check(subject.leverRecipeWriteBlock() == subject.getString(R.string.lever_unresolved))
                    }
                } finally { usable.setBoolean(lever, true) }

                @Suppress("UNCHECKED_CAST") val source = originalDocs as Map<String, CustomCurveDocument>
                docs.set(custom, LinkedHashMap(source).apply { put(document.id, document) })
            }
            val page = MainPageTestHost.launch(test, timeoutMs = 15000)
            val host = page.activity; activity = host
            await("Mock service") { host.currentService?.running == true }
            val service = requireNotNull(host.currentService)
            val address = requireNotNull(service.leverDeviceAddress)
            check(address == "00:00:00:00:00:01")
            val shotState = service.shotState
            fun noMachineOrShot() {
                check(MobileService::class.java.getDeclaredField("hub").apply { isAccessible = true }.get(service) == null)
                check(service.shotState == shotState && app.history.entries == oldHistory)
                check(journalPrefs.all == originalJournal && slotPrefs.all == originalSlots)
            }
            main { noMachineOrShot(); check(service.leverRecipeWriteBlock() == null) }
            fun select(destination: TaskDestination) {
                main { host.select(destination) }
                await("navigation $destination") { runCatching { page.active("root:${destination.name}") }.isSuccess }
            }
            select(TaskDestination.BREW)
            fun assertOnlyLocalSlots() {
                val root = page.active("root:BREW").requireView()
                check(root.findViewWithTag<View>("product/home/direct-preset/0") == null)
                check((1..5).all { root.findViewWithTag<View>("product/home/direct-preset/$it") != null })
                val container = root.findViewWithTag<ViewGroup>("product/home/direct-presets")
                val cards = views(container).filter { it.tag?.toString()?.matches(Regex("product/home/direct-preset/[0-5]")) == true }
                check(cards.size == 5) { "Empty lever card retained layout space" }
            }
            await("empty lever hidden") {
                page.active("root:BREW").requireView().findViewWithTag<View>("product/home/direct-preset/0") == null
            }
            main { assertOnlyLocalSlots() }
            capture("empty-five-shortcuts")
            // First pending/failed/unknown attempts are not a configured lever recipe.
            val fixture = MachineLeverRecipeStore.Snapshot(address, document.id, document.name,
                "08825c0046031e5a0000009608019000000000d8", 1234L, "1.1.5")
            for (state in listOf(MachineLeverRecipeStore.State.FAILED, MachineLeverRecipeStore.State.UNKNOWN)) {
                var token = ""
                main { token = requireNotNull(lever.begin(fixture)) }
                SystemClock.sleep(350)
                main { assertOnlyLocalSlots(); check(lever.complete(token, state)) }
                SystemClock.sleep(350)
                main {
                    assertOnlyLocalSlots()
                    if (state == MachineLeverRecipeStore.State.UNKNOWN) check(lever.acknowledge(token))
                }
            }
            select(TaskDestination.CURVES)
            main {
                val root = page.active("root:CURVES").requireView()
                views(root).filterIsInstance<EditText>().single().setText(document.name)
                val list = views(root).filterIsInstance<ListView>().single()
                check(list.adapter.count == 1)
                check(list.performItemClick(list.getChildAt(0), 0, list.adapter.getItemId(0)))
            }
            await("direct lever action") { page.active("root:CURVES").requireView().findViewWithTag<View>("curve-set-lever-recipe")?.isShown == true }
            android.os.SystemClock.sleep(3500) // Avoid residual Toasts from the preceding check.
            capture("direct-actions")
            fun openConfirmation() {
                main { check(page.active("root:CURVES").requireView().findViewWithTag<View>("curve-set-lever-recipe").performClick()) }
                windowText(host.getString(R.string.lever_confirm, document.name))
            }
            val writesBeforeCancel = memory.writes
            val attemptBeforeCancel = lever.attemptFor(address)
            openConfirmation()
            capture("confirmation")
            windowText(host.getString(android.R.string.cancel), click = true)
            main {
                check(memory.writes == writesBeforeCancel && lever.attemptFor(address) == attemptBeforeCancel)
                check(lever.recordFor(address) == null && !lever.blocked); noMachineOrShot()
            }
            openConfirmation()
            windowText(host.getString(R.string.lever_set_action), click = true)
            await("confirmed Mock send") { lever.recordFor(address)?.snapshot?.name == document.name && !lever.blocked }
            val sent = requireNotNull(lever.recordFor(address))
            check(sent.state == MachineLeverRecipeStore.State.SENT)
            select(TaskDestination.BREW)
            fun tile() = page.active("root:BREW").requireView().findViewWithTag<View>("product/home/direct-preset/0")
            fun tileText(text: String): Boolean = views(tile().parent as View).filterIsInstance<TextView>().any { it.text.toString() == text }
            await("sent first card") { tileText(document.name) }
            main {
                val root = page.active("root:BREW").requireView()
                check((0..5).all { root.findViewWithTag<View>("product/home/direct-preset/$it") != null })
                check(root.findViewWithTag<View>("product/home/direct-preset/0/preview").visibility == View.VISIBLE)
                noMachineOrShot()
            }
            // Repeated telemetry renders must not replace unchanged chrome or preview objects.
            var originalBackground: android.graphics.drawable.Drawable? = null
            var originalPreview: CurveStageParameters.Preview? = null
            main {
                originalBackground = tile().background
                originalPreview = page.active("root:BREW").requireView()
                    .findViewWithTag<CurveLibrarySparklineView>("product/home/direct-preset/0/preview").preview
            }
            android.os.SystemClock.sleep(3500) // Covers many render ticks and lets the send Toast expire.
            main {
                check(tile().background === originalBackground) { "Unchanged lever card recreated its background" }
                check(page.active("root:BREW").requireView()
                    .findViewWithTag<CurveLibrarySparklineView>("product/home/direct-preset/0/preview").preview === originalPreview) {
                    "Unchanged lever card reset its preview"
                }
            }
            capture("sent-first-card")

            // Same immutable source snapshot, a new uncertain attempt. This is only a journal fixture.
            main {
                val token = requireNotNull(lever.begin(sent.snapshot.copy(name = "Uncertain attempt")))
                check(lever.complete(token, MachineLeverRecipeStore.State.UNKNOWN))
            }
            await("unknown first card") { tileText(host.getString(R.string.lever_unknown_short)) }
            main {
                check(service.machineControlSafetyMessage != null)
                check(page.active("root:BREW").requireView().findViewWithTag<View>("product/home/direct-preset/0/preview").visibility != View.VISIBLE)
                check(page.active("root:BREW").requireView()
                    .findViewWithTag<CurveLibrarySparklineView>("product/home/direct-preset/0/preview").preview.stages.isEmpty())
                check(tile().performClick())
            }
            windowText(host.getString(R.string.lever_ack_action))
            capture("unknown-confirmation")
            windowText(host.getString(R.string.lever_ack_action), click = true)
            await("unknown acknowledgement") { !lever.blocked }
            main {
                check(lever.attemptFor(address)?.state == MachineLeverRecipeStore.State.UNKNOWN)
                check(lever.attemptFor(address)?.acknowledged == true)
                check(lever.recordFor(address) == sent) // acknowledgement is never verification
            }
            val writesAfterAck = memory.writes
            SystemClock.sleep(350)
            main { check(memory.writes == writesAfterAck); noMachineOrShot() }

            main {
                val token = requireNotNull(lever.begin(sent.snapshot.copy(name = "Failed attempt")))
                check(lever.complete(token, MachineLeverRecipeStore.State.FAILED))
            }
            await("failed keeps last sent") { tileText(document.name) }
            main { check(lever.attemptFor(address)?.state == MachineLeverRecipeStore.State.FAILED); noMachineOrShot() }
            capture("failed-last-sent")

            val otherAddress = "00:00:00:00:00:02"
            main {
                val token = requireNotNull(lever.begin(sent.snapshot.copy(address = otherAddress, name = "Other machine")))
                check(lever.complete(token, MachineLeverRecipeStore.State.SENT))
            }
            SystemClock.sleep(350)
            main {
                check(tileText(document.name) && !tileText("Other machine"))
                val token = requireNotNull(lever.begin(sent.snapshot.copy(address = otherAddress, name = "Other unknown")))
                check(lever.complete(token, MachineLeverRecipeStore.State.UNKNOWN))
                check(service.acknowledgeLeverRecipeUnknown() != null) // cannot clear another machine's unknown
                check(lever.blocked && lever.blockingAttempt?.snapshot?.address == otherAddress)
                check(tileText(document.name)); noMachineOrShot()
            }
            capture("machine-isolation")
            // Simulate moving to a device without a journal in this isolated in-memory fixture.
            main { records.set(lever, emptyList<MachineLeverRecipeStore.Record>()) }
            await("sent card removed without placeholder") {
                page.active("root:BREW").requireView().findViewWithTag<View>("product/home/direct-preset/0") == null
            }
            main { assertOnlyLocalSlots(); noMachineOrShot() }
            capture("removed-five-shortcuts")
        } finally {
            try {
                main {
                    try { activity?.finish() }
                    finally {
                        docs.set(custom, originalDocs)
                        records.set(lever, originalRecords); usable.setBoolean(lever, originalUsable); storage.set(lever, originalStorage)
                    }
                }
            } finally {
                check(curvePrefs.edit().apply { if (originalSelection == null) remove("selected") else putString("selected", originalSelection) }.commit())
                check(journalPrefs.all == originalJournal) { "User lever journal changed" }
                check(slotPrefs.all == originalSlots) { "User shortcuts changed" }
                check(if (originalCurveBytes == null) !curveFile.exists() else curveFile.readBytes().contentEquals(originalCurveBytes))
                check(app.history.entries == oldHistory)
            }
        }
    }
}
