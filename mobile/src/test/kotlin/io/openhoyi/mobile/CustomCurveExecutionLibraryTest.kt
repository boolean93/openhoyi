package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class CustomCurveExecutionLibraryTest {
    private fun doc()=CustomCurveDocument("draft-00000000-0000-4000-8000-000000000001","My pressure",92,1800,
        CustomCurveDocument.ControlMode.PRESSURE,listOf(CustomCurveDocument.Stage(30,150),CustomCurveDocument.Stage(90,400)))
    private fun item(doc:CustomCurveDocument)=CurveLibraryItem(doc.id,doc.name,"local-draft","",null,customDocument=doc)
    @Test fun explicitlyEnabledStoredPressureRecipeResolvesToLocallyValidatedParameters() {
        val original=item(doc());val library=CurveLibrary(emptyList(),draftsProvider={listOf(original)},enableCustomPressureExecution=true)
        assertTrue(library.canStart(original))
        for(scale in listOf(false,true)) {
            val profile=library.resolve(original.id,scale)
            assertNotNull(profile);assertTrue(library.validated(profile!!))
            assertEquals(CustomPressureCurveAdapter.profile(doc(),scale),profile)
        }
        // Parameter frames do not become a growing unchecked byte whitelist.
        assertTrue(library.legacyVerifiedStartFrames.isEmpty())
    }
    @Test fun editOrRemovalRevokesPreviouslyResolvedProfile() {
        var current=listOf(item(doc()))
        val library=CurveLibrary(emptyList(),draftsProvider={current},enableCustomPressureExecution=true)
        val old=library.resolve(doc().id,false);assertNotNull(old)
        val oldItem=current.single()
        current=listOf(item(doc().copy(temperatureC=93)))
        assertFalse(library.validated(old!!));assertFalse(library.canStart(oldItem))
        assertTrue(library.canStart(current.single()))
        current=emptyList();assertFalse(library.validated(old));assertNull(library.resolve(doc().id,false))
    }
    @Test fun unstoredOrUnsupportedRecipeCannotAcquireExecutionPermission() {
        val library=CurveLibrary(emptyList(),enableCustomPressureExecution=true)
        assertFalse(library.canStart(item(doc())))
        for(document in listOf(doc().copy(controlMode=CustomCurveDocument.ControlMode.FLOW_RAW),
            doc().copy(stages=listOf(CustomCurveDocument.Stage(90,351))),doc().copy(targetHundredthsGram=1801))) {
            val candidate=item(document)
            val stored=CurveLibrary(emptyList(),draftsProvider={listOf(candidate)},enableCustomPressureExecution=true)
            assertFalse(stored.canStart(candidate));assertNull(stored.resolve(document.id,false))
        }
    }
}
