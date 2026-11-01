package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class CurveStageParametersTest {
    private fun item(profile:CurveProfile)=CurveLibraryItem(profile.id,profile.name,"","",profile)
    @Test fun verifiedPressureTargetsUseOneSharedBarScale() {
        assertEquals(listOf(90,65),CurveStageParameters.pressureTargets(item(CurveCatalog.profiles[0])))
        assertEquals(.5f,CurveStageParameters.pressureFraction(60),0.0001f)
        assertEquals(.75f,CurveStageParameters.pressureFraction(90),0.0001f)
        assertEquals(1f,CurveStageParameters.pressureFraction(120),0.0001f)
    }
    @Test fun flowModesDoNotBecomePressurePreviews() {
        for(profile in CurveCatalog.profiles.drop(1))
            assertTrue(CurveStageParameters.pressureTargets(item(profile)).isEmpty())
        val firstOnly=CurveCatalog.profiles[0].copy(parameters=CurveCatalog.profiles[0].parameters.copy(firstSegmentFlowMode=true))
        assertTrue(CurveStageParameters.pressureTargets(item(firstOnly)).isEmpty())
    }
    @Test fun unsupportedPressureIsNotClampedIntoAnApparentlyValidPreview() {
        val base=CurveCatalog.profiles[0]
        assertTrue(CurveStageParameters.pressureTargets(item(base.copy(parameters=base.parameters.copy(target1=255)))).isEmpty())
        assertTrue(runCatching {CurveStageParameters.pressureFraction(-1)}.isFailure)
        assertTrue(runCatching {CurveStageParameters.pressureFraction(121)}.isFailure)
    }
}
