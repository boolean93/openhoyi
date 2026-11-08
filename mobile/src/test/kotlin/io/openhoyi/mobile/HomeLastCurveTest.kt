package io.openhoyi.mobile

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLastCurveTest {
    private val profile = CurveCatalog.profiles[1]
    private val item = CurveLibraryItem(profile.id, profile.name, "", "", profile)
    private val use = CurveUsage("shot-2", profile.id, 2000)

    private fun resolve(latest: CurveUsage? = use, manualAt: Long? = null,
                        items: List<CurveLibraryItem> = listOf(item), startable: Boolean = true,
                        recentObserved: CurveUsage? = null) =
        HomeLastCurve.from(latest, manualAt, items, recentObserved) { startable }

    @Test fun emptyAndLaterManualCupDoNotClaimARecipe() {
        assertEquals(HomeLastCurve.Empty, resolve(null))
        assertEquals(HomeLastCurve.Manual, resolve(manualAt = 3000))
        assertEquals(HomeLastCurve.Manual, resolve(null, manualAt = 3000))
    }

    @Test fun actualLatestIdIsUsedWithoutFilteringToAnotherStartableRecipe() {
        val ready = resolve() as HomeLastCurve.Ready
        assertEquals(profile.id, ready.item.id)
        assertEquals(CurveStageParameters.preview(item), ready.preview)
        assertEquals(HomeLastCurve.Unavailable(HomeLastCurve.Reason.MISSING),
            resolve(items = emptyList()))
        assertEquals(HomeLastCurve.Unavailable(HomeLastCurve.Reason.NOT_STARTABLE),
            resolve(startable = false))
        assertEquals(HomeLastCurve.DataUnavailable,
            resolve(recentObserved = CurveUsage("shot-3", profile.id, 3000)))
        assertEquals(HomeLastCurve.Manual,
            resolve(manualAt = 4000, recentObserved = CurveUsage("shot-3", profile.id, 3000)))
    }

    @Test fun editableDraftNeverMasqueradesAsHistoricalParameters() {
        val draft = CustomCurveDocument("draft-00000000-0000-4000-8000-000000000001", "Now edited", 92, 0,
            CustomCurveDocument.ControlMode.PRESSURE,
            listOf(CustomCurveDocument.Stage(30, 300)))
        val current = CurveLibraryItem(draft.id, draft.name, "", "", null, customDocument = draft)
        assertEquals(HomeLastCurve.Unavailable(HomeLastCurve.Reason.MUTABLE_DRAFT),
            resolve(latest = CurveUsage("shot-3", draft.id, 4000), items = listOf(current)))
        val fingerprint = CustomCurveFingerprint.of(draft)
        val matching = resolve(latest = CurveUsage("shot-3", draft.id, 4000, fingerprint),
            items = listOf(current)) as HomeLastCurve.Ready
        assertEquals(draft.id, matching.item.id)
        assertEquals(CurveStageParameters.preview(current), matching.preview)
        val edited = current.copy(customDocument = draft.copy(stages = listOf(CustomCurveDocument.Stage(40, 300))))
        assertEquals(HomeLastCurve.Unavailable(HomeLastCurve.Reason.MUTABLE_DRAFT),
            resolve(latest = CurveUsage("shot-3", draft.id, 4000, fingerprint), items = listOf(edited)))
    }
}
