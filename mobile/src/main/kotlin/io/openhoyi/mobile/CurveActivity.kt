package io.openhoyi.mobile

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Captured-curve browser. Selection stores only the profile ID; no BLE command is sent. */
class CurveActivity : Activity() {
    private lateinit var details: TextView
    private lateinit var select: Button
    private var selected: CurveProfile? = null
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
        text(content, "曲线库", 28, true)
        text(content, "目前只列出三条来自真实采集的曲线，名称为临时编号。", 14)
        CurveCatalog.profiles.forEach { profile ->
            val card = card(content)
            text(card, profile.name, 20, true)
            text(card, "${profile.temperatureC} °C · 最多 ${profile.maximumWaterMl} ml · ${profile.parameters.segmentCount} 段", 15)
            button(card, "查看详情") { show(profile) }
        }
        val card = card(content)
        details = text(card, "请选择曲线查看详情", 16)
        select = button(card, "设为当前曲线") {
            selected?.let { getSharedPreferences("curves", MODE_PRIVATE).edit().putString("selected", it.id).apply(); finish() }
        }
        select.isEnabled = false
        savedInstanceState?.getString("selected")?.let(CurveCatalog::find)?.let(::show)
    }
    private fun show(profile: CurveProfile) {
        selected = profile
        val p = profile.parameters
        details.text = "${profile.name}\n结束方式：${profile.endMode}\n目标重量：${if (profile.targetHundredthsGram == 0) "不使用" else "${profile.targetHundredthsGram / 100.0} g"}\n冲泡温度：${p.temperatureC} °C\n最大水量：${p.maximumWaterMl} ml\n分段：${p.segmentCount}\n协议校验：${if (CurveCatalog.validated(profile)) "已匹配采集帧" else "未通过"}"
        select.isEnabled = CurveCatalog.validated(profile)
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putString("selected", selected?.id); super.onSaveInstanceState(outState) }
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
