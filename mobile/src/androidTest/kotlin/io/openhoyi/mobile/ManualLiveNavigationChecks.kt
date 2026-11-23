package io.openhoyi.mobile

import android.app.AlertDialog
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.EditText
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import io.openhoyi.protocol.ByteFrame
import io.openhoyi.protocol.ExtractionTelemetry
import io.openhoyi.protocol.IdleTelemetry
import java.io.File

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

    fun run(recordCompletion: Boolean = false) {
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
            runCases(runningHost, service, includeDraftEditors = true, clock = clock, recordCompletion = recordCompletion)
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

    private fun runCases(host: MainPageTestHost, service: MobileService, includeDraftEditors: Boolean, clock: Any?,
        recordCompletion: Boolean = false) {
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
                verifySavedManualCompletion(host, service, clock, recordCompletion)
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
    private fun verifySavedManualCompletion(host: MainPageTestHost, service: MobileService, clock: Any,
        recordCompletion: Boolean) {
        val page = host.extraction()
        val main = host.activity
        val app = main.application as MobileApplication
        val feedback = app.feedbackPreferences
        val previous = feedback.enabled
        // The repository may return queued samples; a separate store proves disk readback.
        val persistedSamples = ShotSamplesStore(File(app.filesDir, "shot_samples"))

        fun manualCup(seconds: Int, paced: Boolean = false): Pair<String, ShotHistory.Entry> {
            val knownIds = app.history.entries.map { it.id }.toSet()
            fun offer(frame: io.openhoyi.protocol.HoyiMessage, delta: Long) {
                MobileMonotonicClock.advanceForMock(clock, MobileMonotonicClock.elapsedRealtime() + delta)
                service.offerMockExtractionFrame(clock, frame)
            }
            fun frame(second: Int) = ExtractionTelemetry(6, second, 89, second * 10, 9300,
                25, 64, 0, ByteFrame(byteArrayOf()))
            fun begin(): String {
                check(!page.feedbackUi.isShowing && !service.manualShotActive)
                offer(frame(1), 450)
                offer(frame(2), 450)
                return requireNotNull(service.manualChartIdentity)
            }
            fun end() {
                // End only after the existing passive idle gap; no fabricated completion call.
                offer(IdleTelemetry(9300, 12000, 0, 0, 0, 0, 25, 0, ByteFrame(byteArrayOf())), 2901)
                check(!service.manualShotActive)
            }
            lateinit var feedbackId: String
            if (paced) {
                test.runOnMainSync { feedbackId = begin() }
                for (second in 3..seconds) {
                    SystemClock.sleep(350)
                    test.runOnMainSync { offer(frame(second), 1000) }
                }
                SystemClock.sleep(650)
                test.runOnMainSync { end() }
            } else test.runOnMainSync {
                feedbackId = begin()
                for (second in 3..seconds) offer(frame(second), 1000)
                end()
            }
            val entry = app.history.entries.single { it.id !in knownIds }
            check(entry.curveId == "manual" && entry.status == ShotHistory.Status.ENDED && entry.id != feedbackId)
            await("manual $seconds-second cup has samples on disk under its committed ID") {
                persistedSamples.load(entry.id).size >= 2
            }
            return feedbackId to entry
        }

        var screenrecord: ParcelFileDescriptor? = null
        try {
            test.runOnMainSync { check(feedback.setEnabled(true)) }
            manualCup(13) // first frame 1 + 12 seconds of actual progress
            test.waitForIdleSync()
            test.runOnMainSync {
                check(service.brewFeedbackResult == null && !page.feedbackUi.isShowing) {
                    "A saved 13-second manual cup must not widen the legacy feedback trigger"
                }
            }

            if (recordCompletion) {
                // This method is reachable only through disposable Mock instrumentation.
                screenrecord = test.uiAutomation.executeShellCommand(
                    "screenrecord --size 1536x960 --bit-rate 6000000 --time-limit 26 /sdcard/openhoyi-manual-completion.mp4")
                SystemClock.sleep(700)
            }
            val (feedbackId, _) = manualCup(18, paced = recordCompletion)
            await("qualified manual cup opens the real completion dialog") {
                service.brewFeedbackResult?.let { it.shotId == feedbackId &&
                    it.machineSeconds == 18 && it.level == BrewFeedbackClips.Level.LOW_FLOW } == true &&
                    page.feedbackUi.isShowing
            }
            val expectedPace = main.getString(R.string.completion_flow_pace_slow)
            check(waitForActiveWindow { window ->
                val matches = window.findAccessibilityNodeInfosByText(expectedPace)
                try { matches.any { it.text?.toString() == expectedPace && it.isVisibleToUser } }
                finally { matches.forEach { it.recycle() } }
            }) { "Qualified manual cup did not show its actual slow-flow label" }

            if (recordCompletion) SystemClock.sleep(2600)
            val share = main.getString(R.string.completion_card_share)
            check(waitForActiveWindow { window ->
                val matches = window.findAccessibilityNodeInfosByText(share)
                try {
                    matches.any { it.contentDescription?.toString() == share && it.isVisibleToUser &&
                        it.isEnabled && it.isClickable && it.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
                } finally { matches.forEach { it.recycle() } }
            }) { "Real completion dialog Share could not be clicked" }
            val systemChooser = setOf("android", "com.android.intentresolver", "com.google.android.intentresolver")
            check(waitForActiveWindow { it.packageName?.toString() in systemChooser }) {
                "Real completion dialog did not open the system chooser"
            }
            if (recordCompletion) SystemClock.sleep(2400)
            // Back from the system window without sending the PNG to any app.
            ParcelFileDescriptor.AutoCloseInputStream(
                test.uiAutomation.executeShellCommand("input keyevent KEYCODE_BACK")
            ).use { it.readBytes() }
            check(waitForActiveWindow { it.packageName?.toString() == main.packageName })
            var restored = false
            await("system Back returns to the same Extraction page") {
                restored = page.feedbackUi.isShowing
                main.supportFragmentManager.primaryNavigationFragment === page &&
                    page.lifecycle.currentState == Lifecycle.State.RESUMED &&
                    (restored || main.hasWindowFocus())
            }
            test.sendStatus(0, Bundle().apply { putString("manualCompletionChooser",
                "SYSTEM_BACK_SAME_EXTRACTION resultStillShowing=$restored externalSend=false") })
            if (recordCompletion) SystemClock.sleep(1800)
            if (restored) {
                test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                await("Back closes the result without leaving the same Extraction page") {
                    !page.feedbackUi.isShowing && main.supportFragmentManager.primaryNavigationFragment === page
                }
            }
        } finally {
            // Wait for end-of-stream so the remote encoder completes the MP4 moov atom
            // before the runner pulls it. screenrecord itself has the fixed 26s limit.
            screenrecord?.let { ParcelFileDescriptor.AutoCloseInputStream(it).use { input -> input.readBytes() } }
            test.runOnMainSync { check(feedback.setEnabled(previous)) }
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
