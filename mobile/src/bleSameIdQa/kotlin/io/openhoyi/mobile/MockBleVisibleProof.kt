package io.openhoyi.mobile

import android.app.AlertDialog
import android.view.View
import android.widget.LinearLayout
import io.openhoyi.protocol.DecodeResult
import io.openhoyi.protocol.HoyiCodec
import io.openhoyi.protocol.IdleTelemetry
import io.openhoyi.protocol.MachineSettingChange
import io.openhoyi.protocol.SleepPart
import io.openhoyi.protocol.WeeklySleepSchedule
import io.openhoyi.session.ExtractionState
import io.openhoyi.session.SettingsWriteTracker
import io.openhoyi.session.ShotStartObservation
import io.openhoyi.session.SleepScheduleWriteTracker

/** Interactive fixture; actual production presenters, but local preview navigation, not MainActivity tabs. */
internal object MockBleVisibleProof {
    fun open(activity: MainActivity, shot: Boolean) {
        check(BuildConfig.MOCK_MODE && BuildConfig.BLE_SAME_ID_QA && activity.packageName == "io.openhoyi.mobile.mock")
        val scope = MobileService.openDetachedCore(activity.applicationContext, shot, shot)
        var shown: AlertDialog? = null
        try {
            val p = scope.port
            p.bootstrap()
            val zh = activity.resources.configuration.locales[0].language == "zh"
            fun label(cn: String, en: String) = if (zh) cn else en
            val style = TaskProductVisualStyle(activity)
            val body = style.column("qa/v2/live-presenters")
            val nav = style.column("qa/v2/local-navigation")
            val content = style.column("qa/v2/visible-content")
            val readout = style.text("", TaskProductVisualStyle.body, "qa/v2/live-readout")
            val status = style.text("", TaskProductVisualStyle.body, "qa/v2/status")
            val error = style.text("", TaskProductVisualStyle.auxiliary, "qa/v2/fail-closed")
            style.add(body, style.text(label("仅模拟。复用产品组件；这里的导航不是正式标签页。",
                "Simulation only. Shared product presenters; preview navigation, not production tabs."), TaskProductVisualStyle.auxiliary))
            style.add(body, nav); style.add(body, content)
            style.add(content, readout); style.add(content, status); style.add(body, error)
            var render = {}
            var failed = false
            fun action(container: LinearLayout, cn: String, en: String, tap: () -> Unit) =
                HoyiUi.button(activity, container, label(cn, en)) {
                    if (!failed) {
                        try { tap(); render() } catch (_: Exception) {
                            failed = true
                            error.text = label("隔离验收中止。请关闭此窗。", "Fixture stopped. Close this panel.")
                            content.isEnabled = false
                            runCatching { scope.close() }
                        }
                    }
                }
            if (!shot) {
                val settings = settings(0xfd)
                p.advance(1); p.observe(settings)
                val first = first(0xfe); val second = second()
                p.advance(1); p.observe(first)
                p.advance(1); p.observe(second)
                val base = requireNotNull(WeeklySleepSchedule.fromReadback(part(first), part(second)))
                val target = WeeklySleepSchedule(base.days.mapIndexed { index, day ->
                    if (index == 0) day.copy(enabled = false) else day
                })
                var tab = 0
                var phase = 0
                var steamSent = false
                var temperature = 9400
                action(nav, "机器预览", "Machine preview") { tab = 0 }
                action(nav, "周表预览", "Schedule preview") { tab = 1 }
                val week = action(content, "修改周表", "Change weekly plan") {
                    check(p.changeSchedule(base, target) == null); phase = 1
                }
                val links = action(content, "模拟两包写入完成", "Complete both fake writes") {
                    p.complete(); p.advance(500); p.complete(); phase = 2
                }
                val telemetry = action(content, "模拟温度更新", "Advance fake telemetry") {
                    temperature += 25
                    p.advance(1); p.observe(idle(temperature))
                }
                val steam = action(content, "关闭蒸汽锅炉", "Turn steam boiler off") {
                    check(p.heatingAdmission.canSubmit)
                    check(p.apply(MachineSettingChange.SteamHeating(false)) == null)
                    steamSent = true
                }
                val heatReport = action(content, "模拟蒸汽回报", "Deliver fake steam settings") {
                    p.complete(); p.advance(1); p.observe(settings(0xed))
                    check(p.settingState == SettingsWriteTracker.State.CONFIRMED)
                    check(p.scheduleState == SleepScheduleWriteTracker.State.WAITING_READBACK)
                    phase = 3
                }
                val weekReport = action(content, "模拟周表双段回报", "Deliver both fake schedule reports") {
                    p.advance(1); p.observe(first(0x7e))
                    check(p.scheduleState == SleepScheduleWriteTracker.State.WAITING_READBACK)
                    p.advance(1); p.observe(second)
                    check(p.scheduleState == SleepScheduleWriteTracker.State.CONFIRMED); phase = 4
                }
                render = {
                    val observed = p.liveFrame as? IdleTelemetry
                    readout.text = observed?.let { formatC(it.brewTemperatureHundredthsC) } ?: "—"
                    val schedule = when (p.scheduleState) {
                        SleepScheduleWriteTracker.State.WRITING -> activity.getString(R.string.home_boiler_schedule_writing)
                        SleepScheduleWriteTracker.State.WAITING_READBACK -> activity.getString(R.string.home_boiler_schedule_readback)
                        SleepScheduleWriteTracker.State.CONFIRMED -> label("周表已由双段回报确认", "Schedule confirmed by both reports")
                        else -> label("周表未修改", "Weekly plan unchanged")
                    }
                    status.text = ("${if(tab==0) label("机器", "Machine") else label("周表", "Schedule")} · $schedule") +
                        if (p.settingState == SettingsWriteTracker.State.CONFIRMED) label(" · 蒸汽已确认", " · Steam confirmed") else ""
                    week.isEnabled = phase == 0
                    links.isEnabled = phase == 1
                    telemetry.isEnabled = phase in 1..3
                    steam.isEnabled = phase == 2 && !steamSent && p.heatingAdmission.canSubmit
                    heatReport.isEnabled = phase == 2 && steamSent
                    weekReport.isEnabled = phase == 3
                }
            } else {
                p.advance(1); p.observe(settings(0xf9)) // Café mode fixture avoids heating, no setting write.
                val profile = p.selectImmutableCurve { it.targetHundredthsGram <= 0 }
                var phase = 0
                val start = action(content, "开始模拟萃取", "Start fake extraction") {
                    check(p.start(profile) == null); p.complete()
                    check(p.startEvidence == ShotStartObservation.WAITING_FOR_MACHINE); phase = 1
                }
                val first = action(content, "模拟首条萃取回报", "Deliver one fake extraction report") {
                    p.advance(20); p.observe(extraction(0), generation = p.generation + 1)
                    p.advance(20); p.observe(extraction(0))
                    check(p.startEvidence == ShotStartObservation.WAITING_FOR_MACHINE); phase = 2
                }
                val next = action(content, "模拟下一条萃取回报", "Deliver advancing fake extraction report") {
                    p.advance(1000); p.observe(extraction(1))
                    check(p.startEvidence == ShotStartObservation.MACHINE_EXTRACTION_OBSERVED); phase = 3
                }
                val stop = action(content, "停止", "Stop") {
                    check(p.stopPresentation.enabled)
                    p.stop(); phase = 4
                    check(p.shotState == ExtractionState.STOP_REQUESTED)
                }
                render = {
                    val observation = ExtractionStartObservationPresentation.label(p.startEvidence)
                    status.text = if (phase == 4) activity.getString(R.string.stop_processing) else
                        observation?.let(activity::getString) ?: label("尚未发出启动请求", "No start request sent")
                    val live = p.liveFrame
                    readout.text = when (live) {
                        is io.openhoyi.protocol.ExtractionTelemetry -> formatC(live.brewTemperatureHundredthsC)
                        is IdleTelemetry -> formatC(live.brewTemperatureHundredthsC)
                        else -> "—"
                    }
                    start.isEnabled = phase == 0; first.isEnabled = phase == 1; next.isEnabled = phase == 2
                    val action = p.stopPresentation
                    stop.visibility = if (action.visible) View.VISIBLE else View.GONE
                    stop.isEnabled = action.enabled
                    stop.text = activity.getString(action.labelResource)
                }
            }
            render()
            shown = AppDialog.show(activity, label("蓝牙 V2 界面模拟", "Bluetooth V2 UI fixture"), body,
                AppDialog.Action(label("关闭", "Close")) {})
            shown.setOnDismissListener { runCatching { scope.close() } }
            body.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) = Unit
                override fun onViewDetachedFromWindow(v: View) {
                    runCatching { scope.close() }
                }
            })
        } catch (failure: Throwable) {
            try { scope.close() } catch (retire: Throwable) { failure.addSuppressed(retire) }
            shown?.dismiss()
            throw failure
        }
    }
    private fun formatC(hundredths: Int) =
        String.format(java.util.Locale.ROOT, "%.2f °C", hundredths / 100.0)
    private fun b(vararg values: Int) = values.map(Int::toByte).toByteArray()
    private fun settings(flags: Int) = b(0x83, 1, 0x13, flags, 92, 0, 125, 15, 53, 0, 25, 0, 0x6e)
    private fun first(enabled: Int) =
        b(0x83, 0x40, enabled, 10, 0, 7, 30, 10, 0, 7, 30, 10, 0, 7, 30, 10, 0, 7, 30, 0x3d)
    private fun second() = b(0x83, 0x80, 10, 0, 7, 30, 10, 0, 7, 30, 10, 0, 7, 30, 0x10)
    private fun idle(c: Int) =
        b(0x40, 0, c shr 8, c and 255, 0x2f, 0x1c, 0x77, 0x0b, 0, 0, 0, 0, 0, 0, 0, 25, 3, 0x21, 0xaf)
    private fun extraction(elapsed: Int) = b(0x80, 0, 8, 0, elapsed, 40, 0, 15, 0x24, 0xbf, 10, 0x40, 0xff)
    private fun part(bytes: ByteArray) = (HoyiCodec.decode(bytes) as DecodeResult.Valid).value as SleepPart
}
