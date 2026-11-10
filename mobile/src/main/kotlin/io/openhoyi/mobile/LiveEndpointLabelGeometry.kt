package io.openhoyi.mobile

/** Captions are observations; only the transient Canvas head follows the existing reveal geometry. */
internal object LiveEndpointLabelGeometry {
    data class Box(val left:Float,val top:Float,val right:Float,val bottom:Float) {
        val width get()=right-left
        val height get()=bottom-top
        fun overlaps(other:Box,gap:Float)=left<other.right+gap && right+gap>other.left && top<other.bottom+gap && bottom+gap>other.top
    }
    data class Anchor(val x:Float,val y:Float)
    data class Head(val caption:ChartValue,val position:ChartValue)
    /** Rebuild control points only when observations replace a segment, never on axis/reveal frames. */
    class HeadSampler {
        private var source:List<ChartValue>?=null
        private var controls:List<LiveChartGeometry.Cubic> = emptyList()
        fun clear() { source=null;controls=emptyList() }
        fun resolve(series:MetricChartSeries,data:FourMetricChartData,reveal:Long?,unavailable:Set<ChartMetric>):Head? {
            if(series.metric !in liveMetrics || series.metric in unavailable) return null
            val observed=data.observedThroughMs ?: return null
            val segment=series.current.lastOrNull {it.isNotEmpty()} ?: return null
            val newest=segment.last()
            if(newest.elapsedMs!=observed || !newest.value.isFinite() ||
                series.metric==ChartMetric.PRESSURE && newest.elapsedMs in data.pauseBaselineMs) return null
            val through=minOf(observed,reveal ?: observed,data.timeEndMs)
            // A new evidence segment cannot attach its new value to a pre-gap curve.
            if(through<segment.first().elapsedMs || through<data.timeStartMs) return null
            val index=segment.indexOfLast {it.elapsedMs<=through}
            val start=segment[index]
            if(!start.value.isFinite() || series.metric==ChartMetric.PRESSURE && start.elapsedMs in data.pauseBaselineMs) return null
            if(index==segment.lastIndex) return Head(newest,start)
            val end=segment[index+1]
            if(!end.value.isFinite()) return null
            val value=if(series.metric==ChartMetric.PRESSURE &&
                ((start.elapsedMs in data.pauseBaselineMs)!=(end.elapsedMs in data.pauseBaselineMs))) start.value
            else if(segment.size==2) {
                val fraction=(through-start.elapsedMs).toDouble()/(end.elapsedMs-start.elapsedMs)
                (start.value+(end.value-start.value)*fraction).toFloat()
            } else {
                if(source !== segment) {source=segment;controls=LiveChartGeometry.cubics(segment)}
                val c=controls[index]
                fun bezier(a:Double,b:Double,c:Double,d:Double,t:Double):Double {
                    val u=1-t;return u*u*u*a+3*u*u*t*b+3*u*t*t*c+t*t*t*d
                }
                // Renderer control times use rounded third-ms positions: invert its exact monotone X.
                var low=0.0;var high=1.0
                repeat(20) {
                    val mid=(low+high)/2
                    val x=bezier(c.start.elapsedMs.toDouble(),c.control1.elapsedMs.toDouble(),c.control2.elapsedMs.toDouble(),c.end.elapsedMs.toDouble(),mid)
                    if(x<through) low=mid else high=mid
                }
                bezier(c.start.value.toDouble(),c.control1.value.toDouble(),c.control2.value.toDouble(),c.end.value.toDouble(),(low+high)/2).toFloat()
            }
            // This point is display geometry only. Caption always remains the unmodified newest sample.
            return Head(newest,ChartValue(through,value))
        }
    }
    fun anchor(point:ChartValue,range:ChartRange,data:FourMetricChartData,plot:Box):Anchor? {
        val span=range.high-range.low
        if(plot.width<=0 || plot.height<=0 || !span.isFinite() || span<=0 || !range.low.isFinite() ||
            !point.value.isFinite() || data.timeEndMs<=data.timeStartMs) return null
        val fx=(point.elapsedMs-data.timeStartMs).toDouble()/(data.timeEndMs-data.timeStartMs)
        val fy=(point.value-range.low)/span
        if(fx !in 0.0..1.0 || fy !in 0f..1f) return null
        val x=plot.left+plot.width*fx.toFloat();val y=plot.bottom-plot.height*fy
        return if(x.isFinite() && y.isFinite()) Anchor(x,y) else null
    }
    /** Only the annotation offset relaxes; the rendered curve head and observed caption never lag. */
    fun relaxedOffset(previous:Float?,target:Float,frameMs:Long):Float {
        if(previous==null || !previous.isFinite()) return target
        val alpha=(1.0-kotlin.math.exp(-frameMs.coerceIn(0L,64L)/120.0)).toFloat()
        return previous+(target-previous)*alpha
    }
    fun leftSide(anchor:Anchor,width:Float,plot:Box,gap:Float,previous:Boolean?):Boolean {
        val canLeft=anchor.x-gap-width>=plot.left
        val canRight=anchor.x+gap+width<=plot.right
        return when {
            previous==true && !canLeft && canRight -> false
            previous==false && !canRight && canLeft -> true
            previous!=null -> previous
            canRight -> false
            canLeft -> true
            else -> anchor.x-plot.left>plot.right-anchor.x
        }
    }
    /** All offsets remain local to the real endpoint; only edge/collision cases move them farther. */
    fun place(anchor:Anchor,width:Float,height:Float,plot:Box,occupied:List<Box>,gap:Float,preferLeft:Boolean?=null,preferredOffsetY:Float?=null):Box? {
        if(!anchor.x.isFinite() || !anchor.y.isFinite() || !width.isFinite() || !height.isFinite() ||
            width<=0 || height<=0 || width>plot.width || height>plot.height) return null
        val left=preferLeft ?: (anchor.x+gap+width>plot.right)
        val wantedX=if(!left) anchor.x+gap else anchor.x-gap-width
        val x=wantedX.coerceIn(plot.left,plot.right-width)
        preferredOffsetY?.let { offset ->
            val y=(anchor.y+offset).coerceIn(plot.top,plot.bottom-height)
            val box=Box(x,y,x+width,y+height)
            if(occupied.none {box.overlaps(it,gap/2)}) return box
        }
        for(attempt in 0..15) {
            val row=attempt/2
            val wantedY=if(attempt%2==0) anchor.y-gap-height-row*(height+gap) else anchor.y+gap+row*(height+gap)
            val y=wantedY.coerceIn(plot.top,plot.bottom-height)
            val box=Box(x,y,x+width,y+height)
            if(occupied.none {box.overlaps(it,gap/2)}) return box
        }
        // Fixed row increments can miss a narrow legal gap. Test exact free-interval boundaries.
        val ideal=anchor.y-gap-height
        val boundaries=ArrayList<Float>(2+occupied.size*2)
        boundaries.add(plot.top);boundaries.add(plot.bottom-height)
        for(other in occupied) {
            boundaries.add(other.top-height-gap/2);boundaries.add(other.bottom+gap/2)
        }
        boundaries.sortBy {kotlin.math.abs(it-ideal)}
        for(top in boundaries) {
            val y=top.coerceIn(plot.top,plot.bottom-height)
            val box=Box(x,y,x+width,y+height)
            if(occupied.none {box.overlaps(it,gap/2)}) return box
        }
        return null
    }
}
