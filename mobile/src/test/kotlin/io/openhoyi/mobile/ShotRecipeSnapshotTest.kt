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
}
