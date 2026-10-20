package io.openhoyi.mobile

import android.app.Activity
import android.view.View
import android.widget.LinearLayout

internal fun settingsGroup(activity: Activity, parent: LinearLayout, title: String, visual: SettingsVisualStyle? = null): LinearLayout {
    if (visual != null) return visual.group(parent, title)
    HoyiUi.label(activity, parent, title, TaskVisualTokens.SECTION_SP, true).apply {
        setPadding(0, HoyiUi.dp(activity, TaskVisualTokens.GROUP_GAP_DP), 0,
            HoyiUi.dp(activity, TaskVisualTokens.RELATED_GAP_DP))
        if (android.os.Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
    }
    return HoyiUi.card(activity, parent).apply {
        (layoutParams as LinearLayout.LayoutParams).topMargin = 0
        background = HoyiUi.shape(activity, R.color.mobile_surface, TaskVisualTokens.CONTROL_RADIUS_DP, R.color.mobile_border)
    }
}

internal fun settingsDivider(activity: Activity, parent: LinearLayout, visual: SettingsVisualStyle? = null) {
    if (visual != null) { visual.divider(parent); return }
    parent.addView(View(activity).apply {
        setBackgroundColor(activity.getColor(R.color.mobile_border))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }, LinearLayout.LayoutParams(-1, HoyiUi.dp(activity, 1)).apply {
        topMargin = HoyiUi.dp(activity, TaskVisualTokens.RELATED_GAP_DP)
        bottomMargin = HoyiUi.dp(activity, TaskVisualTokens.RELATED_GAP_DP)
    })
}
