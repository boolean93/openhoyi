package io.openhoyi.mobile

import android.app.AlertDialog
import android.app.Instrumentation
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.lifecycle.Lifecycle
import io.openhoyi.protocol.ByteFrame
import io.openhoyi.protocol.ExtractionTelemetry
import io.openhoyi.protocol.IdleTelemetry

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
        val gate = (main.application as MobileApplication).manualShotPresentation
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
            flushEnd()
            await("short flush returns to same home instance") { foregroundPage() === home }

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
                val editor = open(main, AiChatActivity::class.java).also { child = it }
                lateinit var edit: EditText
                test.runOnMainSync {
                    edit=descendants(editor.window.decorView).filterIsInstance<EditText>().first()
                    edit.setText("unsent draft");edit.setSelection(2)
                }
                start("editor-deferred")
                SystemClock.sleep(250)
                test.runOnMainSync {
                    check(editor.hasWindowFocus() && edit.text.toString()=="unsent draft" && edit.selectionStart==2)
                    check(foregroundPage()===history)
                }
                flushEnd()
                test.runOnMainSync { edit.text.clear(); editor.finish() }
                await("finished observation does not steal navigation when returning") {
                    foregroundPage()===history && main.hasWindowFocus()
                }
                child = null
            }
            test.runOnMainSync {
                check(main.currentService === service && service.running)
                check(main.getSharedPreferences("curves", 0).getString("selected", null) == beforeSelected)
                check(!ShotGate.active(service.shotState))
            }
        } finally {
            test.runOnMainSync { child?.finish() }
            if (clock == null) test.runOnMainSync { gate.ended() }
            else if (service.manualShotActive) flushEnd()
        }
    }
}
