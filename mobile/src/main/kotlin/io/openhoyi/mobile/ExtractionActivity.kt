package io.openhoyi.mobile

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.util.TypedValue
import android.view.WindowInsets
import android.view.View
import android.widget.*
import io.openhoyi.protocol.ExtractionTelemetry
import io.openhoyi.protocol.IdleTelemetry
import io.openhoyi.session.ExtractionState
import io.openhoyi.session.DeviceState
import io.openhoyi.session.StopReason
import java.util.Locale

/** A screen never owns BLE. Start requires an explicit confirmation; Stop is one tap. */
class ExtractionActivity : ThemedActivity() {
    private val presetSlot: Int by lazy { intent.getIntExtra(PresetSlots.EXTRA_SLOT, 7).takeIf { it in 1..5 } ?: 7 }
    private var service: MobileService? = null
    private var bound = false
    private var visible = false
    private val visibilityToken = java.util.UUID.randomUUID().toString()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var readiness: TextView
    private lateinit var live: TextView
    private lateinit var elapsedValue: TextView
    private lateinit var pressureValue: TextView
    private lateinit var weightValue: TextView
    private lateinit var flowValue: TextView
    private lateinit var weightTarget: TextView
    private lateinit var preparationStatus: TextView
    private lateinit var notificationStatus: TextView
    private lateinit var alarmStatus: TextView
    private lateinit var chart: ShotChartView
    private lateinit var start: Button
    private lateinit var stop: Button
    private lateinit var prepare: Button
    private lateinit var cancelPrepare: Button
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = (binder as MobileService.LocalBinder).service
            service?.screenVisible(visibilityToken, visible); render()
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
        val scroll = ScrollView(this).apply { setBackgroundColor(getColor(R.color.mobile_background)) }
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
        stop = Button(this).apply {
            text = "立即停止"
            isAllCaps = false
            textSize = 18f
            minHeight = dp(56)
            setTextColor(getColor(android.R.color.white))
            background = HoyiUi.shape(this@ExtractionActivity, R.color.mobile_stop_button, 12)
            visibility = View.GONE
            setOnClickListener { service?.stopShot(); render() }
        }
        start = HoyiUi.button(this, root, "开始萃取", primary = true) { confirmStart() }
        (start.layoutParams as LinearLayout.LayoutParams).setMargins(dp(24), dp(4), dp(24), dp(8))
        root.addView(stop, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(dp(24), dp(4), dp(24), dp(12))
        })
        HoyiUi.navigation(this, root, ExtractionActivity::class.java)
        setContentView(root)
        HoyiUi.header(this, content, "实时萃取",
            if (BuildConfig.MOCK_MODE) "模拟模式 · 不发送蓝牙命令" else "确认设备与曲线后开始；请守在机器旁", back = true)
        val wide = HoyiUi.wide(this)
        val columns = if (wide) LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            content.addView(this, LinearLayout.LayoutParams(-1, -2))
        } else null
        val left = if (wide) LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            columns!!.addView(this, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(10) })
        } else content
        val right = if (wide) LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            columns!!.addView(this, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(10) })
        } else content
        val stateCard = card(left)
        HoyiUi.label(this, stateCard, "准备状态", 19, true)
        readiness = text(stateCard, "等待设备服务", 16)
        preparationStatus = text(stateCard, "温度准备：尚未连接", 14)
        notificationStatus = text(stateCard, "", 14)
        alarmStatus = text(stateCard, "尚未收到机器告警状态", 14)
        val metrics = card(left)
        HoyiUi.label(this, metrics, "实时数据", 19, true)
        fun metricRow(a: String, b: String): Pair<TextView, TextView> {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            metrics.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
            fun cell(title: String): TextView {
                val box = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(16), dp(12), dp(16), dp(12))
                    background = HoyiUi.shape(this@ExtractionActivity, R.color.mobile_accent_soft, 12)
                }
                row.addView(box, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })
                HoyiUi.label(this, box, title, 13, muted = true)
                return HoyiUi.label(this, box, "—", 27, true).apply {
                    setPadding(0, dp(8), 0, 0)
                    setSingleLine(true)
                    setAutoSizeTextTypeUniformWithConfiguration(16, 27, 1, TypedValue.COMPLEX_UNIT_SP)
                }
            }
            return cell(a) to cell(b)
        }
        metricRow("萃取时间", "实时压力").also { elapsedValue = it.first; pressureValue = it.second }
        metricRow("电子秤重量", "秤流速").also { weightValue = it.first; flowValue = it.second }
        live = text(metrics, "", 13)
        weightTarget = text(metrics, "", 15, true)
        val chartCard = card(right)
        HoyiUi.label(this, chartCard, "萃取曲线", 19, true)
        chart = ShotChartView(this)
        chartCard.addView(chart, LinearLayout.LayoutParams(-1, dp(if (HoyiUi.wide(this)) 260 else 220)).apply {
            topMargin = dp(12)
        })
        val actions = card(right)
        HoyiUi.label(this, actions, "温度准备", 19, true)
        prepare = button(actions, "预热到曲线温度") { confirmPrepare() }
        cancelPrepare = button(actions, "取消预热") { service?.cancelBrewPreparation()?.let(::toast); render() }
        render()
    }
    override fun onStart() {
        super.onStart(); visible = true
        if (!bound) bound = bindService(Intent(this, MobileService::class.java), connection, 0)
        handler.post(refresh)
        if (!BuildConfig.MOCK_MODE) requestSafetyNotificationsOnce()
    }
    override fun onStop() {
        visible = false; handler.removeCallbacks(refresh)
        service?.screenVisible(visibilityToken, false)
        release(); super.onStop()
    }
    private fun release() { if (bound) { unbindService(connection); bound = false }; service = null }
    private fun notificationsAllowed(): Boolean = BuildConfig.MOCK_MODE || Build.VERSION.SDK_INT < 33 ||
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    private fun requestSafetyNotificationsOnce() {
        if (notificationsAllowed()) return
        val prefs = getSharedPreferences("safety", MODE_PRIVATE)
        if (prefs.getBoolean("asked_for_notifications", false)) return
        prefs.edit().putBoolean("asked_for_notifications", true).apply()
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATIONS)
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIFICATIONS) render()
    }
    private fun selected(): CurveLibraryItem? {
        val library = (application as MobileApplication).curves
        val id = if (presetSlot == 7) getSharedPreferences("curves", MODE_PRIVATE).getString("selected", null)
        else PresetSlots.curveId(presetSlot, getSharedPreferences("presets", MODE_PRIVATE)
            .getString(PresetSlots.key(presetSlot), null))
        return id?.let(library::find)
    }
    private fun confirmStart() {
        val owner = service ?: return
        val library = (application as MobileApplication).curves
        val profile = selected()?.let { library.resolve(it.id, owner.snapshot.scaleState == DeviceState.READY, presetSlot) }
        val blocked = ShotGate.startBlock(profile, owner.snapshot.coffeeState, owner.snapshot.coffee, owner.snapshot.coffeeAt, owner.snapshot.scaleState,
            owner.snapshot.weightAt, SystemClock.elapsedRealtime(), owner.shotState,
            validated = profile?.let(library::validated) == true)
        if (blocked != null) { toast(blocked); render(); return }
        requireNotNull(profile)
        owner.studioStartBlock(profile)?.let { toast(it); render(); return }
        val effect = if (BuildConfig.MOCK_MODE) "仅在本机模拟萃取；不会连接设备或发送蓝牙命令。"
            else "将向咖啡机发送已校验的启动命令。"
        AlertDialog.Builder(this).setTitle(if (BuildConfig.MOCK_MODE) "确认模拟萃取" else "确认开始萃取")
            .setMessage("${profile.name} · ${if (profile.parameters.slot == 7) "当前曲线" else "快捷槽位 ${profile.parameters.slot}"} · ${profile.temperatureC} °C\n最大水量：${profile.maximumWaterMl} ml\n目标重量：${if (profile.targetHundredthsGram > 0) "${number(profile.targetHundredthsGram)} g（电子秤）" else "不使用（由咖啡机按水量结束）"}\n$effect" +
                if (notificationsAllowed()) "" else "\n系统通知未授权，后台断链提醒可能无法显示。")
            .setPositiveButton(if (BuildConfig.MOCK_MODE) "开始模拟" else "确认启动") { _, _ ->
                owner.startShot(profile.id, profile.scaleMode, presetSlot)?.let(::toast)
                render()
            }
            .setNegativeButton("取消", null).show()
    }
    private fun confirmPrepare() {
        val owner = service ?: return
        val profile = selected()?.let {
            (application as MobileApplication).curves.resolve(it.id,
                owner.snapshot.scaleState == DeviceState.READY, presetSlot)
        } ?: run { toast("请先选择可萃取曲线"); return }
        val corrected = owner.currentCorrectedBrewTemperature()
        val current = corrected?.let { number(it) + " °C" } ?: "未知"
        AlertDialog.Builder(this).setTitle(if (BuildConfig.MOCK_MODE) "模拟预热到目标温度" else "预热到曲线目标温度")
            .setMessage("当前冲泡温度：$current\n曲线目标：${profile.temperatureC} °C\n" +
                if (BuildConfig.MOCK_MODE) "温度会在约 5 秒内模拟变化；不会发送蓝牙命令。"
                else "机器写入预热命令后，仍需看到新的温度数据才可启动。")
            .setPositiveButton(if (BuildConfig.MOCK_MODE) "开始模拟预热" else "发送预热命令") { _, _ ->
                owner.prepareBrew(profile.id, profile.scaleMode, presetSlot)?.let(::toast)
                render()
            }
            .setNegativeButton("取消", null).show()
    }
    private fun render() {
        if (!::readiness.isInitialized) return
        val owner = service
        val snapshot = owner?.snapshot ?: MobileSnapshot()
        val state = owner?.shotState ?: ExtractionState.IDLE
        val item = selected()
        val library = (application as MobileApplication).curves
        val profile = item?.let { library.resolve(it.id, snapshot.scaleState == DeviceState.READY, presetSlot) }
        val blocked = if (owner?.manualShotActive == true) "机器手动萃取中，请使用机器拨杆停止；App 只记录数据"
            else if (item != null && profile == null) "这条曲线未通过报文校验，暂不可萃取"
            else ShotGate.startBlock(profile, snapshot.coffeeState, snapshot.coffee, snapshot.coffeeAt, snapshot.scaleState,
                snapshot.weightAt, SystemClock.elapsedRealtime(), state,
                validated = profile?.let(library::validated) == true)
        val unknownAdvice = if (state == ExtractionState.OUTCOME_UNKNOWN && snapshot.coffeeState != io.openhoyi.session.DeviceState.READY)
            "\n连接中断且结果未知；先检查咖啡机，再重连后尝试停止。" else
            owner?.manualSafetyMessage?.let { "\n$it" }.orEmpty()
        val studio = snapshot.settings?.flags?.and(0x04) == 0x04
        val corrected = owner?.currentCorrectedBrewTemperature()
        val temperatureReady = profile != null && corrected != null &&
            BrewPreparation.isAtTarget(corrected, profile.temperatureC)
        val preparation = owner?.brewPreparationState ?: BrewPreparation.State.IDLE
        val studioBlocked = if (owner != null && profile != null) owner.studioStartBlock(profile) else null
        val settingBusy = owner?.settingWriteState in setOf(
            SettingsWriteTracker.State.WRITING, SettingsWriteTracker.State.WAITING_READBACK)
        val sleepBusy = owner?.sleepNowState in setOf(SleepNowTracker.State.WRITING, SleepNowTracker.State.WAITING_ASLEEP)
        val slotLabel = if (presetSlot == 7) "" else " · 快捷槽位 $presetSlot"
        readiness.show("曲线：${item?.name ?: "未选择"}$slotLabel\n咖啡机：${DeviceStatusText.label(snapshot.coffeeState)} · 电子秤：${DeviceStatusText.label(snapshot.scaleState)}\n萃取状态：${if (owner?.scalePreflight == true) "等待电子秤归零后启动" else if (owner?.manualShotActive == true) "机器手动萃取" else shotLabel(state)}${owner?.stopReason?.let { " · 停止原因：${stopLabel(it)}" } ?: ""}\n${blocked ?: studioBlocked ?: "设备与曲线已就绪"}$unknownAdvice")
        preparationStatus.show(if (snapshot.settings == null) "运行模式尚未回读" else if (!studio) "咖啡馆模式 · 按曲线正常启动" else
            "工作室模式 · 当前 ${corrected?.let(::number) ?: "—"} °C / 目标 ${profile?.temperatureC ?: "—"} °C\n" +
                when (preparation) {
                    BrewPreparation.State.IDLE -> if (temperatureReady) "温度已就绪" else "温度未就绪，可先预热"
                    BrewPreparation.State.WRITING -> "正在写入预热命令"
                    BrewPreparation.State.WAITING_TEMP -> "正在等待新鲜温度数据"
                    BrewPreparation.State.READY -> if (temperatureReady) "目标温度已达到，仍需确认启动" else "温度已偏离目标"
                    BrewPreparation.State.CANCELLING -> "正在取消预热"
                    BrewPreparation.State.FAILED -> "预热命令未写入，请取消后重试"
                    BrewPreparation.State.UNKNOWN -> "预热结果未知，请查看机器并取消"
                })
        notificationStatus.show(if (BuildConfig.MOCK_MODE || notificationsAllowed()) "" else
            "系统通知未授权；后台断链提醒可能被隐藏。可在系统设置中允许通知。")
        alarmStatus.show(MachineAlarms.describe(snapshot.alarmBits, snapshot.alarmAt,
            SystemClock.elapsedRealtime()))
        val now = SystemClock.elapsedRealtime()
        val frame = LiveTelemetry.machine(snapshot.coffee, snapshot.coffeeState, snapshot.coffeeAt, now)
        elapsedValue.show(if (frame is ExtractionTelemetry) "${frame.elapsedSeconds} s" else "—")
        pressureValue.show(when (frame) {
            is ExtractionTelemetry -> "${frame.pressureTenthsBar / 10.0} bar"
            is IdleTelemetry -> "${frame.brewPressureTenthsBar / 10.0} bar"
            else -> "—"
        })
        val scale = LiveTelemetry.scale(snapshot.weight, snapshot.scaleState, snapshot.weightAt, now)
        weightValue.show("${scale?.let { number(it.weightHundredthsGram) } ?: "—"} g")
        flowValue.show("${scale?.let { number(it.deviceFlowHundredths) } ?: "—"} g/s")
        live.show("冲泡温度  ${when (frame) {
            is ExtractionTelemetry -> number(frame.brewTemperatureHundredthsC)
            is IdleTelemetry -> number(frame.brewTemperatureHundredthsC)
            else -> "—"
        }} °C")
        val target = if (state != ExtractionState.IDLE) owner?.activeShotTargetHundredthsGram
            else profile?.targetHundredthsGram
        weightTarget.show(if (owner?.manualShotActive != true && target != null && target > 0) {
            val progress = "当前 ${scale?.let { number(it.weightHundredthsGram) } ?: "—"} g / 目标 ${number(target)} g"
            val advice = when {
                owner?.scalePreflight == true -> "秤归零后才会启动咖啡机"
                owner?.stopReason == StopReason.TARGET_WEIGHT.name -> "已按目标重量发出停止命令；请确认机器停水"
                owner?.stopReason == StopReason.MANUAL.name -> "已手动请求停止；请确认机器停水"
                owner?.stopReason == StopReason.SCALE_UNAVAILABLE.name -> "秤数据不可用，已发安全停止；请确认机器停水"
                state == ExtractionState.RUNNING -> "萃取满 7 秒且达到目标重量后自动停止；请守在机器旁"
                state == ExtractionState.STOP_REQUESTED -> "停止命令处理中；请确认机器停水"
                else -> "使用电子秤控制停止"
            }
            "$progress\n$advice"
        } else if (target == 0 && profile != null) "这条曲线不按秤重停机，由咖啡机按曲线结束" else "")
        val points = owner?.chartPoints ?: emptyList()
        if (chart.points != points) chart.points = points
        prepare.isEnabled = owner?.running == true && blocked == null && studio && !temperatureReady &&
            !settingBusy && !sleepBusy &&
            preparation == BrewPreparation.State.IDLE
        cancelPrepare.isEnabled = owner?.running == true && owner?.manualShotActive != true && preparation != BrewPreparation.State.IDLE &&
            snapshot.coffeeState == DeviceState.READY && !ShotGate.active(state)
        start.isEnabled = owner?.running == true && blocked == null && studioBlocked == null &&
            !settingBusy && !sleepBusy
        val stopAction = StopActionPresentation.describe(state, snapshot.coffeeState, owner?.running == true)
        stop.isEnabled = stopAction.enabled
        stop.visibility = if (stopAction.visible) View.VISIBLE else View.GONE
        start.visibility = if (stopAction.visible) View.GONE else View.VISIBLE
        stop.text = if (owner?.scalePreflight == true) "取消启动" else stopAction.label
    }
    private fun TextView.show(value: String) { if (text.toString() != value) text = value }
    private fun shotLabel(state: ExtractionState) = when (state) {
        ExtractionState.IDLE -> "待机"
        ExtractionState.STARTING -> "启动中"
        ExtractionState.RUNNING -> "萃取中"
        ExtractionState.STOP_REQUESTED -> "正在停止"
        ExtractionState.ENDED_OBSERVED -> "已结束"
        ExtractionState.OUTCOME_UNKNOWN -> "结果未知"
    }
    private fun stopLabel(reason: String) = when (reason) {
        StopReason.TARGET_WEIGHT.name -> "达到目标重量"
        StopReason.SCALE_UNAVAILABLE.name -> "电子秤数据不可用"
        StopReason.TARE_UNCONFIRMED.name -> "电子秤归零未确认"
        StopReason.MANUAL.name -> "手动停止"
        else -> "结果待确认"
    }
    private fun number(value: Int): String = String.format(Locale.ROOT, "%.2f", value / 100.0)
    private fun toast(value: String) = Toast.makeText(this, value, Toast.LENGTH_SHORT).show()
    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()
    private companion object { const val NOTIFICATIONS = 31 }
    private fun text(parent: LinearLayout, value: String, size: Int, bold: Boolean = false): TextView = TextView(this).apply {
        text = value; textSize = size.toFloat(); setTextColor(getColor(R.color.mobile_text))
        setPadding(0, dp(6), 0, dp(6)); if (bold) setTypeface(null, Typeface.BOLD); parent.addView(this)
    }
    private fun card(parent: LinearLayout) = HoyiUi.card(this, parent)
    private fun button(parent: LinearLayout, value: String, action: () -> Unit) =
        HoyiUi.button(this, parent, value, action = action)
}
