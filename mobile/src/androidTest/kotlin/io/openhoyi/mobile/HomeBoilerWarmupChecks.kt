package io.openhoyi.mobile

import android.app.Instrumentation
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import io.openhoyi.protocol.ByteFrame
import io.openhoyi.protocol.IdleTelemetry
import io.openhoyi.session.DeviceState
import io.openhoyi.session.ExtractionState
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Genuine Home + service, through the guarded existing synthetic telemetry path. */
internal class HomeBoilerWarmupChecks(private val test: Instrumentation) {
    private fun <T> main(block: () -> T): T = BoundedInstrumentationMain.run("Home boiler warmup recording", 4000, block)

    fun run() {
        val context = test.targetContext
        val app = context.applicationContext as MobileApplication
        val owner = Any()
        val initialClock = 10_000L
        var logical = initialClock
        check(app.history.entries.isEmpty())
        main {
            check(app.languagePreferences.select(AppLanguage.CHINESE) != AppLanguagePreference.Selection.SAVE_FAILED)
            check(app.appearancePreferences.selectDark(false) != AppAppearancePreference.Selection.SAVE_FAILED)
        }
        val out = File(context.filesDir, "boiler-warmup.json")
        MobileMonotonicClock.installForMock(owner, initialClock)
        var page: MainPageTestHost? = null
        var installed: MobileService? = null
        try {
            val host = MainPageTestHost.launch(test, timeoutMs = 15_000).also { page = it }
            var service: MobileService? = null
            val deadline = SystemClock.elapsedRealtime() + 10_000
            while (service == null) {
                service = main { host.activity.currentService?.takeIf { it.running && it.snapshot.coffeeState == DeviceState.READY } }
                check(SystemClock.elapsedRealtime() < deadline) { "Actual Mock Home did not connect" }
                if (service == null) SystemClock.sleep(30)
            }
            val coffee = checkNotNull(service)
            val template = main {
                coffee.installMockExtractionScenario(owner)
                installed = coffee
                checkNotNull(coffee.snapshot.coffee as? IdleTelemetry)
            }
            val targets = main {
                val settings = checkNotNull(coffee.snapshot.settings)
                listOf(settings.brewTemperatureC.toFloat(), settings.steamTemperatureC.toFloat())
            }
            val temperatures = main {
                val root = host.active("root:BREW").requireView()
                listOf("brew", "steam").map { x ->
                    checkNotNull(root.findViewWithTag<TextView>("product/home/$x-boiler/temperature"))
                        .also { check(it.parent is BoilerTemperatureDial && it.isShown) }
                }
            }
            fun read(): List<Float> = main {
                temperatures.map { it.text.toString().substringBefore(' ').toFloatOrNull() ?: Float.NaN }
            }
            fun offer(brew: Float, steam: Float) = main {
                MobileMonotonicClock.advanceForMock(owner, ++logical)
                coffee.offerMockExtractionFrame(owner, template.copy(brewTemperatureHundredthsC = (brew * 100).toInt(),
                    steamTemperatureHundredthsC = (steam * 100).toInt(), sleepStateRaw = 0, alarmBits = 0,
                    raw = ByteFrame(byteArrayOf())))
            }
            val evidence = JSONArray()
            fun observe(expected: List<Float>, previous: List<Float>? = null) {
                val seen = mutableListOf<MutableSet<Float>>(linkedSetOf(), linkedSetOf())
                val until = SystemClock.elapsedRealtime() + 2200
                while (true) {
                    val shown = read()
                    if (previous != null) for (index in shown.indices) {
                        val v = shown[index]
                        if (v > previous[index] && v < expected[index]) {
                            check(v >= (seen[index].lastOrNull() ?: previous[index])) { "Boiler animation moved backwards" }
                            seen[index].add(v)
                        }
                        check(!v.isFinite() || v <= expected[index]) { "Boiler animation overshot actual sample" }
                    }
                    if (shown == expected) break
                    check(SystemClock.elapsedRealtime() < until) { "Actual Home did not settle: $shown vs $expected" }
                    SystemClock.sleep(9)
                }
                if (previous != null) check(seen.all { it.size >= 2 }) { "Both dials must show sustained real intermediates: $seen" }
                evidence.put(JSONObject().put("actual", JSONArray(expected)).put("intermediates", JSONArray(seen.toList())))
            }
            val steps = listOf(listOf(24f, 22f), listOf(30f, 30f), listOf(48f, 55f), listOf(64f, 81f),
                listOf(78f, 103f), targets)
            for ((index, pair) in steps.withIndex()) {
                offer(pair[0], pair[1])
                observe(pair, steps.getOrNull(index - 1))
                SystemClock.sleep(if (index == 0 || index == steps.lastIndex) 850 else 380)
            }
            // After the recorded rising sequence, cooling must snap to actual rather than invent a downward ramp.
            val cool = targets.map { it - 2f }
            offer(cool[0], cool[1])
            val snapUntil = SystemClock.elapsedRealtime() + 1800
            while (true) {
                val shown = read()
                check(shown.indices.all { shown[it] == targets[it] || shown[it] == cool[it] }) { "Cooling animated: $shown" }
                if (shown == cool) break
                check(SystemClock.elapsedRealtime() < snapUntil)
                SystemClock.sleep(9)
            }
            verifyAttachedRetarget(host.activity)
            check(main { coffee.shotState == ExtractionState.IDLE && app.history.entries.isEmpty() })
            out.writeText(JSONObject().put("path", "real_home_guarded_mock_idle_telemetry")
                .put("targets", JSONArray(targets)).put("samples", evidence).put("coolingSnaps", true).put("retargetAndReset", true)
                .put("noPhysicalDevice", true).toString(2) + "\n")
        } finally {
            main {
                installed?.clearMockExtractionScenario(owner)
                page?.activity?.finish()
            }
            MobileMonotonicClock.uninstallForMock(owner)
        }
    }

