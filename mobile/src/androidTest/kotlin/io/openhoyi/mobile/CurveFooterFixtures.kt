package io.openhoyi.mobile

/** Display-only, never persisted or offered as a verified executable profile. */
internal object CurveFooterFixtures {
    fun fourStage(library: CurveLibrary): CurveLibraryItem {
        val id = "curve-footer-four-stage-readonly-fixture"
        val source = requireNotNull(CurveCatalog.find("capture-1"))
        val profile = source.copy(id = id, parameters = source.parameters.copy(
            segmentCount = 4, target1 = 90, target2 = 65, target3 = 40, target4 = 20))
        return CurveLibraryItem(id, "Four-stage read-only UI fixture", "其它", "", profile).also {
            check(CurveStageParameters.pressureTargets(it).size == 4)
            check(!library.canStart(it)) // The non-catalog ID must retain the original validation gate.
        }
    }
}
