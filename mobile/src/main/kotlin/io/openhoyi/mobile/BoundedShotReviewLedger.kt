package io.openhoyi.mobile

import kotlinx.serialization.json.*

/** All rows are keyed to local recent retained shots; corrupt state cannot become an empty ledger. */
internal class BoundedShotReviewLedger(
    private val readBytes: () -> ByteArray?,
    private val writeBytes: (ByteArray) -> Unit,
) : ShotReviewLedger {
    @Volatile private var retained: Set<String> = emptySet()
    private val cached by lazy { runCatching { readBytes()?.let(::decode) ?: emptyMap() } }
    private var committed: Map<String, ShotReviewResult>? = null
    private fun rows() = committed ?: cached.getOrThrow()
    fun publishRetained(ids: Set<String>) {
        // Bounded to the same recorded-cup cap as ShotHistory.
        retained = ids.takeIf { it.size <= ShotHistory.MAX_ENTRIES }.orEmpty()
    }

    @Synchronized override fun reserve(id: String): Boolean = runCatching {
        val allowed = retained
        if (id !in allowed || !AiExtractionSamples.validId(id)) return false
        val current = rows()
        if (id in current) return false
        // This combines pruning and the durable attempt marker in one atomic commit.
        commit(current.filterKeys { it in allowed } + (id to ShotReviewResult(ShotReviewStatus.PENDING)))
        true
    }.getOrDefault(false)

    @Synchronized override fun finish(id: String, result: ShotReviewResult) {
        val current = rows()
        if (current[id]?.status != ShotReviewStatus.PENDING) return
        val allowed = retained
        commit(current.filterKeys { it in allowed }.let { next ->
            if (id in next) next + (id to result) else next
        })
    }

    @Synchronized fun prune() = runCatching {
        val current = rows()
        val allowed = retained
        val next = current.filterKeys { it in allowed }
        if (next.size != current.size) commit(next)
    }.isSuccess

    @Synchronized override fun read(id: String): ShotReviewResult? = runCatching { rows()[id] }.getOrNull()

    private fun commit(next: Map<String, ShotReviewResult>) {
        val data = buildJsonObject {
            next.forEach { (id, result) -> put(id, buildJsonObject {
                put("status", result.status.name)
                result.text?.let { put("text", it) }
            }) }
        }.toString().toByteArray(Charsets.UTF_8)
        require(next.size <= ShotHistory.MAX_ENTRIES && data.size <= MAX_BYTES)
        writeBytes(data)
        committed = next
    }

    private fun decode(bytes: ByteArray): Map<String, ShotReviewResult> {
        require(bytes.size in 2..MAX_BYTES)
        val root = Json.parseToJsonElement(bytes.decodeToString(throwOnInvalidSequence = true)).jsonObject
        require(root.size <= ShotHistory.MAX_ENTRIES)
        return root.mapValues { (id, value) ->
            require(AiExtractionSamples.validId(id))
            val fields = value.jsonObject
            require(fields.keys.all { it == "status" || it == "text" })
            val status = ShotReviewStatus.valueOf(fields.getValue("status").jsonPrimitive.content)
            val text = fields["text"]?.jsonPrimitive?.takeIf { it.isString }?.content
            require(if (status == ShotReviewStatus.READY) text != null && text.length in 1..ShotReviewWire.RESPONSE_CHARS else "text" !in fields)
            ShotReviewResult(status, text)
        }
    }

    companion object {
        // Full cap even for escape-heavy text. App-private file, never a backup/export input.
        const val MAX_BYTES = 2 * 1024 * 1024
    }
}
