package io.openhoyi.mobile

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import org.json.JSONObject
import org.json.JSONArray
import android.widget.TextView

/** Explicit M6 input. Public type only so ScaleOwner's protected optional argument stays source-compatible. */
class ScaleVisualStyle internal constructor(context: Context) {
    internal val palette = TaskProductVisualStyle(context)
    internal val largeReading = TaskProductVisualStyle.Type(64, 700, 96)
    internal val narrowReading = TaskProductVisualStyle.Type(48, 700, 72)
    internal val unit = TaskProductVisualStyle.Type(22, 400, 34)
    internal val warning = ExtractionHistoryVisualStyle(context)
    internal fun page(activity: ThemedActivity, title: Int, subtitle: Int): LinearLayout {
        val body = palette.page(activity, activity.getString(title), activity.getString(subtitle))
        decorate(activity,body,subtitle)
        return body
    }
    internal fun fragmentPage(activity: ThemedActivity, title: Int, subtitle: Int): Pair<LinearLayout,LinearLayout> {
        val (root,body)=palette.fragmentPage(activity,activity.getString(title),activity.getString(subtitle))
        decorate(activity,body,subtitle)
        return root to body
    }
    private fun decorate(activity:ThemedActivity,body:LinearLayout,subtitle:Int) {
        val header = (body.tag as BeanPageRegions).header
        val words = (0 until header.childCount).map(header::getChildAt).filterIsInstance<LinearLayout>().first()
        val explanation=activity.getString(subtitle)+(if(BuildConfig.MOCK_MODE) " · "+activity.getString(R.string.scale_mock_notice) else "")
        words.addView(palette.text(explanation, TaskProductVisualStyle.auxiliary, secondary = true),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = palette.dp(8) })
        (header.layoutParams as LinearLayout.LayoutParams).bottomMargin = palette.dp(16)
    }
}

/** Same reading objects and baseline group; only an overflowing natural budget requests inline. */
internal class ScaleReadingLayout(context:Context,private val style:TaskProductVisualStyle,
    private val label:View,private val quantity:View,private val freshness:View):ViewGroup(context) {
    val productLayoutEvidence=JSONObject()
    private var requested=false
    private var inline=false
    private var contentHeight=0
    init {tag="product/scale/reading";addView(label);addView(quantity);addView(freshness)}
    override fun generateDefaultLayoutParams()=LayoutParams(-2,-2)
    private fun force(view:View) {
        view.forceLayout();if(view is ViewGroup)(0 until view.childCount).forEach {force(view.getChildAt(it))}
    }
    fun requestInline(value:Boolean) {if(requested!=value) {requested=value;force(this)}}
    override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int) {
        val width=MeasureSpec.getSize(widthMeasureSpec)
        val available=(width-paddingLeft-paddingRight).coerceAtLeast(0)
        val free=MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED)
        force(label);force(quantity);label.measure(free,free);quantity.measure(free,free)
        val labelWidth=label.measuredWidth;val quantityWidth=quantity.measuredWidth
        val gap=style.dp(8)
        val fits=labelWidth.toLong()+gap+quantityWidth<=available.toLong()
        inline=requested && fits
        force(label);force(quantity);force(freshness)
        label.measure(MeasureSpec.makeMeasureSpec(if(inline)labelWidth else available,MeasureSpec.EXACTLY),free)
        quantity.measure(MeasureSpec.makeMeasureSpec(if(inline)quantityWidth else available,MeasureSpec.EXACTLY),free)
        freshness.measure(MeasureSpec.makeMeasureSpec(available,MeasureSpec.EXACTLY),free)
        val rowHeight=if(inline)maxOf(label.measuredHeight,quantity.measuredHeight) else label.measuredHeight+gap+quantity.measuredHeight
        contentHeight=rowHeight+gap+freshness.measuredHeight
        productLayoutEvidence.put("availableWidthPx",available).put("labelNaturalWidthPx",labelWidth)
            .put("quantityNaturalWidthPx",quantityWidth).put("inlineRequested",requested).put("inlineWidthFits",fits)
            .put("inlineSelected",inline).put("freshnessNaturalHeightPx",freshness.measuredHeight)
            .put("naturalHeightPx",contentHeight+paddingTop+paddingBottom)
        setMeasuredDimension(resolveSize(width,widthMeasureSpec),resolveSize(contentHeight+paddingTop+paddingBottom,heightMeasureSpec))
    }
    override fun onLayout(changed:Boolean,l:Int,t:Int,r:Int,b:Int) {
        val gap=style.dp(8)
        val top=paddingTop+((height-paddingTop-paddingBottom-contentHeight)/2).coerceAtLeast(0)
        val rowHeight=if(inline)maxOf(label.measuredHeight,quantity.measuredHeight) else label.measuredHeight+gap+quantity.measuredHeight
        fun place(view:View,start:Int,y:Int) {
            val x=if(layoutDirection==LAYOUT_DIRECTION_RTL)width-paddingRight-start-view.measuredWidth else paddingLeft+start
            view.layout(x,y,x+view.measuredWidth,y+view.measuredHeight)
        }
        val groupWidth=label.measuredWidth+gap+quantity.measuredWidth
        val start=if(inline)((width-paddingLeft-paddingRight-groupWidth)/2).coerceAtLeast(0) else 0
        place(label,start,top+if(inline)(rowHeight-label.measuredHeight)/2 else 0)
        place(quantity,if(inline)start+label.measuredWidth+gap else 0,
            top+if(inline)(rowHeight-quantity.measuredHeight)/2 else label.measuredHeight+gap)
        place(freshness,0,top+rowHeight+gap)
        productLayoutEvidence.put("actualHeightPx",height).put("contentHeightPx",contentHeight)
            .put("heightSatisfiable",contentHeight<=height-paddingTop-paddingBottom)
    }
}

