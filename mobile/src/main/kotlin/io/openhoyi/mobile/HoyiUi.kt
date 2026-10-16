package io.openhoyi.mobile

import android.app.Activity
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Reusable native visual language. The views own no machine or BLE state. */
internal object HoyiUi {
    /** Names and batch descriptions must remain readable at phone widths and large fonts. */
    fun spinnerAdapter(activity: Activity, labels: List<String>): ArrayAdapter<String> =
        object : ArrayAdapter<String>(activity, android.R.layout.simple_spinner_dropdown_item, labels) {
            private fun readable(view: View): View = view.apply {
                if (this is TextView) {
                    setSingleLine(false)
                    ellipsize = null
                    minHeight = dp(activity, 48)
                    setTextColor(activity.getColor(R.color.mobile_text))
                    setBackgroundColor(activity.getColor(R.color.mobile_surface))
                    layoutParams = layoutParams?.apply { height = ViewGroup.LayoutParams.WRAP_CONTENT }
                }
            }
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                readable(super.getView(position, convertView, parent))
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
                readable(super.getDropDownView(position, convertView, parent))
        }
    fun dp(activity: Activity, value: Int) = (activity.resources.displayMetrics.density * value).toInt()
    fun wide(activity: Activity): Boolean = activity.resources.configuration.let {
        TaskLayoutPolicy.decide(it.screenWidthDp.coerceAtLeast(1), it.screenHeightDp).twoPaneAllowed
    }
    fun dark(activity: Activity) = activity.resources.configuration.uiMode and
        Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    fun shape(activity: Activity, color: Int, radius: Int = TaskVisualTokens.CARD_RADIUS_DP, stroke: Int? = null): GradientDrawable =
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
        typeface = TaskTypography.face(activity, if (bold) 500 else 400)
        includeFontPadding = false
        parent.addView(this)
    }

    fun card(activity: Activity, parent: LinearLayout, title: String? = null): LinearLayout =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, TaskVisualTokens.CARD_PADDING_DP), dp(activity, TaskVisualTokens.CARD_PADDING_DP),
                dp(activity, TaskVisualTokens.CARD_PADDING_DP), dp(activity, TaskVisualTokens.CARD_PADDING_DP))
            background = shape(activity, R.color.mobile_surface, TaskVisualTokens.CARD_RADIUS_DP, R.color.mobile_border)
            parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(activity, TaskVisualTokens.GROUP_GAP_DP) })
            if (title != null) label(activity, this, title, TaskVisualTokens.SECTION_SP, true).apply {
                setPadding(0, 0, 0, dp(activity, TaskVisualTokens.RELATED_GAP_DP))
            }
        }

    fun button(activity: Activity, parent: LinearLayout, value: String, primary: Boolean = false,
               danger: Boolean = false, action: () -> Unit): Button = Button(activity).apply {
        // Controls align by their bounds, not the final line's text baseline.
        // Baseline alignment can shift a wrapped weighted button below its measured row.
        parent.isBaselineAligned = false
        text = value
        isAllCaps = false
        textSize = TaskVisualTokens.BODY_SP.toFloat()
        typeface = TaskTypography.face(activity, 500)
        val foreground = activity.getColor(when {
            primary -> android.R.color.white
            danger -> R.color.mobile_danger
            else -> R.color.mobile_text
        })
        setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(activity.getColor(R.color.mobile_muted), foreground)))
        minHeight = dp(activity, if (primary || danger) TaskVisualTokens.PRIMARY_TARGET_DP else TaskVisualTokens.TOUCH_TARGET_DP)
        minimumHeight = minHeight
        elevation = 0f
        stateListAnimator = null
        backgroundTintList = null
        val states = StateListDrawable().apply {
            addState(intArrayOf(-android.R.attr.state_enabled),
                shape(activity, R.color.mobile_background, TaskVisualTokens.CONTROL_RADIUS_DP, R.color.mobile_border))
            addState(intArrayOf(android.R.attr.state_focused), shape(activity,
                if (primary) R.color.mobile_primary_button else R.color.mobile_surface, TaskVisualTokens.CONTROL_RADIUS_DP,
                if (primary) android.R.color.white else if (danger) R.color.mobile_danger else R.color.mobile_accent))
            addState(intArrayOf(), shape(activity,
                if (primary) R.color.mobile_primary_button else R.color.mobile_surface, TaskVisualTokens.CONTROL_RADIUS_DP,
                if (primary) null else if (danger) R.color.mobile_danger else R.color.mobile_border))
        }
        val feedback = ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(android.graphics.Color.TRANSPARENT,
                if (primary) 0x33FFFFFF else activity.getColor(R.color.mobile_accent_soft)))
        background = android.graphics.drawable.RippleDrawable(feedback, states,
            shape(activity, android.R.color.white, TaskVisualTokens.CONTROL_RADIUS_DP))
        setOnClickListener { action() }
        parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(activity, TaskVisualTokens.RELATED_GAP_DP) })
    }

    fun tabs(activity: Activity, parent: LinearLayout, labels: List<String>, selected: Int,
             onSelect: (Int) -> Unit) {
        val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; isBaselineAligned = false }
        parent.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(activity, 12) })
        labels.forEachIndexed { index, label ->
            val active = index == selected
            row.addView(TextView(activity).apply {
                text = label
                textSize = 15f
                gravity = Gravity.CENTER
                minHeight = dp(activity, 48)
                isClickable = !active
                isFocusable = true
                isSelected = active
                contentDescription = activity.getString(R.string.ui_tab_description, label, if (active) activity.getString(R.string.ui_current_page_suffix) else "")
                setTextColor(activity.getColor(if (active) R.color.mobile_accent else R.color.mobile_muted))
                background = shape(activity,
                    if (active) R.color.mobile_accent_soft else R.color.mobile_surface, TaskVisualTokens.CONTROL_RADIUS_DP, R.color.mobile_border)
                if (!active) setOnClickListener { onSelect(index) }
            }, LinearLayout.LayoutParams(0, -2, 1f).apply {
                if (index < labels.lastIndex) marginEnd = dp(activity, 8)
            })
        }
    }

    fun header(activity: Activity, parent: LinearLayout, title: String, subtitle: String? = null,
               back: Boolean = false): LinearLayout {
        val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        parent.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(activity, 12) })
        if (back) row.addView(ImageView(activity).apply {
            setImageResource(R.drawable.nav_back)
            imageTintList = ColorStateList.valueOf(activity.getColor(R.color.mobile_text))
            contentDescription = activity.getString(R.string.ui_back)
            isClickable = true
            isFocusable = true
            setPadding(dp(activity, 12), dp(activity, 12), dp(activity, 12), dp(activity, 12))
            background = shape(activity, R.color.mobile_surface, 12, R.color.mobile_border)
            setOnClickListener { activity.finish() }
        }, LinearLayout.LayoutParams(dp(activity, 48), dp(activity, 48)).apply {
            marginEnd = dp(activity, 12)
        })
        val words = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        row.addView(words, LinearLayout.LayoutParams(0, -2, 1f))
        label(activity, words, title, 24, true)
        subtitle?.let { label(activity, words, it, 13, muted = true).apply {
            setPadding(0, dp(activity, 4), 0, 0)
        } }
        row.addView(TaskNavigationView.createToolsHost(activity), LinearLayout.LayoutParams(-2, -2))
        return row
    }

    @Suppress("UNUSED_PARAMETER") // Retained for existing page callers; the actual Activity determines selection.
    fun navigation(activity: Activity, parent: LinearLayout, selected: Class<out Activity>) {
        TaskPageFrame.install(activity, parent)
    }
}
