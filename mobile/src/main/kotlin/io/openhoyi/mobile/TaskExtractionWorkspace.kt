package io.openhoyi.mobile

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView

/** The fixed shot action belongs to the supporting column, not the chart's height budget. */
internal class TaskExtractionWorkspace(context: Context, private val visual: ExtractionHistoryVisualStyle? = null) : LinearLayout(context) {
    private val portraitChart=visual!=null && resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_PORTRAIT
    private val chartScroll=ScrollView(context).apply {tag="product/extraction/page-scroll";isFillViewport=false}
    val primaryBody = LinearLayout(context).apply { orientation = VERTICAL }
    val secondaryBody = LinearLayout(context).apply { orientation = VERTICAL }
    private val tools = ScrollView(context).apply { tag = if (visual != null) "product/extraction/support-scroll" else null; addView(secondaryBody) }
    private val supporting = LinearLayout(context).apply {
        orientation = VERTICAL
        addView(tools, LayoutParams(-1, 0, 1f))
    }
    private var footer: View? = null
    var onPrimarySpaceAvailable: ((width: Int, height: Int) -> Int)? = null

    init {
        tag = "task-workspace"
        if (visual != null) { primaryBody.tag = "product/extraction/main"; secondaryBody.tag = "product/extraction/support" }
        if(portraitChart) {
            primaryBody.setPadding(0,0,0,(8*resources.displayMetrics.density).toInt())
            chartScroll.addView(primaryBody,android.widget.FrameLayout.LayoutParams(-1,-2))
            addView(chartScroll,LayoutParams(-1,0,1f))
        } else addView(primaryBody)
        addView(supporting)
    }
    fun setFooter(view: View) {
        check(footer == null)
        footer = view
        supporting.addView(view, LayoutParams(-1, -2))
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val width = (MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight).coerceAtLeast(0)
        val height = (MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom).coerceAtLeast(0)
        // The live four-metric chart needs the entire time-axis width. Supporting tools scroll below it.
        val wide = visual == null && TaskLayoutPolicy.decide(resources.configuration.screenWidthDp.coerceAtLeast(1),
            (height / density).toInt()).twoPaneAllowed && width >= dp(624)
        val supportWidth = if (wide) (width * .30f).toInt().coerceIn(dp(280), dp(320)) else width
        footer?.measure(MeasureSpec.makeMeasureSpec(supportWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        val footerHeight = footer?.measuredHeight ?: 0
        val primaryWidth = if (wide) (width - supportWidth - dp(24)).coerceAtLeast(0) else width
        val freeHeight = (height - if (wide) 0 else footerHeight).coerceAtLeast(0)
        if(portraitChart) {
            tools.visibility=View.GONE
            orientation=VERTICAL
            val chartHeight=onPrimarySpaceAvailable?.invoke(width,freeHeight) ?: dp(320)
            primaryBody.getChildAt(0)?.let {chart ->
                val params=chart.layoutParams as LayoutParams
                if(params.height!=chartHeight || params.weight!=0f) chart.layoutParams=LayoutParams(-1,chartHeight)
            }
            // Scroll viewport excludes the CTA and parent bottom navigation; neither overlays content.
            supporting.gravity=android.view.Gravity.BOTTOM
            if(supporting.layoutParams.width!=-1 || supporting.layoutParams.height!=footerHeight || (supporting.layoutParams as LayoutParams).weight!=0f)
                supporting.layoutParams=LayoutParams(-1,footerHeight)
            super.onMeasure(widthMeasureSpec,heightMeasureSpec)
            return
        }
        val toolsMinimum = if (visual != null || wide) 0 else minOf(dp(48), freeHeight)
        val primaryRequired = onPrimarySpaceAvailable?.invoke(primaryWidth, freeHeight - toolsMinimum) ?: 0
        val toolsHeight = if (visual != null) 0 else
            minOf(dp(160), freeHeight / 3, (freeHeight - primaryRequired).coerceAtLeast(0)).coerceAtLeast(toolsMinimum)
        // The visual chart exposes these actions through its fixed tools button.
        // A zero-height legacy tools viewport is unused, not a visible scroll area.
        tools.visibility = if (if (wide) height > footerHeight else toolsHeight > 0) View.VISIBLE else View.GONE
        orientation = if (wide) HORIZONTAL else VERTICAL
        fun arrange(view: View, wanted: LayoutParams) {
            val current = view.layoutParams as LayoutParams
            if (current.width != wanted.width || current.height != wanted.height || current.weight != wanted.weight ||
                current.marginStart != wanted.marginStart) view.layoutParams = wanted
        }
        arrange(primaryBody, if (wide) LayoutParams(0, -1, 1f) else LayoutParams(-1, 0, 1f))
        supporting.gravity=android.view.Gravity.TOP
        arrange(supporting, if (wide) LayoutParams(supportWidth, -1).apply { marginStart = dp(24) }
            else LayoutParams(-1, toolsHeight + footerHeight))
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
