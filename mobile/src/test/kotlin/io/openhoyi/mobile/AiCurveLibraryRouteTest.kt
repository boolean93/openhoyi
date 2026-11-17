package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class AiCurveLibraryRouteTest {
    private fun doc() = CustomCurveDocument("draft-00000000-0000-0000-0000-000000000143", "Same name", 93, 3600,
        CustomCurveDocument.ControlMode.PRESSURE, listOf(CustomCurveDocument.Stage(30, 100), CustomCurveDocument.Stage(90, 200)))
    private fun item(d: CustomCurveDocument) = CurveLibraryItem(d.id, d.name, "", "", null, customDocument = d)
    private fun proposal(before: CustomCurveDocument?) = AiCurveCard.proposal(AiCurveWriteCoordinator.Proposal(
        "not-a-permit", AiCurveWriteCoordinator.Epoch("test", 0), if(before == null) "create" else "replace",
        before?.encode(), doc().copy(name="Unsaved new name").encode(), 0))
    @Test fun exactIdAndFingerprintAreRecheckedAtBothEnds() {
        val original = item(doc()); val card = AiCurveCard.recommendation(original)
        assertEquals(card, AiCurveLibraryRoute.resolve(card) { original })
        assertEquals(original, AiCurveLibraryRoute.receive(card.encode().toString()) { original })
        assertNull(AiCurveLibraryRoute.receive(card.encode().toString()) { null })
        val changed = item(doc().copy(temperatureC=94))
        assertNull(AiCurveLibraryRoute.resolve(card) { changed })
        assertNull(AiCurveLibraryRoute.receive(card.encode().toString()) { changed })
        assertNull(AiCurveLibraryRoute.receive("{}") { original })
        assertNull(AiCurveLibraryRoute.receive("x".repeat(AiCurveCard.MAX_ENCODED_BYTES+1)) { original })
    }
    @Test fun editsBrowseOriginalOnlyAndUnsavedCreationHasNoLibraryTarget() {
        val edit=proposal(doc());val original=item(doc())
        val reference=AiCurveLibraryRoute.resolve(edit) { original }!!
        assertEquals(doc(), reference.document)
        assertNull(AiCurveLibraryRoute.receive(edit.encode().toString()) { original })
        assertNull(AiCurveLibraryRoute.resolve(edit) { item(doc().copy(id="draft-00000000-0000-0000-0000-000000000144")) })
        assertNull(AiCurveLibraryRoute.reference(proposal(null)))
    }
    @Test fun navigationExitOnlyAcceptsExplicitNonCurveDestinations() {
        assertNull(AiCurveLibraryRoute.exitDestination(null, true))
        assertNull(AiCurveLibraryRoute.exitDestination("not-a-destination", true))
        assertNull(AiCurveLibraryRoute.exitDestination(TaskDestination.CURVES.name, true))
        for (destination in TaskDestination.entries) {
            assertNull(AiCurveLibraryRoute.exitDestination(destination.name, false))
            if (destination != TaskDestination.CURVES) {
                assertEquals(destination, AiCurveLibraryRoute.exitDestination(destination.name, true))
            }
        }
    }

}
