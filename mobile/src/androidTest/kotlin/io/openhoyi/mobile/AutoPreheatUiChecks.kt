package io.openhoyi.mobile

import android.app.Instrumentation
import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.os.Bundle
import android.view.View
import android.widget.TextView
import io.openhoyi.protocol.Settings
import io.openhoyi.session.BrewPreparation
import java.io.File

/** Actual MainActivity navigation and Mock temperature evidence; never starts an extraction. */
internal class AutoPreheatUiChecks(private val test: Instrumentation) {
    private fun main(action: () -> Unit) = BoundedInstrumentationMain.run("auto preheat UI", 5000, action)
    private fun field(subject: Any, name: String) = subject.javaClass.getDeclaredField(name).apply { isAccessible = true }
    private var diagnostics: () -> String = { "UI fixture not initialized" }
    private var timeoutCapture: (String) -> Unit = {}
    private fun await(stage: String, condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 15000
        while (true) {
            var ready = false
            main { ready = condition() }
            if (ready) return
            if(SystemClock.elapsedRealtime() >= end) {
                var details = ""
                main { details = diagnostics() }
                val failure=IllegalStateException("Auto preheat UI timeout: $stage; $details")
                runCatching { timeoutCapture(stage) }.exceptionOrNull()?.let(failure::addSuppressed)
                test.sendStatus(0,Bundle().apply { putString("stream","AUTO_PREHEAT_UI_TIMEOUT stage=$stage $details\n") })
                throw failure
            }
            SystemClock.sleep(30)
        }
    }
    fun run() {
        val context = test.targetContext
        check(BuildConfig.MOCK_MODE && context.packageName == "io.openhoyi.mobile.mock")
        val appearance = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        val originalDark = appearance.all["dark"] as? Boolean
        val app = context.applicationContext as MobileApplication
        val curves = context.getSharedPreferences("curves", Context.MODE_PRIVATE)
        val originalSelected = curves.getString("selected", null)
        val history = app.history.entries
        val profile = app.curves.items.asSequence().filter { it.factoryCurve != null }
            .mapNotNull { app.curves.resolve(it.id, true, 7) }.first { it.temperatureC >= 85 }
        val directory = File(context.getExternalFilesDir(null), "auto-preheat-ui").apply { check(mkdirs() || isDirectory) }
        var host: MainActivity? = null
        var service: MobileService? = null
        var runtime: Any? = null
        var originalSettings: Settings? = null
        var originalTemperature: Int? = null
        fun capture(name: String) {
            SystemClock.sleep(250)
            val image = checkNotNull(test.uiAutomation.takeScreenshot())
            try { File(directory, "$name.png").outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
            finally { image.recycle() }
        }
        diagnostics = {
            val owner=service
            val button=host?.window?.decorView?.findViewWithTag<TextView>("product/extraction/start")
            "prep=${owner?.brewPreparationState} text=${button?.text} shown=${button?.isShown} " +
                "actual=${owner?.currentCorrectedBrewTemperature()} target=${profile.temperatureC} " +
                "message=${owner?.autoPreheatMessage} selected=${curves.getString("selected",null)} " +
                "preparationProfile=${owner?.brewPreparationProfileId} flags=${owner?.snapshot?.settings?.flags} " +
                "coffeeState=${owner?.snapshot?.coffeeState} shot=${owner?.shotState} " +
                "block=${owner?.studioStartBlock(profile)} " +
                "activityHeating=${host?.getString(R.string.auto_preheat_heating)} systemHeating=${context.getString(R.string.auto_preheat_heating)}"
        }
        timeoutCapture = { stage ->
            val name="failure-"+stage.replace(Regex("[^A-Za-z0-9]+"),"-")
            var details=""
            main { details=diagnostics() }
            File(directory,"$name.txt").writeText(details)
            capture(name)
        }
        try {
            main {
                check(appearance.edit().putBoolean("dark", true).commit())
                check(curves.edit().putString("selected", profile.id).commit())
            }
            val page = MainPageTestHost.launch(test, timeoutMs = 15000)
            val activity = page.activity
            host = activity
            main {
                check(activity.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES)
            }
            await("Mock service bound") { activity.currentService?.running == true }
            val owner = checkNotNull(activity.currentService)
            service = owner
            val shotState = owner.shotState
            main {
                val mock = checkNotNull(field(owner,"mock").get(owner)); runtime = mock
                check(field(owner,"hub").get(owner) == null)
                val settings = field(mock,"settings").get(mock) as Settings
                originalSettings = settings
                originalTemperature = field(mock,"brewTemperatureHundredths").getInt(mock)
                field(mock,"settings").set(mock,settings.copy(flags=settings.flags or 0x04))
                field(mock,"brewTemperatureHundredths").setInt(mock,7500)
            }
            await("cold studio readback") {
                owner.snapshot.settings?.flags?.and(0x04)==0x04 &&
                    (owner.currentCorrectedBrewTemperature() ?: Int.MAX_VALUE) < 8000
            }
            main {
                activity.useCurve(profile.id)
                check(owner.brewPreparationState==BrewPreparation.State.WAITING_TEMP) { diagnostics() }
            }
            fun start() = page.active("extraction").requireView().findViewWithTag<TextView>("product/extraction/start")
            await("waiting button") {
                val prep=field(owner,"brewPreparation").get(owner) as BrewPreparation
                prep.state==BrewPreparation.State.WAITING_TEMP && start().isShown &&
                    start().text.toString().contains(activity.getString(R.string.auto_preheat_heating)) &&
                    owner.studioStartBlock(profile)!=null
            }
            capture("01-waiting")
            await("target restores start action") {
                val prep=field(owner,"brewPreparation").get(owner) as BrewPreparation
                prep.state==BrewPreparation.State.READY && start().isShown &&
                    start().text.toString()==activity.getString(R.string.extraction_start) && owner.studioStartBlock(profile)==null
            }
            capture("02-target-reached")
            main {
                check(owner.shotState==shotState && app.history.entries==history)
                activity.select(TaskDestination.BREW)
            }
            await("leave feedback toast") {
                activity.window.decorView.findViewWithTag<View>("product/preheat/toast")?.isShown==true
            }
            capture("03-leave-cancelled-toast")
            main {
                check(owner.shotState==shotState && app.history.entries==history)
                check(field(owner,"hub").get(owner)==null)
            }
        } catch(error: Throwable) {
            runCatching { timeoutCapture("check-failed") }.exceptionOrNull()?.let(error::addSuppressed)
            throw error
        } finally {
            main { host?.finish() }
            await("Activity destroyed") { host?.isDestroyed != false }
            main {
                service?.let { owner ->
                    owner.cancelBrewPreparation()
                }
                runtime?.let { mock ->
                    originalSettings?.let { field(mock,"settings").set(mock,it) }
                    originalTemperature?.let { field(mock,"brewTemperatureHundredths").setInt(mock,it) }
                }
                check(appearance.edit().apply {
                    if(originalDark==null) remove("dark") else putBoolean("dark",originalDark)
                }.commit())
                check(curves.edit().apply {
                    if(originalSelected==null) remove("selected") else putString("selected",originalSelected)
                }.commit())
                check(app.history.entries==history)
            }
        }
    }
}
