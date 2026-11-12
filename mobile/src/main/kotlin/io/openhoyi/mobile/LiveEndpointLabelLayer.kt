package io.openhoyi.mobile

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import java.util.Locale

/** A decorative overlay on the unchanged renderer; the existing legend owns accessible readings. */
internal class LiveEndpointLabelLayer(context:Context,private val colorFor:(ChartMetric)->Int) {
    private val density=context.resources.displayMetrics.density
    private val text=Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface=TaskTypography.face(context,500);textSize=14*context.resources.displayMetrics.scaledDensity
    }
    private val background=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=(TaskProductVisualStyle(context).surface and 0x00ffffff) or 0xf2000000.toInt()}
    private val line=Paint(Paint.ANTI_ALIAS_FLAG).apply {style=Paint.Style.STROKE;strokeWidth=density}
    private val dot=Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect=RectF()
    private class Label(val metric:ChartMetric) {
        var value=Float.NaN;var caption="";var width=0f
        var anchor=LiveEndpointLabelGeometry.Anchor(0f,0f)
        val sampler=LiveEndpointLabelGeometry.HeadSampler()
        var leftSide:Boolean?=null
        var offsetY:Float?=null
        fun resetPlacement() {leftSide=null;offsetY=null}
    }
    private val cache=liveMetrics.map {Label(it)}
    private val visible=ArrayList<Label>(4)
    private val occupied=ArrayList<LiveEndpointLabelGeometry.Box>(4)
    private var observedThrough:Long?=null
    private var lastFrameAt:Long?=null
    private var trace:String?=null
    fun draw(canvas:Canvas,bounds:RectF,data:FourMetricChartData,reveal:Long?,unavailable:Set<ChartMetric>,traceKey:String?):Boolean {
        if(traceKey!=trace) {cache.forEach {it.resetPlacement();it.sampler.clear()};trace=traceKey}
        if(bounds.isEmpty) return false
        if(data.observedThroughMs==null || observedThrough?.let {data.observedThroughMs!!<it}==true) {
            cache.forEach {it.resetPlacement();it.sampler.clear()}
        }
        observedThrough=data.observedThroughMs
        val now=android.os.SystemClock.uptimeMillis()
        val frameMs=lastFrameAt?.let {now-it} ?: 16L
        lastFrameAt=now
        val inset=2*density
        val plot=LiveEndpointLabelGeometry.Box(bounds.left+inset,bounds.top+inset,bounds.right-inset,bounds.bottom-inset)
        // Map against the renderer's original box; inset is only for annotation containment.
        val mapping=LiveEndpointLabelGeometry.Box(bounds.left,bounds.top,bounds.right,bounds.bottom)
        visible.clear();occupied.clear()
        for(label in cache) {
            val series=data.series.firstOrNull {it.metric==label.metric}
            if(series==null) {label.resetPlacement();label.sampler.clear();continue}
            val head=label.sampler.resolve(series,data,reveal,unavailable)
            if(head==null) {label.resetPlacement();continue}
            val point=head.caption
            val anchor=LiveEndpointLabelGeometry.anchor(head.position,series.range,data,mapping) ?: continue
            label.anchor=anchor
            if(label.value!=point.value) {
                label.value=point.value
                val unit=if(label.metric==ChartMetric.WATER) "mL" else label.metric.unit
                label.caption=String.format(Locale.ROOT,"%.1f %s",point.value,unit)
                label.width=text.measureText(label.caption)+12*density
            }
            visible.add(label)
        }
        // Stable metric priority prevents crossing curves from swapping the entire label stack.
        val fm=text.fontMetrics;val height=fm.descent-fm.ascent+8*density
        var needsNextFrame=false
        canvas.save();canvas.clipRect(bounds)
        for(label in visible) {
            val gap=6*density
            // Change sides only when the opposite side genuinely fits. In a narrow plot both
            // sides can fail; retain the previous side and clamp instead of flipping each frame.
            label.leftSide=LiveEndpointLabelGeometry.leftSide(label.anchor,label.width,plot,gap,label.leftSide)
            val target=LiveEndpointLabelGeometry.place(label.anchor,label.width,height,plot,occupied,gap,label.leftSide) ?: continue
            val offset=LiveEndpointLabelGeometry.relaxedOffset(label.offsetY,target.top-label.anchor.y,frameMs)
            val box=LiveEndpointLabelGeometry.place(label.anchor,label.width,height,plot,occupied,gap,label.leftSide,offset) ?: continue
            label.offsetY=box.top-label.anchor.y
            if(kotlin.math.abs(box.top-target.top)>.5f) needsNextFrame=true
            occupied.add(box);rect.set(box.left,box.top,box.right,box.bottom)
            val color=colorFor(label.metric)
            line.color=color;line.alpha=150
            canvas.drawLine(label.anchor.x,label.anchor.y,label.anchor.x.coerceIn(box.left,box.right),label.anchor.y.coerceIn(box.top,box.bottom),line)
            canvas.drawRoundRect(rect,4*density,4*density,background)
            text.color=color;canvas.drawText(label.caption,box.left+6*density,box.top+4*density-fm.ascent,text)
            dot.color=color;canvas.drawCircle(label.anchor.x,label.anchor.y,2.5f*density,dot)
        }
        canvas.restore()
        return needsNextFrame
    }
}
