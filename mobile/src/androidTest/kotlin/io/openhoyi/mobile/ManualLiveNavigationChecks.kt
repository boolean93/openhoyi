package io.openhoyi.mobile

import android.app.AlertDialog
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.EditText
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import io.openhoyi.protocol.ByteFrame
import io.openhoyi.protocol.ExtractionTelemetry
import io.openhoyi.protocol.IdleTelemetry
import io.openhoyi.protocol.ScaleCapabilities
import io.openhoyi.protocol.ScaleEvidence
import io.openhoyi.protocol.ScaleObservation
import io.openhoyi.session.DeviceState
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.security.KeyStore

/** Real visible MainActivity route, fed only by the existing synthetic Mock service on a disposable runner. */
internal class ManualLiveNavigationChecks(private val test: Instrumentation) {
    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(descendants(view.getChildAt(i)))
    }
    private fun await(label: String, check: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 5000
        do {
            var ready = false
            test.runOnMainSync { ready = check() }
            if (ready) return
            SystemClock.sleep(50)
        } while (SystemClock.elapsedRealtime() < deadline)
        error("Timed out: $label")
    }
    private fun <T : ThemedActivity> open(main: MainActivity, type: Class<T>): T {
        val monitor = test.addMonitor(type.name, null, false)
        try {
            test.runOnMainSync { main.startActivity(Intent(main, type)) }
            val page = requireNotNull(type.cast(test.waitForMonitorWithTimeout(monitor, 10000)))
            await(type.simpleName) { page.lifecycle.currentState == Lifecycle.State.RESUMED && page.hasWindowFocus() }
            return page
        } finally { test.removeMonitor(monitor) }
    }

    fun run() {
        check(BuildConfig.MOCK_MODE && android.os.Build.HARDWARE in setOf("ranchu", "goldfish"))
        val clock = Any()
        test.runOnMainSync { MobileMonotonicClock.installForMock(clock, SystemClock.elapsedRealtime()) }
        var host: MainPageTestHost? = null
        var activeService: MobileService? = null
        try {
            val runningHost = MainPageTestHost.launch(test)
            host = runningHost
            await("running Mock service") { runningHost.activity.currentService?.running == true }
            val service = requireNotNull(runningHost.activity.currentService)
            activeService = service
            test.runOnMainSync { service.installMockExtractionScenario(clock) }
            runCases(runningHost, service, includeDraftEditors = true, clock = clock)
        } finally {
            test.runOnMainSync {
                activeService?.let { service ->
                    if (service.mockExtractionOfferedFrameSerial > 0 || service.chartTraceIdentity == null)
                        runCatching { service.clearMockExtractionScenario(clock) }
                }
                host?.activity?.finish()
                MobileMonotonicClock.uninstallForMock(clock)
            }
        }
    }

    fun run(host: MainPageTestHost, service: MobileService, includeDraftEditors: Boolean = false) =
        runCases(host, service, includeDraftEditors, null)

    private fun runCases(host: MainPageTestHost, service: MobileService, includeDraftEditors: Boolean, clock: Any?) {
        check(BuildConfig.MOCK_MODE && test.targetContext.packageName == "io.openhoyi.mobile.mock")
        val main = host.activity
        val app = main.application as MobileApplication
        val gate = app.manualShotPresentation
        val historyBefore = app.history.entries.map { it.id }
        val curvePrefs = main.getSharedPreferences("curves", 0)
        val series = if (clock == null) MobileService::class.java.getDeclaredField("series")
            .apply { isAccessible = true }.get(service) as ShotSeries else null
        val beforeSelected = main.getSharedPreferences("curves", 0).getString("selected", null)
        fun foregroundPage() = main.supportFragmentManager.primaryNavigationFragment
        fun frame(seconds: Int) = ExtractionTelemetry(6, seconds, 89, seconds * 10, 9300,
            25, 64, 0, ByteFrame(byteArrayOf()))
        fun elapsed(delta: Long) {
            requireNotNull(clock)
            MobileMonotonicClock.advanceForMock(clock, MobileMonotonicClock.elapsedRealtime() + delta)
        }
        fun start(name: String) {
            test.runOnMainSync {
                gate.observedIdle()
                if (clock != null) {
                    elapsed(450); service.offerMockExtractionFrame(clock, frame(1))
                    elapsed(450); service.offerMockExtractionFrame(clock, frame(2))
                } else {
                    gate.ended()
                    val now = MobileMonotonicClock.elapsedRealtime()
                    requireNotNull(series).begin(name, now - 450, "manual")
                    series.machine(frame(1), now - 450, null, null)
                    series.machine(frame(2), now, null, null)
                    gate.started(name)
                }
                check(gate.activeId != null)
            }
        }
        fun flushEnd() = test.runOnMainSync {
            if (clock != null) {
                elapsed(2901)
                service.offerMockExtractionFrame(clock, IdleTelemetry(9300, 12000, 0, 0, 0, 0,
                    25, 0, ByteFrame(byteArrayOf())))
                check(!service.manualShotActive)
            } else gate.discarded()
        }
        fun noOldPopup() = main.supportFragmentManager.fragments.none { it is ManualLiveDialog }
        fun retainsManualLabel(root: View): Boolean {
            val labels = descendants(root).filterIsInstance<TextView>().filter { it.isShown }
                .map { it.text.toString() }.toList()
            val manual = main.getString(R.string.extraction_manual_label)
            val completed = main.getString(R.string.extraction_ended_guidance)
            return labels.any { manual in it } && labels.none { completed in it }
        }
        val home = host.select(TaskDestination.BREW)
        var child: ThemedActivity? = null
        try {
            start("from-home-provisional")
            await("real ExtractionFragment immediately, before any history commit") {
                val page = foregroundPage() as? ExtractionFragment ?: return@await false
                val view = page.view ?: return@await false
                if (clock == null) noOldPopup() else {
                    val controls = descendants(view).filter { it.tag in setOf("product/extraction/start", "product/extraction/stop") }.toList()
                    val chart = descendants(view).filterIsInstance<ExtractionOverlayView>().singleOrNull()
                    controls.size == 2 && controls.all { it.visibility == View.GONE } &&
                        (chart?.points?.size ?: 0) >= 2 && noOldPopup()
                }
            }
            val provisionalId = service.manualChartIdentity
            if (clock != null) test.runOnMainSync {
                elapsed(1000); service.offerMockExtractionFrame(clock, frame(3))
                elapsed(1000); service.offerMockExtractionFrame(clock, frame(4))
                elapsed(1000); service.offerMockExtractionFrame(clock, frame(5))
            }
            flushEnd()
            await("short flush retains the real chart and manual identity without history") {
                val view = (foregroundPage() as? ExtractionFragment)?.view ?: return@await false
                noOldPopup() && app.history.entries.map { it.id } == historyBefore &&
                    (clock == null || (service.manualChartIdentity == provisionalId && service.chartCurveId == "manual" &&
                        service.chartPoints.size >= 3 && service.manualTerminalOutcome == ManualShotPresentationGate.Outcome.DISCARDED &&
                        retainsManualLabel(view)))
            }
            test.runOnMainSync { main.onBackPressedDispatcher.onBackPressed() }
            await("explicit Back returns to same home instance") { foregroundPage() === home }

            val existingExtraction = host.extraction()
            start("existing-extraction")
            await("claim existing page without new presentation") {
                foregroundPage() === existingExtraction && gate.activeId?.let { !gate.eligible(it, true) } == true && noOldPopup()
            }
            flushEnd()
            await("explicitly opened extraction remains after short flush") { foregroundPage() === existingExtraction }

            val history = host.select(TaskDestination.HISTORY)
            lateinit var form: EditText
            lateinit var modal: AlertDialog
            test.runOnMainSync {
                form = EditText(main).apply {setText("unsaved filter");setSelection(2)}
                modal=AlertDialog.Builder(main).setView(form).show()
            }
            await("editor covers Main") { modal.window?.decorView?.hasWindowFocus()==true }
            start("wait-on-modal")
            SystemClock.sleep(250)
            test.runOnMainSync {
                check(foregroundPage()===history && form.text.toString()=="unsaved filter" && form.selectionStart==2)
                modal.dismiss()
            }
            await("modal closed while same cup remains active") { foregroundPage() === existingExtraction }
            test.runOnMainSync { main.onBackPressedDispatcher.onBackPressed() }
            await("Back restores retained history") { foregroundPage()===history }
            SystemClock.sleep(250)
            test.runOnMainSync { check(foregroundPage()===history && noOldPopup()) }
            flushEnd()

            if (includeDraftEditors) {
                fun preservedBelowLive(owner: ThemedActivity, verifyDraft: () -> Unit) {
                    val monitor = test.addMonitor(PassiveExtractionHostActivity::class.java.name, null, false)
                    val live = try {
                        start("standalone-under-live")
                        requireNotNull(test.waitForMonitorWithTimeout(monitor, 10000)) as PassiveExtractionHostActivity
                    } finally {test.removeMonitor(monitor)}
                    await("standalone foreground shows same extraction Fragment without popup") {
                        val frame=live.supportFragmentManager.primaryNavigationFragment as? ExtractionFragment
                            ?: return@await false
                        val root=frame.view ?: return@await false
                        val buttons=descendants(root).filter { it.tag in setOf("product/extraction/start","product/extraction/stop") }.toList()
                        val chart=descendants(root).filterIsInstance<ExtractionOverlayView>().singleOrNull()
                        live.hasWindowFocus() && buttons.size==2 && buttons.all {it.visibility==View.GONE} &&
                            (chart?.points?.size ?: 0)>=2 &&
                            live.supportFragmentManager.fragments.none { it is ManualLiveDialog }
                    }
                    flushEnd()
                    await("short flush stays on shared standalone route until Back") {
                        val frame = live.supportFragmentManager.primaryNavigationFragment as? ExtractionFragment
                        live.hasWindowFocus() && !live.isFinishing && frame?.view?.let { content ->
                            retainsManualLabel(content) &&
                                descendants(content).filterIsInstance<ExtractionOverlayView>().singleOrNull()?.points?.isNotEmpty() == true
                        } == true && service.chartCurveId == "manual" && app.history.entries.map { it.id } == historyBefore
                    }
                    test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
                    await("same underlying standalone Activity resumes") {
                        owner.hasWindowFocus() && owner.lifecycle.currentState==Lifecycle.State.RESUMED
                    }
                    SystemClock.sleep(250)
                    test.runOnMainSync {
                        check(owner.hasWindowFocus() && !owner.isFinishing)
                        verifyDraft()
                    }
                }
                val editor = open(main, AiChatActivity::class.java).also { child = it }
                lateinit var edit: EditText
                test.runOnMainSync {
                    edit=descendants(editor.window.decorView).filterIsInstance<EditText>().first()
                    edit.setText("unsent draft");edit.setSelection(2)
                }
                preservedBelowLive(editor) {
                    check(edit.isAttachedToWindow && edit.text.toString()=="unsent draft" && edit.selectionStart==2)
                }
                test.runOnMainSync { edit.text.clear(); editor.finish() }
                await("AI editor return retains selected history tab") {
                    foregroundPage()===history && main.hasWindowFocus()
                }
                child = null
                val compare=open(main,BrewComparisonActivity::class.java).also {child=it}
                preservedBelowLive(compare) {
                    check(foregroundPage()===history)
                }
                test.runOnMainSync { compare.finish() }
                await("comparison return retains selected history tab") {
                    foregroundPage()===history && main.hasWindowFocus()
                }
                child=null
            }
            test.runOnMainSync {
                check(main.currentService === service && service.running)
                check(main.getSharedPreferences("curves", 0).getString("selected", null) == beforeSelected)
                check(!ShotGate.active(service.shotState))
            }
            if (clock != null && includeDraftEditors) {
                val homeAgain = host.select(TaskDestination.BREW)
                val settings = requireNotNull(service.snapshot.settings)
                start("short-interrupted")
                await("short interruption starts in real shared page") { foregroundPage() is ExtractionFragment }
                val interruptedId = service.manualChartIdentity
                test.runOnMainSync {
                    service.offerMockExtractionCoffeeState(clock, io.openhoyi.session.DeviceState.DISCONNECTED)
                }
                await("short disconnect retains last manual trace with no saved cup") {
                    foregroundPage() is ExtractionFragment && !service.manualShotActive &&
                        service.manualChartIdentity == interruptedId && service.chartPoints.size >= 2 &&
                        app.history.entries.map { it.id } == historyBefore && noOldPopup()
                }
                test.runOnMainSync {
                    main.onBackPressedDispatcher.onBackPressed()
                    service.offerMockExtractionCoffeeState(clock, io.openhoyi.session.DeviceState.READY)
                    service.offerMockExtractionSettings(clock, settings)
                    elapsed(1); service.offerMockExtractionFrame(clock, IdleTelemetry(9300, 12000, 0, 0, 0, 0,
                        25, 0, ByteFrame(byteArrayOf())))
                }
                await("Back retains Home after disconnect") { foregroundPage() === homeAgain }
                verifySavedManualCompletion(host, service, clock)
                host.select(TaskDestination.BREW)
                start("next-app-shot")
                await("live route for last short flush") { foregroundPage() is ExtractionFragment }
                flushEnd()
                val oldId = service.manualChartIdentity
                var selectedId: String? = null
                test.runOnMainSync {
                    val scaleReady = service.snapshot.scaleState == io.openhoyi.session.DeviceState.READY
                    val next = app.curves.items.asSequence().mapNotNull { app.curves.resolve(it.id, scaleReady, 7) }
                        .first { it.targetHundredthsGram == 0 && service.extractionStartBlock(it) == null && service.studioStartBlock(it) == null }
                    selectedId = next.id
                    // Disposable CI only: set the existing explicit selection without changing route.
                    curvePrefs.edit().putString("selected", next.id).apply()
                }
                await("normal next-cup Start available on retained shared route") {
                    (foregroundPage() as? ExtractionFragment)?.view?.findViewWithTag<View>("product/extraction/start")
                        ?.let { it.isShown && it.isEnabled } == true
                }
                test.runOnMainSync {
                    val startButton = (foregroundPage() as ExtractionFragment).requireView().findViewWithTag<View>("product/extraction/start")
                    check(startButton.performClick())
                }
                await("the explicit App action replaces the short flush with its own identity and selected curve") {
                    service.mockExtractionPendingStartId != null && service.chartCurveId == selectedId &&
                        service.chartTraceIdentity != oldId && service.manualChartIdentity == null && service.chartPoints.isEmpty() &&
                        foregroundPage() is ExtractionFragment && noOldPopup()
                }
            }
        } finally {
            if (clock != null) test.runOnMainSync { curvePrefs.edit().putString("selected", beforeSelected).apply() }
            test.runOnMainSync { child?.finish() }
            if (clock == null) test.runOnMainSync { gate.ended() }
            else if (service.manualShotActive) flushEnd()
        }
    }
    /** Uses the real service + visible Extraction result delivery, never the screenshot preview. */
    private fun verifySavedManualCompletion(host: MainPageTestHost, service: MobileService, clock: Any) {
        val page = host.extraction()
        val main = host.activity
        val app = main.application as MobileApplication
        val feedback = app.feedbackPreferences
        val previous = feedback.enabled
        val sandbox = AiAndroidHttpSandbox.install()
        val requestCount = sandbox.requestCount
        val key = AiKeyStore(app)
        val consent = app.getSharedPreferences("ai_consent", 0)
        val keyAlias = "io.openhoyi.mobile.deepseek_api_key.v1"
        val androidKeys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        check(key.status() == AiKeyStatus.MISSING && !androidKeys.containsAlias(keyAlias) &&
            !consent.contains(AiConsentPolicy.VERSION_KEY))
        var seededKey = false
        var chat: AiChatActivity? = null
        val draft = "Unsent coffee notes"
        val reply = "For the next cup, try grinding slightly finer and keep the dose unchanged."
        val followUp = "What should I change next?"
        val followUpReply = "Change only the grind setting so you can compare the two cups."

        fun captureWindow(name: String) {
            val image = requireNotNull(test.uiAutomation.takeScreenshot())
            try {
                File(app.filesDir, name).outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            } finally { image.recycle() }
        }

        fun captureFailure(activity: AiChatActivity?) {
            val snapshot = JSONObject().put("requests", sandbox.requestCount - requestCount)
            test.runOnMainSync {
                if (activity != null && !activity.isDestroyed) {
                    val state = ViewModelProvider(activity)[AiChatViewState::class.java]
                    val question = activity.getString(R.string.completion_card_request)
                    snapshot.put("lifecycle", activity.lifecycle.currentState.name)
                        .put("windowFocused", activity.hasWindowFocus())
                        .put("ticketPending", state.completionReviewTicket != null)
                        .put("restored", state.restored)
                        .put("credentialReady", state.credentialEpoch != null)
                        .put("draftPreserved", state.draft == draft)
                        .put("draftSendPending", state.submission.pending != null)
                        .put("notice", if (state.notice == 0) "none" else activity.resources.getResourceEntryName(state.notice))
                        .put("stage", state.diagnostic?.stage?.name ?: "none")
                        .put("failureKind", state.diagnostic?.kind?.name ?: "none")
                        .put("protocolMessages", state.protocol.messages.size)
                        .put("transcriptLines", state.lines.size)
                    val expected = listOf(question, reply, followUp, followUpReply)
                    expected.forEachIndexed { index, text ->
                        val tag = if (index % 2 == 0) "ai/chat/user" else "ai/chat/assistant"
                        val matches = descendants(activity.window.decorView).filter { it.tag == tag }
                            .flatMap { row -> descendants(row).filterIsInstance<TextView>() }
                            .filter { it.text.toString() == text }.toList()
                        snapshot.put("message$index", JSONObject()
                            .put("committed", state.lines.count { it.text == text })
                            .put("rendered", matches.size)
                            .put("visible", matches.count { it.isShown && it.getGlobalVisibleRect(Rect()) }))
                    }
                }
            }
            File(app.filesDir, "completion-chat-failure.json").writeText(snapshot.toString(2))
            captureWindow("completion-chat-failure.png")
        }
        // The repository may return queued samples; a separate store proves disk readback.
        val persistedSamples = ShotSamplesStore(File(app.filesDir, "shot_samples"))

        fun manualCup(seconds: Int): Pair<String, ShotHistory.Entry> {
            val knownIds = app.history.entries.map { it.id }.toSet()
            fun offer(frame: io.openhoyi.protocol.HoyiMessage, delta: Long, weight: Int? = null) {
                MobileMonotonicClock.advanceForMock(clock, MobileMonotonicClock.elapsedRealtime() + delta)
                if (weight != null) service.offerScaleVisualInput(clock, DeviceState.READY,
                    ScaleObservation(weight, MobileMonotonicClock.elapsedRealtime(), ScaleCapabilities(),
                        ScaleEvidence.LIVE_READ_ONLY, raw = ByteFrame(byteArrayOf())))
                service.offerMockExtractionFrame(clock, frame)
            }
            fun frame(second: Int) = ExtractionTelemetry(6, second, 89, second * 10, 9300,
                25, 64, 0, ByteFrame(byteArrayOf()))
            lateinit var feedbackId: String
            test.runOnMainSync {
                check(!page.feedbackUi.isShowing && !service.manualShotActive)
                offer(frame(1), 450, weight = 12000)
                offer(frame(2), 450, weight = 12050)
                feedbackId = requireNotNull(service.manualChartIdentity)
                for (second in 3..seconds) offer(frame(second), 1000)
                // End only after the existing passive idle gap; no fabricated completion call.
                offer(IdleTelemetry(9300, 12000, 0, 0, 0, 0, 25, 0, ByteFrame(byteArrayOf())), 2901, weight = 15490)
                check(!service.manualShotActive)
            }
            val entry = app.history.entries.single { it.id !in knownIds }
            check(entry.curveId == "manual" && entry.status == ShotHistory.Status.ENDED && entry.id != feedbackId)
            await("manual $seconds-second cup has samples on disk under its committed ID") {
                persistedSamples.load(entry.id).size >= 2
            }
            return feedbackId to entry
        }

        test.runOnMainSync { service.installScaleVisualInput(clock) }
        try {
            seededKey = true
            check(key.save("fake-sentinel-manual-completion"))
            check(consent.edit().putInt(AiConsentPolicy.VERSION_KEY, AiConsentPolicy.CURRENT_VERSION).commit())
            val draftOwner = open(main, AiChatActivity::class.java).also { chat = it }
            test.runOnMainSync {
                descendants(draftOwner.window.decorView).filterIsInstance<EditText>().single().setText(draft)
                draftOwner.finish()
            }
            await("Chat draft closes before completing the cup") { draftOwner.isDestroyed && main.hasWindowFocus() }
            chat = null

            test.runOnMainSync { check(feedback.setEnabled(true)) }
            manualCup(13) // first frame 1 + 12 seconds of actual progress
            test.waitForIdleSync()
            test.runOnMainSync {
                check(service.brewFeedbackResult == null && !page.feedbackUi.isShowing) {
                    "A saved 13-second manual cup must not widen the legacy feedback trigger"
                }
            }

            val (feedbackId, entry) = manualCup(18)
            await("qualified manual cup opens the real completion dialog") {
                service.brewFeedbackResult?.let { it.shotId == feedbackId &&
                    it.machineSeconds == 18 && it.level == BrewFeedbackClips.Level.LOW_FLOW &&
                    it.completionWeight == ExtractionScaleReadout.CompletionWeight(3490, ExtractionScaleReadout.Source.BEVERAGE) &&
                    it.endCupCount == 25 } == true &&
                    page.feedbackUi.isShowing
            }
            val expectedPace = main.getString(R.string.completion_card_flow_slow)
            val expectedNote = main.getString(R.string.feedback_note)
            check(waitForActiveWindow { window ->
                listOf(expectedPace, expectedNote).all { expected ->
                    val matches = window.findAccessibilityNodeInfosByText(expected)
                    try { matches.any { it.isVisibleToUser && it.text?.toString() == expected } }
                    finally { matches.forEach { it.recycle() } }
                }
            }) { "Qualified manual cup did not show the slow-flow badge and feedback note" }

            val screenshot = requireNotNull(test.uiAutomation.takeScreenshot())
            try {
                File(app.filesDir, "porcelain-manual-result.png").outputStream().use {
                    check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
            } finally { screenshot.recycle() }

            check(sandbox.requestCount == requestCount)
            val toolArguments = JSONObject().put("shotId", entry.id).put("limit", 2)
            sandbox.enqueue(body = AiAndroidHttpSandbox.toolAnswer("extraction_samples", toolArguments.toString()))
            sandbox.enqueue(body = AiAndroidHttpSandbox.answer(reply))
            val monitor = test.addMonitor(AiChatActivity::class.java.name, null, false)
            val review = main.getString(R.string.completion_card_review)
            val reviewed = try {
                check(waitForActiveWindow { window ->
                    val matches = window.findAccessibilityNodeInfosByText(review)
                    try {
                        matches.any { it.text?.toString() == review && it.isVisibleToUser &&
                            it.isEnabled && it.isClickable && it.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
                    } finally { matches.forEach { it.recycle() } }
                }) { "Real completion dialog AI review could not be clicked" }
                test.waitForMonitorWithTimeout(monitor, 5000) as? AiChatActivity
                    ?: error("Completion AI review did not open the ordinary chat")
            } finally { test.removeMonitor(monitor) }
            chat = reviewed
            val question = reviewed.getString(R.string.completion_card_request)
            fun bubbleTexts(tag: String) = descendants(reviewed.window.decorView)
                .filter { it.tag == tag && it.isShown }
                .flatMap { row -> descendants(row).filterIsInstance<TextView>() }
                .filter { it.getGlobalVisibleRect(Rect()) }
                .map { it.text.toString() }.toList()
            await("ordinary Chat displays the completion question and its offline reply") {
                bubbleTexts("ai/chat/user").count { it == question } == 1 &&
                    bubbleTexts("ai/chat/assistant").contains(reply) && reviewed.hasWindowFocus()
            }
            captureWindow("completion-chat.png")
            fun messagesForRequest(index: Int): List<JSONObject> {
                val messages = JSONObject(sandbox.lastBodies[index]).getJSONArray("messages")
                return (0 until messages.length()).map(messages::getJSONObject)
            }
            check(sandbox.requestCount == requestCount + 2)
            val userText = messagesForRequest(requestCount).last { it.getString("role") == "user" }.getString("content")
            check(userText.startsWith(question) && "shotId=${entry.id}." in userText && feedbackId !in userText)
            val toolResult = messagesForRequest(requestCount + 1).single { it.getString("role") == "tool" }
            check(toolResult.getString("tool_call_id") == "call1")
            // Koog serializes the tool's String result as a JSON string in the message content.
            val encodedSamples = JSONTokener(toolResult.getString("content"))
            val rawSamples = encodedSamples.nextValue() as? String ?: error("Tool result was not a JSON string")
            check(!encodedSamples.more()) { "Unexpected data after encoded tool result" }
            val samples = JSONObject(rawSamples)
            check(samples.getString("status") == "available" && samples.getString("shotId") == entry.id)
            val saved = persistedSamples.load(entry.id)
            val observed = samples.getJSONArray("items")
            check(samples.getInt("total") == saved.size && observed.length() == 2)
            saved.take(2).forEachIndexed { index, point ->
                val item = observed.getJSONObject(index)
                check(item.getLong("elapsedMs") == point.elapsedMs &&
                    item.getDouble("pressureBar") == point.pressureTenthsBar / 10.0)
            }
            sandbox.enqueue(body = AiAndroidHttpSandbox.answer(followUpReply))
            test.runOnMainSync {
                val views = descendants(reviewed.window.decorView)
                val input = views.filterIsInstance<EditText>().single()
                check(input.text.toString() == draft)
                input.setText(followUp)
                val send = views.single { it.contentDescription == reviewed.getString(R.string.ai_send) }
                check(send.isEnabled && send.performClick())
            }
            await("ordinary follow-up stays in the completed cup's conversation") {
                bubbleTexts("ai/chat/user").containsAll(listOf(question, followUp)) &&
                    bubbleTexts("ai/chat/assistant").contains(followUpReply)
            }
            check(sandbox.requestCount == requestCount + 3)
            val followUpUsers = messagesForRequest(requestCount + 2)
                .filter { it.getString("role") == "user" }.map { it.getString("content") }
            check(followUpUsers.takeLast(2) == listOf(userText, followUp))
            sandbox.assertDrained()
            test.runOnMainSync { reviewed.finish() }
            await("returning from normal Chat leaves the same Extraction page without reopening the card") {
                !page.feedbackUi.isShowing && main.supportFragmentManager.primaryNavigationFragment === page &&
                    page.lifecycle.currentState == Lifecycle.State.RESUMED && main.hasWindowFocus()
            }
        } catch (failure: Throwable) {
            runCatching { captureFailure(chat) }.exceptionOrNull()?.let(failure::addSuppressed)
            throw failure
        } finally {
            test.runOnMainSync {
                chat?.finish()
                service.clearScaleVisualInput(clock)
                check(feedback.setEnabled(previous))
            }
            check(consent.edit().remove(AiConsentPolicy.VERSION_KEY).commit())
            if (seededKey) {
                check(key.delete())
                androidKeys.deleteEntry(keyAlias)
            }
        }
    }

    private fun waitForActiveWindow(matches: (AccessibilityNodeInfo) -> Boolean): Boolean {
        val deadline = SystemClock.elapsedRealtime() + 5000
        do {
            val window = test.uiAutomation.rootInActiveWindow
            if (window != null) try {
                if (matches(window)) return true
            } finally { window.recycle() }
            SystemClock.sleep(50)
        } while (SystemClock.elapsedRealtime() < deadline)
        return false
    }

}
