package io.openhoyi.mobile

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.view.WindowInsets
import android.widget.*
import io.openhoyi.bluetooth.DiscoveredDevice
import io.openhoyi.protocol.ExtractionTelemetry
import io.openhoyi.protocol.IdleTelemetry
import io.openhoyi.protocol.MachineSettingChange
import io.openhoyi.session.DeviceRole
import io.openhoyi.session.DeviceState
import io.openhoyi.session.ExtractionState
import java.util.Locale

/** First native product screen: BLE connection, live values, and a selected captured curve. */
class HomeActivity : ThemedActivity() {
    private var service: MobileService? = null
    private var bound = false
    private var visible = false
    private var scanAfterBind = false
    private val visibilityToken = java.util.UUID.randomUUID().toString()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var safetyWarning: TextView
    private lateinit var coffee: TextView
    private lateinit var leverStatus: TextView
    private lateinit var leverButton: Button
    private lateinit var sleepStatus: TextView
    private lateinit var sleepButton: Button
    private lateinit var emergencyStop: Button
    private lateinit var alarmStatus: TextView
    private lateinit var scale: TextView
    private lateinit var tareStatus: TextView
    private lateinit var selection: TextView
    private lateinit var presetButtons: List<Button>
    private lateinit var candidates: LinearLayout
    private lateinit var scanButton: Button
    private lateinit var coffeeDisconnect: Button
    private lateinit var scaleDisconnect: Button
    private lateinit var tareButton: Button
    private var candidateKeys = emptyList<String>()
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = (binder as MobileService.LocalBinder).service
            service?.screenVisible(visibilityToken, visible)
            if (scanAfterBind) { scanAfterBind = false; service?.scan() }
            render()
        }
        override fun onServiceDisconnected(name: ComponentName) { service = null; render() }
        override fun onBindingDied(name: ComponentName) { release(); render() }
    }
    private val refresh = object : Runnable {
        override fun run() { render(); if (visible) handler.postDelayed(this, 250) }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(getColor(R.color.mobile_background)) }
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(20), dp(24), dp(28)) }
        scroll.addView(content); setContentView(scroll)
        text(content, "OpenHOYI Alpha", 28, true)
        content.addView(Switch(this).apply {
            setText(R.string.theme_dark_mode)
            isChecked = getSharedPreferences("appearance", MODE_PRIVATE).getBoolean("dark", false)
            setOnCheckedChangeListener { _, dark ->
                getSharedPreferences("appearance", MODE_PRIVATE).edit().putBoolean("dark", dark).apply()
                recreate()
            }
        })
        text(content, "原生连接与实时状态", 14)
        safetyWarning = text(content, "", 18, true).apply {
            setTextColor(getColor(R.color.mobile_danger))
            visibility = View.GONE
        }
        val connectionCard = card(content, "设备")
        status = text(connectionCard, "尚未连接", 16)
        scanButton = button(connectionCard, "扫描并连接设备") { enableAndScan() }
        candidates = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        connectionCard.addView(candidates)
        val coffeeCard = card(content, "咖啡机")
        coffee = text(coffeeCard, "未连接", 20)
        leverStatus = text(coffeeCard, "拨杆模式：尚未收到设置", 14)
        leverButton = button(coffeeCard, "切换拨杆模式") { chooseLeverMode() }
        sleepStatus = text(coffeeCard, "睡眠状态：未知", 14)
        sleepButton = button(coffeeCard, "立即睡眠") { confirmSleepNow() }
        alarmStatus = text(coffeeCard, "尚未收到机器告警状态", 14)
        emergencyStop = button(coffeeCard, "立即停止萃取") { service?.stopShot(); render() }.apply {
            setTextColor(getColor(R.color.mobile_danger))
        }
        button(coffeeCard, "查看机器设置") { startActivity(Intent(this, MachineSettingsActivity::class.java)) }
        coffeeDisconnect = button(coffeeCard, "断开咖啡机") { service?.disconnect(DeviceRole.COFFEE) }
        val scaleCard = card(content, "电子秤")
        scale = text(scaleCard, "未连接", 20)
        tareStatus = text(scaleCard, "去皮状态：尚未操作", 14)
        tareButton = button(scaleCard, "电子秤去皮") { service?.tareScale()?.let(::toast); render() }
        scaleDisconnect = button(scaleCard, "断开电子秤") { service?.disconnect(DeviceRole.BOOKOO) }
        val curveCard = card(content, "曲线库")
        selection = text(curveCard, "尚未选择曲线", 16)
        button(curveCard, "查看曲线库") { startActivity(Intent(this, CurveActivity::class.java)) }
        button(curveCard, "进入萃取页面") { startActivity(Intent(this, ExtractionActivity::class.java)) }
        button(curveCard, "萃取历史") { startActivity(Intent(this, HistoryActivity::class.java)) }
        text(curveCard, "工厂曲线经旧版启动报文逐字节校验；实际机器行为仍待验收。", 13)
        val presets = card(content, "五个快捷槽位")
        text(presets, "点选槽位进入萃取确认；可在曲线库中更换各槽位曲线。", 13)
        presetButtons = (1..5).map { slot ->
            button(presets, "槽位 $slot") {
                startActivity(Intent(this, ExtractionActivity::class.java).putExtra(PresetSlots.EXTRA_SLOT, slot))
            }
        }
        button(content, "导出操作记录 ZIP") {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/zip").putExtra(Intent.EXTRA_TITLE, "openhoyi-alpha-${System.currentTimeMillis()}.zip"), EXPORT)
        }
        button(content, "停止设备服务") {
            val owner = service
            owner?.shutdown()
            if (owner?.running == true) toast("设备操作仍在处理，服务保持运行") else release()
            render()
        }
        render()
    }
    override fun onStart() {
        super.onStart()
        visible = true
        if (!startRememberedScaleService()) bindExisting()
        handler.post(refresh)
    }
    override fun onStop() {
        visible = false; handler.removeCallbacks(refresh)
        service?.screenVisible(visibilityToken, false)
        release(); super.onStop()
    }
    private fun bindExisting() { if (!bound) bound = bindService(Intent(this, MobileService::class.java), connection, 0) }
    private fun startRememberedScaleService(): Boolean {
        val address = getSharedPreferences("devices", MODE_PRIVATE).getString("scale", null)
        if (address == null || !BluetoothAdapter.checkBluetoothAddress(address) || missingBle().isNotEmpty())
            return false
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter ?: return false
        return try {
            if (!adapter.isEnabled) return false
            startForegroundService(Intent(this, MobileService::class.java).setAction(MobileService.AUTO_SCALE))
            if (!bound) bound = bindService(Intent(this, MobileService::class.java), connection, Context.BIND_AUTO_CREATE)
            bound
        } catch (_: SecurityException) { false }
        catch (_: RuntimeException) { false }
    }
    private fun release() { if (bound) { unbindService(connection); bound = false }; service = null }
    private fun missingBle(): List<String> {
        val required = if (Build.VERSION.SDK_INT >= 31) listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            else listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        return required.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
    }
    private fun enableAndScan() {
        val missing = missingBle()
        if (missing.isNotEmpty()) { requestPermissions(missing.toTypedArray(), PERMISSIONS); return }
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null) { toast("此设备不支持蓝牙"); return }
        try {
            if (!adapter.isEnabled) { startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), ENABLE); return }
            if (service?.running == true) { service?.scan(); return }
            release()
            startForegroundService(Intent(this, MobileService::class.java))
            scanAfterBind = true
            bound = bindService(Intent(this, MobileService::class.java), connection, Context.BIND_AUTO_CREATE)
            if (!bound) { scanAfterBind = false; toast("无法绑定设备服务") }
        } catch (_: SecurityException) { toast("蓝牙权限已失效") }
        catch (error: RuntimeException) { toast("启动失败：${error.javaClass.simpleName}") }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS) {
            if (missingBle().isEmpty()) enableAndScan() else toast("连接设备需要蓝牙权限")
        }
    }
    @Deprecated("Platform activity results")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ENABLE && resultCode == RESULT_OK) enableAndScan()
        if (requestCode == EXPORT && resultCode == RESULT_OK) data?.data?.let { (application as MobileApplication).export(it) }
    }
    private fun choose(device: DiscoveredDevice) {
        if (device.candidateRole == DeviceRole.BOOKOO) { service?.connectScale(device.address); return }
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(InputFilter.LengthFilter(6)); hint = "六位设备密码"
            isSaveEnabled = false; importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }
        val dialog = AlertDialog.Builder(this).setTitle("连接咖啡机")
            .setMessage("密码只在本次认证中使用，不会保存。")
            .setView(input).setPositiveButton("连接", null).setNegativeButton("取消", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val password = input.text.toString()
                if (!password.matches(Regex("[0-9]{6}"))) input.error = "请输入六位数字"
                else { service?.connectCoffee(device.address, password); input.text.clear(); dialog.dismiss() }
            }
        }
        dialog.setOnDismissListener { input.text.clear() }
        dialog.show()
    }
    private fun chooseLeverMode() {
        val modes = listOf(
            MachineSettingChange.LeverMode(false, false),
            MachineSettingChange.LeverMode(true, false),
            MachineSettingChange.LeverMode(true, true),
        )
        AlertDialog.Builder(this).setTitle("选择拨杆模式")
            .setItems(modes.map { MachineSettingsPresentation.change(it).substringAfter('：') }.toTypedArray()) { _, index ->
                val change = modes[index]
                AlertDialog.Builder(this).setTitle("确认修改拨杆模式")
                    .setMessage("${MachineSettingsPresentation.change(change)}\n机器回读后才能确认生效。")
                    .setPositiveButton("发送") { _, _ -> service?.changeMachineSetting(change)?.let(::toast); render() }
                    .setNegativeButton("取消", null).show()
            }.show()
    }
    private fun confirmSleepNow() {
        AlertDialog.Builder(this).setTitle("让咖啡机立即睡眠")
            .setMessage("机器入睡后，App 没有唤醒命令。需要用机器拨杆唤醒。")
            .setPositiveButton("发送入睡命令") { _, _ -> service?.enterSleepNow()?.let(::toast); render() }
            .setNegativeButton("取消", null).show()
    }
    private fun render() {
        if (!::status.isInitialized) return
        val owner = service
        val s = owner?.snapshot ?: MobileSnapshot()
        val now = SystemClock.elapsedRealtime()
        val running = owner?.running == true
        val warning = ShotSafetyAlert.message(owner?.shotState ?: ExtractionState.IDLE, s.coffeeState)
        safetyWarning.visibility = if (warning == null) View.GONE else View.VISIBLE
        safetyWarning.show(warning.orEmpty())
        status.show(if (running) s.message else "点击扫描启动设备服务")
        scanButton.isEnabled = !s.scanning
        val shotActive = owner?.shotState?.let(ShotGate::active) == true
        emergencyStop.isEnabled = running && owner?.shotState?.let { it in setOf(ExtractionState.STARTING,
            ExtractionState.RUNNING, ExtractionState.OUTCOME_UNKNOWN) } == true &&
            (owner?.shotState != ExtractionState.OUTCOME_UNKNOWN || s.coffeeState == DeviceState.READY)
        val settingBusy = owner?.settingWriteState in setOf(
            SettingsWriteTracker.State.WRITING, SettingsWriteTracker.State.WAITING_READBACK)
        val preparationIdle = owner?.brewPreparationState == BrewPreparation.State.IDLE
        val freshAwakeIdle = s.coffee is IdleTelemetry && s.coffee.sleepStateRaw == 0 &&
            s.coffeeAt?.let { now >= it && now - it <= 1500 } == true
        leverButton.isEnabled = running && !shotActive && !settingBusy && preparationIdle &&
            s.coffeeState == DeviceState.READY && s.settings != null && freshAwakeIdle
        leverStatus.show(MachineSettingsPresentation.leverMode(s.settings) +
            when (owner?.pendingSetting) {
                is MachineSettingChange.LeverMode -> " · " + when (owner?.settingWriteState) {
                    SettingsWriteTracker.State.WRITING -> "正在写入"
                    SettingsWriteTracker.State.WAITING_READBACK -> "等待机器回读"
                    SettingsWriteTracker.State.CONFIRMED -> "已回读确认"
                    SettingsWriteTracker.State.FAILED -> "写入失败"
                    SettingsWriteTracker.State.UNKNOWN -> "结果未知，请查看机器"
                    SettingsWriteTracker.State.IDLE, null -> ""
                }
                else -> ""
            })
        coffeeDisconnect.isEnabled = running && !shotActive && s.coffeeState != DeviceState.DISCONNECTED
        scaleDisconnect.isEnabled = running && !shotActive && s.scaleState != DeviceState.DISCONNECTED
        tareButton.isEnabled = running && !shotActive && s.scaleState == DeviceState.READY &&
            owner?.tareState !in setOf(StandaloneTare.State.WRITING, StandaloneTare.State.WAITING_ZERO)
        tareStatus.show("去皮状态：" + when (owner?.tareState ?: StandaloneTare.State.IDLE) {
            StandaloneTare.State.IDLE -> "尚未操作"
            StandaloneTare.State.WRITING -> "正在发送"
            StandaloneTare.State.WAITING_ZERO -> "已写入，等待归零"
            StandaloneTare.State.CONFIRMED -> "已收到归零读数"
            StandaloneTare.State.FAILED -> "写入失败"
            StandaloneTare.State.UNKNOWN -> "结果未知，请查看秤"
        })
        val coffeeFresh = s.coffeeAt?.let { now >= it && now - it <= 1500 } == true && s.coffeeState == DeviceState.READY
        val idle = s.coffee as? IdleTelemetry
        val sleepBusy = owner?.sleepNowState in setOf(SleepNowTracker.State.WRITING, SleepNowTracker.State.WAITING_ASLEEP)
        sleepButton.isEnabled = running && !shotActive && !settingBusy && !sleepBusy && preparationIdle && coffeeFresh &&
            idle?.sleepStateRaw == 0
        val reportedSleep = if (!coffeeFresh) "暂无新鲜状态" else when (idle?.sleepStateRaw) {
            0 -> "已唤醒"
            1 -> "已入睡 · 用机器拨杆唤醒"
            else -> "未知"
        }
        val sleepProgress = when (owner?.sleepNowState) {
            SleepNowTracker.State.WRITING -> " · 正在写入"
            SleepNowTracker.State.WAITING_ASLEEP -> " · 等待机器回报"
            SleepNowTracker.State.CONFIRMED -> if (idle?.sleepStateRaw == 1) " · 已确认" else ""
            SleepNowTracker.State.FAILED -> " · 写入失败"
            SleepNowTracker.State.UNKNOWN -> " · 上次结果未知"
            else -> ""
        }
        sleepStatus.show("睡眠状态：$reportedSleep$sleepProgress")
        alarmStatus.show(MachineAlarms.describe(s.alarmBits, s.alarmAt, now))
        val machine = when (val frame = s.coffee) {
            is IdleTelemetry -> "冲泡 ${number(frame.brewTemperatureHundredthsC)} °C · ${frame.brewPressureTenthsBar / 10.0} bar\n蒸汽 ${number(frame.steamTemperatureHundredthsC)} °C · ${frame.steamPressureTenthsBar / 10.0} bar"
            is ExtractionTelemetry -> "萃取 ${frame.elapsedSeconds} s · ${frame.pressureTenthsBar / 10.0} bar\n冲泡 ${number(frame.brewTemperatureHundredthsC)} °C"
            else -> "温度与压力：—"
        }
        coffee.show("${label(s.coffeeState)} · ${if (coffeeFresh) "实时" else "暂无实时数据"}\n$machine\n${s.settings?.let { "固件 ${it.firmwareMajor}.${it.firmwareMinor}.${it.firmwarePatch}" } ?: "固件未知"}")
        val scaleFresh = s.weightAt?.let { now >= it && now - it <= 1500 } == true && s.scaleState == DeviceState.READY
        scale.show("${label(s.scaleState)} · ${if (scaleFresh) "实时" else "暂无实时数据"}\n${s.weight?.let { number(it.weightHundredthsGram) } ?: "—"} g")
        val library = (application as MobileApplication).curves
        val selected = getSharedPreferences("curves", MODE_PRIVATE).getString("selected", null)?.let(library::find)
        selection.show(selected?.let { "当前：${it.name} · ${if (!library.canStart(it)) "仅浏览，不可萃取" else "可萃取"}" } ?: "尚未选择曲线")
        val presetPrefs = getSharedPreferences("presets", MODE_PRIVATE)
        presetButtons.forEachIndexed { index, button ->
            val slot = index + 1
            val id = PresetSlots.curveId(slot, presetPrefs.getString(PresetSlots.key(slot), null))
            val item = library.find(id)
            val label = "槽位 $slot · ${item?.name ?: id}"
            if (button.text.toString() != label) button.text = label
        }
        val keys = s.candidates.map { "${it.address}:${it.advertisedName}" }
        if (keys != candidateKeys) {
            candidateKeys = keys; candidates.removeAllViews()
            s.candidates.forEach { device ->
                button(candidates, "${device.advertisedName} · ${if (device.candidateRole == DeviceRole.COFFEE) "咖啡机" else "电子秤"}") { choose(device) }
            }
        }
    }
    private fun label(state: DeviceState): String = when (state) {
        DeviceState.READY -> "已连接"; DeviceState.DISCONNECTED -> "未连接"; DeviceState.FAILED -> "连接失败"
        DeviceState.UNSUPPORTED -> "固件未验证 · 只读"; else -> "连接中"
    }
    private fun TextView.show(value: String) { if (text.toString() != value) text = value }
    private fun number(hundredths: Int) = String.format(Locale.ROOT, "%.2f", hundredths / 100.0)
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()
    private fun text(parent: LinearLayout, value: String, size: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value; textSize = size.toFloat(); setTextColor(getColor(R.color.mobile_text))
            setPadding(0, dp(6), 0, dp(6)); if (bold) setTypeface(null, Typeface.BOLD)
            parent.addView(this)
        }
    private fun card(parent: LinearLayout, title: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(16), dp(20), dp(16))
        background = GradientDrawable().apply { setColor(getColor(R.color.mobile_surface)); cornerRadius = dp(18).toFloat() }
        parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })
        text(this, title, 18, true)
    }
    private fun button(parent: LinearLayout, value: String, action: () -> Unit): Button = Button(this).apply {
        text = value; setOnClickListener { action() }
        parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
    }
    companion object { private const val PERMISSIONS = 12; private const val ENABLE = 13; private const val EXPORT = 14 }
}
