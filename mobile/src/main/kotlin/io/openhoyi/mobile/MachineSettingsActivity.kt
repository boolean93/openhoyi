package io.openhoyi.mobile

import android.app.Activity
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
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.openhoyi.session.DeviceState
import java.util.UUID

/** Device settings received over BLE. This Activity has no write controls. */
class MachineSettingsActivity : Activity() {
    private var service: MobileService? = null
    private var bound = false
    private var visible = false
    private val visibilityToken = UUID.randomUUID().toString()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var connectionState: TextView
    private lateinit var settings: TextView
    private lateinit var schedule: TextView
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
        text(body, "此页只显示机器返回的数据，不修改配置。", 14)
        val summaryCard = card(body, "连接状态")
        connectionState = text(summaryCard, "未连接", 16)
        val settingsCard = card(body, "当前设置")
        settings = text(settingsCard, "尚未收到机器设置", 16)
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
        val snapshot = service?.snapshot ?: MobileSnapshot()
        val ready = snapshot.coffeeState == DeviceState.READY
        connectionState.update("${snapshot.coffeeState.name}${if (ready) " · 已认证" else " · 数据不可视为当前生效配置"}")
        settings.update(MachineSettingsPresentation.settings(snapshot.settings))
        schedule.update(MachineSettingsPresentation.schedule(snapshot.sleepFirst, snapshot.sleepSecond))
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
