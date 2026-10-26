package io.openhoyi.mobile

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.widget.*

internal class TaskNavigationView(private val activity: Activity, private val productVisualStyle: TaskProductVisualStyle? = null,
    private val destinationProvider: () -> TaskDestination? = { TaskNavigationModel.forPage(activity.javaClass.simpleName) },
    private val onDestination: (TaskDestination) -> Unit = { open(activity, it) }) : LinearLayout(activity) {
    private var rendered: Pair<TaskNavigationMode, Boolean>? = null
    fun refreshSelection() { rendered = null; requestLayout() }
    fun render(mode: TaskNavigationMode, includeTools: Boolean) {
        val next = mode to includeTools
        if (rendered == next) return
        rendered = next
        removeAllViews()
        orientation = if (mode == TaskNavigationMode.RAIL) VERTICAL else HORIZONTAL
        gravity = Gravity.CENTER
        setBackgroundColor(productVisualStyle?.surface ?: activity.getColor(R.color.mobile_surface))
        val styledRail = productVisualStyle != null && mode == TaskNavigationMode.RAIL
        val shellRail = styledRail && activity is MainActivity
        setPadding(if (styledRail) HoyiUi.dp(activity, if (shellRail) 4 else 8) else 0,
            if (styledRail) HoyiUi.dp(activity, if (shellRail) 4 else 16) else 0,
            if (styledRail) HoyiUi.dp(activity, if (shellRail) 4 else 8) else 0,
            if (styledRail) HoyiUi.dp(activity, if (shellRail) 4 else 8) else 0)
        if (styledRail && !shellRail) addView(productVisualStyle!!.text("HOYI", TaskProductVisualStyle.action, "product/nav/brand").apply {
            gravity = Gravity.CENTER; minimumHeight = HoyiUi.dp(activity, 32)
        }, LayoutParams(-1, -2).apply { bottomMargin = HoyiUi.dp(activity, 16) })
        val items = TaskNavigationModel.primary + if (includeTools) TaskNavigationModel.tools else emptyList()
        items.forEach { destination ->
            if (mode == TaskNavigationMode.RAIL && destination == TaskDestination.SCALE) {
                addView(View(activity), LayoutParams(-1, 0, 1f))
            }
            val active = destinationProvider() == destination
            val tint = productVisualStyle?.let { if (active) it.accent else it.muted }
                ?: activity.getColor(if (active) R.color.mobile_accent else R.color.mobile_muted)
            val item = LinearLayout(activity).apply {
                tag = "task-nav:${destination.name}"
                orientation = VERTICAL
                gravity = Gravity.CENTER
                minimumHeight = HoyiUi.dp(activity, if (shellRail) 48 else if (styledRail) 64 else 48)
                minimumWidth = HoyiUi.dp(activity, 48)
                setPadding(0, HoyiUi.dp(activity, if (shellRail) 1 else 4), 0, HoyiUi.dp(activity, if (shellRail) 1 else 4))
                contentDescription = activity.getString(label(destination))
                isSelected = active
                isClickable = true
                isFocusable = true
                if (active) background = productVisualStyle?.shape(productVisualStyle.selected)
                    ?: HoyiUi.shape(activity, R.color.mobile_accent_soft, 10)
                setOnClickListener { onDestination(destination) }
            }
            val glyph: View = if (productVisualStyle != null) HomeVisualIcon(activity, when (destination) {
                TaskDestination.BREW -> "coffee"
                TaskDestination.CURVES -> "curve"
                TaskDestination.HISTORY -> "history"
                TaskDestination.BEANS -> "bean"
                TaskDestination.SCALE -> "scale"
                TaskDestination.SETTINGS -> "settings"
            }, tint) else ImageView(activity).apply {
                setImageResource(icon(destination)); imageTintList = ColorStateList.valueOf(tint)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            item.addView(glyph, LayoutParams(HoyiUi.dp(activity, if (styledRail && !shellRail) 24 else 20),
                HoyiUi.dp(activity, if (styledRail && !shellRail) 24 else 20)))
            item.addView(TextView(activity).apply {
                text = activity.getString(label(destination)); textSize = if (shellRail) 12f else 14f; gravity = Gravity.CENTER
                productVisualStyle?.apply(this, TaskProductVisualStyle.auxiliary)
                gravity = Gravity.CENTER
                setTextColor(tint); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LayoutParams(-1, -2).apply { if (styledRail) topMargin = HoyiUi.dp(activity, if (shellRail) 2 else 8) })
            addView(item, if (mode == TaskNavigationMode.RAIL) LayoutParams(-1, -2).apply {
                if (styledRail && !shellRail && destination != items.first() && destination != TaskDestination.SCALE) topMargin = HoyiUi.dp(activity, 8)
            } else LayoutParams(0, -2, 1f))
        }
    }
    companion object {
        private fun label(destination: TaskDestination) = when (destination) {
            TaskDestination.BREW -> R.string.ui_brew
            TaskDestination.CURVES -> R.string.ui_curves
            TaskDestination.HISTORY -> R.string.ui_history
            TaskDestination.BEANS -> R.string.ui_beans
            TaskDestination.SCALE -> R.string.scale_title
            TaskDestination.SETTINGS -> R.string.ui_settings
        }
        private fun icon(destination: TaskDestination) = when (destination) {
            TaskDestination.BREW -> R.drawable.nav_home
            TaskDestination.CURVES -> R.drawable.nav_curves
            TaskDestination.HISTORY -> R.drawable.nav_history
            TaskDestination.BEANS -> R.drawable.nav_beans
            TaskDestination.SCALE -> R.drawable.nav_scale
            TaskDestination.SETTINGS -> R.drawable.nav_settings
        }
        private fun open(activity: Activity, destination: TaskDestination) {
            val target = when (destination) {
                TaskDestination.BREW -> HomeActivity::class.java
                TaskDestination.CURVES -> CurveActivity::class.java
                TaskDestination.HISTORY -> HistoryActivity::class.java
                TaskDestination.BEANS -> BeanInventoryActivity::class.java
                TaskDestination.SCALE -> ScaleActivity::class.java
                TaskDestination.SETTINGS -> AppSettingsActivity::class.java
            }
            if (activity.javaClass == target || destination == TaskDestination.BREW && activity is ExtractionActivity) return
            activity.startActivity(Intent(activity, target).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        }
        fun createToolsHost(activity: Activity, onDestination: (TaskDestination) -> Unit = { open(activity, it) }): LinearLayout = LinearLayout(activity).apply {
            tag = "task-tools-host"; visibility = GONE
            addView(TextView(activity).apply {
                tag = "task-tools-menu"; text = activity.getString(R.string.task_tools); textSize = 14f
                gravity = Gravity.CENTER; minWidth = HoyiUi.dp(activity, 48); minHeight = HoyiUi.dp(activity, 48)
                setTextColor(activity.getColor(R.color.mobile_accent)); isClickable = true; isFocusable = true
                contentDescription = text
                setOnClickListener {
                    val options = TaskNavigationModel.tools
                    val dialog = AppDialog.choices(activity, activity.getString(R.string.task_tools),
                        options.map { activity.getString(label(it)) }) { index ->
                        options.getOrNull(index)?.let(onDestination)
                    }
                    (activity as? MainActivity)?.trackDialog(dialog)
                }
            }, LayoutParams(-2, -2))
        }
    }
}
