package io.openhoyi.mobile

import android.os.Build
import android.os.Bundle
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.openhoyi.session.StopReason
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Historical chart is decoded from bounded local samples off the UI thread. */
class HistoryDetailActivity : ThemedActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.mobile_background))
            setOnApplyWindowInsetsListener { view, insets ->
                val area = if (Build.VERSION.SDK_INT >= 30) {
                    val a = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                    intArrayOf(a.left, a.top, a.right, a.bottom)
                } else {
                    @Suppress("DEPRECATION")
                    intArrayOf(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                        insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                }
                view.setPadding(area[0], area[1], area[2], area[3]); insets
            }
        }
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(28))
        }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        HoyiUi.navigation(this, root, HistoryActivity::class.java)
        setContentView(root)
        HoyiUi.header(this, body, if (BuildConfig.MOCK_MODE) "模拟萃取详情" else "萃取详情", back = true)
        val shotId = intent.getStringExtra("shotId")
        val app = application as MobileApplication
        val entry = runCatching { app.history.entries.firstOrNull { it.id == shotId } }.getOrNull()
        if (entry == null) {
            HoyiUi.label(this, HoyiUi.card(this, body), "记录不存在或已过保留期限", 17)
            return
        }
        val curve = if (entry.curveId == "manual") "机器手动萃取" else
            runCatching { app.curves.find(entry.curveId)?.name }.getOrNull() ?: entry.curveId
        val summary = HoyiUi.card(this, body, curve)
        HoyiUi.label(this, summary, status(entry.status), 20, true).apply {
            setTextColor(getColor(if (entry.status == ShotHistory.Status.UNKNOWN) R.color.mobile_danger else R.color.mobile_accent))
        }
        val detail = buildString {
            entry.slot?.takeIf { it in 1..5 }?.let { appendLine("快捷槽位：$it") }
            appendLine("开始请求：${date(entry.startedAtMs)}")
            entry.endedAtMs?.let { appendLine("观察到结束：${date(it)}") }
            entry.elapsedMs?.let { appendLine("持续：${"%.1f".format(Locale.ROOT, it / 1000.0)} 秒") }
            entry.reason?.let { appendLine("停止原因：${reasonLabel(it)}") }
            entry.weightHundredthsGram?.let {
                appendLine("结束时秤读数：${"%.2f".format(Locale.ROOT, it / 100.0)} g（未经杯重校准）")
            }
            if (entry.status == ShotHistory.Status.UNKNOWN) append("结果未确认，请以咖啡机实际状态为准。")
        }
        HoyiUi.label(this, summary, detail, 15).apply { setPadding(0, dp(12), 0, 0) }
        val chartCard = HoyiUi.card(this, body, "萃取曲线")
        val chart = ShotChartView(this)
        chartCard.addView(chart, LinearLayout.LayoutParams(-1, dp(300)))
        val chartStatus = HoyiUi.label(this, chartCard, "正在读取本机采样…", 13, muted = true)
        HoyiUi.button(this, body, "返回历史") { finish() }
        Thread({
            val points = runCatching { app.samples.load(entry.id) }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                points.onSuccess {
                    chart.points = it
                    chartStatus.text = if (it.isEmpty()) "暂无保存的采样点" else
                        "${it.size} 个${if (entry.status == ShotHistory.Status.ENDED) "" else "部分"}采样点 · " +
                            "蓝色压力 / 绿色机器水流 / 紫色秤流速 / 橙色秤重 / 红色温度"
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
    private fun reasonLabel(value: String) = when (value) {
        StopReason.TARGET_WEIGHT.name -> "达到目标重量"
        StopReason.SCALE_UNAVAILABLE.name -> "电子秤数据不可用"
        StopReason.TARE_UNCONFIRMED.name -> "电子秤归零未确认"
        StopReason.MANUAL.name -> "手动停止"
        else -> value
    }
    private fun date(epochMs: Long) = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date(epochMs))
    private fun dp(value: Int) = HoyiUi.dp(this, value)
}
