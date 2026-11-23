package io.openhoyi.mobile

/** One frozen display snapshot; never query a current or most recent cup when sharing. */
internal data class BrewCompletedShare(
    val feedback: BrewFeedbackResult,
    val model: ShotShareCardRenderer.Model,
) {
    companion object {
        fun create(title: String, curve: String?, feedback: BrewFeedbackResult,
                   entry: ShotHistory.Entry?, points: List<ShotPoint>, doseMg: Long? = null): BrewCompletedShare {
            val brewing = points.filter { it.brewing == true }
            // Accepted net scale provenance only; raw scale or water amounts never become output grams.
            val weight = ComparisonDifferences.summarize(entry?.status?.name.orEmpty(), points)
                .get(ComparisonDifferences.Metric.NET_WEIGHT)
            val finalNet = (weight as? ComparisonDifferences.Reading.Value)?.displayUnits?.div(100.0)
            return BrewCompletedShare(feedback, ShotShareCardRenderer.Model(
                title = title,
                curveName = curve,
                durationSeconds = entry?.observedMachineElapsedSeconds?.toDouble() ?: feedback.machineSeconds.toDouble(),
                outputGrams = finalNet,
                doseGrams = doseMg?.takeIf { it > 0 }?.div(1000.0),
                samples = brewing.map { ShotShareCardRenderer.Sample(it.elapsedMs / 1000.0, it.pressureTenthsBar / 10.0) },
            ))
        }
    }
}
