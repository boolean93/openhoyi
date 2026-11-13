package io.openhoyi.mobile

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** In-app transient feedback matching the chosen dark, rounded design. */
internal object PreheatToast {
    private const val TAG = "product/preheat/toast"
    fun show(activity: Activity, message: String) {
        if (activity.isFinishing || activity.isDestroyed || message.isBlank()) return
        val root = activity.window.decorView as? ViewGroup ?: return
        root.findViewWithTag<android.view.View>(TAG)?.let(root::removeView)
        val density = activity.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val bar = LinearLayout(activity).apply {
            tag = TAG
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(8), dp(12))
            minimumHeight = dp(48)
            background = GradientDrawable().apply { setColor(0xFF2D3333.toInt()); cornerRadius = dp(10).toFloat() }
            elevation = dp(8).toFloat()
            accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        val icon = TextView(activity).apply {
            text = "i"; textSize = 14f; gravity = Gravity.CENTER; setTextColor(0xFF18392B.toInt())
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFF54C97B.toInt()) }
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        bar.addView(icon, LinearLayout.LayoutParams(dp(20), dp(20)))
        bar.addView(TextView(activity).apply {
            text = message; textSize = 14f; setTextColor(Color.WHITE)
            typeface = TaskTypography.face(activity, 500)
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) })
        bar.addView(TextView(activity).apply {
            text = "×"; textSize = 22f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            contentDescription = activity.getString(R.string.auto_preheat_dismiss)
            setOnClickListener { root.removeView(bar) }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(bar, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            setMargins(dp(24), 0, dp(24), dp(92))
        })
        bar.postDelayed({ if (bar.parent === root) root.removeView(bar) }, 5000)
    }
}
