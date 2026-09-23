package io.openhoyi.mobile

import android.app.Activity
import android.app.AlertDialog
import android.content.*
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.view.WindowInsets
import android.widget.*
import io.openhoyi.protocol.ExtractionTelemetry
import io.openhoyi.protocol.IdleTelemetry
import io.openhoyi.session.ExtractionState
import io.openhoyi.session.DeviceState
import java.util.Locale

/** A screen never owns BLE. Start requires an explicit confirmation; Stop is one tap. */
class ExtractionActivity : Activity() {
    private var service: MobileService? = null
    private var bound = false
    private var visible = false
    private val visibilityToken = java.util.UUID.randomUUID().toString()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var readiness: TextView
    private lateinit var live: TextView
    private lateinit var chart: ShotChartView
    private lateinit var start: Button
    private lateinit var stop: Button
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
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(244, 241, 235)) }
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
        text(content, "实时萃取", 28, true)
        text(content, "连接、曲线和重量由服务层再次校验。页面退出不会断开正在运行的连接。", 14)
        val card = card(content)
        readiness = text(card, "等待设备服务", 18)
        live = text(card, "暂无实时数据", 22)
        chart = ShotChartView(this)
        card.addView(chart, LinearLayout.LayoutParams(-1, dp(260)).apply { topMargin = dp(12) })
        start = button(card, "开始萃取") { confirmStart() }
        stop = button(card, "立即停止") { service?.stopShot(); render() }
        button(content, "返回首页") { finish() }
        render()
    }
    override fun onStart() {
        super.onStart(); visible = true
        if (!bound) bound = bindService(Intent(this, MobileService::class.java), connection, 0)
        handler.post(refresh)
    }
    override fun onStop() {
        visible = false; handler.removeCallbacks(refresh)
        service?.screenVisible(visibilityToken, false)
        release(); super.onStop()
    }
    private fun release() { if (bound) { unbindService(connection); bound = false }; service = null }
    private fun selected(): CurveLibraryItem? {
        val library = (application as MobileApplication).curves
        return getSharedPreferences("curves", MODE_PRIVATE).getString("selected", null)?.let(library::find)
    }
    private fun confirmStart() {
        val owner = service ?: return
        val library = (application as MobileApplication).curves
        val profile = selected()?.let { library.resolve(it.id, owner.snapshot.scaleState == DeviceState.READY) }
        val blocked = ShotGate.startBlock(profile, owner.snapshot.coffeeState, owner.snapshot.coffee, owner.snapshot.coffeeAt, owner.snapshot.scaleState,
            owner.snapshot.weightAt, SystemClock.elapsedRealtime(), owner.shotState,
            validated = profile?.let(library::validated) == true)
        if (blocked != null) { toast(blocked); render(); return }
        requireNotNull(profile)
        AlertDialog.Builder(this).setTitle("确认开始萃取")
            .setMessage("${profile.name} · ${profile.temperatureC} °C\n最大水量：${profile.maximumWaterMl} ml\n目标重量：${if (profile.targetHundredthsGram > 0) "${number(profile.targetHundredthsGram)} g（电子秤）" else "不使用（由咖啡机按水量结束）"}\n将向咖啡机发送已校验的启动命令。")
            .setPositiveButton("确认启动") { _, _ ->
                owner.startShot(profile.id, profile.scaleMode)?.let(::toast)
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
        val profile = item?.let { library.resolve(it.id, snapshot.scaleState == DeviceState.READY) }
        val blocked = if (item != null && profile == null) "这条曲线未通过报文校验，暂不可萃取"
            else ShotGate.startBlock(profile, snapshot.coffeeState, snapshot.coffee, snapshot.coffeeAt, snapshot.scaleState,
                snapshot.weightAt, SystemClock.elapsedRealtime(), state,
                validated = profile?.let(library::validated) == true)
        val unknownAdvice = if (state == ExtractionState.OUTCOME_UNKNOWN && snapshot.coffeeState != io.openhoyi.session.DeviceState.READY)
            "\n连接中断且结果未知；先检查咖啡机，再重连后尝试停止。" else ""
        readiness.show("曲线：${item?.name ?: "未选择"}\n咖啡机：${snapshot.coffeeState.name} · 电子秤：${snapshot.scaleState.name}\n萃取状态：${state.name}${owner?.stopReason?.let { " · 停止原因：$it" } ?: ""}\n${blocked ?: "设备与曲线已就绪"}$unknownAdvice")
        val machine = when (val frame = snapshot.coffee) {
            is ExtractionTelemetry -> "${frame.elapsedSeconds} s · ${frame.pressureTenthsBar / 10.0} bar · ${number(frame.brewTemperatureHundredthsC)} °C"
            is IdleTelemetry -> "待机 · ${frame.brewPressureTenthsBar / 10.0} bar · ${number(frame.brewTemperatureHundredthsC)} °C"
            else -> "时间/压力/温度：—"
        }
        val now = SystemClock.elapsedRealtime()
        val weightFresh = snapshot.weightAt?.let { now >= it && now - it <= 1500 } == true
        live.show("$machine\n重量：${snapshot.weight?.let { number(it.weightHundredthsGram) } ?: "—"} g${if (weightFresh) "" else "（非实时）"}")
        val points = owner?.chartPoints ?: emptyList()
        if (chart.points != points) chart.points = points
        start.isEnabled = owner?.running == true && blocked == null
        stop.isEnabled = owner?.running == true && ShotGate.active(state) &&
            (state != ExtractionState.OUTCOME_UNKNOWN || snapshot.coffeeState == io.openhoyi.session.DeviceState.READY)
    }
    private fun TextView.show(value: String) { if (text.toString() != value) text = value }
    private fun number(value: Int): String = String.format(Locale.ROOT, "%.2f", value / 100.0)
    private fun toast(value: String) = Toast.makeText(this, value, Toast.LENGTH_SHORT).show()
    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()
    private fun text(parent: LinearLayout, value: String, size: Int, bold: Boolean = false): TextView = TextView(this).apply {
        text = value; textSize = size.toFloat(); setTextColor(Color.rgb(32, 38, 42))
        setPadding(0, dp(6), 0, dp(6)); if (bold) setTypeface(null, Typeface.BOLD); parent.addView(this)
    }
    private fun card(parent: LinearLayout): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(16), dp(20), dp(16))
        background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(18).toFloat() }
        parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })
    }
    private fun button(parent: LinearLayout, value: String, action: () -> Unit): Button = Button(this).apply {
        text = value; setOnClickListener { action() }
        parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
    }
}
