package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class ShotRecipeSnapshotTest {
    @Test fun neverUsesPreviousCupOrLaterLibraryEdits() {
        val original = CurveCatalog.profiles.first()
        val snapshot = ShotRecipeSnapshot.capture("cup-a", original)
        val edited = original.copy(name = "changed later", targetHundredthsGram = 9999)
        assertSame(snapshot, snapshot.forTrace("cup-a"))
        assertNull(snapshot.forTrace("cup-b"))
        assertNull(snapshot.forTrace("manual-cup"))
        assertNull(snapshot.forTrace(null))
        assertEquals(original.name, snapshot.name)
        assertEquals(original.temperatureC, snapshot.temperatureC)
        assertEquals(original.targetHundredthsGram, snapshot.targetHundredthsGram)
        assertNotEquals(edited.targetHundredthsGram, snapshot.targetHundredthsGram)
    }
    @Test fun draftFingerprintIsFrozenOnlyForTheActualResolvedProfile() {
        val document = CustomCurveDocument("draft-00000000-0000-4000-8000-000000000001", "Draft", 92, 0,
            CustomCurveDocument.ControlMode.PRESSURE,
            listOf(CustomCurveDocument.Stage(30, 300)))
        val profile = CustomPressureCurveAdapter.profile(document, false)!!
        val snapshot = ShotRecipeSnapshot.capture("cup-a", profile, document)
        assertEquals(CustomCurveFingerprint.of(document), snapshot.draftFingerprint)
        assertNull(ShotRecipeSnapshot.capture("cup-b", profile, document.copy(
            stages = listOf(CustomCurveDocument.Stage(40, 300)))).draftFingerprint)
        assertNull(ShotRecipeSnapshot.capture("cup-c", profile).draftFingerprint)
    }
}
