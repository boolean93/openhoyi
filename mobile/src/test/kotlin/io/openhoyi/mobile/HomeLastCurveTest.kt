package io.openhoyi.mobile

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLastCurveTest {
    private val profile = CurveCatalog.profiles[1]
    private val item = CurveLibraryItem(profile.id, profile.name, "", "", profile)
    private val use = CurveUsage("shot-2", profile.id, 2000)

    private fun resolve(latest: CurveUsage? = use,
                        items: List<CurveLibraryItem> = listOf(item), startable: Boolean = true,
                        recentObserved: CurveUsage? = null) =
        HomeLastCurve.from(latest, items, recentObserved) { startable }

    @Test fun manualShotsKeepLatestCurveAcrossRestartAndRemainInHistory() {
        val uses = object : CurveUsageLedger.Storage {
            var value = ""
            override fun read() = value
            override fun write(value: String) { this.value = value }
        }
        val shots = object : ShotHistory.Storage {
            var value = ""
            override fun read() = value
            override fun write(value: String) { this.value = value }
        }
        val ledger = CurveUsageLedger(uses)
        var time = 1000L
        var id = 0
        val history = ShotHistory(shots, now = { time }, newId = { "shot-${++id}" },
            onObservedUse = { ledger.record(it.id, it.curveId, it.startedAtMs) })
        fun brew(curveId: String) {
            history.begin(curveId, slot = if (curveId == "manual") 6 else 7)
            history.observeRunning()
            history.transition(io.openhoyi.session.ExtractionState.ENDED_OBSERVED, null, null)
            time += 1000
        }
        fun home() = resolve(ledger.latest(), recentObserved = history.entries
            .firstOrNull { it.curveId != "manual" && it.observedRunning }
            ?.let { CurveUsage(it.id, it.curveId, it.startedAtMs) })

        brew("manual")
        assertEquals(HomeLastCurve.Empty, home())
        brew(profile.id)
        val ready = home() as HomeLastCurve.Ready
        brew("manual")
        brew("manual")
        assertEquals(ready, home())
        assertEquals(ready, resolve(CurveUsageLedger(uses).latest()))
        assertEquals(3, ShotHistory(shots, now = { time }).entries.count { it.curveId == "manual" })
        brew(profile.id)
        assertEquals(5000L, ledger.latest()?.startedAtMs)
        assertEquals(ready, home())
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
        val changedLimit = current.copy(customDocument = draft.copy(totalWaterLimitMl = 40))
        assertEquals(HomeLastCurve.Unavailable(HomeLastCurve.Reason.MUTABLE_DRAFT),
            resolve(latest = CurveUsage("shot-3", draft.id, 4000, fingerprint), items = listOf(changedLimit)))
        val edited = current.copy(customDocument = draft.copy(stages = listOf(CustomCurveDocument.Stage(40, 300))))
        assertEquals(HomeLastCurve.Unavailable(HomeLastCurve.Reason.MUTABLE_DRAFT),
            resolve(latest = CurveUsage("shot-3", draft.id, 4000, fingerprint), items = listOf(edited)))
    }
}
