package io.openhoyi.mobile

import io.openhoyi.session.ExtractionState
import java.util.Base64
import java.util.UUID

/** Small product history separate from diagnostic traces; stores outcomes, not raw protocol data. */
class ShotHistory(
    private val storage: Storage,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    interface Storage {
        fun read(): String
        fun write(value: String)
    }
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
    )

    private val records = decode(storage.read()).toMutableList()
    private var activeId: String? = null
    val entries: List<Entry> get() = records.sortedByDescending { it.startedAtMs }.toList()

    init {
        var changed = false
        for (index in records.indices) {
            val entry = records[index]
            if (entry.status in setOf(Status.STARTING, Status.RUNNING, Status.STOP_REQUESTED)) {
                records[index] = entry.copy(status = Status.UNKNOWN, reason = "进程中断")
                changed = true
            }
        }
        if (trim()) changed = true
        if (changed) persist()
    }

    fun begin(curveId: String, atMs: Long = now(), slot: Int = 7): String {
        require(activeId == null) { "Previous shot has not been resolved in the history" }
        require(CurveCatalog.find(curveId) != null || curveId.matches(Regex("factory-v3-(00[1-9]|0[1-9][0-9]|100)")))
        require(slot in 1..5 || slot == 7)
        val id = newId()
        require(id.isNotBlank() && records.none { it.id == id })
        records.add(Entry(id, curveId, atMs, null, null, Status.STARTING, null, null, slot))
        activeId = id
        persist()
        return id
    }

    fun transition(state: ExtractionState, reason: String?, weightHundredthsGram: Int?, atMs: Long = now()) {
        val id = activeId ?: return
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
        val updated = entry.copy(
            endedAtMs = if (terminal) atMs else null,
            elapsedMs = if (terminal) (atMs - entry.startedAtMs).coerceAtLeast(0) else null,
            status = status,
            reason = reason ?: entry.reason,
            weightHundredthsGram = if (terminal) weightHundredthsGram else null,
        )
        if (entry != updated) {
            records[index] = updated
            persist()
        }
        if (terminal) activeId = null
    }

    private fun persist() {
        trim()
        storage.write(records.joinToString("\n", postfix = if (records.isEmpty()) "" else "\n", transform = ::encode))
    }

    private fun trim(): Boolean {
        val before = records.size
        val cutoff = now() - 30L * 24 * 60 * 60 * 1000
        records.removeAll { it.startedAtMs < cutoff }
        if (records.size > 500) {
            val oldest = records.sortedBy { it.startedAtMs }.take(records.size - 500).map { it.id }.toSet()
            records.removeAll { it.id in oldest }
        }
        return records.size != before
    }

    private fun encode(value: Entry): String = listOf(
        safe(value.id), safe(value.curveId), value.startedAtMs.toString(),
        value.endedAtMs?.toString().orEmpty(), value.elapsedMs?.toString().orEmpty(),
        value.status.name, safe(value.reason.orEmpty()), value.weightHundredthsGram?.toString().orEmpty(),
        value.slot?.toString().orEmpty(),
    ).joinToString("\t")

    private fun decode(value: String): List<Entry> = value.lineSequence().mapNotNull { line ->
        val fields = line.split('\t')
        if (fields.size !in 8..9) return@mapNotNull null
        runCatching {
            Entry(unsafe(fields[0]), unsafe(fields[1]), fields[2].toLong(),
                fields[3].takeIf(String::isNotEmpty)?.toLong(), fields[4].takeIf(String::isNotEmpty)?.toLong(),
                Status.valueOf(fields[5]), unsafe(fields[6]).ifEmpty { null },
                fields[7].takeIf(String::isNotEmpty)?.toInt(),
                fields.getOrNull(8)?.takeIf(String::isNotEmpty)?.toInt())
        }.getOrNull()?.takeIf { it.id.isNotBlank() && it.curveId.isNotBlank() && it.startedAtMs >= 0 &&
            (it.slot == null || it.slot in 1..5 || it.slot == 7) }
    }.toList()

    private fun safe(value: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(value.toByteArray(Charsets.UTF_8))
    private fun unsafe(value: String): String = String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
}
