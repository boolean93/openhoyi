package io.openhoyi.mobile

/** A label for the next app start, never a replacement for the observed manual record. */
internal object ManualPendingRecipe {
    fun name(observedCup: Boolean, chartCurveId: String?, resolvedName: String?, validated: Boolean): String? =
        resolvedName?.takeIf { observedCup && chartCurveId == "manual" && validated && it.isNotBlank() }
}
