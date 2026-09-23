package io.openhoyi.mobile

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.InputType
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import io.openhoyi.protocol.MachineSettingChange
import io.openhoyi.session.DeviceState
import java.util.UUID

/** Only known setting commands are exposed; applied state requires a subsequent 0x83 readback. */
class MachineSettingsActivity : Activity() {
    private var service: MobileService? = null
    private var bound = false
    private var visible = false
    private val visibilityToken = UUID.randomUUID().toString()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var connectionState: TextView
    private lateinit var settings: TextView
    private lateinit var schedule: TextView
    private lateinit var writeStatus: TextView
    private lateinit var brewInput: EditText
    private lateinit var steamInput: EditText
    private lateinit var brewHeatingButton: Button
    private lateinit var steamHeatingButton: Button
    private lateinit var lightButton: Button
    private val controlButtons = mutableListOf<Button>()
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
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(244, 241, 235))
            setOnApplyWindowInsetsListener { view, insets ->
                if (Build.VERSION.SDK_INT >= 30) {
                    val area = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                    view.setPadding(area.left, area.top, area.right, area.bottom)
                } else {
                    @Suppress("DEPRECATION")
                    view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                        insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                }
                insets
            }
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(28))
        }
        scroll.addView(body)
        setContentView(scroll)
        text(body, "机器设置", 28, true)
        text(body, "更改后等待机器回读确认；蓝牙写入成功不代表设置已生效。", 14)
        val summaryCard = card(body, "连接状态")
        connectionState = text(summaryCard, "未连接", 16)
        val settingsCard = card(body, "当前设置")
        settings = text(settingsCard, "尚未收到机器设置", 16)
        val controls = card(body, "常用设置")
        writeStatus = text(controls, "尚未修改", 14)
        brewInput = temperatureInput(controls, "萃取设定温度（75–105 °C）")
        controlButtons += action(controls, "设置萃取温度") {
            val c = brewInput.text.toString().toIntOrNull()
            if (c == null || c !in 75..105) brewInput.error = "请输入 75–105"
            else confirm(MachineSettingChange.BrewTemperature(c))
        }
        steamInput = temperatureInput(controls, "蒸汽设定温度（110–145 °C）")
        controlButtons += action(controls, "设置蒸汽温度") {
            val c = steamInput.text.toString().toIntOrNull()
            if (c == null || c !in 110..145) steamInput.error = "请输入 110–145"
            else confirm(MachineSettingChange.SteamTemperature(c))
        }
        brewHeatingButton = action(controls, "切换萃取加热") {
            service?.snapshot?.settings?.let { confirm(MachineSettingChange.BrewHeating(!it.brewHeating)) }
        }
        steamHeatingButton = action(controls, "切换蒸汽加热") {
            service?.snapshot?.settings?.let { confirm(MachineSettingChange.SteamHeating(!it.steamHeating)) }
        }
        lightButton = action(controls, "切换照明") {
            service?.snapshot?.settings?.let { confirm(MachineSettingChange.Light(it.flags and 0x08 == 0)) }
        }
        controlButtons += listOf(brewHeatingButton, steamHeatingButton, lightButton)
        controlButtons += action(controls, "设置自动待机时间") { chooseStandbyDelay() }
        val sleepCard = card(body, "每周睡眠计划")
        schedule = text(sleepCard, "尚未收到睡眠计划", 16)
        Button(this).apply {
            text = "返回首页"
            setOnClickListener { finish() }
            body.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20) })
        }
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
    private fun release() {
        if (bound) { unbindService(connection); bound = false }
        service = null
    }
    private fun render() {
        if (!::connectionState.isInitialized) return
        val owner = service
        val snapshot = owner?.snapshot ?: MobileSnapshot()
        val ready = snapshot.coffeeState == DeviceState.READY
        connectionState.update("${snapshot.coffeeState.name}${if (ready) " · 已认证" else " · 数据不可视为当前生效配置"}")
        settings.update(MachineSettingsPresentation.settings(snapshot.settings))
        schedule.update(MachineSettingsPresentation.schedule(snapshot.sleepFirst, snapshot.sleepSecond))
        val pending = owner?.settingWriteState ?: SettingsWriteTracker.State.IDLE
        writeStatus.update("设置状态：${owner?.pendingSetting?.let(MachineSettingsPresentation::change) ?: "尚未修改"} · " +
            when (pending) {
                SettingsWriteTracker.State.IDLE -> "尚未修改"
                SettingsWriteTracker.State.WRITING -> "正在写入"
                SettingsWriteTracker.State.WAITING_READBACK -> "已写入，等待机器回读"
                SettingsWriteTracker.State.CONFIRMED -> "机器回读已确认"
                SettingsWriteTracker.State.FAILED -> "写入失败"
                SettingsWriteTracker.State.UNKNOWN -> "结果未知，请查看机器"
            })
        val editable = ready && snapshot.settings != null && owner?.shotState?.let(ShotGate::active) != true &&
            pending !in setOf(SettingsWriteTracker.State.WRITING, SettingsWriteTracker.State.WAITING_READBACK)
        controlButtons.forEach { it.isEnabled = editable }
        brewHeatingButton.text = getString(R.string.setting_toggle_status,
            getString(R.string.setting_brew_heating),
            getString(if (snapshot.settings?.brewHeating == true) R.string.setting_on else R.string.setting_off))
        steamHeatingButton.text = getString(R.string.setting_toggle_status,
            getString(R.string.setting_steam_heating),
            getString(if (snapshot.settings?.steamHeating == true) R.string.setting_on else R.string.setting_off))
        lightButton.text = getString(R.string.setting_toggle_status,
            getString(R.string.setting_light),
            getString(if (snapshot.settings?.flags?.and(0x08) == 0x08) R.string.setting_on else R.string.setting_off))
    }
    private fun confirm(change: MachineSettingChange) {
        AlertDialog.Builder(this).setTitle("确认修改机器设置")
            .setMessage(MachineSettingsPresentation.change(change) + "\n写入后需等待机器回读确认。")
            .setPositiveButton("发送") { _, _ ->
                service?.changeMachineSetting(change)?.let {
                    Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
                }
                render()
            }
            .setNegativeButton("取消", null).show()
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
    private fun temperatureInput(parent: LinearLayout, hintText: String): EditText = EditText(this).apply {
        hint = hintText
        inputType = InputType.TYPE_CLASS_NUMBER
        parent.addView(this, LinearLayout.LayoutParams(-1, -2))
    }
    private fun action(parent: LinearLayout, title: String, onClick: () -> Unit): Button = Button(this).apply {
        text = title
        setOnClickListener { onClick() }
        parent.addView(this, LinearLayout.LayoutParams(-1, -2))
    }
    private fun TextView.update(value: String) { if (text.toString() != value) text = value }
    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()
    private fun text(parent: LinearLayout, value: String, size: Int, bold: Boolean = false): TextView = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(Color.rgb(32, 38, 42))
        setPadding(0, dp(6), 0, dp(6))
        if (bold) setTypeface(null, Typeface.BOLD)
        parent.addView(this)
    }
    private fun card(parent: LinearLayout, title: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(16), dp(20), dp(16))
        background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(18).toFloat() }
        parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })
        text(this, title, 18, true)
    }
}
