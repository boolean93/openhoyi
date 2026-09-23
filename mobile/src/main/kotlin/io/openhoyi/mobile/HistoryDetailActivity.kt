package io.openhoyi.mobile

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Historical chart is decoded from the app's own bounded sample file off the UI thread. */
class HistoryDetailActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = ScrollView(this).apply {
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
        root.addView(body)
        setContentView(root)
        text(body, "萃取详情", 28, true)
        val shotId = intent.getStringExtra("shotId")
        val app = application as MobileApplication
        val entry = runCatching { app.history.entries.firstOrNull { it.id == shotId } }.getOrNull()
        if (entry == null) {
            text(body, "记录不存在或已过保留期限", 17)
            return
        }
        val curve = runCatching { app.curves.find(entry.curveId)?.name }.getOrNull() ?: entry.curveId
        val detail = buildString {
            appendLine("曲线：$curve")
            appendLine("开始请求：${date(entry.startedAtMs)}")
            appendLine("状态：${status(entry.status)}")
            entry.endedAtMs?.let { appendLine("观察到结束：${date(it)}") }
            entry.elapsedMs?.let { appendLine("持续：${"%.1f".format(Locale.ROOT, it / 1000.0)} 秒") }
            entry.reason?.let { appendLine("停止原因：$it") }
            entry.weightHundredthsGram?.let {
                appendLine("结束时秤读数：${"%.2f".format(Locale.ROOT, it / 100.0)} g（未经杯重校准）")
            }
            if (entry.status == ShotHistory.Status.UNKNOWN) append("结果未确认，请以咖啡机实际状态为准。")
        }
        text(body, detail, 16)
        val chart = ShotChartView(this)
        body.addView(chart, LinearLayout.LayoutParams(-1, dp(300)).apply { topMargin = dp(16) })
        val chartStatus = text(body, "正在读取本机采样…", 14)
        Button(this).apply {
            text = "返回历史"
            setOnClickListener { finish() }
            body.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        }
        Thread({
            val points = runCatching { app.samples.load(entry.id) }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                points.onSuccess {
                    chart.points = it
                    chartStatus.text = if (it.isEmpty()) "暂无保存的采样点" else "${it.size} 个采样点 · 蓝色压力 / 绿色机器水流 / 橙色秤重"
                }.onFailure { chartStatus.text = "采样读取失败" }
            }
        }, "history-detail-reader").start()
    }
    private fun status(value: ShotHistory.Status) = when (value) {
        ShotHistory.Status.STARTING -> "启动中"
        ShotHistory.Status.RUNNING -> "萃取中"
        ShotHistory.Status.STOP_REQUESTED -> "停止请求中"
        ShotHistory.Status.ENDED -> "已观察结束"
        ShotHistory.Status.UNKNOWN -> "结果未知"
        ShotHistory.Status.NOT_STARTED -> "未启动"
    }
    private fun date(epochMs: Long) = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date(epochMs))
    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()
    private fun text(parent: LinearLayout, value: String, size: Int, bold: Boolean = false): TextView = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(Color.rgb(32, 38, 42))
        setPadding(0, dp(8), 0, dp(8))
        if (bold) setTypeface(null, Typeface.BOLD)
        parent.addView(this)
    }
}