    private fun verifyAttachedRetarget(activity: MainActivity) {
        lateinit var dial: BoilerTemperatureDial
        lateinit var value: TextView
        val group = main {
            value = TextView(activity)
            dial = BoilerTemperatureDial(activity, TextView(activity), value, TextView(activity),
                TextView(activity), HoyiSwitch(activity), steam = false).apply { visibility = View.INVISIBLE }
            val root = activity.window.decorView as ViewGroup
            root.addView(dial, FrameLayout.LayoutParams(1, 1))
            check(dial.isAttachedToWindow)
            root
        }
        val session = Any()
        fun reading() = value.text.toString().substringBefore(' ').toFloatOrNull()
        try {
            main {
                dial.present(30f, 92f, session)
                check(reading() == 30f)
                dial.present(60f, 92f, session)
            }
            var retargeted = false
            val limit = SystemClock.elapsedRealtime() + 1500
            while (!retargeted) {
                retargeted = main {
                    val displayed = reading() ?: error("Missing midpoint")
                    if (displayed <= 30f || displayed >= 60f) false
                    else {
                        dial.present(60f, 92f, session) // same sample must leave this frame alone
                        check(reading() == displayed)
                        dial.present(78f, 92f, session)
                        check(reading() == displayed) { "Retarget skipped previously displayed value" }
                        true
                    }
                }
                check(SystemClock.elapsedRealtime() < limit) { "Could not retarget from an actual in-flight value" }
                if (!retargeted) SystemClock.sleep(8)
            }
            while (main { reading() } != 78f) {
                check(SystemClock.elapsedRealtime() < limit) { "Did not settle at retargeted sample" }
                SystemClock.sleep(8)
            }
            main {
                dial.present(82f, 92f, session)
                dial.present(82f, 96f, session)
                check(reading() == 82f) // target changes snap the projection
                dial.present(null, 96f, null)
                check(value.text.toString() == "—")
                dial.present(88f, 96f, Any())
                check(reading() == 88f)
                dial.present(90f, 96f, session)
                dial.present(92f, 96f, session)
                group.removeView(dial)
                check(reading() == 92f) // detach cancels motion and preserves actual
            }
        } finally {
            main { if (dial.parent === group) group.removeView(dial) }
        }
    }

}
