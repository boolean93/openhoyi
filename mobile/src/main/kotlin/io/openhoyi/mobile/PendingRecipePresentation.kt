package io.openhoyi.mobile

/** A next-start label only when the plotted record is not the button's current recipe. */
internal object PendingRecipePresentation {
    fun name(observedCup: Boolean, chartCurveId: String?, selectedId: String?, selectedName: String?,
        validated: Boolean, startVisible: Boolean): String? = selectedName?.takeIf {
        observedCup && chartCurveId != null && selectedId != null && chartCurveId != selectedId &&
            validated && startVisible && it.isNotBlank()
    }
}
