package io.openhoyi.mobile

import android.content.Context
import java.util.Locale

internal val ShotHistory.Entry.timingLabel: Int get() =
    when {
        observedMachineElapsedSeconds != null -> R.string.history_duration
        observedSampleCoverageMs != null -> R.string.history_sample_coverage
        timingEvidenceVersion == 0 -> R.string.history_record_elapsed_legacy
        else -> R.string.history_duration
    }
internal val ShotHistory.Entry.timingValue: String? get() =
    when {
        observedMachineElapsedSeconds != null -> observedMachineElapsedSeconds.toString()
        observedSampleCoverageMs != null -> "%.1f".format(Locale.ROOT, observedSampleCoverageMs / 1000.0)
        timingEvidenceVersion == 0 -> elapsedMs?.let { "%.1f".format(Locale.ROOT, it / 1000.0) }
        else -> null
    }
internal fun Context.shotTimingSummary(entry: ShotHistory.Entry): String =
    getString(entry.timingLabel) + " " + (entry.timingValue?.let { "$it s" } ?: "—")
internal fun Context.shotRecordElapsed(entry: ShotHistory.Entry): String =
    getString(if (entry.timingEvidenceVersion == 0) R.string.history_record_elapsed_legacy else R.string.history_record_elapsed) +
        " " + (entry.elapsedMs?.let { "%.1f s".format(Locale.ROOT, it / 1000.0) } ?: "—")
