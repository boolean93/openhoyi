package io.openhoyi.mobile

import android.app.AlertDialog
import android.app.Instrumentation
import android.widget.Button
import io.openhoyi.session.ExtractionState

/** Uses the existing navigation fixture's Mock-only cup; its caller owns stop/preferences cleanup. */
internal class DirectStartChecks(private val test: Instrumentation) {
    fun startOnce(host: MainPageTestHost, service: MobileService) {
        check(BuildConfig.MOCK_MODE && host.activity.packageName == "io.openhoyi.mobile.mock")
        val fragment = host.extraction()
        var failure: Throwable? = null
        test.runOnMainSync { try {
            val field = MobileService::class.java.getDeclaredField("hub").apply { isAccessible = true }
            check(field.get(service) == null && service.running && !ShotGate.active(service.shotState))
            check(MobileService::class.java.getDeclaredField("mock").apply { isAccessible = true }
                .get(service) is MockDeviceRuntime)
            val before = service.chartTraceIdentity
            val start = requireNotNull(fragment.requireView().findViewWithTag<Button>("product/extraction/start"))
            check(start.isShown && start.isEnabled && start.performClick())
            // A generic confirmation must not be dismissed to get a cup: start is synchronous.
            check(service.shotState == ExtractionState.RUNNING)
            val trace = requireNotNull(service.chartTraceIdentity)
            check(trace != before)
            @Suppress("UNCHECKED_CAST")
            val dialogs = ExtractionFragment::class.java.getDeclaredField("transientDialogs")
                .apply { isAccessible = true }.get(fragment) as List<AlertDialog>
            check(dialogs.none { it.isShowing }) { "Start opened an ordinary confirmation" }
            val message = service.snapshot.message
            // Simulate an already queued second click on the same View, even though render has
            // now hidden Start. The real handler/service must reject it, not create another cup.
            check(start.performClick())
            check(service.shotState == ExtractionState.RUNNING && service.chartTraceIdentity == trace &&
                service.snapshot.message === message && dialogs.none { it.isShowing })
        } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }
}
