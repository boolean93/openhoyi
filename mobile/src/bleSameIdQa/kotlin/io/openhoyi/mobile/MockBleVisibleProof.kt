package io.openhoyi.mobile

import android.app.AlertDialog
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import io.openhoyi.protocol.CoffeeCommands
import io.openhoyi.protocol.DecodeResult
import io.openhoyi.protocol.HoyiCodec
import io.openhoyi.protocol.IdleTelemetry
import io.openhoyi.protocol.MachineSettingChange
import io.openhoyi.protocol.Settings
import io.openhoyi.protocol.SleepPart
import io.openhoyi.protocol.WeeklySleepSchedule
import io.openhoyi.session.ExtractionState
import io.openhoyi.session.SettingsWriteTracker
import io.openhoyi.session.ShotStartObservation
import io.openhoyi.session.SleepScheduleWriteTracker

/** Interactive fixture; actual production presenters, but local preview navigation, not MainActivity tabs. */
internal object MockBleVisibleProof {
    fun openSettingsSync(activity:MainActivity) {
        check(BuildConfig.MOCK_MODE && BuildConfig.BLE_SAME_ID_QA && activity.packageName=="io.openhoyi.mobile.mock")
        val scope=MobileService.openDetachedCore(activity.applicationContext)
        try {
            val port=scope.port
            port.bootstrap()
            repeat(3) {port.advance(60_000L)}
            port.advance(1)
            port.observe(idle(9400))
            val initialWrites=port.calls.count {it.operation is io.openhoyi.session.GattOperation.Write}
            val zh=activity.resources.configuration.locales[0].language=="zh"
            fun label(cn:String,en:String)=if(zh)cn else en
            val style=TaskProductVisualStyle(activity)
            val body=style.column("qa/v2/stale-settings")
            val state=style.text("",TaskProductVisualStyle.body,"qa/v2/stale-settings/evidence")
            val result=style.text("",TaskProductVisualStyle.auxiliary,"qa/v2/stale-settings/status").apply {
                accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE
            }
            style.add(body,style.text(label("仅模拟：共用首页与机器设置的控件状态，不操作真实设备。",
                "Simulation only: shared Home and settings control state; no real device."),
                TaskProductVisualStyle.auxiliary))
            style.add(body,state)
            var render:()->Unit={}
            var completed=false
            var failed=false
            var acked=false
            var expectedStatus:Int?=null
            var retries=0
            val confirmedBytes=settings(0xfd)
            val confirmedBit=((HoyiCodec.decode(confirmedBytes) as DecodeResult.Valid).value as Settings).brewHeating
            var fresh=false
            var renderingSwitch=false
            var gestureProbe=false
            var freshOwner=Any() // Synthetic presentation identity, not a Bluetooth disconnect.
            fun writePackets()=port.calls.mapNotNull {it.operation as? io.openhoyi.session.GattOperation.Write}
                .drop(initialWrites)
            fun writes()=writePackets().map {it.bytes.first().toInt() and 255}
            fun freshIdentity():Pair<Any,Boolean>?=if(fresh && port.heatingAdmission.canSubmit)freshOwner to confirmedBit else null
            fun conduct(tap:()->Unit) {
                if(completed || failed)return
                try {tap();render()}
                catch(_:Exception) {failed=true;runCatching {scope.close()};state.text=label("用例失败，请关闭。","Fixture failed; close it.")}
            }
            val quiet=HoyiUi.button(activity,body,label("模拟被动等待","Advance fake passive wait")) {
                conduct {
                    port.pollAutomaticStaleSettings()
                    port.advance(349)
                    port.pollAutomaticStaleSettings()
                    check(!port.settingsSyncPending && writes().isEmpty() && port.liveFrame is IdleTelemetry)
                    port.advance(1)
                    port.pollAutomaticStaleSettings()
                    check(port.settingsSyncPending && writes()==listOf(1))
                }
            }.apply {tag="qa/v2/stale-settings/passive-time"}
            style.add(body,result)
            val retrySwitch=HoyiSwitch(activity).apply {
                tag="qa/v2/stale-settings/same-switch"
                contentDescription=label("冲泡锅炉","Brew boiler")
                authoritativeToggleIdentity=::freshIdentity
                setOnCheckedChangeListener {_,checked->
                    if(!renderingSwitch)conduct {
                        check(fresh && !gestureProbe && checked!=confirmedBit)
                        check(writes()==List(1+retries){1})
                        check(port.apply(MachineSettingChange.BrewHeating(checked))==null)
                        check(writes()==List(1+retries){1}+0x0c)
                        val expected=CoffeeCommands.brewHeating(checked).frame.toByteArray()
                        check(writePackets().last().bytes.contentEquals(expected))
                        completed=true
                    }
                }
            }
            style.add(body,retrySwitch)
            fun motion(action:Int,down:Long) {
                check(retrySwitch.isAttachedToWindow && retrySwitch.width>0 && retrySwitch.height>0)
                val event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,
                    retrySwitch.width/2f,retrySwitch.height/2f,0).apply {
                    source=android.view.InputDevice.SOURCE_TOUCHSCREEN
                }
                try {check(retrySwitch.dispatchTouchEvent(event))} finally {event.recycle()}
            }
            val retryClock=HoyiUi.button(activity,body,label("模拟重试冷却结束","Advance fake retry cooldown")) {
                conduct {
                    repeat(2){port.advance(1000);port.observe(idle(9400));port.pollAutomaticStaleSettings()}
                    check(!port.settingsSyncPending && port.canRetrySettingsFromSwitch && writes()==List(1+retries){1})
                }
            }
            val ack=HoyiUi.button(activity,body,label("模拟写入回执","Deliver fake write callback")) {
                conduct {port.complete();acked=true;check(port.settingsSyncPending && !port.heatingAdmission.canSubmit)}
            }
            val good=HoyiUi.button(activity,body,label("模拟新设置回报","Deliver current settings report")) {
                conduct {
                    // A finger down before fresh evidence must not become a write on release.
                    gestureProbe=true
                    val staleDown=SystemClock.uptimeMillis()
                    motion(MotionEvent.ACTION_DOWN,staleDown)
                    port.advance(1);port.observe(confirmedBytes)
                    check(!port.settingsSyncPending && port.heatingAdmission.canSubmit && writes()==List(1+retries){1})
                    expectedStatus=R.string.machine_settings_sync_observed
                    fresh=true
                    render()
                    motion(MotionEvent.ACTION_UP,staleDown)
                    check(retrySwitch.isChecked==confirmedBit && writes()==List(1+retries){1})
                    // Exercise Android's posted native click with a changed presentation owner
                    // after UP. This is not a simulated physical reconnection.
                    val nativeDown=SystemClock.uptimeMillis()
                    motion(MotionEvent.ACTION_DOWN,nativeDown)
                    motion(MotionEvent.ACTION_UP,nativeDown)
                    freshOwner=Any()
                    render()
                    check(retrySwitch.post {
                        conduct {
                            check(retrySwitch.isChecked==confirmedBit && writes()==List(1+retries){1})
                            gestureProbe=false
                        }
                    })
                }
            }
            val timeout=HoyiUi.button(activity,body,label("模拟等待超时","Advance to timeout")) {
                conduct {
                    repeat(5) {port.advance(1000);port.observe(idle(9400));port.pollAutomaticStaleSettings()}
                    check(!port.settingsSyncPending && !port.heatingAdmission.canSubmit &&
                        port.liveFrame is IdleTelemetry && writes()==List(1+retries){1})
                    expectedStatus=R.string.machine_settings_sync_unconfirmed
                }
            }
            render={
                port.renderSettingsSync(result)
                if(port.settingsSyncPending) {
                    check(result.visibility==View.VISIBLE &&
                        result.text.toString()==activity.getString(R.string.machine_settings_sync_pending))
                }
                expectedStatus?.let { expected ->
                    check(result.visibility==View.VISIBLE && result.text.toString()==activity.getString(expected))
                }
                state.text=if(completed && fresh)label("通过：旧手势没有写入；重新点击只发送了一条模拟锅炉命令。",
                    "PASS: older gestures sent nothing; a distinct tap sent one fake boiler command.")
                    else if(fresh && !gestureProbe)label("手势检查通过，未发锅炉命令；请重新点一下同一个开关。",
                        "Gesture checks passed with no boiler write. Tap the same switch again.")
                    else if(fresh)label("正在检查回读与点击之间的手势边界。","Checking the gesture transition after readback.")
                    else label("机器已就绪；开关暂不可操作。","Machine ready; switch not available yet.")
                renderingSwitch=true
                try {if(!completed && retrySwitch.isChecked!=confirmedBit)retrySwitch.isChecked=confirmedBit}
                finally {renderingSwitch=false}
                retryClock.isEnabled=!completed && expectedStatus==R.string.machine_settings_sync_unconfirmed &&
                    !port.canRetrySettingsFromSwitch
                retrySwitch.readbackRetry=if(!completed && port.canRetrySettingsFromSwitch) ({
                    conduct {
                        check(port.retrySettingsFromSwitch())
                        retries++
                        acked=false
                        expectedStatus=null
                        check(port.settingsSyncPending && writes()==List(1+retries){1})
                    }
                }) else null
                retrySwitch.drawnToggleIdentity=freshIdentity()
                retrySwitch.isEnabled=!completed && (retrySwitch.readbackRetry!=null || freshIdentity()!=null)
                quiet.isEnabled=!completed && !acked && !port.settingsSyncPending && writes().isEmpty()
                ack.isEnabled=!completed && !acked && port.settingsSyncPending
                good.isEnabled=!completed && acked && port.settingsSyncPending
                timeout.isEnabled=good.isEnabled
            }
            render()
            check(!port.heatingAdmission.canSubmit && quiet.isEnabled && writes().isEmpty())
            val dialog=AlertDialog.Builder(activity).setTitle(label("模拟：设置同步","Fixture: settings sync"))
                .setView(body).setNegativeButton(android.R.string.cancel,null).create()
            dialog.setOnDismissListener {scope.close()}
            activity.trackDialog(dialog)
            AppDialog.show(dialog)
        } catch(error:Throwable) {scope.close();throw error}
    }

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
                var tab = 1
                val machinePanel = style.column("qa/v2/machine-panel")
                val weeklyPanel = style.column("qa/v2/weekly-panel")
                val machineAction = style.text("", TaskProductVisualStyle.auxiliary, "qa/v2/machine-admission")
                val weeklyPending = style.text("", TaskProductVisualStyle.auxiliary, "qa/v2/week-pending")
                style.add(machinePanel, style.text(label("咖啡机","Machine"), TaskProductVisualStyle.section))
                style.add(machinePanel, machineAction)
                style.add(weeklyPanel, style.text(label("每周计划","Weekly plan"), TaskProductVisualStyle.section))
                style.add(weeklyPanel, weeklyPending)
                style.add(content, machinePanel)
                style.add(content, weeklyPanel)
                var phase = 0
                var steamSent = false
                var temperature = 9400
                action(nav, "机器预览", "Machine preview") { tab = 0 }
                action(nav, "周表预览", "Schedule preview") { tab = 1 }
                val week = action(weeklyPanel, "修改周表", "Change weekly plan") {
                    check(p.changeSchedule(base, target) == null); phase = 1
                }
                val links = action(weeklyPanel, "模拟两包写入完成", "Complete both fake writes") {
                    p.complete(); p.advance(500); p.complete(); phase = 2
                }
                val telemetry = action(machinePanel, "模拟温度更新", "Advance fake telemetry") {
                    temperature += 25
                    p.advance(1); p.observe(idle(temperature))
                }
                val steam = action(machinePanel, "关闭蒸汽锅炉", "Turn steam boiler off") {
                    check(p.heatingAdmission.canSubmit)
                    check(p.apply(MachineSettingChange.SteamHeating(false)) == null)
                    steamSent = true
                }
                val heatReport = action(machinePanel, "模拟蒸汽回报", "Deliver fake steam settings") {
                    p.complete(); p.advance(1); p.observe(settings(0xed))
                    check(p.settingState == SettingsWriteTracker.State.CONFIRMED)
                    check(p.scheduleState == SleepScheduleWriteTracker.State.WAITING_READBACK)
                    phase = 3
                }
                val weekReport = action(weeklyPanel, "模拟周表双段回报", "Deliver both fake schedule reports") {
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
                    machinePanel.visibility = if(tab==0) View.VISIBLE else View.GONE
                    weeklyPanel.visibility = if(tab==1) View.VISIBLE else View.GONE
                    val admission=p.heatingAdmission
                    machineAction.text = admission.reason?.let(activity::getString) ?: if(admission.canSubmit)
                        label("可操作蒸汽锅炉", "Steam control available") else label("蒸汽暂不可操作", "Steam control unavailable")
                    weeklyPending.text = when(p.scheduleState) {
                        SleepScheduleWriteTracker.State.WRITING -> label("写入中，尚不可操作蒸汽", "Writing: steam is not yet available")
                        SleepScheduleWriteTracker.State.WAITING_READBACK -> label("仍等待周表双段回报", "Still waiting for the two schedule reports")
                        SleepScheduleWriteTracker.State.CONFIRMED -> label("收到周表双段回报", "Both schedule reports received")
                        else -> label("等待修改本周计划", "Ready to edit this week's plan")
                    }
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
