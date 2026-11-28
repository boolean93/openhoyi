package io.openhoyi.mobile

/** Draft IDs only; recommendations never become selections. Records are in journal order. */
internal data class HistoryCupSelection(val firstId: String? = null, val secondId: String? = null) {
    fun reconcile(records: List<BrewJournal.Observation>): HistoryCupSelection {
        if (records.none { it.id == firstId }) return HistoryCupSelection()
        if (firstId == secondId || records.none { it.id == secondId }) return copy(secondId = null)
        return this
    }

    fun choose(id: String, records: List<BrewJournal.Observation>): HistoryCupSelection {
        val current = reconcile(records)
        if (id == current.firstId || records.none { it.id == id }) return current
        return if (current.firstId == null) HistoryCupSelection(firstId = id) else current.copy(secondId = id)
    }

    fun confirmedPair(records: List<BrewJournal.Observation>): Pair<String, String>? {
        val current = reconcile(records)
        return (current.firstId ?: return null) to (current.secondId ?: return null)
    }

    fun previousSameCurve(records: List<BrewJournal.Observation>): BrewJournal.Observation? {
        val first = records.firstOrNull { it.id == firstId } ?: return null
        return records.firstOrNull { it.curveId == first.curveId && it.startedAtMs < first.startedAtMs }
    }

    fun candidates(records: List<BrewJournal.Observation>): List<BrewJournal.Observation> {
        val previous = previousSameCurve(records)
        return listOfNotNull(previous) + records.filter { it.id != firstId && it.id != previous?.id }
    }
}
