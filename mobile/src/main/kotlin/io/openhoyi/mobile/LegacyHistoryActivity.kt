package io.openhoyi.mobile

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Imported UniApp records have their own list and never join Alpha control/history state. */
class LegacyHistoryActivity : ThemedActivity() {
    private data class RowViews(val title: TextView, val subtitle: TextView)
    private var rows = emptyList<LegacyShot>()
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var count: TextView
    private lateinit var list: ListView
    private lateinit var emptyState: LinearLayout
    private lateinit var emptyTitle: TextView
    private lateinit var emptyMessage: TextView
    private var refreshGeneration = 0

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
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val card = convertView as? LinearLayout ?: LinearLayout(this@LegacyHistoryActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    minimumHeight = dp(76)
                    setPadding(dp(18), dp(13), dp(18), dp(13))
                    background = HoyiUi.shape(this@LegacyHistoryActivity, R.color.mobile_surface, 12, R.color.mobile_border)
                    val title = HoyiUi.label(this@LegacyHistoryActivity, this, "", 17, true)
                    val subtitle = HoyiUi.label(this@LegacyHistoryActivity, this, "", 13, muted = true)
                    subtitle.setPadding(0, dp(7), 0, 0)
                    tag = RowViews(title, subtitle)
                }
                val item = rows[position]
                val views = card.tag as RowViews
                views.title.text = item.profileName.ifBlank { "未命名曲线" }
                views.subtitle.text = "${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date(item.createdAtMs))} · ${item.durationSec} 秒 · 旧版只读"
                return card
            }
        }
        val content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(14) })
        list = ListView(this).apply {
            adapter = this@LegacyHistoryActivity.adapter
            divider = null
            dividerHeight = dp(8)
            setOnItemClickListener { _, _, position, _ ->
                startActivity(Intent(this@LegacyHistoryActivity, LegacyHistoryDetailActivity::class.java)
                    .putExtra("legacyId", rows[position].id))
            }
        }
        content.addView(list, FrameLayout.LayoutParams(-1, -1))
        emptyState = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            visibility = View.GONE
        }
        content.addView(emptyState, FrameLayout.LayoutParams(-1, -1))
        val emptyCard = HoyiUi.card(this, emptyState)
        if (HoyiUi.wide(this)) (emptyCard.layoutParams as LinearLayout.LayoutParams).apply {
            width = dp(520)
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        }.also { emptyCard.layoutParams = it }
        emptyTitle = HoyiUi.label(this, emptyCard, "尚无旧版历史", 19, true)
        emptyMessage = HoyiUi.label(this, emptyCard, "在萃取历史页选择“导入旧版历史”后，这里会显示只读记录。", 15, muted = true)
        emptyMessage.setPadding(0, dp(10), 0, 0)
        HoyiUi.navigation(this, root, HistoryActivity::class.java)
    }

    override fun onStart() {
        super.onStart()
        val generation = ++refreshGeneration
        count.text = "正在读取旧版历史…"
        emptyState.visibility = View.GONE
        list.visibility = View.GONE
        Thread({
            val result = runCatching { (application as MobileApplication).legacyHistory.list() }
            runOnUiThread {
                if (isFinishing || isDestroyed || generation != refreshGeneration) return@runOnUiThread
                rows = result.getOrDefault(emptyList())
                count.text = result.fold({ "从旧版文件导入 ${it.size} 条 · 只读，不参与机器控制" },
                    { "旧版历史读取失败：${it.message ?: it.javaClass.simpleName}" })
                adapter.clear()
                adapter.addAll(rows.map { it.id })
                emptyTitle.text = if (result.isFailure) "旧版历史读取失败" else "尚无旧版历史"
                emptyMessage.text = if (result.isFailure) "读取失败，原有记录未修改。请返回后重试。"
                    else "在萃取历史页选择“导入旧版历史”后，这里会显示只读记录。"
                emptyState.visibility = if (adapter.count == 0) View.VISIBLE else View.GONE
                list.visibility = if (adapter.count == 0) View.GONE else View.VISIBLE
            }
        }, "legacy-history-list").start()
    }

    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()
}
