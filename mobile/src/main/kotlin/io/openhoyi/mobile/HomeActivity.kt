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
    private lateinit var acknowledgeManual: Button
    private lateinit var coffee: TextView
    private lateinit var coffeeDot: View
    private lateinit var brewTemperature: TextView
    private lateinit var brewPressure: TextView
    private lateinit var steamTemperature: TextView
    private lateinit var steamPressure: TextView
    private lateinit var leverStatus: TextView
    private lateinit var leverButton: Button
    private lateinit var sleepStatus: TextView
    private lateinit var sleepButton: Button
    private lateinit var emergencyStop: Button
    private lateinit var alarmStatus: TextView
    private lateinit var scale: TextView
    private lateinit var scaleDot: View
    private lateinit var scaleWeight: TextView
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
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.mobile_background))
        }
        val scroll = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(getColor(R.color.mobile_background)) }
        root.setOnApplyWindowInsetsListener { view, insets ->
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
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        emergencyStop = Button(this).apply {
            text = "立即停止萃取"
            isAllCaps = false
            textSize = 18f
            minHeight = dp(56)
            setTextColor(getColor(android.R.color.white))
            background = HoyiUi.shape(this@HomeActivity, R.color.mobile_stop_button, 12)
            visibility = View.GONE
            setOnClickListener { service?.stopShot(); render() }
        }
        root.addView(emergencyStop, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(dp(24), dp(4), dp(24), dp(12))
        })
        HoyiUi.navigation(this, root, HomeActivity::class.java)
        setContentView(root)
        HoyiUi.header(this, content, "HOYI", if (BuildConfig.MOCK_MODE) "Mock · 本机模拟，不发送蓝牙命令" else "咖啡工作台")
        val themeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        content.addView(themeRow)
        HoyiUi.label(this, themeRow, "深色模式", 14, muted = true)
        themeRow.addView(Switch(this).apply {
            contentDescription = "切换深色模式"
            isChecked = getSharedPreferences("appearance", MODE_PRIVATE).getBoolean("dark", false)
            setOnCheckedChangeListener { _, dark ->
                getSharedPreferences("appearance", MODE_PRIVATE).edit().putBoolean("dark", dark).apply()
                recreate()
            }
        })
        safetyWarning = text(content, "", 17, true).apply {
            setTextColor(getColor(R.color.mobile_danger)); visibility = View.GONE
        }
        acknowledgeManual = button(content, "已检查机器，清除提示") {
            AlertDialog.Builder(this).setTitle("确认已检查机器")
                .setMessage("这只会清除 App 提示，不会改变机器状态或历史中的“结果未知”。")
                .setPositiveButton("清除提示") { _, _ -> service?.acknowledgeManualSafety(); render() }
                .setNegativeButton("取消", null).show()
        }.apply { visibility = View.GONE }

        val deviceCard = card(content, "设备连接")
        status = text(deviceCard, "尚未连接", 14)
        val deviceRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        deviceCard.addView(deviceRow)
        val machineChip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        val scaleChip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        deviceRow.addView(machineChip, LinearLayout.LayoutParams(0, -2, 1f))
        deviceRow.addView(scaleChip, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) })
        coffeeDot = statusDot(machineChip)
        coffee = text(machineChip, "咖啡机 · 未连接", 15, true)
        scaleDot = statusDot(scaleChip)
        scale = text(scaleChip, "电子秤 · 未连接", 15, true)
        scanButton = button(deviceCard, "扫描并连接设备") { enableAndScan() }
        candidates = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        deviceCard.addView(candidates)

        val wide = HoyiUi.wide(this)
        val workArea = LinearLayout(this).apply { orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL }
        content.addView(workArea)
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val right = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (wide) {
            workArea.addView(left, LinearLayout.LayoutParams(0, -2, .9f))
            workArea.addView(right, LinearLayout.LayoutParams(0, -2, 1.1f).apply { marginStart = dp(16) })
        } else {
            workArea.addView(right, LinearLayout.LayoutParams(-1, -2))
            workArea.addView(left, LinearLayout.LayoutParams(-1, -2))
        }
        val machineCard = card(left, "咖啡机")
        machineCard.addView(ImageView(this).apply {
            setImageResource(R.drawable.hoyi_machine)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "HOYI 咖啡机外观"
        }, LinearLayout.LayoutParams(-1, dp(if (wide) 240 else 150)))
        alarmStatus = text(machineCard, "尚未收到机器告警状态", 14)
        leverStatus = text(machineCard, "拨杆模式：尚未收到设置", 14)
        sleepStatus = text(machineCard, "睡眠状态：未知", 14)
        val machineControls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        val controlsToggle = button(machineCard, "展开设备控制") {}
        controlsToggle.setOnClickListener {
            machineControls.visibility = if (machineControls.visibility == View.GONE) View.VISIBLE else View.GONE
            controlsToggle.text = if (machineControls.visibility == View.VISIBLE) "收起设备控制" else "展开设备控制"
        }
        machineCard.addView(machineControls)
        leverButton = button(machineControls, "拨杆模式") { chooseLeverMode() }
        sleepButton = button(machineControls, "立即睡眠") { confirmSleepNow() }
        button(machineControls, "机器设置") { startActivity(Intent(this, MachineSettingsActivity::class.java)) }
        coffeeDisconnect = button(machineControls, "断开咖啡机") { service?.disconnect(DeviceRole.COFFEE) }
        val scaleCard = card(left, "电子秤")
        scaleWeight = text(scaleCard, "— g", 27, true)
        tareStatus = text(scaleCard, "去皮状态：尚未操作", 14)
        tareButton = button(scaleCard, "电子秤去皮") { service?.tareScale()?.let(::toast); render() }
        scaleDisconnect = button(scaleCard, "断开电子秤") { service?.disconnect(DeviceRole.BOOKOO) }

        val metrics = card(right, "实时状态")
        fun metricRow(first: String, second: String): Pair<TextView, TextView> {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            metrics.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            fun item(label: String): TextView {
                val box = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12))
                    background = HoyiUi.shape(this@HomeActivity, R.color.mobile_accent_soft, 12)
                }
                row.addView(box, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })
                HoyiUi.label(this, box, label, 13, muted = true)
                return HoyiUi.label(this, box, "—", 25, true).apply { setPadding(0, dp(8), 0, 0) }
            }
            return item(first) to item(second)
        }
        metricRow("冲泡温度", "冲泡压力").also { brewTemperature = it.first; brewPressure = it.second }
        metricRow("蒸汽温度", "蒸汽压力").also { steamTemperature = it.first; steamPressure = it.second }
        val curveCard = card(right, "当前曲线")
        selection = text(curveCard, "尚未选择曲线", 19, true)
        button(curveCard, "开始萃取") { startActivity(Intent(this, ExtractionActivity::class.java)) }
        button(curveCard, "浏览曲线库") { startActivity(Intent(this, CurveActivity::class.java)) }
        val presets = card(right, "快捷曲线")
        HoyiUi.label(this, presets, "点选后进入萃取确认；可在曲线库更换。", 13, muted = true)
        presetButtons = (1..5).map { slot ->
            button(presets, "槽位 $slot") {
                startActivity(Intent(this, ExtractionActivity::class.java).putExtra(PresetSlots.EXTRA_SLOT, slot))
            }
        }
        if (!wide) {
            right.removeView(curveCard)
            right.addView(curveCard, 0)
        }
        val tools = card(content, "记录与维护")
        button(tools, "萃取历史") { startActivity(Intent(this, HistoryActivity::class.java)) }
        button(tools, "导出操作记录 ZIP") {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/zip").putExtra(Intent.EXTRA_TITLE, "openhoyi-alpha-${System.currentTimeMillis()}.zip"), EXPORT)
        }
        button(tools, "停止设备服务") {
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
        if (BuildConfig.MOCK_MODE) {
            startService(Intent(this, MobileService::class.java))
            if (!bound) bound = bindService(Intent(this, MobileService::class.java), connection, Context.BIND_AUTO_CREATE)
            return bound
        }
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
        if (BuildConfig.MOCK_MODE) {
            if (service?.running == true) service?.scan()
            else {
                startService(Intent(this, MobileService::class.java))
                scanAfterBind = true
                if (!bound) bound = bindService(Intent(this, MobileService::class.java), connection, Context.BIND_AUTO_CREATE)
            }
            return
        }
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
        if (BuildConfig.MOCK_MODE) {
            if (device.candidateRole == DeviceRole.BOOKOO) service?.connectScale(device.address)
            else service?.connectCoffee(device.address, "000000")
            render()
            return
        }
        if (device.candidateRole == DeviceRole.BOOKOO) { service?.connectScale(device.address); return }
        if (service?.connectRememberedCoffee(device.address) == true) return
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(InputFilter.LengthFilter(6)); hint = "六位设备密码"
            isSaveEnabled = false; importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }
        val dialog = AlertDialog.Builder(this).setTitle("连接咖啡机")
            .setMessage("首次连接需要输入设备密码。连接成功后会加密保存在本机，下次无需重复输入；连续连接失败时会重新询问。")
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
        if (BuildConfig.MOCK_MODE) { toast("Mock 版本不修改机器拨杆模式"); return }
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
        if (BuildConfig.MOCK_MODE) { toast("Mock 版本不发送入睡命令"); return }
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
            ?: owner?.manualSafetyMessage
        safetyWarning.visibility = if (warning == null) View.GONE else View.VISIBLE
        safetyWarning.show(warning.orEmpty())
        acknowledgeManual.visibility = if (owner?.manualSafetyMessage == null) View.GONE else View.VISIBLE
        status.show(if (owner?.manualShotActive == true)
            "机器手动萃取中 · 正在被动记录；请用机器拨杆停止" else if (running) s.message else "点击扫描启动设备服务")
        scanButton.isEnabled = !s.scanning
        val shotActive = owner?.shotState?.let(ShotGate::active) == true
        val stopAction = StopActionPresentation.describe(owner?.shotState ?: ExtractionState.IDLE,
            s.coffeeState, running)
        emergencyStop.visibility = if (stopAction.visible) View.VISIBLE else View.GONE
        emergencyStop.isEnabled = stopAction.enabled
        emergencyStop.text = if (owner?.scalePreflight == true) "取消启动" else stopAction.label
        val settingBusy = owner?.settingWriteState in setOf(
            SettingsWriteTracker.State.WRITING, SettingsWriteTracker.State.WAITING_READBACK)
        val preparationIdle = owner?.brewPreparationState == BrewPreparation.State.IDLE
        val freshAwakeIdle = s.coffee is IdleTelemetry && s.coffee.sleepStateRaw == 0 &&
            s.coffeeAt?.let { now >= it && now - it <= 1500 } == true
        leverButton.isEnabled = running && !shotActive && owner?.manualShotActive != true && !settingBusy && preparationIdle &&
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
        coffeeDisconnect.isEnabled = running && !shotActive && owner?.manualShotActive != true && s.coffeeState != DeviceState.DISCONNECTED
        scaleDisconnect.isEnabled = running && !shotActive && owner?.manualShotActive != true && s.scaleState != DeviceState.DISCONNECTED
        tareButton.isEnabled = running && !shotActive && owner?.manualShotActive != true && s.scaleState == DeviceState.READY &&
            owner?.tareState !in setOf(StandaloneTare.State.WRITING, StandaloneTare.State.WAITING_ZERO)
        tareStatus.show("去皮状态：" + when (owner?.tareState ?: StandaloneTare.State.IDLE) {
            StandaloneTare.State.IDLE -> "尚未操作"
            StandaloneTare.State.WRITING -> "正在发送"
            StandaloneTare.State.WAITING_ZERO -> "已写入，等待归零"
            StandaloneTare.State.CONFIRMED -> "已收到归零读数"
            StandaloneTare.State.FAILED -> "写入失败"
            StandaloneTare.State.UNKNOWN -> "结果未知，请查看秤"
        })
        val liveMachine = LiveTelemetry.machine(s.coffee, s.coffeeState, s.coffeeAt, now)
        val coffeeFresh = liveMachine != null
        val idle = liveMachine as? IdleTelemetry
        val sleepBusy = owner?.sleepNowState in setOf(SleepNowTracker.State.WRITING, SleepNowTracker.State.WAITING_ASLEEP)
        sleepButton.isEnabled = running && !shotActive && owner?.manualShotActive != true && !settingBusy && !sleepBusy && preparationIdle && coffeeFresh &&
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
        coffee.show("咖啡机 · ${label(s.coffeeState)}${if (coffeeFresh) " · 实时" else ""}")
        coffeeDot.background = dotShape(when (s.coffeeState) {
            DeviceState.READY -> R.color.mobile_success
            DeviceState.FAILED, DeviceState.UNSUPPORTED -> R.color.mobile_danger
            else -> R.color.mobile_muted
        })
        when (val frame = liveMachine) {
            is IdleTelemetry -> {
                brewTemperature.show("${number(frame.brewTemperatureHundredthsC)} °C")
                brewPressure.show("${frame.brewPressureTenthsBar / 10.0} bar")
                steamTemperature.show("${number(frame.steamTemperatureHundredthsC)} °C")
                steamPressure.show("${frame.steamPressureTenthsBar / 10.0} bar")
            }
            is ExtractionTelemetry -> {
                brewTemperature.show("${number(frame.brewTemperatureHundredthsC)} °C")
                brewPressure.show("${frame.pressureTenthsBar / 10.0} bar")
                steamTemperature.show("—")
                steamPressure.show("—")
            }
            else -> listOf(brewTemperature, brewPressure, steamTemperature, steamPressure).forEach { it.show("—") }
        }
        val liveScale = LiveTelemetry.scale(s.weight, s.scaleState, s.weightAt, now)
        scale.show("电子秤 · ${label(s.scaleState)}${if (liveScale != null) " · 实时" else ""}")
        scaleDot.background = dotShape(when (s.scaleState) {
            DeviceState.READY -> R.color.mobile_success
            DeviceState.FAILED, DeviceState.UNSUPPORTED -> R.color.mobile_danger
            else -> R.color.mobile_muted
        })
        scaleWeight.show("${liveScale?.let { number(it.weightHundredthsGram) } ?: "—"} g" +
            "  ·  ${liveScale?.let { number(it.deviceFlowHundredths) } ?: "—"} g/s")
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
    private fun dotShape(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(getColor(color))
    }
    private fun statusDot(parent: LinearLayout): View = View(this).apply {
        background = dotShape(R.color.mobile_muted)
        parent.addView(this, LinearLayout.LayoutParams(dp(9), dp(9)).apply { marginEnd = dp(8) })
    }
    private fun text(parent: LinearLayout, value: String, size: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value; textSize = size.toFloat(); setTextColor(getColor(R.color.mobile_text))
            setPadding(0, dp(6), 0, dp(6)); if (bold) setTypeface(null, Typeface.BOLD)
            parent.addView(this)
        }
    private fun card(parent: LinearLayout, title: String) = HoyiUi.card(this, parent, title)
    private fun button(parent: LinearLayout, value: String, action: () -> Unit) =
        HoyiUi.button(this, parent, value, primary = value == "开始萃取", action = action)
    companion object { private const val PERMISSIONS = 12; private const val ENABLE = 13; private const val EXPORT = 14 }
}
