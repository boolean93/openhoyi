package io.openhoyi.mobile

import android.content.Intent
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

/** Imported UniApp records have their own list and never join Alpha control/history state. */
class LegacyHistoryActivity : ThemedActivity() {
    private var rows = emptyList<LegacyShot>()
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var count: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
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
                view.setPadding(dp(20) + bars[0], dp(12) + bars[1], dp(20) + bars[2], dp(12) + bars[3])
                insets
            }
        }
        setContentView(root)
        HoyiUi.header(this, root, "旧版历史", "导入自旧版应用的只读记录", back = true)
        count = TextView(this).apply { textSize = 15f; setTextColor(getColor(R.color.mobile_text)) }
        root.addView(count)
        adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, mutableListOf()) {
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View =
                (convertView as? TextView ?: TextView(this@LegacyHistoryActivity)).apply {
                    text = getItem(position); textSize = 15f
                    setTextColor(getColor(R.color.mobile_text)); minHeight = dp(68)
                    setPadding(dp(14), dp(8), dp(14), dp(8))
                    background = HoyiUi.shape(this@LegacyHistoryActivity, R.color.mobile_surface, 12, R.color.mobile_border)
                }
        }
        root.addView(ListView(this).apply {
            this.adapter = this@LegacyHistoryActivity.adapter
            setOnItemClickListener { _, _, position, _ ->
                startActivity(Intent(this@LegacyHistoryActivity, LegacyHistoryDetailActivity::class.java)
                    .putExtra("legacyId", rows[position].id))
            }
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        HoyiUi.navigation(this, root, HistoryActivity::class.java)
    }

    override fun onStart() {
        super.onStart()
        count.text = "正在读取旧版历史…"
        Thread({
            val result = runCatching { (application as MobileApplication).legacyHistory.list() }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                rows = result.getOrDefault(emptyList())
                count.text = result.fold({ "从旧版文件导入 ${it.size} 条 · 只读，不参与机器控制" },
                    { "旧版历史读取失败：${it.message ?: it.javaClass.simpleName}" })
                adapter.clear()
                adapter.addAll(rows.map {
                    "${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date(it.createdAtMs))} · ${it.durationSec} 秒\n" +
                        "旧版 · ${it.profileName.ifBlank { "未命名曲线" }}"
                })
            }
        }, "legacy-history-list").start()
    }

    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()
}
