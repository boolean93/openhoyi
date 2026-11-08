package io.openhoyi.mobile

import java.util.Base64

data class CurveUsageStats(val count: Long, val lastUsedAtMs: Long?)
data class CurveUsage(val shotId: String, val curveId: String, val startedAtMs: Long, val fingerprint: String? = null)

/** Permanent observed uses, independent of the bounded sample/history cache. One app-owned writer. */
class CurveUsageLedger(private val storage: Storage) {
    interface Storage {
        fun read(): String
        /** Must atomically replace the previous document, or throw without changing it. */
        fun write(value: String)
    }
    private data class Use(val shotId: String, val curveId: String, val atMs: Long, val fingerprint: String? = null)
    private var uses = decode(storage.read())

    @Synchronized
    fun record(shotId: String, curveId: String, startedAtMs: Long): Boolean {
        require(shotId.isNotBlank() && curveId.isNotBlank() && curveId != "manual" && startedAtMs >= 0)
        val use = Use(shotId, curveId, startedAtMs)
        uses[shotId]?.let {
            require(it.shotId == use.shotId && it.curveId == use.curveId && it.atMs == use.atMs) {
                "Conflicting usage identity"
            }
            return false
        }
        val next = uses + (shotId to use)
        storage.write(encode(next.values))
        uses = next
        return true
    }

    @Synchronized
    fun stats(curveId: String): CurveUsageStats {
        val selected = uses.values.filter { it.curveId == curveId }
        return CurveUsageStats(selected.size.toLong(), selected.maxOfOrNull { it.atMs })
    }

    @Synchronized
    fun latest(): CurveUsage? = uses.values.maxWithOrNull(compareBy<Use> { it.atMs }.thenBy { it.shotId })
        ?.let { CurveUsage(it.shotId, it.curveId, it.atMs, it.fingerprint) }

    @Synchronized
    fun attachFingerprint(shotId: String, fingerprint: String): Boolean {
        require(fingerprint.matches(Regex("[0-9a-f]{64}")))
        val prior = uses[shotId] ?: return false
        require(prior.curveId.startsWith("draft-"))
        prior.fingerprint?.let {
            require(it == fingerprint) { "Conflicting historical curve fingerprint" }
            return false
        }
        val next = uses + (shotId to prior.copy(fingerprint = fingerprint))
        storage.write(encode(next.values))
        uses = next
        return true
    }

    private fun encode(values: Collection<Use>): String = HEADER + "\n" + values.joinToString("\n") {
        "${safe(it.shotId)}\t${safe(it.curveId)}\t${it.atMs}" +
            (it.fingerprint?.let { fingerprint -> "\t$fingerprint" } ?: "")
    }

    private fun decode(raw: String): Map<String, Use> {
        if (raw.isEmpty()) return emptyMap()
        val rows = raw.lineSequence().toList()
        require(rows.first() == HEADER) { "Unsupported curve usage document" }
        val result = linkedMapOf<String, Use>()
        for (row in rows.drop(1)) {
            require(row.isNotEmpty()) { "Incomplete curve usage row" }
            val fields = row.split('\t')
            require(fields.size in 3..4) { "Invalid curve usage row" }
            val use = try { Use(unsafe(fields[0]), unsafe(fields[1]), fields[2].toLong(), fields.getOrNull(3)) }
                catch (error: RuntimeException) { throw IllegalArgumentException("Invalid curve usage document", error) }
            require(use.shotId.isNotBlank() && use.curveId.isNotBlank() && use.curveId != "manual" && use.atMs >= 0)
            require(use.fingerprint == null ||
                (use.curveId.startsWith("draft-") && use.fingerprint.matches(Regex("[0-9a-f]{64}"))))
            require(result.put(use.shotId, use) == null) { "Duplicate curve usage identity" }
        }
        return result
    }

    private fun safe(value: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))
    private fun unsafe(value: String) = String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
    private companion object { const val HEADER = "OPENHOYI_CURVE_USES_1" }
}
