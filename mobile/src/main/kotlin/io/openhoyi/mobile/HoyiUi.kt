package io.openhoyi.mobile

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Reusable native visual language. The views own no machine or BLE state. */
internal object HoyiUi {
    fun dp(activity: Activity, value: Int) = (activity.resources.displayMetrics.density * value).toInt()
    fun wide(activity: Activity): Boolean = activity.resources.configuration.screenWidthDp >= 700
    fun dark(activity: Activity) = activity.resources.configuration.uiMode and
        Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    fun shape(activity: Activity, color: Int, radius: Int = 16, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(activity.getColor(color))
            cornerRadius = dp(activity, radius).toFloat()
            stroke?.let { setStroke(dp(activity, 1), activity.getColor(it)) }
        }

    fun label(activity: Activity, parent: LinearLayout, value: String, size: Int,
              bold: Boolean = false, muted: Boolean = false): TextView = TextView(activity).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(activity.getColor(if (muted) R.color.mobile_muted else R.color.mobile_text))
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        includeFontPadding = false
        parent.addView(this)
    }

    fun card(activity: Activity, parent: LinearLayout, title: String? = null): LinearLayout =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 20), dp(activity, 18), dp(activity, 20), dp(activity, 18))
            background = shape(activity, R.color.mobile_surface, 18, R.color.mobile_border)
            parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(activity, 14) })
            if (title != null) label(activity, this, title, 19, true).apply {
                setPadding(0, 0, 0, dp(activity, 12))
            }
        }

    fun button(activity: Activity, parent: LinearLayout, value: String, primary: Boolean = false,
               danger: Boolean = false, action: () -> Unit): Button = Button(activity).apply {
        text = value
        isAllCaps = false
        textSize = if (primary || danger) 17f else 15f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        val foreground = activity.getColor(when {
            primary -> android.R.color.white
            danger -> R.color.mobile_danger
            else -> R.color.mobile_text
        })
        setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(activity.getColor(R.color.mobile_muted), foreground)))
        minHeight = dp(activity, if (primary || danger) 54 else 48)
        minimumHeight = minHeight
        elevation = 0f
        background = StateListDrawable().apply {
            addState(intArrayOf(-android.R.attr.state_enabled),
                shape(activity, R.color.mobile_accent_soft, 12, R.color.mobile_border))
            addState(intArrayOf(), shape(activity,
                if (primary) R.color.mobile_primary_button else R.color.mobile_surface, 12,
                if (primary) null else if (danger) R.color.mobile_danger else R.color.mobile_border))
        }
        setOnClickListener { action() }
        parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(activity, 10) })
    }

    fun header(activity: Activity, parent: LinearLayout, title: String, subtitle: String? = null,
               back: Boolean = false) {
        val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        parent.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(activity, 12) })
        if (back) label(activity, row, "‹", 34).apply {
            contentDescription = "返回"
            minWidth = dp(activity, 48)
            minHeight = dp(activity, 48)
            gravity = Gravity.CENTER_VERTICAL
            setOnClickListener { activity.finish() }
        }
        val words = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        row.addView(words, LinearLayout.LayoutParams(0, -2, 1f))
        label(activity, words, title, 28, true)
        subtitle?.let { label(activity, words, it, 13, muted = true).apply {
            setPadding(0, dp(activity, 4), 0, 0)
        } }
    }

    fun navigation(activity: Activity, parent: LinearLayout, selected: Class<out Activity>) {
        val bar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(activity, 8), dp(activity, 6), dp(activity, 8), dp(activity, 6))
            background = shape(activity, R.color.mobile_surface, 0)
        }
        parent.addView(bar, LinearLayout.LayoutParams(-1, dp(activity, 62)))
        val items = listOf(
            Triple("⌂  首页", HomeActivity::class.java, "首页"),
            Triple("⌁  曲线", CurveActivity::class.java, "曲线库"),
            Triple("▷  萃取", ExtractionActivity::class.java, "实时萃取"),
            Triple("◷  历史", HistoryActivity::class.java, "萃取历史"),
            Triple("⚙  设置", MachineSettingsActivity::class.java, "机器设置"),
        )
        items.forEach { (title, target, description) ->
            TextView(activity).apply {
                text = if (wide(activity)) title else when (target) {
                    HomeActivity::class.java -> "首页"
                    CurveActivity::class.java -> "曲线"
                    ExtractionActivity::class.java -> "萃取"
                    HistoryActivity::class.java -> "历史"
                    else -> "设置"
                }
                textSize = if (wide(activity)) 14f else 12f
                gravity = Gravity.CENTER
                contentDescription = description
                setTextColor(activity.getColor(if (selected == target) R.color.mobile_accent else R.color.mobile_muted))
                if (selected == target) {
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    background = shape(activity, R.color.mobile_accent_soft, 10)
                }
                setOnClickListener {
                    if (activity.javaClass != target) {
                        activity.startActivity(Intent(activity, target).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                    }
                }
                bar.addView(this, LinearLayout.LayoutParams(0, -1, 1f).apply {
                    marginStart = dp(activity, 2); marginEnd = dp(activity, 2)
                })
            }
        }
    }
}
