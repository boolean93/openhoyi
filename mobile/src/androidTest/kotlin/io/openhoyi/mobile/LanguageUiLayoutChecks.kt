package io.openhoyi.mobile

import android.app.Activity
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/** Measures production components; never attaches them, clicks a control, or changes device state. */
internal object LanguageUiLayoutChecks {
    fun components(home: HomeActivity, language: AppLanguage, dark: Boolean) {
        check(BuildConfig.MOCK_MODE && home.packageName == "io.openhoyi.mobile.mock")
        check(!HoyiUi.wide(home)) { "Compact layout fixture requires a compact Activity configuration" }
        verifyClippingDetection(home)
        val pages = listOf(HomeActivity::class.java, CurveActivity::class.java, ExtractionActivity::class.java,
            HistoryActivity::class.java, MachineSettingsActivity::class.java)
        val labels = listOf(R.string.ui_home, R.string.ui_curves, R.string.ui_extraction, R.string.ui_history, R.string.ui_settings)
        val expectedDirection = if (language.rightToLeft) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        for (widthDp in listOf(320, 360, 600)) for (page in pages) {
            val fixture = "${language.tag} dark=$dark width=$widthDp selected=${page.simpleName}"
            val root = LinearLayout(home).apply {
                orientation = LinearLayout.VERTICAL
                layoutDirection = expectedDirection
            }
            HoyiUi.navigation(home, root, page)
            measure(home, root, widthDp)
            check(root.layoutDirection == expectedDirection)
            val bar = root.getChildAt(0) as ViewGroup
            check(bar.childCount == 5) { "$fixture missing navigation entries" }
            for (index in 0 until bar.childCount) {
                val item = bar.getChildAt(index) as ViewGroup
                check(item.isClickable && item.isFocusable && item.height >= HoyiUi.dp(home, 48) &&
                    item.width >= HoyiUi.dp(home, 48)) { "$fixture navigation target too small" }
                val label = (0 until item.childCount).map { item.getChildAt(it) }.filterIsInstance<TextView>().single()
                check(label.text.toString().isNotBlank() && label.text.toString() == home.getString(labels[index])) {
                    "$fixture incorrect navigation label at $index"
                }
                textFits(label, fixture)
                val bounds = Rect(0, 0, label.width, label.height)
                bar.offsetDescendantRectToMyCoords(label, bounds)
                check(bounds.left >= 0 && bounds.top >= 0 && bounds.right <= bar.width && bounds.bottom <= bar.height) {
                    "$fixture navigation label outside bar: ${label.text} $bounds bar=${bar.width}x${bar.height}"
                }
            }
            val body = LinearLayout(home).apply {
                orientation = LinearLayout.VERTICAL
                layoutDirection = expectedDirection
            }
            HoyiUi.header(home, body, home.getString(R.string.machine_settings_title), home.getString(R.string.machine_settings_subtitle))
            val stop = HoyiUi.button(home, body, home.getString(R.string.home_stop), danger = true) { error("Layout fixture must not click controls") }
            measure(home, body, widthDp - 48)
            check(body.layoutDirection == expectedDirection)
            fun inspect(view: View) {
                if (view is TextView) textFits(view, fixture)
                if (view is ViewGroup) for (index in 0 until view.childCount) inspect(view.getChildAt(index))
            }
            inspect(body)
            check(stop.height >= HoyiUi.dp(home, 48)) { "$fixture stop target too short" }
        }
    }

    private fun measure(activity: Activity, view: View, widthDp: Int) {
        view.measure(View.MeasureSpec.makeMeasureSpec(HoyiUi.dp(activity, widthDp), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
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
