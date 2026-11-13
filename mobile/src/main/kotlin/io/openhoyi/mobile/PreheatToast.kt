package io.openhoyi.mobile

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
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
            setPadding(dp(16), 0, dp(8), 0)
            minimumHeight = dp(48)
            background = GradientDrawable().apply { setColor(0xFF2D3333.toInt()); cornerRadius = dp(10).toFloat() }
            elevation = dp(8).toFloat()
            accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        val icon = object : View(activity) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            override fun onDraw(canvas: Canvas) {
                val cx = width / 2f; val cy = height / 2f; val radius = minOf(width, height) / 2f
                paint.color = 0xFF54C97B.toInt()
                canvas.drawCircle(cx, cy, radius, paint)
                paint.color = 0xFF18392B.toInt()
                canvas.drawCircle(cx, cy - radius * .45f, radius * .105f, paint)
                canvas.drawRoundRect(cx - radius * .1f, cy - radius * .15f,
                    cx + radius * .1f, cy + radius * .55f, radius * .08f, radius * .08f, paint)
            }
        }.apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        bar.addView(icon, LinearLayout.LayoutParams(dp(20), dp(20)))
        bar.addView(TextView(activity).apply {
            val notSent = activity.getString(R.string.service_auto_cancel_not_sent)
            text = if (message == activity.getString(R.string.service_auto_cancel_blocked, notSent)) notSent else message
            textSize = 14f; setTextColor(Color.WHITE); includeFontPadding = false
            setPadding(0, dp(8), 0, dp(8))
            typeface = TaskTypography.face(activity, 500)
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) })
        bar.addView(TextView(activity).apply {
            text = "×"; textSize = 22f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            includeFontPadding = false; setPadding(0, 0, 0, 0)
            contentDescription = activity.getString(R.string.auto_preheat_dismiss)
            setOnClickListener { root.removeView(bar) }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        fun bottomNavigation(view: View): TaskNavigationView? {
            if (view is TaskNavigationView && view.orientation == LinearLayout.HORIZONTAL && view.isShown) return view
            if (view is ViewGroup) for (index in 0 until view.childCount)
                bottomNavigation(view.getChildAt(index))?.let { return it }
            return null
        }
        val navigation = bottomNavigation(root)
        val bottom = if (navigation != null) {
            val rootPosition = IntArray(2); val navigationPosition = IntArray(2)
            root.getLocationOnScreen(rootPosition); navigation.getLocationOnScreen(navigationPosition)
            root.height - (navigationPosition[1] - rootPosition[1]) + dp(8)
        } else {
            @Suppress("DEPRECATION")
            val systemBottom = root.rootWindowInsets?.systemWindowInsetBottom ?: 0
            systemBottom + dp(16)
        }
        root.addView(bar, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            setMargins(dp(24), 0, dp(24), bottom)
        })
        bar.postDelayed({ if (bar.parent === root) root.removeView(bar) }, 5000)
    }
}