/** Width-first permanent panes. Only the support content scrolls; the scale's controls do not. */
internal class ScaleVisualWorkspace(context: Context, private val style: ScaleVisualStyle) : ViewGroup(context) {
    val primary = style.palette.column("product/scale/primary")
    val secondary = style.palette.column("product/scale/support-body")
    val supportScroll = ScrollView(context).apply { tag = "product/scale/support-scroll"; addView(secondary) }
    val productLayoutEvidence = JSONObject()
    var onPrimarySpaceAvailable: ((Int, Int) -> Int)? = null
    private var split = false
    private var primaryWidth = 0
    private var primaryHeight = 0
    private val rule = Paint().apply { color = this@ScaleVisualWorkspace.style.palette.border; strokeWidth = this@ScaleVisualWorkspace.style.palette.dp(1).toFloat() }
    init { tag = "product/scale/workspace"; addView(primary); addView(supportScroll); setWillNotDraw(false) }
    override fun generateDefaultLayoutParams() = LayoutParams(-1, -1)
    private fun invalidateMeasured(view: View) {
        view.forceLayout()
        if (view is ViewGroup) (0 until view.childCount).forEach { invalidateMeasured(view.getChildAt(it)) }
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val gap = style.palette.dp(24)
        split = width >= style.palette.dp(240 + 280 + 24)
        val secondaryWidth = if (split) (width * .3f).toInt().coerceIn(style.palette.dp(280), style.palette.dp(320)) else width
        primaryWidth = if (split) width - gap - secondaryWidth else width
        secondary.measure(MeasureSpec.makeMeasureSpec(secondaryWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        val secondaryNatural = secondary.measuredHeight
        val minimumSources = JSONArray()
        var supportControlMinimum = style.palette.dp(48)
        fun collectMinimum(view: View) {
            if (view.visibility != View.VISIBLE) return
            if (view.isClickable) {
                supportControlMinimum = maxOf(supportControlMinimum, view.measuredHeight)
                minimumSources.put(JSONObject().put("tag", view.tag?.toString() ?: JSONObject.NULL)
                    .put("class", view.javaClass.name).put("naturalHeightPx", view.measuredHeight)
                    .put("text", (view as? TextView)?.text?.toString()?.take(96) ?: JSONObject.NULL))
            }
            if (view is ViewGroup) (0 until view.childCount).forEach { collectMinimum(view.getChildAt(it)) }
        }
        collectMinimum(secondary)
        val minimumSupport = supportControlMinimum + supportScroll.paddingTop + supportScroll.paddingBottom
        var supportHeight = if (split) height else minOf(secondaryNatural, maxOf(style.palette.dp(180), height / 3))
        primaryHeight = if (split) height else (height - gap - supportHeight).coerceAtLeast(0)
        val required = onPrimarySpaceAvailable?.invoke(primaryWidth, primaryHeight) ?: 0
        if (!split && required > primaryHeight) {
            primaryHeight = minOf(required, (height - gap - minimumSupport).coerceAtLeast(0))
            supportHeight = (height - primaryHeight - gap).coerceAtLeast(0)
        }
        invalidateMeasured(primary); invalidateMeasured(supportScroll)
        primary.measure(MeasureSpec.makeMeasureSpec(primaryWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(primaryHeight, MeasureSpec.EXACTLY))
        supportScroll.measure(MeasureSpec.makeMeasureSpec(secondaryWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(supportHeight, MeasureSpec.EXACTLY))
        productLayoutEvidence.put("availableWidthPx", width).put("availableHeightPx", height).put("widthFirstSplit", split)
            .put("primaryWidthPx", primaryWidth).put("primaryHeightPx", primaryHeight).put("primaryMinimumPx", required)
            .put("primaryBudgetSatisfiable", required <= primaryHeight).put("supportNaturalPx", secondaryNatural)
            .put("supportViewportPx", supportHeight).put("supportWidthPx", secondaryWidth)
            .put("supportMinimumPx", minimumSupport).put("supportMinimumSources", minimumSources)
            .put("supportBudgetSatisfiable", supportHeight >= minimumSupport)
            .put("combinedMinimumSatisfiable", if (split) required <= height && minimumSupport <= height
                else required + gap + minimumSupport <= height)
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec))
    }
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val gap = style.palette.dp(24)
        if (split) {
            val primaryLeft = if (layoutDirection == LAYOUT_DIRECTION_RTL) width - primaryWidth else 0
            val supportLeft = if (layoutDirection == LAYOUT_DIRECTION_RTL) 0 else primaryWidth + gap
            primary.layout(primaryLeft, 0, primaryLeft + primaryWidth, primaryHeight)
            supportScroll.layout(supportLeft, 0, supportLeft + supportScroll.measuredWidth, supportScroll.measuredHeight)
        } else {
            primary.layout(0, 0, width, primaryHeight)
            supportScroll.layout(0, primaryHeight + gap, width, primaryHeight + gap + supportScroll.measuredHeight)
        }
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (split) {
            val x = if (layoutDirection == LAYOUT_DIRECTION_RTL) width - primaryWidth - style.palette.dp(12) else primaryWidth + style.palette.dp(12)
            canvas.drawLine(x.toFloat(), 0f, x.toFloat(), height.toFloat(), rule)
        }
    }
}

/** The status text and complete native action group wrap only when their natural widths do not fit. */
internal class ScaleVisualTwoGroups(context:Context,private val style:TaskProductVisualStyle,
    private val words:View,private val actions:ViewGroup):ViewGroup(context) {
    val productLayoutEvidence=JSONObject()
    private var inline=false
    init {addView(words);addView(actions)}
    override fun generateDefaultLayoutParams()=LayoutParams(-2,-2)
    private fun invalidateMeasured(view:View) {
        view.forceLayout();if(view is ViewGroup)(0 until view.childCount).forEach {invalidateMeasured(view.getChildAt(it))}
    }
    override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int) {
        val available=(MeasureSpec.getSize(widthMeasureSpec)-paddingLeft-paddingRight).coerceAtLeast(0)
        val free=MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED)
        invalidateMeasured(words);words.measure(free,free)
        val wordsNatural=words.measuredWidth
        var actionsNatural=0
        for(index in 0 until actions.childCount) {
            val child=actions.getChildAt(index);invalidateMeasured(child);child.measure(free,free)
            actionsNatural+=child.measuredWidth+if(index>0)style.dp(16) else 0
        }
        inline=wordsNatural+style.dp(16)+actionsNatural<=available
        invalidateMeasured(words);invalidateMeasured(actions)
        actions.measure(MeasureSpec.makeMeasureSpec(if(inline)actionsNatural else available,MeasureSpec.EXACTLY),free)
        words.measure(MeasureSpec.makeMeasureSpec(if(inline)(available-style.dp(16)-actionsNatural).coerceAtLeast(0) else available,MeasureSpec.EXACTLY),free)
        val required=if(inline)maxOf(words.measuredHeight,actions.measuredHeight) else words.measuredHeight+style.dp(8)+actions.measuredHeight
        productLayoutEvidence.put("availableWidthPx",available).put("wordsNaturalWidthPx",wordsNatural)
            .put("actionsNaturalWidthPx",actionsNatural).put("inline",inline).put("naturalHeightPx",required+paddingTop+paddingBottom)
        setMeasuredDimension(resolveSize(available+paddingLeft+paddingRight,widthMeasureSpec),resolveSize(required+paddingTop+paddingBottom,heightMeasureSpec))
    }
    override fun onLayout(changed:Boolean,l:Int,t:Int,r:Int,b:Int) {
        fun place(view:View,x:Int,y:Int) {
            val left=if(layoutDirection==LAYOUT_DIRECTION_RTL)width-paddingRight-x-view.measuredWidth else paddingLeft+x
            view.layout(left,paddingTop+y,left+view.measuredWidth,paddingTop+y+view.measuredHeight)
        }
        place(words,0,if(inline)(height-paddingTop-paddingBottom-words.measuredHeight)/2 else 0)
        place(actions,if(inline)words.measuredWidth+style.dp(16) else 0,
            if(inline)(height-paddingTop-paddingBottom-actions.measuredHeight)/2 else words.measuredHeight+style.dp(8))
    }
}
