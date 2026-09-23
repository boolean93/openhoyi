package io.openhoyi.lab

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.*
import android.provider.Settings as AndroidSettings
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.view.WindowInsets
import android.widget.*
import io.openhoyi.bluetooth.DiscoveredDevice
import io.openhoyi.protocol.*
import io.openhoyi.session.*

/** Native diagnostic surface. No control commands, protocol bytes or Activity-owned links. */
class LabActivity : Activity() {
    private var service: LabService? = null
    private var bound = false
    private var started = false
    private var scanAfterBind = false
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private lateinit var machine: TextView
    private lateinit var scale: TextView
    private lateinit var logs: TextView
    private lateinit var devices: LinearLayout
    private lateinit var scanButton: Button
    private lateinit var exportButton: Button
    private lateinit var stopButton: Button
    private lateinit var disconnectCoffee: Button
    private lateinit var disconnectScale: Button
    private var deviceKeys: List<String> = emptyList()
    private var passwordDialog: AlertDialog? = null
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = (binder as LabService.LocalBinder).service
            service?.screenVisible(started)
            if (scanAfterBind) { scanAfterBind = false; service?.scan() }
            render()
        }
        override fun onServiceDisconnected(name: ComponentName) { service = null; render() }
        override fun onBindingDied(name: ComponentName) { releaseBinding(); render() }
    }
    private val refresh = object : Runnable {
        override fun run() { render(); if (started) handler.postDelayed(this, 250) }
    }
    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        val scroll = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(Color.rgb(241,245,250)) }
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24),dp(20),dp(24),dp(24)) }
        scroll.addView(content)
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
        setContentView(scroll)
        title("OpenHOYI Lab", 28)
        text(content, "原生蓝牙诊断 · 连接、观察、记录", 16)
        text(content, "本版不提供萃取或设置控制。咖啡机连接会认证并同步时间；秤连接会执行协议初始化。", 14)
        val connectionCard = card("设备连接")
        status = text(connectionCard, "服务未启动", 16).apply { tag = "connection_status" }
        scanButton = button(connectionCard, "扫描设备", "scan") { enableAndScan() }
        devices = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        connectionCard.addView(devices)
        val coffeeCard = card("咖啡机")
        machine = text(coffeeCard, "尚无数据", 20).apply { tag = "coffee_data" }
        disconnectCoffee = button(coffeeCard, "断开咖啡机", "disconnect_coffee") { service?.disconnect(DeviceRole.COFFEE) }
        val scaleCard = card("电子秤")
        scale = text(scaleCard, "尚无数据", 20).apply { tag = "scale_data" }
        disconnectScale = button(scaleCard, "断开电子秤", "disconnect_scale") { service?.disconnect(DeviceRole.BOOKOO) }
        val traceCard = card("诊断日志")
        text(traceCard, "本地保存，密码脱敏。导出包含传输阶段、设备通知、操作记录及丢失统计。", 14)
        logs = text(traceCard, "暂无事件", 14).apply { tag = "trace_status"; setTextIsSelectable(true) }
        exportButton = button(traceCard, "导出日志 ZIP", "export") {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/zip").putExtra(Intent.EXTRA_TITLE, "openhoyi-${System.currentTimeMillis()}.zip"), EXPORT)
        }
        stopButton = button(content, "停止诊断服务", "stop_service") {
            service?.shutdown(); releaseBinding(); render()
        }
        render()
    }
    override fun onStart() { super.onStart(); started = true; bindExisting(); handler.post(refresh) }
    override fun onStop() {
        started = false; handler.removeCallbacks(refresh)
        if (!isChangingConfigurations) service?.screenVisible(false)
        releaseBinding()
        passwordDialog?.dismiss(); passwordDialog = null
        super.onStop()
    }
    private fun bindExisting() {
        if (!bound) bound = bindService(Intent(this, LabService::class.java), connection, 0)
    }
    private fun releaseBinding() { if (bound) { unbindService(connection); bound = false }; service = null }
    private fun missingBle(): List<String> {
        val permissions = if (Build.VERSION.SDK_INT >= 31) listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            else listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        return permissions.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
    }
    private fun enableAndScan() {
        val missing = missingBle()
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), BLE_PERMISSION); return
        }
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null) { toast("此设备不支持蓝牙"); return }
        try {
            if (!adapter.isEnabled) { startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), ENABLE); return }
            if (service?.running == true) { service?.scan(); return }
            releaseBinding()
            startForegroundService(Intent(this, LabService::class.java))
            scanAfterBind = true
            // AUTO_CREATE is safe here: the explicit foreground start precedes this bind.
            bound = bindService(Intent(this, LabService::class.java), connection, Context.BIND_AUTO_CREATE)
            if (!bound) { scanAfterBind = false; toast("无法绑定诊断服务") }
        } catch (_: SecurityException) { toast("蓝牙权限已被撤销，请重新授权") }
        catch (e: RuntimeException) { toast("无法启动：${e.javaClass.simpleName}") }
    }
    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        if (code == BLE_PERMISSION) {
            if (missingBle().isEmpty()) {
                enableAndScan()
                if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION)
            } else AlertDialog.Builder(this).setMessage("蓝牙扫描和连接需要设备权限。可在应用设置中开启。")
                .setPositiveButton("应用设置") { _, _ -> startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
                .setNegativeButton("取消", null).show()
        }
    }
    @Deprecated("Platform activity results")
    override fun onActivityResult(request: Int, result: Int, data: Intent?) {
        super.onActivityResult(request, result, data)
        if (request == ENABLE && result == RESULT_OK) enableAndScan()
        if (request == EXPORT && result == RESULT_OK) data?.data?.let {
            (application as LabApplication).export(it)
        }
    }
    private fun choose(device: DiscoveredDevice) {
        if (device.candidateRole == DeviceRole.BOOKOO) { service?.connect(device.candidateRole, device.address); return }
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(InputFilter.LengthFilter(6)); hint = "六位设备密码"
            isSaveEnabled = false; importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            setPadding(dp(24),dp(16),dp(24),dp(16))
        }
        val dialog = AlertDialog.Builder(this).setTitle("连接咖啡机")
            .setMessage("密码只用于本次认证，不保存、不写入日志。")
            .setView(input).setPositiveButton("连接", null).setNegativeButton("取消", null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val password = input.text.toString()
            if (!password.matches(Regex("[0-9]{6}"))) input.error = "请输入六位数字"
            else { service?.connect(DeviceRole.COFFEE, device.address, password); input.text.clear(); dialog.dismiss() }
        } }
        dialog.setOnDismissListener { input.text.clear(); passwordDialog = null }
        passwordDialog = dialog; dialog.show()
    }
    private fun render() {
        if (!::status.isInitialized) return
        val owner = service
        val s = owner?.snapshot ?: LabSnapshot()
        val now = SystemClock.elapsedRealtime()
        val active = owner?.running == true
        status.showText(when { missingBle().isNotEmpty() -> "需要蓝牙权限"; s.scanning -> "正在扫描…"; active -> "诊断服务运行中 · 已发现 ${s.devices.size} 个设备"; else -> "服务未启动，点击扫描开始" })
        scanButton.isEnabled = !s.scanning
        exportButton.isEnabled = active; stopButton.isEnabled = active
        disconnectCoffee.isEnabled = active && s.coffeeState != DeviceState.DISCONNECTED
        disconnectScale.isEnabled = active && s.scaleState != DeviceState.DISCONNECTED
        val machineConnected = s.coffeeState == DeviceState.READY || s.coffeeState == DeviceState.UNSUPPORTED
        val coffeeText = when (val frame = s.coffee) {
            is IdleTelemetry -> "冲泡 ${hundredths(frame.brewTemperatureHundredthsC)} °C  ·  ${frame.brewPressureTenthsBar / 10.0} bar\n蒸汽 ${hundredths(frame.steamTemperatureHundredthsC)} °C  ·  ${frame.steamPressureTenthsBar / 10.0} bar\n报警位 0x${frame.alarmBits.toString(16)}"
            is ExtractionTelemetry -> "萃取 ${frame.elapsedSeconds} s  ·  ${frame.pressureTenthsBar / 10.0} bar\n冲泡 ${hundredths(frame.brewTemperatureHundredthsC)} °C\n流量 ${frame.flowTenthsMlPerSecond / 10.0} ml/s  ·  水量 ${frame.totalWaterTenthsMl / 10.0} ml"
            else -> "— °C  ·  — bar"
        }
        val firmware = s.settings?.let { "固件 ${it.firmwareMajor}.${it.firmwareMinor}.${it.firmwarePatch}" } ?: "固件未知"
        machine.showText("${s.coffeeState.label()} · ${freshness(s.coffeeAt, now, machineConnected)}\n$coffeeText\n$firmware")
        scale.showText("${s.scaleState.label()} · ${freshness(s.weightAt, now, s.scaleState == DeviceState.READY)}\n${s.weight?.let { hundredths(it.weightHundredthsGram) } ?: "—"} g")
        logs.showText((owner?.logStatus ?: "尚未记录") + "\n" + s.events.takeLast(8).joinToString("\n"))
        val keys = s.devices.map { "${it.address}:${it.advertisedName}" }
        if (keys != deviceKeys) {
            deviceKeys = keys; devices.removeAllViews()
            s.devices.forEach { device -> button(devices, "${device.advertisedName}  ·  ${device.address}", "device_${device.address}") { choose(device) } }
        }
    }
    /** Avoid repeated layout/accessibility events for unchanged 250ms polling results. */
    private fun TextView.showText(value: String) { if (text.toString() != value) text = value }
    private fun title(value: String, size: Int) = text(content, value, size).apply { setTypeface(null, Typeface.BOLD) }
    private fun card(title: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(20),dp(16),dp(20),dp(16))
        background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(18).toFloat() }
        content.addView(this, LinearLayout.LayoutParams(-1,-2).apply { topMargin = dp(18) })
        text(this, title, 18).setTypeface(null, Typeface.BOLD)
    }
    private fun text(parent: LinearLayout, value: String, size: Int) = TextView(this).apply {
        text = value; textSize = size.toFloat(); setTextColor(Color.rgb(30,44,63)); setPadding(0,dp(6),0,dp(6))
        parent.addView(this, LinearLayout.LayoutParams(-1,-2))
    }
    private fun button(parent: LinearLayout, value: String, id: String, action: () -> Unit) = Button(this).apply {
        text = value; tag = id; isAllCaps = false; minHeight = dp(48)
        setOnClickListener { action() }; parent.addView(this, LinearLayout.LayoutParams(-1,-2))
    }
    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()
    private fun toast(value: String) { Toast.makeText(this,value,Toast.LENGTH_LONG).show() }
    companion object { private const val BLE_PERMISSION=10; private const val NOTIFICATION_PERMISSION=11; private const val ENABLE=12; private const val EXPORT=13 }
}
