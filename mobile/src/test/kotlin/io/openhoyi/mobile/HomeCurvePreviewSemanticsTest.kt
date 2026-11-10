package io.openhoyi.mobile

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class HomeCurvePreviewSemanticsTest {
    private val factory=FactoryCurveCatalog.load(File("src/main/assets/factory_curves_v3.tsv").inputStream())
    private fun item(curve:FactoryCurve)=CurveLibraryItem(curve.id,curve.name,"","",null,curve)
    @Test fun realFactoryPressureAndMixedStagesKeepTargetsUnitsAndIndependentScales() {
        val items=factory.take(5).map(::item)
        val scale=CurveStageParameters.SharedScale.from(items)
        assertEquals(CurveStageParameters.SharedScale(12f,6f),scale)
        val expected=listOf(listOf(90,60),listOf(30,90,60),listOf(20,30),listOf(50,90,60),listOf(50,20))
        items.forEachIndexed { index,item ->
            val home=HomePresetPreview.from(item,scale)!!
            assertEquals(expected[index],home.preview.stages.map {it.target})
            assertEquals(scale,home.scale)
            home.preview.stages.forEachIndexed { stage,entry ->
                val flow=index in listOf(2,4) && stage>0
                assertEquals(if(flow) CurveStageParameters.Unit.FLOW_ML_PER_SECOND else CurveStageParameters.Unit.PRESSURE_BAR,entry.unit)
            }
        }
    }
    @Test fun pureFlowIsSupportedParameterFixtureNotClaimedFactoryRecipe() {
        val profile=CurveCatalog.profiles[1].let {it.copy(parameters=it.parameters.copy(firstSegmentFlowMode=true))}
        val preview=HomePresetPreview.from(CurveLibraryItem(profile.id,profile.name,"","",profile),CurveStageParameters.SharedScale(12f,6f))!!
        assertTrue(preview.preview.stages.all {it.unit==CurveStageParameters.Unit.FLOW_ML_PER_SECOND})
        assertEquals(listOf(20,38,20),preview.preview.stages.map {it.target})
    }
    @Test fun unknownOrMissingHasNoChart() {
        assertNull(HomePresetPreview.from(null,CurveStageParameters.SharedScale(12f,6f)))
        val raw=CustomCurveDocument("draft-raw","raw",92,0,CustomCurveDocument.ControlMode.FLOW_RAW,listOf(CustomCurveDocument.Stage(30,300)))
        assertNull(HomePresetPreview.from(CurveLibraryItem(raw.id,raw.name,"","",null,customDocument=raw),CurveStageParameters.SharedScale(12f,6f)))
    }
}
