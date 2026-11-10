package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class LiveEndpointLabelGeometryTest {
    private val range=ChartRange(0f,10f)
    private fun series(metric:ChartMetric=ChartMetric.PRESSURE,points:List<ChartValue> = listOf(ChartValue(1000,4f),ChartValue(1250,5f)))=
        MetricChartSeries(metric,listOf(points),emptyList(),range,2f)
    private fun data(s:MetricChartSeries=series(),pause:Set<Long> = emptySet())=FourMetricChartData(listOf(s),1250,10000,emptyList(),emptyList(),pause)
    private fun head(s:MetricChartSeries,d:FourMetricChartData=data(s),reveal:Long?=null,unavailable:Set<ChartMetric> = emptySet())=
        LiveEndpointLabelGeometry.HeadSampler().resolve(s,d,reveal,unavailable)
    @Test fun latestTrueCaptionMovesContinuouslyWithTwoPointLinearHead() {
        val s=series();val sampler=LiveEndpointLabelGeometry.HeadSampler()
        val first=sampler.resolve(s,data(s),1125,emptySet())!!
        assertSame(s.current[0].last(),first.caption)
        assertEquals(1125,first.position.elapsedMs)
        assertEquals(4.5f,first.position.value,0.0001f)
        val next=sampler.resolve(s,data(s),1141,emptySet())!!
        assertEquals(4.564f,next.position.value,0.0001f)
        assertSame(first.caption,next.caption)
        assertNull(head(s,reveal=999))
    }
    @Test fun exactCubicHeadUsesRendererControlTimesWhileCaptionRemainsObserved() {
        val s=series(points=listOf(ChartValue(0,1f),ChartValue(751,6f),ChartValue(1250,8f)))
        val controls=LiveChartGeometry.cubics(s.current.single())
        val c=controls[1]
        fun v(a:Double,b:Double,c:Double,d:Double,t:Double):Double {val u=1-t;return u*u*u*a+3*u*u*t*b+3*u*t*t*c+t*t*t*d}
        val at=v(c.start.elapsedMs.toDouble(),c.control1.elapsedMs.toDouble(),c.control2.elapsedMs.toDouble(),c.end.elapsedMs.toDouble(),.5).toLong()
        val h=head(s,reveal=at)!!
        assertEquals(8f,h.caption.value)
        assertEquals(v(c.start.value.toDouble(),c.control1.value.toDouble(),c.control2.value.toDouble(),c.end.value.toDouble(),.5).toFloat(),h.position.value,.01f)
    }
    @Test fun missingStaleReferenceAndNewSegmentBeforeRevealNeverReviveOldValue() {
        val s=series()
        assertNull(head(s,unavailable=setOf(ChartMetric.PRESSURE)))
        val stale=series(points=listOf(ChartValue(1000,4f)))
        assertNull(head(stale))
        assertNull(head(s.copy(current=emptyList(),reference=s.current)))
        assertNull(head(series(points=listOf(ChartValue(1250,Float.NaN)))))
        val gap=s.copy(current=listOf(listOf(ChartValue(500,2f)),listOf(ChartValue(1250,5f))))
        assertNull(head(gap,reveal=1000))
        assertEquals(5f,head(gap,reveal=1250)!!.caption.value)
    }
    @Test fun pressurePauseAndResumeFollowRendererStepWithoutOldPauseLabel() {
        val s=series(points=listOf(ChartValue(1000,4f),ChartValue(1100,0f),ChartValue(1250,5f)))
        assertNull(head(s,data(s,setOf(1250))))
        assertNull(head(s,data(s,setOf(1100)),1150))
        assertEquals(5f,head(s,data(s,setOf(1100)),1250)!!.position.value)
        val water=series(ChartMetric.WATER)
        assertNotNull(head(water,data(water,setOf(1250))))
    }
    @Test fun rememberedSideAndOffsetMoveAtAnchorCadenceWithoutRelayoutJumps() {
        val plot=LiveEndpointLabelGeometry.Box(0f,0f,400f,300f)
        val a=LiveEndpointLabelGeometry.Anchor(280f,180f)
        val b=LiveEndpointLabelGeometry.place(a,90f,24f,plot,emptyList(),6f,true,-30f)!!
        val next=LiveEndpointLabelGeometry.place(a.copy(x=282f,y=179f),90f,24f,plot,emptyList(),6f,true,b.top-a.y)!!
        assertEquals(2f,next.left-b.left,.001f)
        assertEquals(-1f,next.top-b.top,.001f)
    }
    @Test fun endpointsFollowActualTimeWindowAndAnimatedAxisRanges() {
        val s=series();val d=data(s).copy(timeStartMs=1000,timeEndMs=11000)
        val plot=LiveEndpointLabelGeometry.Box(100f,10f,400f,210f);val point=ChartValue(2000,5f)
        assertEquals(LiveEndpointLabelGeometry.Anchor(130f,110f),LiveEndpointLabelGeometry.anchor(point,range,d,plot))
        assertEquals(LiveEndpointLabelGeometry.Anchor(160f,160f),LiveEndpointLabelGeometry.anchor(point,ChartRange(0f,20f),d.copy(timeEndMs=6000),plot))
        assertNull(LiveEndpointLabelGeometry.anchor(point,ChartRange(0f,0f),d,plot))
        assertNull(LiveEndpointLabelGeometry.anchor(point,ChartRange(Float.NaN,20f),d,plot))
        assertNull(LiveEndpointLabelGeometry.anchor(point,range,d,LiveEndpointLabelGeometry.Box(0f,0f,0f,0f)))
        assertNull(LiveEndpointLabelGeometry.anchor(point,range,d.copy(timeStartMs=3000),plot))
    }
    @Test fun unnecessaryCollisionOffsetRelaxesContinuouslyWithoutChangingAnchorOrCaption() {
        var offset=-160f
        val first=LiveEndpointLabelGeometry.relaxedOffset(offset,-30f,16)
        assertTrue(first>offset && first< -30f)
        repeat(30) {offset=LiveEndpointLabelGeometry.relaxedOffset(offset,-30f,16)}
        assertEquals(-30f,offset,2.5f)
        assertEquals(-30f,LiveEndpointLabelGeometry.relaxedOffset(null,-30f,16),0f)
        assertEquals(-160f,LiveEndpointLabelGeometry.relaxedOffset(-160f,-30f,0),0f)
    }
    @Test fun narrowPlotDoesNotAlternateSidesWhenNeitherSideFits() {
        val plot=LiveEndpointLabelGeometry.Box(0f,0f,140f,200f)
        val anchor=LiveEndpointLabelGeometry.Anchor(70f,100f)
        var side:Boolean?=false
        repeat(60) {side=LiveEndpointLabelGeometry.leftSide(anchor,100f,plot,6f,side);assertEquals(false,side)}
        assertEquals(true,LiveEndpointLabelGeometry.leftSide(anchor.copy(x=135f),100f,plot,6f,false))
    }
    @Test fun fourthNarrowLabelUsesExactFreeGapMissedByWholeRowCandidates() {
        val plot=LiveEndpointLabelGeometry.Box(0f,0f,140f,310f)
        val occupied=listOf(LiveEndpointLabelGeometry.Box(34f,0f,134f,70f),
            LiveEndpointLabelGeometry.Box(34f,230f,134f,310f),LiveEndpointLabelGeometry.Box(34f,80f,134f,150f))
        val anchor=LiveEndpointLabelGeometry.Anchor(140f,180f)
        val box=LiveEndpointLabelGeometry.place(anchor,100f,70f,plot,occupied,6f,true)!!
        assertEquals(153f,box.top,0f)
        assertTrue(occupied.none {box.overlaps(it,3f)})
        assertTrue(box.left>=plot.left && box.right<=plot.right && box.bottom<=plot.bottom)
    }
    @Test fun fourCoincidentEndpointsRemainAnchoredWithSeparateContainedLabelsAtEveryEdge() {
        val plot=LiveEndpointLabelGeometry.Box(0f,0f,400f,300f)
        for(anchor in listOf(LiveEndpointLabelGeometry.Anchor(400f,0f),LiveEndpointLabelGeometry.Anchor(400f,300f),LiveEndpointLabelGeometry.Anchor(0f,150f),LiveEndpointLabelGeometry.Anchor(200f,150f))) {
            val placed=ArrayList<LiveEndpointLabelGeometry.Box>()
            repeat(4) {
                val box=LiveEndpointLabelGeometry.place(anchor,90f,24f,plot,placed,6f)!!
                assertTrue(box.left>=plot.left && box.right<=plot.right && box.top>=plot.top && box.bottom<=plot.bottom)
                assertTrue(placed.none {box.overlaps(it,3f)});placed.add(box)
            }
        }
    }
}
