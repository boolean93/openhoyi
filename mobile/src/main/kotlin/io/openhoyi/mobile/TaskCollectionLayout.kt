package io.openhoyi.mobile

import android.content.Context
import android.view.View
import android.widget.LinearLayout

/** Stable list/detail regions. Measurement never changes selection, search or preferences. */
internal class TaskCollectionLayout(context: Context, private val browserFraction: Float = .4f,
    private val retainTwoPaneDuringIme: Boolean = false) : LinearLayout(context) {
    val browserBody = LinearLayout(context).apply { orientation = VERTICAL }
    val detailBody = LinearLayout(context).apply { orientation = VERTICAL }
    private var twoPane: Boolean? = null
    var onPresentationChanged: ((twoPane: Boolean, detailVisible: Boolean) -> Unit)? = null
    var onDetailSpaceAvailable: ((width: Int, height: Int) -> Unit)? = null
    var onImeVisibilityChanged: ((Boolean) -> Unit)? = null
    private var measuredImeVisible: Boolean? = null
    private var imeCallbackPosted = false
    var detailRequested = false
        set(value) {
            if (field == value) return
            field = value
            updatePresentation()
            requestLayout()
        }

    init {
        tag = "task-collection"
        addView(browserBody)
        addView(detailBody)
    }

    private fun updatePresentation() {
        val split = twoPane == true
        browserBody.visibility = if (split || !detailRequested) View.VISIBLE else View.GONE
        detailBody.visibility = if (split || detailRequested) View.VISIBLE else View.GONE
        onPresentationChanged?.invoke(split, detailBody.visibility == View.VISIBLE)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val width = (MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight).coerceAtLeast(0)
        val height = (MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom).coerceAtLeast(0)
        val imeVisible = retainTwoPaneDuringIme && android.os.Build.VERSION.SDK_INT >= 30 &&
            rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime()) == true
        if (retainTwoPaneDuringIme && measuredImeVisible != imeVisible) {
            measuredImeVisible = imeVisible
            if (!imeCallbackPosted) {
                imeCallbackPosted = true
                post {
                    imeCallbackPosted = false
                    measuredImeVisible?.let { onImeVisibilityChanged?.invoke(it) }
                }
            }
        }
        val policyHeight = if (imeVisible) maxOf((height / density).toInt(), 360) else (height / density).toInt()
        val split = TaskLayoutPolicy.decide(resources.configuration.screenWidthDp.coerceAtLeast(1),
            policyHeight).twoPaneAllowed && width >= dp(320 + 280 + 24)
        val browserWidth = if (split) (width * browserFraction).toInt().coerceIn(dp(320), width - dp(280 + 24)) else width
        val changed = twoPane != split
        twoPane = split
        orientation = if (split) HORIZONTAL else VERTICAL
        if (split) {
            if (browserBody.layoutParams.width != browserWidth) {
                browserBody.layoutParams = LayoutParams(browserWidth, -1)
                detailBody.layoutParams = LayoutParams(0, -1, 1f).apply { marginStart = dp(24) }
            }
        } else if (changed) {
            browserBody.layoutParams = LayoutParams(-1, -1)
            detailBody.layoutParams = LayoutParams(-1, -1)
        }
        if (changed) updatePresentation()
        onDetailSpaceAvailable?.invoke(if (split) width - browserWidth - dp(24) else width, height)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
