package io.openhoyi.mobile

import android.app.AlertDialog
import android.app.Instrumentation
import android.content.Intent
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import androidx.lifecycle.Lifecycle
import io.openhoyi.protocol.ByteFrame
import io.openhoyi.protocol.ExtractionTelemetry

/** Isolated Mock only: feed a read-only display observation, not a transport or machine command. */
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

    private fun dialog(host: ThemedActivity) =
        host.supportFragmentManager.findFragmentByTag(ManualLiveDialog.TAG) as? ManualLiveDialog

    private fun awaitLive(host: ThemedActivity, id: String): ManualLiveDialog {
        await("$id on ${host.javaClass.simpleName}") {
            val layer = dialog(host)
            val popup = layer?.dialog ?: return@await false
            val window = popup.window ?: return@await false
            val chart = layer.view?.let { descendants(it).filterIsInstance<ExtractionOverlayView>().firstOrNull() }
            popup.isShowing && window.decorView.hasWindowFocus() && layer.observationId == id && chart?.points?.size == 2 &&
                chart.measuredWidth > 0 && chart.measuredHeight > 0 &&
                window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
        }
        return requireNotNull(dialog(host))
    }

    private fun close(host: ThemedActivity, layer: ManualLiveDialog) {
        test.runOnMainSync {
            val close = descendants(requireNotNull(layer.view)).single { it.tag == "product/manual-live/close" }
            check(close.performClick())
        }
        await("same host after closing") { dialog(host) == null && host.lifecycle.currentState == Lifecycle.State.RESUMED }
    }

    private fun <T : ThemedActivity> open(main: MainActivity, type: Class<T>): T {
        val monitor = test.addMonitor(type.name, null, false)
        try {
            test.runOnMainSync { main.startActivity(Intent(main, type)) }
            val page = requireNotNull(type.cast(test.waitForMonitorWithTimeout(monitor, 10000))) { "Failed to open ${type.simpleName}" }
            await(type.simpleName) { page.lifecycle.currentState == Lifecycle.State.RESUMED && page.hasWindowFocus() }
            return page
        } finally { test.removeMonitor(monitor) }
    }

    fun run() {
        val host = MainPageTestHost.launch(test)
        try {
            await("running Mock service") { host.activity.currentService?.running == true }
            run(host, requireNotNull(host.activity.currentService))
        } finally { test.runOnMainSync { host.activity.finish() } }
    }

    fun run(host: MainPageTestHost, service: MobileService, includeDraftEditors: Boolean = true) {
        check(BuildConfig.MOCK_MODE && test.targetContext.packageName == "io.openhoyi.mobile.mock")
        val main = host.activity
        val gate = (main.application as MobileApplication).manualShotPresentation
        val series = MobileService::class.java.getDeclaredField("series").apply { isAccessible = true }.get(service) as ShotSeries
        fun start(id: String) = test.runOnMainSync {
            gate.ended()
            val now = MobileMonotonicClock.elapsedRealtime()
            series.begin(id, now - 400, "manual")
            fun frame(seconds: Int) = ExtractionTelemetry(6, seconds, 89, seconds * 10, 9300,
                25, 64, 0, ByteFrame(byteArrayOf()))
            series.machine(frame(1), now - 400, null, null)
            series.machine(frame(2), now, null, null)
            gate.started(id)
        }
        fun end() = test.runOnMainSync { gate.ended() }
        fun finish(page: ThemedActivity) {
            test.runOnMainSync { page.finish() }
            await("Main resumed after ${page.javaClass.simpleName}") {
                main.lifecycle.currentState == Lifecycle.State.RESUMED && main.hasWindowFocus()
            }
        }

        test.runOnMainSync { gate.observedIdle(); gate.ended() }
        var page: ThemedActivity? = null
        try {
            host.extraction()
            start("manual-live-already-live")
            await("existing realtime consumes without overlay") { !gate.eligible("manual-live-already-live", true) }
            test.runOnMainSync { check(dialog(main) == null) }
            end()

            val underlying = host.select(TaskDestination.HISTORY)
            lateinit var form: EditText
            lateinit var formDialog: AlertDialog
            test.runOnMainSync {
                form = EditText(main).apply { setText("unsaved filter") }
                formDialog = AlertDialog.Builder(main).setView(form).create()
                main.trackDialog(formDialog)
                formDialog.show()
            }
            start("manual-live-main-form")
            close(main, awaitLive(main, "manual-live-main-form"))
            test.runOnMainSync {
                check(formDialog.isShowing && form.text.toString() == "unsaved filter")
                check(main.supportFragmentManager.primaryNavigationFragment === underlying)
                formDialog.dismiss()
            }
            end()

            val comparison = open(main, BrewComparisonActivity::class.java).also { page = it }
            start("manual-live-comparison")
            awaitLive(comparison, "manual-live-comparison")
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            await("Back closes only the comparison overlay") { dialog(comparison) == null && comparison.hasWindowFocus() }
            SystemClock.sleep(450)
            test.runOnMainSync {
                check(dialog(comparison) == null && !comparison.isFinishing)
                check(main.supportFragmentManager.primaryNavigationFragment === underlying)
            }
            end()
            finish(comparison)
            page = null

            fun preserveDraft(owner: ThemedActivity, id: String, draft: String) {
                lateinit var edit: EditText
                test.runOnMainSync {
                    edit = descendants(owner.window.decorView).filterIsInstance<EditText>().first()
                    edit.requestFocus()
                    edit.setText(draft)
                    edit.setSelection(2)
                }
                start(id)
                close(owner, awaitLive(owner, id))
                test.runOnMainSync {
                    check(edit.isAttachedToWindow && edit.text.toString() == draft && edit.selectionStart == 2)
                    check(!owner.isFinishing && owner.lifecycle.currentState == Lifecycle.State.RESUMED)
                }
                end()
            }
            if (includeDraftEditors) {
                val ai = open(main, AiChatActivity::class.java).also { page = it }
                preserveDraft(ai, "manual-live-ai", "short unsent chat draft")
                // This focused runner uses disposable Mock data; do not leave an unsent test draft.
                test.runOnMainSync { descendants(ai.window.decorView).filterIsInstance<EditText>().first().text.clear() }
                finish(ai)
                page = null
                val editor = open(main, CurveEditorActivity::class.java).also { page = it }
                preserveDraft(editor, "manual-live-editor", "short unsaved curve draft")
                finish(editor)
                page = null
            }

            test.runOnMainSync {
                check(main.supportFragmentManager.primaryNavigationFragment === underlying)
                check(main.currentService === service && service.running)
                check(!ShotGate.active(service.shotState))
            }
        } finally {
            end()
            test.runOnMainSync { page?.finish() }
        }
    }
}
