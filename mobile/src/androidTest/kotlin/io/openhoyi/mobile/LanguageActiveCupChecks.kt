package io.openhoyi.mobile

import android.app.Instrumentation
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import io.openhoyi.protocol.ExtractionTelemetry
import io.openhoyi.session.ExtractionState

/** Runs real product start and translated stop affordances, only against the guarded Mock runtime. */
internal class LanguageActiveCupChecks(private val test: Instrumentation) {
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    private fun visibleRecipeTemperature(screen: MainActivity, recipe: ShotRecipeSnapshot, language: String) {
        val fragment = page(screen)
        var tools: AlertDialog? = null
        var details: AlertDialog? = null
        try {
            test.runOnMainSync {
                val trigger = requireNotNull(fragment.requireView().findViewWithTag<View>("product/extraction/chart-tools"))
                check(trigger.isShown && trigger.isEnabled && trigger.performClick()) {
                    "$language running chart tools are not reachable"
                }
                tools = ExtractionFragment::class.java.getDeclaredField("toolsDialog")
                    .apply { isAccessible = true }.get(fragment) as? AlertDialog
                check(tools?.isShowing == true)
            }
            test.waitForIdleSync()
            test.runOnMainSync {
                val overview = descendants(requireNotNull(tools?.window).decorView)
                    .filterIsInstance<Button>().single {
                        it.isShown && it.isEnabled && it.text.toString() == screen.getString(R.string.extraction_overview)
                    }
                check(overview.performClick()) { "$language overview action was not handled" }
                val dialogs = ExtractionFragment::class.java.getDeclaredField("transientDialogs")
                    .apply { isAccessible = true }.get(fragment) as List<*>
                details = dialogs.filterIsInstance<AlertDialog>().last { it.isShowing }
            }
            test.waitForIdleSync()
            lateinit var message: TextView
            var lineTop = 0
            var lineBottom = 0
            test.runOnMainSync {
                message = requireNotNull(details?.findViewById<TextView>(android.R.id.message)) {
                    "$language overview has no real message view"
                }
                val expected = screen.getString(R.string.journal_field_value,
                    screen.getString(R.string.task_recipe_temperature), "${recipe.temperatureC} °C")
                val start = message.text.indexOf(expected)
                check(start >= 0) { "$language overview lost the cup-bound recipe temperature: $expected" }
                val layout = requireNotNull(message.layout)
                lineTop = layout.getLineTop(layout.getLineForOffset(start))
                lineBottom = layout.getLineBottom(layout.getLineForOffset(start + expected.length - 1))
                message.requestRectangleOnScreen(Rect(0, lineTop, message.width, lineBottom), true)
            }
            test.waitForIdleSync()
            test.runOnMainSync {
                val visible = Rect()
                val origin = IntArray(2)
                message.getLocationOnScreen(origin)
                check(message.isShown && message.getGlobalVisibleRect(visible) &&
                    visible.top <= origin[1] + lineTop && visible.bottom >= origin[1] + lineBottom) {
                    "$language overview recipe temperature is outside the real dialog viewport: $visible"
                }
                LanguageUiLayoutChecks.textFits(message, "$language active cup overview recipe temperature")
            }
        } finally {
            test.runOnMainSync { details?.dismiss(); tools?.dismiss() }
            test.waitForIdleSync()
        }
    }

