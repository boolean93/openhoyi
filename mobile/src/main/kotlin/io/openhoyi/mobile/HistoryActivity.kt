package io.openhoyi.mobile

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Native extraction outcomes with separate legacy import actions. */
class HistoryActivity : ThemedActivity() {
    private data class RowViews(val title: TextView, val status: TextView, val meta: TextView)
    private lateinit var adapter: ArrayAdapter<ShotHistory.Entry>
    private lateinit var count: TextView
    private lateinit var emptyTitle: TextView
    private lateinit var emptyMessage: TextView
    private lateinit var emptyAction: Button
    private var rows = emptyList<ShotHistory.Entry>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.mobile_background))
            setOnApplyWindowInsetsListener { view, insets ->
                val area = if (Build.VERSION.SDK_INT >= 30) {
                    val x = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                    intArrayOf(x.left, x.top, x.right, x.bottom)
                } else {
                    @Suppress("DEPRECATION")
                    intArrayOf(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                        insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                }
                view.setPadding(area[0], area[1], area[2], area[3]); insets
            }
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(12))
        }
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        HoyiUi.navigation(this, root, HistoryActivity::class.java)
        setContentView(root)
        HoyiUi.header(this, body, "萃取历史", "每杯记录的状态与曲线采样", back = true)
        count = HoyiUi.label(this, body, "", 14, muted = true)
        val list = ListView(this).apply {
            divider = null
            dividerHeight = dp(8)
        }
        adapter = object : ArrayAdapter<ShotHistory.Entry>(this, android.R.layout.simple_list_item_1, mutableListOf()) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val card = convertView as? LinearLayout ?: LinearLayout(this@HistoryActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    minimumHeight = dp(80)
                    setPadding(dp(18), dp(13), dp(18), dp(13))
                    background = HoyiUi.shape(this@HistoryActivity, R.color.mobile_surface, 14, R.color.mobile_border)
                    val header = LinearLayout(this@HistoryActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                    }
                    addView(header)
                    val title = TextView(this@HistoryActivity).apply {
                        textSize = 17f
                        setTextColor(getColor(R.color.mobile_text))
                        maxLines = 2
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    }
                    header.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
                    val state = TextView(this@HistoryActivity).apply { textSize = 13f }
                    header.addView(state, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(12) })
                    val meta = TextView(this@HistoryActivity).apply {
                        textSize = 13f
                        setTextColor(getColor(R.color.mobile_muted))
                        setPadding(0, dp(8), 0, 0)
                    }
                    addView(meta)
                    tag = RowViews(title, state, meta)
                }
                val entry = requireNotNull(getItem(position))
                val views = card.tag as RowViews
                val library = runCatching { (application as MobileApplication).curves }.getOrNull()
                views.title.text = if (entry.curveId == "manual") "机器手动萃取"
                    else library?.find(entry.curveId)?.name ?: entry.curveId
                views.status.text = status(entry.status)
                views.status.setTextColor(getColor(if (entry.status == ShotHistory.Status.UNKNOWN)
                    R.color.mobile_danger else R.color.mobile_accent))
                views.meta.text = buildString {
                    append(date(entry.startedAtMs))
                    entry.elapsedMs?.let { append(" · %.1f 秒".format(Locale.ROOT, it / 1000.0)) }
                    entry.weightHundredthsGram?.let { append(" · 秤读数 %.2f g".format(Locale.ROOT, it / 100.0)) }
                    entry.slot?.takeIf { it in 1..5 }?.let { append(" · 快捷槽位 $it") }
                }
                return card
            }
        }
        list.adapter = adapter
        val content = FrameLayout(this)
        body.addView(content, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(12) })
        content.addView(list, FrameLayout.LayoutParams(-1, -1))
        val emptyHolder = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
        }
        content.addView(emptyHolder, FrameLayout.LayoutParams(-1, -1))
        val emptyCard = HoyiUi.card(this, emptyHolder)
        if (HoyiUi.wide(this)) (emptyCard.layoutParams as LinearLayout.LayoutParams).apply {
            width = dp(520)
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        }.also { emptyCard.layoutParams = it }
        emptyTitle = HoyiUi.label(this, emptyCard, "还没有萃取记录", 19, true)
        emptyMessage = HoyiUi.label(this, emptyCard,
            "完成第一杯后，这里会显示使用的曲线、萃取状态和采样数据。", 16, muted = true)
        emptyAction = HoyiUi.button(this, emptyCard, "去曲线库选一条曲线", primary = true) {
            startActivity(Intent(this, CurveActivity::class.java))
        }
        list.emptyView = emptyHolder
        list.setOnItemClickListener { _, _, position, _ ->
            startActivity(Intent(this, HistoryDetailActivity::class.java).putExtra("shotId", rows[position].id))
        }
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        body.addView(actions)
        fun action(title: String, click: () -> Unit) {
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            actions.addView(box, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) })
            HoyiUi.button(this, box, title, action = click)
        }
        action(if (HoyiUi.wide(this)) "导出历史 ZIP" else "导出") {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("application/zip")
                .putExtra(Intent.EXTRA_TITLE, "openhoyi-history-${System.currentTimeMillis()}.zip"), EXPORT_HISTORY)
        }
        action(if (HoyiUi.wide(this)) "导入旧版历史" else "导入") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), IMPORT_LEGACY)
        }
        action(if (HoyiUi.wide(this)) "查看旧版历史" else "旧版") {
            startActivity(Intent(this, LegacyHistoryActivity::class.java))
        }
    }
    override fun onStart() { super.onStart(); render() }

    @Deprecated("Platform activity results")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == EXPORT_HISTORY && resultCode == RESULT_OK)
            data?.data?.let { (application as MobileApplication).exportHistory(it) }
        if (requestCode == IMPORT_LEGACY && resultCode == RESULT_OK)
            data?.data?.let { (application as MobileApplication).importLegacyHistory(it) }
    }
    private fun render() {
        val history = runCatching { (application as MobileApplication).history }.getOrNull()
        if (history == null) {
            count.text = "历史记录暂不可用"; rows = emptyList()
            emptyTitle.text = "历史记录暂不可用"
            emptyMessage.text = "请稍后重新打开；当前无法判断本机记录是否为空。"
            emptyAction.visibility = View.GONE
        } else {
            rows = history.entries
            count.text = if (rows.isEmpty()) "暂无本机记录" else "本机记录 ${rows.size} 条 · 最多保留 30 天 / 500 条"
            emptyTitle.text = "还没有萃取记录"
            emptyMessage.text = "完成第一杯后，这里会显示使用的曲线、萃取状态和采样数据。"
            emptyAction.visibility = View.VISIBLE
        }
        adapter.clear()
        adapter.addAll(rows)
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
    private fun dp(value: Int) = HoyiUi.dp(this, value)
    private companion object { const val EXPORT_HISTORY = 41; const val IMPORT_LEGACY = 42 }
}
