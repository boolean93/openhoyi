package io.openhoyi.mobile

import android.app.Instrumentation
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import java.io.File

/** Actual Home action and inline device route; no connection or machine command is invoked. */
internal class HomeWorkbenchChecks(private val test: Instrumentation) {
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun main(action: () -> Unit) {
        var failure: Throwable? = null
        test.runOnMainSync { try { action() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }
    private fun await(condition: () -> Boolean) {
        val deadline=SystemClock.elapsedRealtime()+10000
        while(true) {
            var ready=false; main { ready=condition() }
            if(ready)return
            check(SystemClock.elapsedRealtime()<deadline) { "Actual Home/device route did not become ready" }
            Thread.sleep(25)
        }
    }
    fun run(expectedFontScale: Float) {
        val context=test.targetContext
        check(BuildConfig.MOCK_MODE && context.packageName=="io.openhoyi.mobile.mock")
        val app=context.applicationContext as MobileApplication
        val preferences=context.getSharedPreferences("curves",Context.MODE_PRIVATE)
        val before=preferences.all.toMap()
        val inventory=app.beanInventoryResult.getOrThrow().events()
        val preparation=app.beanPreparationResult.getOrThrow().current()
        val journal=app.journalResult.getOrThrow().exportJson()
        val usage=app.curves.items.associate {it.id to app.curveUsageResult.getOrThrow().stats(it.id)}
        val chosen=app.curves.items.first {CurveStageParameters.pressureTargets(it).isNotEmpty()}
        try {
            main {check(preferences.edit().putString("selected",chosen.id).commit())}
            val page=MainPageTestHost.launch(test);val activity=page.activity
            val home=page.active("root:BREW") as HomeFragment
            try {
                await {activity.currentService?.running==true}
                val owner=requireNotNull(activity.currentService);val message=owner.snapshot.message;val shot=owner.shotState
                val directory=File(context.getExternalFilesDir(null),"home-workbench-checks").apply {check(isDirectory || mkdirs())}
                fun invariant() {
                    check(activity.currentService===owner && owner.running && owner.snapshot.message===message && owner.shotState==shot)
                    check(app.beanInventoryResult.getOrThrow().events()==inventory && app.beanPreparationResult.getOrThrow().current()==preparation)
                    check(app.journalResult.getOrThrow().exportJson()==journal &&
                        app.curves.items.associate {it.id to app.curveUsageResult.getOrThrow().stats(it.id)}==usage)
                    check(preferences.getString("selected",null)==chosen.id)
                }
                fun capture(name:String) {
                    val bitmap=requireNotNull(test.uiAutomation.takeScreenshot())
                    try {File(directory,"$name.png").outputStream().use {check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))}}
                    finally {bitmap.recycle()}
                }
                fun complete(view:View):Rect {
                    val bounds=Rect();check(view.isShown && view.getGlobalVisibleRect(bounds) &&
                        bounds.width()==view.width && bounds.height()==view.height) {"Actual Home/device target clipped: ${view.tag}"}
                    if(view is TextView)LanguageUiLayoutChecks.textFits(view,"Home/device route")
                    return bounds
                }
                main {
                    val style = TaskProductVisualStyle(activity)
                    val root = home.requireView()
                    check((root.background as android.graphics.drawable.ColorDrawable).color == style.homePage)
                    val dials = descendants(root).filterIsInstance<BoilerTemperatureDial>()
                    val homeConfig = activity.resources.configuration
                    val compact = TaskLayoutPolicy.compactHome(homeConfig.screenHeightDp,
                        homeConfig.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT)
                    check(dials.size == if (compact) 0 else 2)
                    dials.forEach { dial ->
                        check((dial.background as android.graphics.drawable.GradientDrawable).color?.defaultColor == style.surface)
                        descendants(dial).filterIsInstance<TextView>().forEach { text ->
                            check(text.currentTextColor == style.homeDialInk)
                        }
                    }
                    for (slot in 1..5) {
                        val preset = requireNotNull(root.findViewWithTag<View>("product/home/direct-preset/$slot"))
                        val color = (preset.background as android.graphics.drawable.GradientDrawable).color?.defaultColor
                        check(color == style.surface || color == style.homeSelected)
                    }
                    check(kotlin.math.abs(activity.resources.configuration.fontScale-expectedFontScale)<.01f)
                    val action=page.viewField<TextView>(home,"quickLaunch")
                    val bounds=complete(action);val config=activity.resources.configuration
                    File(directory,"selected-window.txt").writeText("screenDp=${config.screenWidthDp}x${config.screenHeightDp} font=${config.fontScale} curve=${chosen.id} complete=true bounds=$bounds noStart=true\n")
                    invariant()
                }
                capture("selected-window")
                main {check(page.viewField<View>(home,"machineConnect").performClick())}
                test.waitForIdleSync()
                await {
                    val settings=activity.supportFragmentManager.primaryNavigationFragment as? AppSettingsFragment
                    settings!=null && AppSettingsFragment::class.java.getDeclaredField("service").apply {isAccessible=true}.get(settings)===owner &&
                        activity.findViewById<View>(android.R.id.content).findViewWithTag<View>("settings/heading/devices")?.isLaidOut==true
                }
                main {
                    val settings=page.active("root:SETTINGS") as AppSettingsFragment
                    check(AppSettingsFragment::class.java.getDeclaredField("service").apply {isAccessible=true}.get(settings)===owner)
                    val heading=requireNotNull(activity.findViewById<View>(android.R.id.content).findViewWithTag<TextView>("settings/heading/devices"))
                    complete(heading);check(heading.text.toString()==activity.getString(R.string.home_device_connection))
                    val controls=requireNotNull(AppSettingsFragment::class.java.getDeclaredField("deviceControls").apply {isAccessible=true}.get(settings))
                    @Suppress("UNCHECKED_CAST")
                    val states=controls.javaClass.getDeclaredField("states").apply {isAccessible=true}.get(controls) as Map<io.openhoyi.session.DeviceRole,TextView>
                    states.forEach {(role,status)->
                        val row=requireNotNull(activity.findViewById<View>(android.R.id.content).findViewWithTag<View>("settings/devices/${role.name}"))
                        val state=if(role==io.openhoyi.session.DeviceRole.COFFEE)owner.snapshot.coffeeState else owner.snapshot.scaleState
                        complete(row);complete(status)
                        check(status.text.toString()==activity.getString(R.string.application_mode_mock)+" · "+DeviceStatusText.label(activity,state))
                    }
                    check(states.size==2)
                    invariant()
                }
                capture("devices-inline")
                main {activity.onBackPressedDispatcher.onBackPressed()};test.waitForIdleSync()
                await {activity.supportFragmentManager.primaryNavigationFragment is HomeFragment && activity.hasWindowFocus()}
                main {check(page.active("root:BREW")===home);complete(page.viewField<TextView>(home,"quickLaunch"));invariant()}
                capture("home-restored")
                main {ScaleTrendPanelChecks.verify(activity,directory);invariant()}
                File(directory,"route.txt").writeText("actualConnect=true devicesAnchor=true returnedHome=true sameService=true dataUnchanged=true noCommands=true\n")
            } finally {main {activity.finish()};test.waitForIdleSync()}
        } finally {main {restoreMockPreferences(preferences,before)}}
    }
}
