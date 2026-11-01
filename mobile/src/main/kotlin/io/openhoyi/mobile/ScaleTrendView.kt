package io.openhoyi.mobile

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

/** Physical grams against monotonic reception time; segments never bridge missing data. */
internal class ScaleTrendView(context: Context, visual: ScaleVisualStyle? = null) : View(context) {
    private val productRenderer=visual?.let { ScaleTrendProductRenderer(context,it) }
    internal val visualEvidence get()=productRenderer?.evidence ?: org.json.JSONArray()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = TaskTypography.face(context) }
    private var points = emptyList<ScaleTrendWindow.Point>()
    private var nowMs = 0L
    fun present(values: List<ScaleTrendWindow.Point>, now: Long) {
        points = values.filter { it.atMs <= now && now - it.atMs <= 10_000 }
        nowMs = now
        contentDescription = if (points.isEmpty()) context.getString(R.string.scale_trend_empty)
        else context.getString(R.string.scale_trend_summary, number(points.last().hundredthsGram / 100.0),
            number((now - points.last().atMs) / 1000.0))
        invalidate()
    }
    private fun dp(value: Float) = value * resources.displayMetrics.density
    private fun number(value: Double) = String.format(resources.configuration.locales[0], "%.1f", value)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        productRenderer?.let {it.draw(canvas,width,height,points,nowMs);return}
        paint.textSize = 12 * resources.displayMetrics.scaledDensity
        paint.style = Paint.Style.FILL
        val low = floor(minOf(0.0, points.minOfOrNull { it.hundredthsGram / 100.0 } ?: 0.0))
        val high = ceil(maxOf(low + 1.0, points.maxOfOrNull { it.hundredthsGram / 100.0 } ?: 1.0))
        val labels = (0..2).map { number(low + (high - low) * it / 2) }
        val fm = paint.fontMetrics
        val line = fm.descent - fm.ascent
        val left = labels.maxOf { paint.measureText(it) } + dp(12f)
        val right = width - dp(16f)
        val top = line + dp(8f)
        val bottom = height - line - dp(12f)
        if (right <= left || bottom <= top) return
        paint.color = context.getColor(R.color.mobile_muted)
        canvas.drawText("g", left, -fm.ascent, paint)
        labels.forEachIndexed { index, label ->
            val y = bottom - (bottom - top) * index / 2
            paint.color = context.getColor(R.color.chart_grid)
            paint.strokeWidth = dp(1f)
            canvas.drawLine(left, y, right, y, paint)
            paint.color = context.getColor(R.color.mobile_muted)
            canvas.drawText(label, left - dp(8f) - paint.measureText(label), y - (fm.ascent + fm.descent) / 2, paint)
        }
        listOf(-10, -5, 0).forEachIndexed { index, seconds ->
            val x = left + (right - left) * index / 2
            val text = String.format(Locale.ROOT, "%d s", seconds)
            val textX = (x - paint.measureText(text) / 2).coerceIn(0f, (width - paint.measureText(text)).coerceAtLeast(0f))
            canvas.drawText(text, textX, height - fm.descent, paint)
        }
        if (points.isEmpty()) return
        fun x(point: ScaleTrendWindow.Point) = left + (right - left) * (1 - (nowMs - point.atMs) / 10000f)
        fun y(point: ScaleTrendWindow.Point) = bottom - (bottom - top) * ((point.hundredthsGram / 100.0 - low) / (high - low)).toFloat()
        val save = canvas.save()
        canvas.clipRect(left, top - dp(3f), right + dp(3f), bottom + dp(3f))
        paint.color = context.getColor(R.color.mobile_accent)
        paint.strokeWidth = dp(2f)
        points.forEachIndexed { index, point ->
            val previous = points.getOrNull(index - 1)
            if (previous != null && previous.segment == point.segment)
                canvas.drawLine(x(previous), y(previous), x(point), y(point), paint)
            if (previous == null || previous.segment != point.segment || index == points.lastIndex)
                canvas.drawCircle(x(point), y(point), dp(2.5f), paint)
        }
        canvas.restoreToCount(save)
    }
}

