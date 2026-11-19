package io.openhoyi.mobile

import io.openhoyi.session.ExtractionState
import java.util.Base64
import java.util.UUID

/** Small product history separate from diagnostic traces; stores outcomes, not raw protocol data. */
class ShotHistory(
    private val storage: Storage,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val onObservedUse: (Entry) -> Unit = {},
    private val onEntryChanged: (Entry) -> Unit = {},
    private val onTimingEvidenceError: (Throwable) -> Unit = {},
) {
    interface Storage {
        fun read(): String
        fun write(value: String)
    }
    interface AtomicTimingStorage : Storage {
        fun readTiming(): String?
        /** Save both values through one durable transaction. */
        fun writeWithTiming(value: String, timing: String)
    }
    internal companion object { const val MAX_ENTRIES = 500 }
    enum class Status { STARTING, RUNNING, STOP_REQUESTED, ENDED, UNKNOWN, NOT_STARTED }
    data class Entry(
        val id: String,
        val curveId: String,
        val startedAtMs: Long,
        val endedAtMs: Long?,
        val elapsedMs: Long?,
        val status: Status,
        val reason: String?,
        val weightHundredthsGram: Int?,
        val slot: Int? = null,
        val observedRunning: Boolean = false,
        val observedMachineElapsedSeconds: Int? = null,
        val timingEvidenceVersion: Int = 0,
        val observedSampleCoverageMs: Long? = null,
        val machineTimingInterrupted: Boolean? = null,
    )

    private val timingStorage = storage as? AtomicTimingStorage
    private var timingRows = emptyMap<ShotTimingSidecar.Identity, ShotTimingSidecar.Evidence>()
    private var timingReadable = true
    private val records = decode(storage.read()).toMutableList()
    private var activeId: String? = null
    val entries: List<Entry> get() = records.sortedByDescending { it.startedAtMs }.toList()

    init {
        val readTiming = runCatching { ShotTimingSidecar.decode(timingStorage?.readTiming()) }
        timingReadable = readTiming.isSuccess
        timingRows = readTiming.getOrElse { error ->
            runCatching { onTimingEvidenceError(error) }
            emptyMap()
        }
        val needsInlineMigration = records.any { it.machineTimingInterrupted != null }
        for (index in records.indices) {
            val entry = records[index]
            val evidence = timingRows[with(ShotTimingSidecar) { entry.identity() }] ?: continue
            records[index] = when {
                evidence.interrupted -> entry.copy(machineTimingInterrupted = true, observedMachineElapsedSeconds = evidence.seconds)
                entry.machineTimingInterrupted == true -> entry
                else -> entry.copy(machineTimingInterrupted = false)
            }
        }
        // Migrate only entries that positively establish observed use, before restart marks them unknown.
        records.filter { it.curveId != "manual" && it.observedRunning }.forEach(onObservedUse)
        var changed = false
        val interrupted = mutableListOf<Entry>()
        if (timingReadable) for (index in records.indices) {
            val entry = records[index]
            if (entry.status in setOf(Status.STARTING, Status.RUNNING, Status.STOP_REQUESTED)) {
                records[index] = entry.copy(status = Status.UNKNOWN, reason = "进程中断", machineTimingInterrupted = true)
                interrupted += records[index]
                changed = true
            }
        }
        // Keep an unrecoverable sidecar byte-for-byte; do not write recovered history
        // without its matching evidence or trim records on that unsuccessful transaction.
        if (timingReadable) {
            val evidence = records.toList()
            if (trim() || changed || needsInlineMigration) write(records, evidence)
        }
        if (timingReadable) interrupted.forEach(::notifyChanged)
    }

    fun begin(curveId: String, atMs: Long = now(), slot: Int = 7): String {
        require(activeId == null) { "Previous shot has not been resolved in the history" }
        require(curveId == "manual" || CurveCatalog.find(curveId) != null ||
            curveId.matches(Regex("factory-v3-(00[1-9]|0[1-9][0-9]|100)")) || canonicalDraftId(curveId))
        require(if (curveId == "manual") slot == 6 else if (canonicalDraftId(curveId)) slot == 7 else slot in 1..5 || slot == 7)
        val id = newId()
        require(id.isNotBlank() && records.none { it.id == id })
        val next = records.toMutableList().apply {
            add(Entry(id, curveId, atMs, null, null, Status.STARTING, null, null, slot, timingEvidenceVersion = 1))
            val cutoff = now() - 30L * 24 * 60 * 60 * 1000
            removeAll { it.startedAtMs < cutoff }
            if (size > 500) {
                val oldest = sortedBy { it.startedAtMs }.take(size - 500).map { it.id }.toSet()
                removeAll { it.id in oldest }
            }
        }
        write(next)
        records.clear()
        records.addAll(next)
        activeId = id
        records.firstOrNull { it.id == id }?.let(::notifyChanged)
        return id
    }

    fun transition(state: ExtractionState, reason: String?, weightHundredthsGram: Int?, atMs: Long = now(),
        timing: ShotTimingEvidence? = null) {
        val id = activeId ?: return
        if (timing != null && timing.shotId != id) return
        val index = records.indexOfFirst { it.id == id }
        if (index < 0) { activeId = null; return }
        val entry = records[index]
        val status = when (state) {
            ExtractionState.IDLE -> Status.NOT_STARTED
            ExtractionState.STARTING -> Status.STARTING
            ExtractionState.RUNNING -> Status.RUNNING
            ExtractionState.STOP_REQUESTED -> Status.STOP_REQUESTED
            ExtractionState.ENDED_OBSERVED -> Status.ENDED
            ExtractionState.OUTCOME_UNKNOWN -> Status.UNKNOWN
        }
        val terminal = status == Status.NOT_STARTED || status == Status.ENDED
        val evidence = timing
        val partial = status == Status.UNKNOWN || evidence?.interrupted == true
        val updated = entry.copy(
            endedAtMs = if (terminal) atMs else null,
            elapsedMs = if (terminal) (atMs - entry.startedAtMs).coerceAtLeast(0) else null,
            status = status,
            reason = reason ?: entry.reason,
            weightHundredthsGram = if (terminal) weightHundredthsGram else null,
            observedMachineElapsedSeconds = when {
                entry.machineTimingInterrupted == true || evidence == null -> entry.observedMachineElapsedSeconds
                else -> evidence.seconds // An explicit missing timer is not backfilled.
            },
            observedSampleCoverageMs = evidence?.sampleCoverageMs ?: entry.observedSampleCoverageMs,
            machineTimingInterrupted = when {
                entry.machineTimingInterrupted == true || partial -> true
                terminal && evidence != null -> false
                else -> entry.machineTimingInterrupted
            },
        )
        if (entry != updated) replace(index, updated)
        if (terminal) activeId = null
        if (updated.observedRunning && updated.curveId != "manual") onObservedUse(updated)
    }

    /** Attach only evidence owned by this series, including after terminal clears activeId. */
    fun attachTimingEvidence(id: String, seconds: Int?, sampleCoverageMs: Long? = null) {
        require(seconds == null || seconds in 0..65535)
        require(sampleCoverageMs == null || sampleCoverageMs > 0)
        val index = records.indexOfFirst { it.id == id }
        if (index < 0) return
        val entry = records[index]
        replace(index, entry.copy(
            observedMachineElapsedSeconds = if (entry.machineTimingInterrupted == true) entry.observedMachineElapsedSeconds
                else seconds ?: entry.observedMachineElapsedSeconds,
            timingEvidenceVersion = 1,
            observedSampleCoverageMs = sampleCoverageMs ?: entry.observedSampleCoverageMs))
    }

    /** Recover only explicitly observed sampling coverage; never invent a machine timer or end. */
    fun recoverSampleCoverage(id: String, points: List<ShotPoint>) {
        val index = records.indexOfFirst { it.id == id }
        if (index < 0 || records[index].observedSampleCoverageMs != null) return
        val coverage = observedSampleCoverage(points) ?: return
        val updated = records[index].copy(observedSampleCoverageMs = coverage)
        val next = records.toMutableList().apply { set(index, updated) }
        write(next)
        records[index] = updated
        notifyChanged(updated)
    }

    /** Fresh valve-open extraction telemetry, not a successful command callback, establishes use. */
    fun observeRunning(): String? {
        val index = records.indexOfFirst { it.id == activeId }
        if (index < 0 || records[index].curveId == "manual") return null
        val entry = records[index]
        if (!entry.observedRunning) {
            records[index] = entry.copy(observedRunning = true)
            persist()
            notifyChanged(records[index])
        }
        onObservedUse(records[index])
        return records[index].id
    }

    /** A disconnected passive shot has no session to reconcile; retain UNKNOWN and free the next slot. */
    fun abandon(id: String, reason: String, atMs: Long = now(), timing: ShotTimingEvidence? = null) {
        if (activeId != id || (timing != null && timing.shotId != id)) return
        transition(ExtractionState.OUTCOME_UNKNOWN, reason, null, atMs, timing)
        activeId = null
    }

    private fun replace(index: Int, entry: Entry) {
        val next = records.toMutableList().apply { set(index, entry) }
        trim(next)
        write(next, next + entry)
        records.clear()
        records.addAll(next)
        notifyChanged(entry)
    }

    // Optional reporting must never alter the machine callback/control path.
    private fun notifyChanged(entry: Entry) { runCatching { onEntryChanged(entry) } }

    private fun persist() {
        trim()
        write(records)
    }

    private fun write(entries: List<Entry>, evidenceEntries: List<Entry> = entries) {
        check(timingReadable) { "Cannot overwrite unreadable shot timing evidence" }
        val legacy = entries.joinToString("\n", postfix = if (entries.isEmpty()) "" else "\n", transform = ::encode)
        val target = timingStorage
        if (target == null) {
            // Isolated legacy in-memory fixtures do not supply an app-private sidecar.
            storage.write(legacy)
            return
        }
        val next = timingRows.toMutableMap()
        // Sidecar outlives recent-history trimming; a permanent Journal row may still need it.
        evidenceEntries.forEach { entry ->
            val interrupted = entry.machineTimingInterrupted ?: return@forEach
            val identity = with(ShotTimingSidecar) { entry.identity() }
            if (next[identity]?.interrupted == true) return@forEach
            next[identity] = ShotTimingSidecar.Evidence(interrupted,
                entry.observedMachineElapsedSeconds.takeIf { interrupted })
        }
        if (next == timingRows) target.write(legacy)
        else target.writeWithTiming(legacy, ShotTimingSidecar.encode(next))
        timingRows = next
    }

    private fun trim(entries: MutableList<Entry> = records): Boolean {
        val before = entries.size
        val cutoff = now() - 30L * 24 * 60 * 60 * 1000
        entries.removeAll { it.startedAtMs < cutoff }
        if (entries.size > MAX_ENTRIES) {
            val oldest = entries.sortedBy { it.startedAtMs }.take(entries.size - MAX_ENTRIES).map { it.id }.toSet()
            entries.removeAll { it.id in oldest }
        }
        return entries.size != before
    }

    private fun encode(value: Entry): String = listOf(
        safe(value.id), safe(value.curveId), value.startedAtMs.toString(),
        value.endedAtMs?.toString().orEmpty(), value.elapsedMs?.toString().orEmpty(),
        value.status.name, safe(value.reason.orEmpty()), value.weightHundredthsGram?.toString().orEmpty(),
        value.slot?.toString().orEmpty(),
        value.observedRunning.toString(),
        value.observedMachineElapsedSeconds?.takeUnless { value.machineTimingInterrupted == true }?.toString().orEmpty(), value.timingEvidenceVersion.toString(), value.observedSampleCoverageMs?.toString().orEmpty(),
    ).joinToString("\t")

    private fun decode(value: String): List<Entry> = value.lineSequence().mapNotNull { line ->
        val fields = line.split('\t')
        if (fields.size !in 8..14) return@mapNotNull null
        runCatching {
            Entry(unsafe(fields[0]), unsafe(fields[1]), fields[2].toLong(),
                fields[3].takeIf(String::isNotEmpty)?.toLong(), fields[4].takeIf(String::isNotEmpty)?.toLong(),
                Status.valueOf(fields[5]), unsafe(fields[6]).ifEmpty { null },
                fields[7].takeIf(String::isNotEmpty)?.toInt(),
                fields.getOrNull(8)?.takeIf(String::isNotEmpty)?.toInt(),
                fields.getOrNull(9)?.toBooleanStrict() ?: false,
                fields.getOrNull(10)?.takeIf(String::isNotEmpty)?.toInt(),
                fields.getOrNull(11)?.toInt() ?: 0,
                fields.getOrNull(12)?.takeIf(String::isNotEmpty)?.toLong(),
                fields.getOrNull(13)?.toBooleanStrict())
        }.getOrNull()?.takeIf { it.id.isNotBlank() && it.curveId.isNotBlank() && it.startedAtMs >= 0 &&
            (it.observedMachineElapsedSeconds == null || it.observedMachineElapsedSeconds in 0..65535) &&
            (it.observedSampleCoverageMs == null || it.observedSampleCoverageMs > 0) &&
            it.timingEvidenceVersion in 0..1 && (it.slot == null || if (it.curveId == "manual") it.slot == 6
                else it.slot in 1..5 || it.slot == 7) }
    }.toList()

    // History records identity only; this is not a device-execution permission.
    private fun canonicalDraftId(value:String):Boolean = value.startsWith("draft-") &&
        runCatching { UUID.fromString(value.removePrefix("draft-")).toString() == value.removePrefix("draft-") }.getOrDefault(false)

    private fun safe(value: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(value.toByteArray(Charsets.UTF_8))
    private fun unsafe(value: String): String = String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
}
