package io.openhoyi.mobile

import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LegacyHistoryDetailActivity : ThemedActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = ScrollView(this).apply {
            setBackgroundColor(getColor(R.color.mobile_background))
            setOnApplyWindowInsetsListener { view, insets ->
                val bars = if (Build.VERSION.SDK_INT >= 30) {
                    val area = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                    intArrayOf(area.left, area.top, area.right, area.bottom)
                } else {
                    @Suppress("DEPRECATION")
                    intArrayOf(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                        insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                }
                view.setPadding(bars[0], bars[1], bars[2], bars[3]); insets
            }
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(28))
        }
        root.addView(body); setContentView(root)
        text(body, "旧版萃取详情", 28, true)
        val id = intent.getStringExtra("legacyId")
        val status = TextView(this).apply {
            text = "正在读取旧版记录…"; textSize = 16f; setTextColor(getColor(R.color.mobile_text))
            body.addView(this)
        }
        Thread({
            val result = runCatching { (application as MobileApplication).legacyHistory.list().firstOrNull { it.id == id } }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                val shot = result.getOrNull()
                if (shot == null) { status.text = "旧版记录不可用"; return@runOnUiThread }
                body.removeView(status)
                showShot(body, shot)
            }
        }, "legacy-history-detail").start()
    }

    private fun showShot(body: LinearLayout, shot: LegacyShot) {
        text(body, "${shot.profileName.ifBlank { "未命名曲线" }}\n" +
            "${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date(shot.createdAtMs))} · ${shot.durationSec} 秒\n" +
            "来源：旧版 UniApp · 只读记录", 16)
        body.addView(LegacyShotChartView(this).apply { points = shot.points },
            LinearLayout.LayoutParams(-1, dp(300)).apply { topMargin = dp(16) })
        text(body, "蓝线为旧版压力（bar），绿线为旧版机器水流（ml/s）。" +
            "原始 wFlow/wTrend 已保留，但旧版计算含平滑和基线处理，不等同原生秤重或秤流速；" +
            "旧记录没有原生机器温度、累计水量及结束确认，不能据此证明机器实际停止。", 14)
    }

    private fun text(parent: LinearLayout, value: String, size: Int, bold: Boolean = false) {
        parent.addView(TextView(this).apply {
            text = value; textSize = size.toFloat(); setTextColor(getColor(R.color.mobile_text))
            if (bold) setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(6), 0, dp(6))
        })
    }
    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()
}