/** M6 opt-in renderer. The default component path above is unchanged. */
private class ScaleTrendProductRenderer(private val context:Context,private val visual:ScaleVisualStyle) {
    private val style=visual.palette
    private val labels=Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface=TaskTypography.face(context,400);textSize=this@ScaleTrendProductRenderer.style.sp(14);isFakeBoldText=false;textScaleX=1f;textSkewX=0f
    }
    private val grid=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=this@ScaleTrendProductRenderer.style.border;strokeWidth=dp(1f);strokeCap=Paint.Cap.BUTT}
    private val data=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=this@ScaleTrendProductRenderer.style.accent;strokeWidth=dp(2f);strokeCap=Paint.Cap.BUTT}
    private val fill=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=this@ScaleTrendProductRenderer.style.accent;alpha=kotlin.math.round(255*.12f).toInt()}
    var evidence=org.json.JSONArray();private set
    private fun dp(value:Float)=value*context.resources.displayMetrics.density
    private fun rect(value:android.graphics.RectF)=org.json.JSONArray(listOf(value.left,value.top,value.right,value.bottom))
    private fun argb(value:Int)=String.format(Locale.ROOT,"#%08X",value)
    private data class Label(val text:String,val x:Float,val baseline:Float,val role:String)
    fun draw(canvas:Canvas,width:Int,height:Int,points:List<ScaleTrendWindow.Point>,now:Long) {
        evidence=org.json.JSONArray()
        val fm=labels.fontMetrics;val line=fm.descent-fm.ascent
        val low=floor(minOf(0.0,points.minOfOrNull {it.hundredthsGram/100.0} ?: 0.0))
        val high=ceil(maxOf(low+1,points.maxOfOrNull {it.hundredthsGram/100.0} ?: 1.0))
        fun number(value:Double)=String.format(context.resources.configuration.locales[0],"%.1f",value)
        val ticks=(0..2).map {number(low+(high-low)*it/2)}
        val box=android.graphics.RectF(ticks.maxOf {labels.measureText(it)}+dp(16f),line+dp(8f),width-dp(16f),height-line-dp(16f))
        if(box.width()<=0 || box.height()<=0)return
        val text=mutableListOf(Label("g",box.left,-fm.ascent,"axis-unit"))
        ticks.forEachIndexed {index,value->text+=Label(value,box.left-dp(8f)-labels.measureText(value),
            box.bottom-box.height()*index/2-(fm.ascent+fm.descent)/2,"weight-tick")}
        listOf(-10,-5,0).forEachIndexed {index,seconds->
            val value="$seconds s";val x=box.left+box.width()*index/2
            text+=Label(value,(x-labels.measureText(value)/2).coerceIn(0f,(width-labels.measureText(value)).coerceAtLeast(0f)),height-fm.descent,"time-tick")
        }
        val protections=text.map {label->
            val ink=android.graphics.Rect();labels.getTextBounds(label.text,0,label.text.length,ink)
            android.graphics.RectF(label.x+ink.left,label.baseline+ink.top,label.x+ink.right,label.baseline+ink.bottom)
                .also {bounds->evidence.put(org.json.JSONObject().put("operation","textProtection").put("text",label.text)
                    .put("glyphBoundsPx",rect(bounds)).put("protectedBoundsPx",rect(android.graphics.RectF(bounds).apply {inset(-dp(2f),-dp(2f))})))}
                .apply {inset(-dp(2f),-dp(2f))}
        }
        evidence.put(org.json.JSONObject().put("operation","plot").put("boundsPx",rect(box)).put("pointCount",points.size)
            .put("nowMs",now).put("lowGrams",low).put("highGrams",high))
        fun gridLine(id:String,x1:Float,y1:Float,x2:Float,y2:Float) {
            val horizontal=y1==y2
            var spans=listOf((if(horizontal)x1 else y1) to (if(horizontal)x2 else y2))
            protections.forEach {protection->
                val p=android.graphics.RectF(protection).apply {inset(-grid.strokeWidth/2,-grid.strokeWidth/2)}
                if(if(horizontal)y1 in p.top..p.bottom else x1 in p.left..p.right) {
                    val lowEdge=if(horizontal)p.left else p.top;val highEdge=if(horizontal)p.right else p.bottom
                    spans=spans.flatMap {(start,end)->if(highEdge<=start || lowEdge>=end)listOf(start to end)
                        else listOfNotNull((start to minOf(end,lowEdge)).takeIf {it.first<it.second},(maxOf(start,highEdge) to end).takeIf {it.first<it.second})}
                }
            }
            val visible=org.json.JSONArray()
            spans.forEach {(start,end)->val a=if(horizontal)listOf(start,y1,end,y2) else listOf(x1,start,x2,end)
                canvas.drawLine(a[0],a[1],a[2],a[3],grid);visible.put(org.json.JSONArray(a))}
            evidence.put(org.json.JSONObject().put("operation","gridSegmentPlan").put("gridId",id)
                .put("originalEndpointsPx",org.json.JSONArray(listOf(x1,y1,x2,y2))).put("visibleSegmentsPx",visible).put("strokeWidthPx",grid.strokeWidth).put("colorArgb",argb(grid.color)))
        }
        (0..2).forEach {i->val y=box.bottom-box.height()*i/2;gridLine("h$i",box.left,y,box.right,y)
            val x=box.left+box.width()*i/2;gridLine("v$i",x,box.top,x,box.bottom)}
        labels.color=style.muted
        text.forEach {label->
            canvas.drawText(label.text,label.x,label.baseline,labels)
            val ink=android.graphics.Rect();labels.getTextBounds(label.text,0,label.text.length,ink)
            evidence.put(org.json.JSONObject().put("operation","text").put("text",label.text).put("role",label.role)
                .put("originPx",org.json.JSONArray(listOf(label.x,label.baseline))).put("textSizePx",labels.textSize)
                .put("paintTypefaceIdentity",System.identityHashCode(labels.typeface)).put("paintTypefaceWeight",if(android.os.Build.VERSION.SDK_INT>=28)labels.typeface.weight else org.json.JSONObject.NULL)
                .put("paintTypefaceItalic",labels.typeface.isItalic).put("fakeBold",labels.isFakeBoldText).put("textScaleX",labels.textScaleX).put("textSkewX",labels.textSkewX)
                .put("fontMetricsPx",org.json.JSONArray(listOf(fm.top,fm.ascent,fm.descent,fm.bottom,fm.leading)))
                .put("fontAssetSha256",if(context.resources.configuration.locales[0].language=="zh")TaskProductVisualStyle.FONT_SHA else org.json.JSONObject.NULL)
                .put("glyphBoundsPx",rect(android.graphics.RectF(label.x+ink.left,label.baseline+ink.top,label.x+ink.right,label.baseline+ink.bottom)))
                .put("argb",argb(labels.color)))
        }
        if(points.isEmpty())return
        fun x(p:ScaleTrendWindow.Point)=box.left+box.width()*(1-(now-p.atMs)/10000f)
        fun y(p:ScaleTrendWindow.Point)=box.bottom-box.height()*((p.hundredthsGram/100.0-low)/(high-low)).toFloat()
        val save=canvas.save();canvas.clipRect(box.left-dp(3f),box.top-dp(3f),box.right+dp(3f),box.bottom+dp(3f))
        points.groupBy {it.segment}.forEach {(segment,part)->
            if(part.size>=2) {
                val path=android.graphics.Path().apply {moveTo(x(part.first()),box.bottom);part.forEach {lineTo(x(it),y(it))};lineTo(x(part.last()),box.bottom);close()}
                canvas.drawPath(path,fill)
                evidence.put(org.json.JSONObject().put("operation","segmentFill").put("segment",segment).put("alphaByte",fill.alpha)
                    .put("colorArgb",argb(fill.color)).put("configuredAlphaFraction",.12).put("pointsPx",org.json.JSONArray(part.map {org.json.JSONArray(listOf(x(it),y(it)))})))
            }
            part.forEachIndexed {index,p->
                if(index>0) {val prior=part[index-1];canvas.drawLine(x(prior),y(prior),x(p),y(p),data)
                    evidence.put(org.json.JSONObject().put("operation","dataLine").put("segment",segment).put("strokeWidthPx",data.strokeWidth).put("colorArgb",argb(data.color))
                        .put("endpointsPx",org.json.JSONArray(listOf(x(prior),y(prior),x(p),y(p)))))}
                if(index==0 || index==part.lastIndex) {
                    canvas.drawCircle(x(p),y(p),dp(2.5f),data)
                    evidence.put(org.json.JSONObject().put("operation","endpointCircle").put("segment",segment)
                        .put("centerPx",org.json.JSONArray(listOf(x(p),y(p)))).put("radiusPx",dp(2.5f))
                        .put("colorArgb",argb(data.color)).put("paintStyle",data.style.name))
                }
            }
        }
        canvas.restoreToCount(save)
    }
}