    private fun awaitMain(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10000
        while (true) {
            var ready = false
            test.runOnMainSync { ready = condition() }
            if (ready) return
            check(SystemClock.elapsedRealtime() < deadline) { "Active language condition timed out" }
            Thread.sleep(50)
        }
    }
    private fun service(screen: MainActivity): MobileService? = screen.currentService
    private fun page(screen: MainActivity): ExtractionFragment =
        MainPageTestHost(test, screen).active("extraction") as ExtractionFragment
    private fun stop(screen: MainActivity): Button = MainPageTestHost(test, screen).viewField(page(screen), "stop")
    private fun observedState(owner: MobileService): ExtractionState = MobileService::class.java
        .getDeclaredField("lastShotState").apply { isAccessible = true }.get(owner) as ExtractionState
    private fun cupId(owner: MobileService): String? {
        val series = MobileService::class.java.getDeclaredField("series").apply { isAccessible = true }.get(owner)
        return ShotSeries::class.java.getDeclaredField("id").apply { isAccessible = true }.get(series) as? String
    }
    private fun captureRunning(screen: MainActivity, owner: MobileService, connected: Boolean) {
        awaitMain { (owner.snapshot.coffee as? ExtractionTelemetry)?.elapsedSeconds?.let { it >= 5 } == true &&
            owner.chartPoints.size >= 2 && screen.hasWindowFocus() }
        test.waitForIdleSync()
        Thread.sleep(300)
        val directory = java.io.File(requireNotNull(test.targetContext.getExternalFilesDir(null)),
            "active-cup-visual/${if (connected) "with-scale" else "no-scale"}").apply { check(mkdirs() || isDirectory) }
        test.runOnMainSync {
            check(owner.shotState == ExtractionState.RUNNING)
            val points = owner.chartPoints
            check(points.size >= 2 && points.last().waterTenthsMl > 0 && points.last().pressureTenthsBar > 0 &&
                points.last().temperatureHundredthsC > 0)
            if (connected) check(points.any { it.weightHundredthsGram != null })
            else check(points.all { it.weightHundredthsGram == null } && owner.snapshot.weight == null && owner.snapshot.weightAt == null)
            val label = ExtractionFragment::class.java.getDeclaredField("quantityLabel").apply { isAccessible = true }
                .get(page(screen)) as android.widget.TextView
            check(label.isShown && label.text.toString() == screen.getString(
                if (connected) R.string.task_extraction_scale else R.string.task_extraction_water))
            check(stop(screen).isShown && stop(screen).isEnabled)
            val alarm = ExtractionFragment::class.java.getDeclaredField("alarmStatus").apply { isAccessible = true }
                .get(page(screen)) as android.widget.TextView
            check(owner.snapshot.alarmBits == 0 && owner.snapshot.alarmAt?.let {
                val now = SystemClock.elapsedRealtime()
                it <= now && now - it <= 1500
            } == true && !alarm.isShown) { "Fresh all-clear still consumes running plot space" }
            TaskTypographyEvidence.capture(screen, directory)
            fun bounds(view: android.view.View): org.json.JSONObject {
                val location = IntArray(2)
                view.getLocationOnScreen(location)
                return org.json.JSONObject().put("x", location[0]).put("y", location[1])
                    .put("width", view.width).put("height", view.height).put("visible", view.isShown)
            }
            val overlay = ExtractionFragment::class.java.getDeclaredField("chart").apply { isAccessible = true }
                .get(page(screen)) as ExtractionOverlayView
            fun chartChild(name: String) = ExtractionOverlayView::class.java.getDeclaredField(name)
                .apply { isAccessible = true }.get(overlay) as android.view.View
            val stopBounds = android.graphics.Rect()
            check(stop(screen).getGlobalVisibleRect(stopBounds) && stopBounds.width() == stop(screen).width &&
                stopBounds.height() == stop(screen).height) { "Running fixed stop is clipped" }
            if (screen.resources.configuration.screenWidthDp >= 1000) {
                val chartLocation = IntArray(2)
                overlay.getLocationOnScreen(chartLocation)
                check(chartLocation[1] + overlay.height > stopBounds.top) {
                    "Full-width action row still steals the chart's bottom area"
                }
            }
            val plot = chartChild("plot")
            val axes = plot.javaClass.getDeclaredField("plotBounds").apply { isAccessible = true }
                .get(plot) as android.graphics.RectF
            check(plot.isShown && !axes.isEmpty) { "Running chart has no measurable data plot" }
            val plotLocation = IntArray(2)
            plot.getLocationOnScreen(plotLocation)
            val dataBounds = org.json.JSONObject().put("x", plotLocation[0] + axes.left)
                .put("y", plotLocation[1] + axes.top).put("width", axes.width()).put("height", axes.height())
            val geometry = org.json.JSONObject().put("density", screen.resources.displayMetrics.density)
                .put("fontScale", screen.resources.configuration.fontScale)
                .put("content", bounds(screen.findViewById(android.R.id.content)))
                .put("chart", bounds(overlay)).put("legend", bounds(chartChild("legend")))
                .put("explanation", bounds(chartChild("explanation"))).put("plotView", bounds(plot))
                .put("dataPlot", dataBounds).put("stop", bounds(stop(screen)))
                .put("dataPlotFractionOfChart", axes.height() / overlay.height)
            java.io.File(directory, "geometry.json").writeText(geometry.toString(2))
            java.io.File(directory, "source.txt").writeText(
                "mock=true scaleConnected=$connected samples=${points.size} weightedSamples=${points.count { it.weightHundredthsGram != null }} state=${owner.shotState}\n")
        }
        val bitmap = requireNotNull(test.uiAutomation.takeScreenshot())
        try {
            java.io.File(directory, "window.png").outputStream().use {
                check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { bitmap.recycle() }
    }
    fun run(visualEvidence: Boolean = false) {
        val context = test.targetContext
        check(BuildConfig.MOCK_MODE && context.packageName == "io.openhoyi.mobile.mock")
        val app = context.applicationContext as MobileApplication
        val oldLanguage = app.languagePreferences.current
        val languagePrefs = context.getSharedPreferences("app_language", Context.MODE_PRIVATE)
        val oldLanguageStorage = languagePrefs.all.toMap()
        val curves = context.getSharedPreferences("curves", Context.MODE_PRIVATE)
        val oldCurves = curves.all.toMap()
        // Bootstrap one real host; the active ExtractionFragment owns the attached chart and stop.
        var screen = MainPageTestHost.launch(test, extraction = true).activity
        test.waitForIdleSync()
        awaitMain { service(screen)?.machineSettingsFresh == true }
        var owner: MobileService? = null
        test.runOnMainSync { owner = service(screen) }
        val original = requireNotNull(owner)
        val runtime = MobileService::class.java.getDeclaredField("mock").apply { isAccessible = true }
            .get(original) as MockDeviceRuntime
        val originalScaleConnected = runtime.scaleConnected
        val safety = listOf("devices", "presets", "shot_safety", "machine_write_safety")
            .associateWith { context.getSharedPreferences(it, Context.MODE_PRIVATE).all.toMap() }
        try {
            test.runOnMainSync { check(curves.edit().putString("selected", "capture-2").commit()) }
            val cases = if (visualEvidence) listOf(AppLanguage.CHINESE to true, AppLanguage.CHINESE to false)
                else AppLanguage.entries.map { it to originalScaleConnected }
            for ((language, connected) in cases) {
                if (visualEvidence) {
                    test.runOnMainSync { runtime.scaleConnected = connected }
                    awaitMain { (original.snapshot.scaleState == io.openhoyi.session.DeviceState.READY) == connected }
                }
                test.runOnMainSync { check(original.startShot("capture-2", null, 7) == null) }
                awaitMain { original.snapshot.coffee is ExtractionTelemetry && original.chartPoints.isNotEmpty() &&
                    observedState(original) == ExtractionState.RUNNING }
                var recipe: ShotRecipeSnapshot? = null
                var id: String? = null
                var first: ShotPoint? = null
                var message: SnapshotMessage? = null
                test.runOnMainSync {
                    id = requireNotNull(cupId(original)); first = original.chartPoints.first()
                    recipe = requireNotNull(original.activeShotRecipe?.forTrace(original.chartTraceIdentity))
                    check(recipe!!.traceId == id) { "Recipe snapshot belongs to a different cup" }
                    message = original.snapshot.message
                    check(app.languagePreferences.select(language) != AppLanguagePreference.Selection.SAVE_FAILED)
                }
                val monitor = test.addMonitor(MainActivity::class.java.name, null, false)
                try {
                    test.runOnMainSync { screen.recreate() }
                    screen = test.waitForMonitorWithTimeout(monitor, 10000) as? MainActivity
                        ?: error("Active Extraction recreation not observed")
                    test.waitForIdleSync()
                    awaitMain { service(screen) === original && stop(screen).isEnabled && stop(screen).isShown }
                } finally { test.removeMonitor(monitor) }
                test.runOnMainSync {
                    check(screen.resources.configuration.locales[0] == language.locale)
                    check(original.shotState == ExtractionState.RUNNING && cupId(original) == id)
                    check(original.chartPoints.first() == first && original.snapshot.message === message)
                    check(original.activeShotRecipe === recipe && recipe?.forTrace(original.chartTraceIdentity) != null)
                }
                visibleRecipeTemperature(screen, requireNotNull(recipe), language.tag)
                test.runOnMainSync {
                    val action = stop(screen)
                    check(action.text.toString() == screen.getString(R.string.home_stop))
                    check(action.getGlobalVisibleRect(android.graphics.Rect()))
                    LanguageUiLayoutChecks.textFits(action, "${language.tag} active cup fixed stop")
                    safety.forEach { (name, values) ->
                        check(context.getSharedPreferences(name, Context.MODE_PRIVATE).all == values)
                    }
                }
                if (visualEvidence) captureRunning(screen, original, connected)
                test.runOnMainSync {
                    check(stop(screen).performClick())
                    // Mock stop is synchronous. Do not let a later natural ending satisfy the button check.
                    check(original.shotState == ExtractionState.ENDED_OBSERVED)
                }
                awaitMain { original.shotState == ExtractionState.ENDED_OBSERVED && original.snapshot.coffee !is ExtractionTelemetry &&
                    observedState(original) == ExtractionState.ENDED_OBSERVED && cupId(original) == null }
            }
        } finally {
            test.runOnMainSync {
                original.stopShot()
                runtime.scaleConnected = originalScaleConnected
                check(app.languagePreferences.select(oldLanguage) != AppLanguagePreference.Selection.SAVE_FAILED)
                restoreMockPreferences(languagePrefs, oldLanguageStorage)
                restoreMockPreferences(curves, oldCurves)
                screen.finish()
            }
            test.waitForIdleSync()
        }
    }
}
