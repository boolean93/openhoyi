package io.openhoyi.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomePresetPreviewTest {
    private val sharedScale = CurveStageParameters.SharedScale(20f, 10f)

    @Test fun missingOrUnrenderableSlotHasNoChart() {
        assertNull(HomePresetPreview.from(null, sharedScale))
        val raw = CustomCurveDocument("draft-raw", "raw", 92, 0,
            CustomCurveDocument.ControlMode.FLOW_RAW,
            listOf(CustomCurveDocument.Stage(30, 300)))
        val item = CurveLibraryItem(raw.id, raw.name, "", "", null, customDocument = raw)
        assertNull(HomePresetPreview.from(item, sharedScale))
    }

    @Test fun populatedSlotUsesLibraryPreviewAndSharedScale() {
        val profile = CurveCatalog.profiles[1]
        val item = CurveLibraryItem(profile.id, profile.name, "", "", profile)
        val result = HomePresetPreview.from(item, sharedScale)!!
        assertEquals(CurveStageParameters.preview(item), result.preview)
        assertEquals(sharedScale, result.scale)
    }
}
