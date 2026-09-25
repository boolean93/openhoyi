package io.openhoyi.mobile

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.InputType
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import io.openhoyi.protocol.MachineSettingChange
import io.openhoyi.protocol.SleepDay
import io.openhoyi.protocol.WeeklySleepDay
import io.openhoyi.protocol.WeeklySleepSchedule
import io.openhoyi.session.DeviceState
import java.util.UUID
import java.util.Locale

/** Only known setting commands are exposed; applied state requires a subsequent 0x83 readback. */
class MachineSettingsActivity : ThemedActivity() {
    private var service: MobileService? = null
    private var bound = false
    private var visible = false
    private val visibilityToken = UUID.randomUUID().toString()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var connectionState: TextView
    private lateinit var settings: TextView
    private lateinit var settingsOverview: TextView
    private lateinit var settingsToggle: Button
    private var detailsExpanded = false
    private lateinit var schedule: TextView
    private lateinit var writeStatus: TextView
    private lateinit var scheduleWriteStatus: TextView
    private lateinit var cupResetStatus: TextView
    private lateinit var cupResetButton: Button
    private lateinit var brewInput: EditText
    private lateinit var compensationInput: EditText
    private lateinit var steamInput: EditText
    private lateinit var standbyTemperatureInput: EditText
    private lateinit var brewHeatingButton: Button
    private lateinit var steamHeatingButton: Button
    private lateinit var lightButton: Button
    private lateinit var waterSupplyButton: Button
    private lateinit var runModeButton: Button
    private lateinit var sleepScheduleButton: Button
    private val controlButtons = mutableListOf<Button>()
    private val scheduleButtons = mutableListOf<Button>()
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = (binder as MobileService.LocalBinder).service
            service?.screenVisible(visibilityToken, visible)
            render()
        }
        override fun onServiceDisconnected(name: ComponentName) { service = null; render() }
        override fun onBindingDied(name: ComponentName) { release(); render() }
    }
    private val refresh = object : Runnable {
        override fun run() { render(); if (visible) handler.postDelayed(this, 500) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        detailsExpanded = savedInstanceState?.getBoolean("settingsExpanded") ?: false
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.mobile_background))
            setOnApplyWindowInsetsListener { view, insets ->
                val area = if (Build.VERSION.SDK_INT >= 30) {
                    val x = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                    intArrayOf(x.left, x.top, x.right, x.bottom)
                } else {
                    @Suppress("DEPRECATION")
                    intArrayOf(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                        insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                }
                view.setPadding(area[0], area[1], area[2], area[3])
                insets
            }
        }
        val scroll = ScrollView(this)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        HoyiUi.navigation(this, root, MachineSettingsActivity::class.java)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(28))
        }
        scroll.addView(body)
        setContentView(root)
        HoyiUi.header(this, body, "机器设置", "修改后等待机器回读确认", back = true)
        text(body, if (BuildConfig.MOCK_MODE) "以下为模拟设置与睡眠计划；修改操作不会发送蓝牙命令。"
            else "更改后等待机器回读确认；蓝牙写入成功不代表设置已生效。", 14)
        val overview = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val controlsPane = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (HoyiUi.wide(this)) {
            val columns = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            body.addView(columns)
            columns.addView(overview, LinearLayout.LayoutParams(0, -2, .8f))
            columns.addView(controlsPane, LinearLayout.LayoutParams(0, -2, 1.2f).apply { marginStart = dp(16) })
        } else {
            body.addView(overview)
            body.addView(controlsPane)
        }
        val summaryCard = card(overview, "连接状态")
        connectionState = text(summaryCard, "未连接", 16)
        val settingsCard = card(overview, "当前设置")
        settingsOverview = text(settingsCard, "尚未收到机器设置", 16)
        settings = text(settingsCard, "", 14).apply {
            visibility = if (detailsExpanded) View.VISIBLE else View.GONE
        }
        settingsToggle = action(settingsCard, if (detailsExpanded) "收起完整回读" else "查看完整回读") {
            detailsExpanded = !detailsExpanded
            settings.visibility = if (detailsExpanded) View.VISIBLE else View.GONE
            settingsToggle.text = if (detailsExpanded) "收起完整回读" else "查看完整回读"
        }
        val controls = card(controlsPane, "常用设置")
        writeStatus = text(controls, "尚未修改", 14)
        HoyiUi.label(this, controls, "温度", 16, true).apply { setPadding(0, dp(14), 0, dp(4)) }
        brewInput = temperatureInput(controls, "萃取设定温度（75–105 °C）")
        controlButtons += action(controls, "设置萃取温度") {
            val c = brewInput.text.toString().toIntOrNull()
            if (c == null || c !in 75..105) brewInput.error = "请输入 75–105"
            else confirm(MachineSettingChange.BrewTemperature(c))
        }
        compensationInput = temperatureInput(controls, "冲泡温差补偿（0–5 °C）")
        controlButtons += action(controls, "设置温差补偿") {
            val c = compensationInput.text.toString().toIntOrNull()
            if (c == null || c !in 0..5) compensationInput.error = "请输入 0–5"
            else confirm(MachineSettingChange.BrewCompensation(c))
        }
        steamInput = temperatureInput(controls, "蒸汽设定温度（110–145 °C）")
        controlButtons += action(controls, "设置蒸汽温度") {
            val c = steamInput.text.toString().toIntOrNull()
            if (c == null || c !in 110..145) steamInput.error = "请输入 110–145"
            else confirm(MachineSettingChange.SteamTemperature(c))
        }
        HoyiUi.label(this, controls, "加热与机器功能", 16, true).apply { setPadding(0, dp(18), 0, dp(4)) }
        brewHeatingButton = action(controls, "切换萃取加热") {
            service?.snapshot?.settings?.let { confirm(MachineSettingChange.BrewHeating(!it.brewHeating)) }
        }
        steamHeatingButton = action(controls, "切换蒸汽加热") {
            service?.snapshot?.settings?.let { confirm(MachineSettingChange.SteamHeating(!it.steamHeating)) }
        }
        lightButton = action(controls, "切换照明") {
            service?.snapshot?.settings?.let { confirm(MachineSettingChange.Light(it.flags and 0x08 == 0)) }
        }
        waterSupplyButton = action(controls, "切换供水方式") {
            service?.snapshot?.settings?.let { confirm(MachineSettingChange.WaterSupply(it.flags and 0x02 == 0)) }
        }
        runModeButton = action(controls, "切换运行模式") {
            service?.snapshot?.settings?.let { confirm(MachineSettingChange.RunMode(it.flags and 0x04 == 0)) }
        }
        controlButtons += listOf(brewHeatingButton, steamHeatingButton, lightButton, waterSupplyButton, runModeButton)
        HoyiUi.label(this, controls, "待机", 16, true).apply { setPadding(0, dp(18), 0, dp(4)) }
        controlButtons += action(controls, "设置自动待机时间") { chooseStandbyDelay() }
        standbyTemperatureInput = temperatureInput(controls, "待机温度（0–100 °C）")
        controlButtons += action(controls, "设置待机温度") {
            val c = standbyTemperatureInput.text.toString().toIntOrNull()
            if (c == null || c !in 0..100) standbyTemperatureInput.error = "请输入 0–100"
            else {
                val minutes = service?.snapshot?.settings?.standbyMinutes
                if (minutes == null || minutes !in listOf(0, 15, 30, 60, 120))
                    Toast.makeText(this, "机器自动待机档位未知，暂不能修改温度", Toast.LENGTH_SHORT).show()
                else confirm(MachineSettingChange.StandbyTemperature(c, minutes))
            }
        }
        val sleepCard = card(controlsPane, "每周睡眠计划")
        schedule = text(sleepCard, "尚未收到睡眠计划", 16)
        scheduleWriteStatus = text(sleepCard, "时间修改：尚未修改", 14)
        sleepScheduleButton = action(sleepCard, "切换睡眠计划总开关") {
            service?.snapshot?.settings?.let {
                confirm(MachineSettingChange.SleepScheduleEnabled(it.flags and 0x01 == 0))
            }
        }
        controlButtons += sleepScheduleButton
        val scheduleEditor = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = android.view.View.GONE }
        val scheduleToggle = action(sleepCard, "编辑每周时间") {}
        scheduleToggle.setOnClickListener {
            scheduleEditor.visibility = if (scheduleEditor.visibility == android.view.View.GONE)
                android.view.View.VISIBLE else android.view.View.GONE
            scheduleToggle.text = if (scheduleEditor.visibility == android.view.View.VISIBLE)
                "收起每周编辑" else "编辑每周时间"
        }
        sleepCard.addView(scheduleEditor)
        listOf("周日", "周一", "周二", "周三", "周四", "周五", "周六").forEachIndexed { index, name ->
            scheduleButtons += action(scheduleEditor, "编辑 $name 的睡眠/唤醒时间") { editScheduleDay(index, name) }
        }
        controlButtons += scheduleButtons
        val cupCard = card(overview, "累计杯数")
        cupResetStatus = text(cupCard, "尚未重置", 14)
        cupResetButton = action(cupCard, "重置累计杯数") { confirmCupReset() }
        render()
    }

    override fun onStart() {
        super.onStart()
        visible = true
        if (!bound) bound = bindService(Intent(this, MobileService::class.java), connection, 0)
        handler.post(refresh)
    }
    override fun onStop() {
        visible = false
        handler.removeCallbacks(refresh)
        service?.screenVisible(visibilityToken, false)
        release()
        super.onStop()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("settingsExpanded", detailsExpanded)
        super.onSaveInstanceState(outState)
    }
    private fun release() {
        if (bound) { unbindService(connection); bound = false }
        service = null
    }
    private fun render() {
        if (!::connectionState.isInitialized) return
        val owner = service
        val snapshot = owner?.snapshot ?: MobileSnapshot()
        val ready = snapshot.coffeeState == DeviceState.READY
        connectionState.update(if (BuildConfig.MOCK_MODE) "Mock 模拟设备 · 无蓝牙连接" else
            "${DeviceStatusText.label(snapshot.coffeeState)}${if (ready) " · 已认证" else " · 数据不可视为当前生效配置"}")
        settingsOverview.update(MachineSettingsPresentation.overview(snapshot.settings))
        settings.update(MachineSettingsPresentation.settings(snapshot.settings))
        schedule.update(MachineSettingsPresentation.schedule(snapshot.sleepFirst, snapshot.sleepSecond))
        scheduleWriteStatus.update("时间修改：" + when (owner?.scheduleWriteState) {
            SleepScheduleWriteTracker.State.WRITING -> "正在顺序写入两包计划"
            SleepScheduleWriteTracker.State.WAITING_READBACK -> "已写入，等待两段机器回报"
            SleepScheduleWriteTracker.State.CONFIRMED -> "整周回读已确认"
            SleepScheduleWriteTracker.State.FAILED -> "首包写入失败"
            SleepScheduleWriteTracker.State.UNKNOWN -> "结果未知，请核对机器上的整周计划"
            SleepScheduleWriteTracker.State.RECONCILED -> "已重新读取整周计划，请核对后再编辑"
            else -> "尚未修改"
        })
        val pending = owner?.settingWriteState ?: SettingsWriteTracker.State.IDLE
        cupResetStatus.update("重置状态：" + when (owner?.cupResetState) {
            CupResetTracker.State.WRITING -> "正在写入命令"
            CupResetTracker.State.WAITING_ZERO -> "已写入，等待机器回报 0 杯"
            CupResetTracker.State.CONFIRMED -> "机器已回报 0 杯"
            CupResetTracker.State.FAILED -> "命令未写入"
            CupResetTracker.State.UNKNOWN -> "结果未知，请查看机器杯数"
            else -> "尚未重置"
        })
        writeStatus.update("设置状态：${owner?.pendingSetting?.let(MachineSettingsPresentation::change) ?: "尚未修改"} · " +
            when (pending) {
                SettingsWriteTracker.State.IDLE -> "尚未修改"
                SettingsWriteTracker.State.WRITING -> "正在写入"
                SettingsWriteTracker.State.WAITING_READBACK -> "已写入，等待机器回读"
                SettingsWriteTracker.State.CONFIRMED -> "机器回读已确认"
                SettingsWriteTracker.State.FAILED -> "写入失败"
                SettingsWriteTracker.State.UNKNOWN -> "结果未知，请查看机器"
            })
        val idle = snapshot.coffee as? io.openhoyi.protocol.IdleTelemetry
        val now = android.os.SystemClock.elapsedRealtime()
        val freshIdle = idle?.sleepStateRaw == 0 &&
            snapshot.coffeeAt?.let { it <= now && now - it <= 1500 } == true
        val editable = ready && snapshot.settings != null && freshIdle && owner?.manualShotActive != true &&
            owner?.shotState?.let(ShotGate::active) != true &&
            owner?.brewPreparationState == BrewPreparation.State.IDLE &&
            pending !in setOf(SettingsWriteTracker.State.WRITING, SettingsWriteTracker.State.WAITING_READBACK) &&
            owner?.scheduleWriteState !in setOf(SleepScheduleWriteTracker.State.WRITING,
                SleepScheduleWriteTracker.State.WAITING_READBACK) &&
            owner?.cupResetState !in setOf(CupResetTracker.State.WRITING,
                CupResetTracker.State.WAITING_ZERO)
        controlButtons.forEach { it.isEnabled = editable }
        cupResetButton.isEnabled = editable && snapshot.settings?.cupCount?.let { it > 0 && it == idle?.cupCount } == true &&
            owner?.sleepNowState !in setOf(SleepNowTracker.State.WRITING, SleepNowTracker.State.WAITING_ASLEEP)
        scheduleButtons.forEach { it.isEnabled = editable &&
            owner?.scheduleWriteState != SleepScheduleWriteTracker.State.UNKNOWN &&
            WeeklySleepSchedule.fromReadback(snapshot.sleepFirst, snapshot.sleepSecond) != null }
        sleepScheduleButton.isEnabled = editable &&
            (snapshot.settings?.flags?.and(0x01) == 1 ||
                SleepScheduleSafety.canEnable(snapshot.sleepFirst, snapshot.sleepSecond))
        brewHeatingButton.text = getString(R.string.setting_toggle_status,
            getString(R.string.setting_brew_heating),
            getString(if (snapshot.settings?.brewHeating == true) R.string.setting_on else R.string.setting_off))
        steamHeatingButton.text = getString(R.string.setting_toggle_status,
            getString(R.string.setting_steam_heating),
            getString(if (snapshot.settings?.steamHeating == true) R.string.setting_on else R.string.setting_off))
        lightButton.text = getString(R.string.setting_toggle_status,
            getString(R.string.setting_light),
            getString(if (snapshot.settings?.flags?.and(0x08) == 0x08) R.string.setting_on else R.string.setting_off))
        waterSupplyButton.text = getString(R.string.setting_water_supply_status,
            getString(if (snapshot.settings?.flags?.and(0x02) == 0x02)
                R.string.setting_water_piped else R.string.setting_water_tank))
        runModeButton.text = getString(R.string.setting_run_mode_status,
            getString(if (snapshot.settings?.flags?.and(0x04) == 0x04)
                R.string.setting_run_studio else R.string.setting_run_cafe))
        sleepScheduleButton.setText(when (snapshot.settings?.flags?.and(0x01)) {
            1 -> R.string.setting_sleep_schedule_on
            0 -> R.string.setting_sleep_schedule_off
            else -> R.string.setting_sleep_schedule_unknown
        })
    }
    private fun confirm(change: MachineSettingChange) {
        if (BuildConfig.MOCK_MODE) {
            AlertDialog.Builder(this).setTitle("模拟设置确认")
                .setMessage("${MachineSettingsPresentation.change(change)}\n仅检查界面流程；模拟值保持不变，不发送蓝牙命令。")
                .setPositiveButton("完成", null).show()
            return
        }
        AlertDialog.Builder(this).setTitle("确认修改机器设置")
            .setMessage(MachineSettingsPresentation.change(change) +
                if (change is MachineSettingChange.WaterSupply)
                    "\n请先核对机器实际进水方式；设置错误可能导致缺水。写入后等待机器回读确认。"
                else if (change is MachineSettingChange.RunMode)
                    "\n工作室模式下，曲线温度未到达目标时需先预热，达到后才能启动萃取。写入后等待机器回读确认。"
                else if (change is MachineSettingChange.BrewCompensation)
                    "\n此值会影响显示的冲泡温度和工作室模式的预热判断。写入后等待机器回读确认。"
                else "\n写入后需等待机器回读确认。")
            .setPositiveButton("发送") { _, _ ->
                service?.changeMachineSetting(change)?.let {
                    Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
                }
                render()
            }
            .setNegativeButton("取消", null).show()
    }
    private fun confirmCupReset() {
        if (BuildConfig.MOCK_MODE) {
            Toast.makeText(this, "Mock 版本不重置机器杯数", Toast.LENGTH_SHORT).show()
            return
        }
        val expected = service?.snapshot?.settings?.cupCount ?: return
        val input = EditText(this).apply {
            hint = "输入当前杯数 $expected"
            inputType = InputType.TYPE_CLASS_NUMBER
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        val dialog = AlertDialog.Builder(this).setTitle("核对累计杯数")
            .setMessage("请输入机器当前累计杯数。重置后无法恢复。")
            .setView(input).setPositiveButton("下一步", null).setNegativeButton("取消", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (input.text.toString().toIntOrNull() != expected) {
                    input.error = "请输入 $expected"
                    return@setOnClickListener
                }
                dialog.dismiss()
                AlertDialog.Builder(this).setTitle("确认重置累计杯数")
                    .setMessage("机器当前回报 $expected 杯。发送后杯数将归零，无法撤销。只有机器重新回报 0 杯才会显示成功。")
                    .setPositiveButton("发送重置命令") { _, _ ->
                        service?.resetCupCount(expected)?.let {
                            Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                        }
                        render()
                    }.setNegativeButton("取消", null).show()
            }
        }
        dialog.show()
    }
    private fun chooseStandbyDelay() {
        val values = listOf(15, 30, 60, 120, 0)
        val labels = arrayOf("15 分钟", "30 分钟", "1 小时", "2 小时", "永不")
        AlertDialog.Builder(this).setTitle("自动待机时间")
            .setItems(labels) { _, index ->
                val temperature = service?.snapshot?.settings?.standbyTemperatureC
                if (temperature == null) Toast.makeText(this, "尚未收到机器设置", Toast.LENGTH_SHORT).show()
                else confirm(MachineSettingChange.StandbyDelay(values[index], temperature))
            }.show()
    }
    private fun editScheduleDay(index: Int, name: String) {
        val baseline = service?.snapshot?.let {
            WeeklySleepSchedule.fromReadback(it.sleepFirst, it.sleepSecond)
        } ?: return
        val previous = baseline.days[index]
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(4))
        }
        val enabled = CheckBox(this).apply { text = "当天启用"; isChecked = previous.enabled; form.addView(this) }
        val sleepHour = numberInput(form, "睡眠时（0–23）", previous.time.sleepHour)
        val sleepMinute = numberInput(form, "睡眠分（0–59）", previous.time.sleepMinute)
        val wakeHour = numberInput(form, "唤醒时（0–23）", previous.time.wakeHour)
        val wakeMinute = numberInput(form, "唤醒分（0–59）", previous.time.wakeMinute)
        val dialog = AlertDialog.Builder(this).setTitle("编辑 $name")
            .setMessage(if (BuildConfig.MOCK_MODE) "模拟计划仅用于检查界面；提交后不会发送蓝牙命令。"
                else "旧版协议会重写整周计划；其他六天保持机器当前回报值。")
            .setView(form).setPositiveButton("核对计划", null).setNegativeButton("取消", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val sh = sleepHour.text.toString().toIntOrNull()
                val sm = sleepMinute.text.toString().toIntOrNull()
                val wh = wakeHour.text.toString().toIntOrNull()
                val wm = wakeMinute.text.toString().toIntOrNull()
                if (sh == null || sh !in 0..23 || wh == null || wh !in 0..23 ||
                    sm == null || sm !in 0..59 || wm == null || wm !in 0..59) {
                    Toast.makeText(this, "请输入有效的 24 小时时间", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val target = WeeklySleepSchedule(baseline.days.toMutableList().apply {
                    this[index] = WeeklySleepDay(enabled.isChecked, SleepDay(sh, sm, wh, wm))
                })
                dialog.dismiss()
                if (BuildConfig.MOCK_MODE) {
                    AlertDialog.Builder(this).setTitle("模拟计划预览")
                        .setMessage(String.format(Locale.CHINA,
                            "$name ${if (enabled.isChecked) "启用" else "关闭"}：%02d:%02d 睡眠，%02d:%02d 唤醒。\n模拟值保持不变，不发送蓝牙命令。",
                            sh, sm, wh, wm))
                        .setPositiveButton("完成", null).show()
                    return@setOnClickListener
                }
                AlertDialog.Builder(this).setTitle("确认写入整周睡眠计划")
                    .setMessage(String.format(Locale.CHINA,
                        "$name ${if (enabled.isChecked) "启用" else "关闭"}：%02d:%02d 睡眠，%02d:%02d 唤醒。\n将发送两包计划，并等待机器回报整周内容。",
                        sh, sm, wh, wm))
                    .setPositiveButton("发送") { _, _ ->
                        service?.changeSleepSchedule(baseline, target)?.let {
                            Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                        }
                        render()
                    }.setNegativeButton("取消", null).show()
            }
        }
        dialog.show()
    }
    private fun numberInput(parent: LinearLayout, label: String, value: Int): EditText = EditText(this).apply {
        hint = label
        inputType = InputType.TYPE_CLASS_NUMBER
        setText(String.format(Locale.getDefault(), "%d", value))
        minHeight = dp(48)
        setPadding(dp(14), dp(8), dp(14), dp(8))
        background = HoyiUi.shape(this@MachineSettingsActivity, R.color.mobile_surface, 12, R.color.mobile_border)
        parent.addView(this, LinearLayout.LayoutParams(-1, -2))
    }
    private fun temperatureInput(parent: LinearLayout, hintText: String): EditText = EditText(this).apply {
        hint = hintText
        inputType = InputType.TYPE_CLASS_NUMBER
        minHeight = dp(48)
        setPadding(dp(14), dp(8), dp(14), dp(8))
        background = HoyiUi.shape(this@MachineSettingsActivity, R.color.mobile_surface, 12, R.color.mobile_border)
        parent.addView(this, LinearLayout.LayoutParams(-1, -2))
    }
    private fun action(parent: LinearLayout, title: String, onClick: () -> Unit): Button =
        HoyiUi.button(this, parent, title, action = onClick)
    private fun TextView.update(value: String) { if (text.toString() != value) text = value }
    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()
    private fun text(parent: LinearLayout, value: String, size: Int, bold: Boolean = false): TextView = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(getColor(R.color.mobile_text))
        setPadding(0, dp(6), 0, dp(6))
        if (bold) setTypeface(null, Typeface.BOLD)
        parent.addView(this)
    }
    private fun card(parent: LinearLayout, title: String) = HoyiUi.card(this, parent, title)
}
