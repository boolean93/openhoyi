package io.openhoyi.mobile

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.WindowInsets
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

/** Imported rows are browse-only and never feed the machine-control curve library. */
class LegacyCurveActivity : ThemedActivity() {
    private lateinit var count: TextView
    private lateinit var adapter: ArrayAdapter<String>
    private var bundle: LegacyCurveBundle? = null
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
        HoyiUi.header(this, root, "旧版曲线库", "导入内容只供浏览，不发送到咖啡机", back = true)
        count = label("正在读取…", 14)
        root.addView(count)
        HoyiUi.button(this, root, getString(R.string.curve_import_legacy)) {
                @Suppress("DEPRECATION")
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), IMPORT_CURVES)
        }
        val list = ListView(this)
        adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, mutableListOf()) {
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View =
                (convertView as? TextView ?: TextView(this@LegacyCurveActivity)).apply {
                    text = getItem(position); textSize = 15f
                    setTextColor(getColor(R.color.mobile_text))
                    minHeight = dp(54); setPadding(dp(14), dp(8), dp(14), dp(8))
                    background = HoyiUi.shape(this@LegacyCurveActivity, R.color.mobile_surface, 12, R.color.mobile_border)
                }
        }
        list.adapter = adapter
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        HoyiUi.navigation(this, root, CurveActivity::class.java)
        list.setOnItemClickListener { _, _, position, _ ->
            val data = bundle ?: return@setOnItemClickListener
            val curve = data.curves[position]
            val category = data.categoryLabels[curve.category] ?: curve.category.ifBlank { "未分类" }
            val detail = getString(R.string.legacy_curve_detail, curve.name, category, position + 1,
                getString(if (curve.factory) R.string.legacy_curve_factory else R.string.legacy_curve_user),
                curve.temperatureC?.let { "$it °C" } ?: "未知",
                curve.waterMl?.let { "$it ml" } ?: "未知",
                curve.weightTenthsGram?.let { String.format(Locale.CHINA, "%.1f g", it / 10.0) } ?: "未知",
                curve.segments?.toString() ?: "未知")
            AlertDialog.Builder(this).setTitle(curve.name).setMessage(detail)
                .setPositiveButton("关闭", null).show()
        }
    }

    override fun onStart() { super.onStart(); refresh() }

    @Deprecated("Platform activity results")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != IMPORT_CURVES || resultCode != Activity.RESULT_OK) return
        val uri = data?.data ?: return
        count.text = "正在校验旧版曲线库…"
        Thread({
            val app = application as MobileApplication
            val result = runCatching {
                app.legacyCurves.import(contentResolver.openInputStream(uri) ?: error("No input stream"))
            }
            app.logs.record(if (result.isSuccess) "legacy.curves_import_finished" else "legacy.curves_import_failed",
                mapOf("result" to result.fold({ "${it.count} curves, changed=${it.changed}" },
                    { it.javaClass.simpleName })))
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                Toast.makeText(this, result.fold(
                    { "${if (it.changed) "已导入" else "未变化"} ${it.count} 条旧版曲线；仅供浏览" },
                    { "导入失败：${it.message ?: it.javaClass.simpleName}；原有数据未改变" }),
                    Toast.LENGTH_LONG).show()
                refresh()
            }
        }, "legacy-curve-import").start()
    }

    private fun refresh() {
        val generation = ++refreshGeneration
        count.text = "正在读取旧版曲线库…"
        Thread({
            val result = runCatching { (application as MobileApplication).legacyCurves.load() }
            runOnUiThread {
                if (isFinishing || isDestroyed || generation != refreshGeneration) return@runOnUiThread
                bundle = result.getOrNull()
                count.text = result.fold({ data ->
                    if (data == null) "尚未导入 · 旧版曲线与原生可萃取曲线分开保存"
                    else "旧版 ${data.curves.size} 条 · 只读，不参与机器控制"
                }, { "旧版曲线读取失败：${it.message ?: it.javaClass.simpleName}" })
                adapter.clear()
                adapter.addAll(bundle?.curves?.map { curve ->
                    val category = bundle?.categoryLabels?.get(curve.category)
                        ?: curve.category.ifBlank { "未分类" }
                    "${curve.index + 1}. ${curve.name} · $category · 仅浏览"
                } ?: emptyList())
            }
        }, "legacy-curve-list").start()
    }

    private fun label(value: String, size: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size.toFloat(); setTextColor(getColor(R.color.mobile_text))
        setPadding(0, dp(5), 0, dp(5))
        if (bold) setTypeface(null, Typeface.BOLD)
    }
    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()
    private companion object { const val IMPORT_CURVES = 43 }
}
