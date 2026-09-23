package io.openhoyi.mobile

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.WindowInsets
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Local Alpha-only shot outcomes; no legacy import and no BLE commands. */
class HistoryActivity : Activity() {
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var count: TextView
    private var rows = emptyList<ShotHistory.Entry>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(244, 241, 235))
            setOnApplyWindowInsetsListener { view, insets ->
                val bars = if (Build.VERSION.SDK_INT >= 30) {
                    val area = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                    intArrayOf(area.left, area.top, area.right, area.bottom)
                } else {
                    @Suppress("DEPRECATION")
                    intArrayOf(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                        insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                }
                view.setPadding(dp(20) + bars[0], dp(12) + bars[1], dp(20) + bars[2], dp(12) + bars[3])
                insets
            }
        }
        setContentView(root)
        title(root, "萃取历史", 28, true)
        title(root, "仅记录原生 Alpha 发起的萃取；结果未知时不会标为成功。", 14)
        count = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.rgb(32, 38, 42))
            setPadding(0, dp(12), 0, dp(8))
        }
        root.addView(count)
        val list = ListView(this)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf<String>())
        list.adapter = adapter
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        list.setOnItemClickListener { _, _, position, _ -> show(rows[position]) }
    }
    override fun onStart() { super.onStart(); render() }

    private fun render() {
        val history = runCatching { (application as MobileApplication).history }.getOrNull()
        if (history == null) {
            count.text = "历史记录暂不可用"
            rows = emptyList()
        } else {
            rows = history.entries
            count.text = if (rows.isEmpty()) "暂无本机记录" else "本机记录 ${rows.size} 条 · 最多保留 30 天 / 500 条"
        }
        val library = runCatching { (application as MobileApplication).curves }.getOrNull()
        adapter.clear()
        adapter.addAll(rows.map { entry ->
            "${date(entry.startedAtMs)}   ${status(entry.status)}\n${library?.find(entry.curveId)?.name ?: entry.curveId}"
        })
    }

    private fun show(entry: ShotHistory.Entry) {
        val curve = runCatching { (application as MobileApplication).curves.find(entry.curveId)?.name }.getOrNull()
            ?: entry.curveId
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
        AlertDialog.Builder(this).setTitle("萃取记录").setMessage(detail).setPositiveButton("关闭", null).show()
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
    private fun title(parent: LinearLayout, value: String, size: Int, bold: Boolean = false) {
        parent.addView(TextView(this).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(Color.rgb(32, 38, 42))
            setPadding(0, dp(4), 0, dp(4))
            if (bold) setTypeface(null, Typeface.BOLD)
        })
    }
}
