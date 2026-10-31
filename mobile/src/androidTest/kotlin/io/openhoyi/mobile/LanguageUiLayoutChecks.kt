package io.openhoyi.mobile

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/** Measures production components; never attaches them, clicks a control, or changes device state. */
internal object LanguageUiLayoutChecks {
    fun components(home: MainActivity, language: AppLanguage, dark: Boolean) {
        check(BuildConfig.MOCK_MODE && home.packageName == "io.openhoyi.mobile.mock")
        verifyClippingDetection(home)
        val primary = listOf(TaskDestination.BREW, TaskDestination.CURVES, TaskDestination.HISTORY, TaskDestination.BEANS)
        val tools = listOf(TaskDestination.SCALE, TaskDestination.SETTINGS)
        val labels = mapOf(TaskDestination.BREW to R.string.ui_brew, TaskDestination.CURVES to R.string.ui_curves,
            TaskDestination.HISTORY to R.string.ui_history, TaskDestination.BEANS to R.string.ui_beans,
            TaskDestination.SCALE to R.string.scale_title, TaskDestination.SETTINGS to R.string.ui_settings)
        val expectedDirection = if (language.rightToLeft) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        val normalTextSizes = mutableMapOf<String, Float>()
        val heightDp = 640
        for (widthDp in listOf(320, 360, 600, 700, 1000)) for (fontScale in listOf(1f, 1.3f)) {
            val configuration = Configuration(home.resources.configuration).apply {
                screenWidthDp = widthDp
                screenHeightDp = heightDp
                this.fontScale = fontScale
            }
            val activity = LayoutHost(home.createConfigurationContext(configuration))
            // Independent approved policy: 840dp breakpoint, room for both panes, and usable height.
            val expectedWide = widthDp >= 840 && widthDp >= 320 + 280 + 72 + 24 + 32 && heightDp >= 360
            check(HoyiUi.wide(activity) == expectedWide && activity.resources.configuration.screenWidthDp == widthDp)
            check(activity.resources.configuration.screenHeightDp == heightDp && activity.resources.configuration.fontScale == fontScale)
            val fixture = "${language.tag} dark=$dark width=$widthDp height=$heightDp fontScale=$fontScale host=LayoutHost"
            val root = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutDirection = expectedDirection
            }
            val frame = TaskPageFrame.host(activity, TaskProductVisualStyle(activity),
                { TaskDestination.BREW }, {}).also { root.addView(it, LinearLayout.LayoutParams(-1, -1)) }
            measure(activity, root, widthDp, heightDp)
            check(root.findViewWithTag<View>(TaskPageFrame.TAG) === frame)
            check(frame.width == HoyiUi.dp(activity, widthDp) && frame.height == HoyiUi.dp(activity, heightDp)) {
                "$fixture frame did not receive the exact window size"
            }
            val expectedMode = if (widthDp >= 600) TaskNavigationMode.RAIL else TaskNavigationMode.BOTTOM
            check(frame.mode == expectedMode && frame.navigation.orientation ==
                if (expectedMode == TaskNavigationMode.RAIL) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL) {
                "$fixture incorrect navigation mode/orientation"
            }
            check(root.layoutDirection == expectedDirection && frame.layoutDirection == expectedDirection &&
                frame.navigation.layoutDirection == expectedDirection) { "$fixture incorrect navigation direction" }
            val targets = descendants(frame.navigation).filter { it.tag?.toString()?.startsWith("task-nav:") == true }
            val expectedDestinations = primary + tools
            check(targets.map { it.tag } == expectedDestinations.map { "task-nav:${it.name}" }) {
                "$fixture incorrect primary/tool navigation entries or order"
            }
            check(!frame.toolsInTopBar) { "$fixture root frame hid Scale/Settings behind a tools menu" }
            fun checkScaled(label: TextView, key: String) {
                if (fontScale == 1f) normalTextSizes[key] = label.textSize
                else check(label.textSize > requireNotNull(normalTextSizes[key])) { "$fixture font scaling did not enlarge actual text" }
            }
            targets.forEachIndexed { index, target ->
                val item = target as LinearLayout
                check(item.isSelected == (expectedDestinations[index] == TaskDestination.BREW)) {
                    "$fixture root frame selected the wrong tab: ${item.tag}"
                }
                checkTarget(activity, item, fixture)
                check(item.layoutDirection == expectedDirection) { "$fixture incorrect item direction" }
                val label = descendants(item).filterIsInstance<TextView>().single()
                val expectedLabel = activity.getString(requireNotNull(labels[expectedDestinations[index]]))
                check(label.text.toString().isNotBlank() && label.text.toString() == expectedLabel &&
                    item.contentDescription.toString() == expectedLabel) { "$fixture incorrect navigation label at $index" }
                checkScaled(label, "$widthDp/${expectedDestinations[index]}")
                textFits(label, fixture)
                // Rail scrolling is allowed; the complete label must fit its own touch target.
                inside(item, label, fixture)
            }
            check(descendants(frame).none {
                it.tag == "task-tools-menu" && it.visibility == View.VISIBLE &&
                    (it.parent as? View)?.visibility == View.VISIBLE
            }) { "$fixture root frame exposed a redundant tools menu" }
            val body = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutDirection = expectedDirection
            }
            HoyiUi.header(activity, body, activity.getString(R.string.app_settings_title), activity.getString(R.string.app_settings_subtitle))
            val stop = HoyiUi.button(activity, body, activity.getString(R.string.home_stop), danger = true) { error("Layout fixture must not click controls") }
            measure(activity, body, widthDp - 48)
            check(body.layoutDirection == expectedDirection)
            fun inspect(view: View) {
                // GONE subtrees are not measured; INVISIBLE views still reserve layout space.
                if (view.visibility == View.GONE) return
                if (view is TextView) textFits(view, fixture)
                if (view is ViewGroup) for (index in 0 until view.childCount) inspect(view.getChildAt(index))
            }
            inspect(body)
            check(stop.height >= HoyiUi.dp(activity, 48)) { "$fixture stop target too short" }
        }
    }

    /** Never launched or registered; only supplies the explicit native resource configuration. */
    private class LayoutHost(context: Context) : Activity() {
        init { attachBaseContext(android.view.ContextThemeWrapper(context, R.style.MobileTheme)) }
        // Avoid Activity's task/window initialization: this host owns only detached native views.
        override fun getTheme(): android.content.res.Resources.Theme = baseContext.theme
    }

    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup) {
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) }
    } else emptyList()

    private fun checkTarget(activity: Activity, view: View, fixture: String) {
        check(view.isClickable && view.isFocusable && view.height >= HoyiUi.dp(activity, 48) &&
            view.width >= HoyiUi.dp(activity, 48)) { "$fixture navigation target too small: ${view.tag}" }
    }

    private fun inside(parent: ViewGroup, child: View, fixture: String) {
        val bounds = Rect(0, 0, child.width, child.height)
        parent.offsetDescendantRectToMyCoords(child, bounds)
        check(bounds.left >= 0 && bounds.top >= 0 && bounds.right <= parent.width && bounds.bottom <= parent.height) {
            "$fixture label outside its target: $bounds target=${parent.width}x${parent.height}"
        }
    }

    private fun measure(activity: Activity, view: View, widthDp: Int, heightDp: Int? = null) {
        view.measure(View.MeasureSpec.makeMeasureSpec(HoyiUi.dp(activity, widthDp), View.MeasureSpec.EXACTLY),
            if (heightDp == null) View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            else View.MeasureSpec.makeMeasureSpec(HoyiUi.dp(activity, heightDp), View.MeasureSpec.EXACTLY))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    private fun verifyClippingDetection(activity: Activity) {
        val shortened = TextView(activity).apply {
            text = "STOP STOP STOP"
            textSize = 18f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        measure(activity, shortened, 40)
        check(requireNotNull(shortened.layout).getEllipsisCount(0) > 0) { "Negative fixture was not ellipsized" }
        check(runCatching { textFits(shortened, "intentional ellipsis") }.exceptionOrNull() is IllegalStateException) {
            "Text checker accepted ellipsized content"
        }
        val clipped = TextView(activity).apply { text = "STOP"; textSize = 18f }
        measure(activity, clipped, 200)
        clipped.layout(0, 0, clipped.measuredWidth, 1)
        check(requireNotNull(clipped.layout).height > 1)
        check(runCatching { textFits(clipped, "intentional height clipping") }.exceptionOrNull() is IllegalStateException) {
            "Text checker accepted height-clipped content"
        }
    }

    fun textFits(view: TextView, fixture: String) {
        val layout = requireNotNull(view.layout) { "$fixture missing text layout: ${view.text}" }
        check(layout.lineCount > 0 && layout.getLineEnd(layout.lineCount - 1) == view.text.length) {
            "$fixture incomplete text: ${view.text}"
        }
        for (line in 0 until layout.lineCount) {
            check(layout.getEllipsisCount(line) == 0) { "$fixture ellipsized text: ${view.text}" }
            check(layout.getLineLeft(line) >= -1f && layout.getLineRight(line) <= layout.width + 1f) {
                "$fixture horizontal text overflow: ${view.text} line=$line"
            }
        }
        val available = view.height - view.compoundPaddingTop - view.compoundPaddingBottom
        check(layout.getLineBottom(layout.lineCount - 1) <= available + 1) {
            "$fixture vertical text overflow: ${view.text} layout=${layout.height} available=$available"
        }
    }
}
