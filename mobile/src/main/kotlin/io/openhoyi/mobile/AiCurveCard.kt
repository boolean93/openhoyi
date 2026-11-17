package io.openhoyi.mobile

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** Display evidence only. This object never contains a save permit, a command or a route. */
internal class AiCurveCard private constructor(
    val kind: Kind,
    val id: String,
    val source: String,
    val fingerprint: String,
    private val snapshot: String,
    private val beforeSnapshot: String?,
    private val factorySourceJson: String? = null,
) {
    enum class Kind { RECOMMENDATION, CREATE, REPLACE, COPY_FACTORY }
    override fun toString() = "AiCurveCard"
    override fun equals(other: Any?) = other is AiCurveCard && kind == other.kind && id == other.id &&
        source == other.source && fingerprint == other.fingerprint && snapshot == other.snapshot && beforeSnapshot == other.beforeSnapshot && factorySourceJson == other.factorySourceJson
    override fun hashCode() = listOf(kind, id, source, fingerprint, snapshot, beforeSnapshot, factorySourceJson).hashCode()
    val document: CustomCurveDocument? get() = if (source == "local") CustomCurveDocument.decode(snapshot) else null
    val factorySource: AiCurveCard? get() = factorySourceJson?.let { decode(JSONObject(it)) }
    val before: CustomCurveDocument? get() = beforeSnapshot?.let(CustomCurveDocument::decode)
    fun matchesProposal(proposal: AiCurveWriteCoordinator.Proposal?): Boolean = proposal != null && kind != Kind.RECOMMENDATION &&
        runCatching { Companion.proposal(proposal) == this }.getOrDefault(false)
    val item: CurveLibraryItem get() = document?.let {
        CurveLibraryItem(it.id, it.name, "", "", null, customDocument = it)
    } ?: factoryItem(decodeFactory(JSONObject(snapshot)))

    /** Every tap re-resolves the trusted library. A changed/deleted recipe is never silently substituted. */
    fun matches(current: CurveLibraryItem?): Boolean = current != null && kind == Kind.RECOMMENDATION &&
        runCatching { recommendation(current).fingerprint == fingerprint && current.id == id }.getOrDefault(false)

    fun encode(): JSONObject = JSONObject().put("schema", 1).put("kind", kind.name).put("id", id)
        .put("source", source).put("fingerprint", fingerprint).put("snapshot", snapshot)
        .put("before", beforeSnapshot ?: JSONObject.NULL).put("factorySource", factorySourceJson ?: JSONObject.NULL)

    companion object {
        const val MAX_CARDS_PER_TURN = 10
        const val MAX_ENCODED_BYTES = 32 * 1024
        fun recommendation(item: CurveLibraryItem): AiCurveCard {
            val custom = item.customDocument
            require((custom != null) xor (item.factoryCurve != null))
            val source = if (custom != null) "local" else "factory-v3"
            val snapshot = custom?.also { require(it.id == item.id && it.isEditable) }?.encode()
                ?: encodeFactory(requireNotNull(item.factoryCurve).also { require(it.id == item.id) }).toString()
            return make(Kind.RECOMMENDATION, item.id, source, snapshot, null)
        }
        fun proposal(proposal: AiCurveWriteCoordinator.Proposal): AiCurveCard = make(
            if (proposal.factorySource != null) Kind.COPY_FACTORY else if (proposal.before == null) Kind.CREATE else Kind.REPLACE,
            proposal.after.id, "local", proposal.after.encode(), proposal.before?.encode(), proposal.factorySource?.encode()?.toString())

        /** Restored evidence is inert until matches() checks the current native library. */
        fun decode(value: JSONObject): AiCurveCard {
            require(value.toString().toByteArray(Charsets.UTF_8).size <= MAX_ENCODED_BYTES)
            require(value.keys().asSequence().toSet() == setOf("schema", "kind", "id", "source", "fingerprint", "snapshot", "before", "factorySource"))
            require(value.getInt("schema") == 1)
            val card = make(Kind.valueOf(value.getString("kind")), value.getString("id"),
                value.getString("source"), value.getString("snapshot"),
                if (value.isNull("before")) null else value.getString("before"),
                if (value.isNull("factorySource")) null else value.getString("factorySource"))
            require(card.fingerprint == value.getString("fingerprint"))
            return card
        }
        private fun make(kind: Kind, id: String, source: String, snapshot: String, before: String?, factorySource: String? = null): AiCurveCard {
            require(snapshot.toByteArray(Charsets.UTF_8).size <= MAX_ENCODED_BYTES / 2)
            require(before == null || before.toByteArray(Charsets.UTF_8).size <= MAX_ENCODED_BYTES / 2)
            require(source == "local" || source == "factory-v3")
            if (source == "local") {
                val doc = CustomCurveDocument.decode(snapshot)
                require(doc.id == id && AiCurveDetail.validId(id) && doc.isEditable && snapshot == doc.encode())
                if (kind != Kind.RECOMMENDATION) require(doc.isEditable)
                before?.let {
                    val previous = CustomCurveDocument.decode(it)
                    require(previous.id == id && previous.isEditable && previous.encode() == it)
                }
            } else {
                require(kind == Kind.RECOMMENDATION && before == null)
                val factory = decodeFactory(JSONObject(snapshot))
                require(factory.id == id && encodeFactory(factory).toString() == snapshot)
            }
            require((kind == Kind.REPLACE || kind == Kind.COPY_FACTORY) == (before != null))
            require((kind == Kind.COPY_FACTORY) == (factorySource != null))
            factorySource?.let {
                require(it.toByteArray(Charsets.UTF_8).size <= MAX_ENCODED_BYTES / 2)
                val j = JSONObject(it)
                require(j.isNull("factorySource") && j.getString("kind") == Kind.RECOMMENDATION.name && j.getString("source") == "factory-v3")
                val sourceCard = decode(j)
                require(sourceCard.source == "factory-v3")
                require(CustomCurveDocument.fromLibraryItem(sourceCard.item, id).encode() == before)
            }
            val bytes = "$source\n$snapshot\n${before ?: ""}\n${factorySource ?: ""}".toByteArray(Charsets.UTF_8)
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            return AiCurveCard(kind, id, source, digest, snapshot, before, factorySource)
        }
        private fun factoryItem(f: FactoryCurve) = CurveLibraryItem(f.id, f.name, f.category, "", null, f)
        private fun encodeFactory(f: FactoryCurve): JSONObject = JSONObject().put("id", f.id).put("category", f.category)
            .put("name", f.name).put("tips", f.tips).put("iconPath", f.iconPath).put("flowMl", f.flowMl)
            .put("weightTenthsGram", f.weightTenthsGram).put("weightCompensationTenthsGram", f.weightCompensationTenthsGram)
            .put("preinfusionSeconds", f.preinfusionSeconds).put("temperatureC", f.temperatureC)
            .put("pressureLogic", f.pressureLogic).put("variableFlowLogic", f.variableFlowLogic)
            .put("segmentCount", f.segmentCount).put("targets", JSONArray(f.targets))
            .put("segmentFlowMl", JSONArray(f.segmentFlowMl)).put("firstDurationSeconds", f.firstDurationSeconds)
        private fun decodeFactory(j: JSONObject): FactoryCurve {
            fun ints(key: String): List<Int> = j.getJSONArray(key).let { a -> require(a.length() == 4); (0..3).map(a::getInt) }
            val id = j.getString("id")
            require(Regex("factory-v3-(00[1-9]|0[1-9][0-9]|100)").matches(id))
            val count = j.getInt("segmentCount"); require(count in 1..4)
            val targets = ints("targets"); val water = ints("segmentFlowMl")
            require(targets.all { it in 0..255 } && water.all { it in 0..65535 })
            return FactoryCurve(id, j.getString("category"), j.getString("name"), j.getString("tips"),
                j.getString("iconPath"), j.getInt("flowMl"), j.getInt("weightTenthsGram"),
                j.getInt("weightCompensationTenthsGram"), j.getInt("preinfusionSeconds"), j.getInt("temperatureC"),
                j.getBoolean("pressureLogic"), j.getBoolean("variableFlowLogic"), count, targets, water, j.getInt("firstDurationSeconds"))
        }
    }
}

/** Native tool evidence and explicit display selection. Reading a library page never publishes cards. */
internal class AiCurveCardCollector(private val resolve: (String) -> CurveLibraryItem?) {
    companion object { const val MAX_CANDIDATES = 100 }
    private val candidates = linkedMapOf<String, AiCurveCard>()
    private var selected: List<AiCurveCard> = emptyList()
    @Synchronized fun captureToolResult(result: String): String {
        if (result.toByteArray(Charsets.UTF_8).size > 24 * 1024) return result
        val root = runCatching { JSONObject(result) }.getOrNull() ?: return result
        if (root.optString("status") != "available" || !root.optBoolean("dataOnly", false)) return result
        val items = root.optJSONArray("items") ?: return result
        if (items.length() > AiChatPolicy.PAGE_SIZE) return result
        val fresh = linkedMapOf<String, AiCurveCard>()
        for (index in 0 until items.length()) {
            val row = items.optJSONObject(index) ?: continue
            val id = row.optString("id")
            val item = runCatching { resolve(id) }.getOrNull() ?: continue
            val expectedOrigin = if (item.customDocument != null) "local" else "factory"
            if (row.optString("origin") != expectedOrigin || item.id != id) continue
            runCatching { AiCurveCard.recommendation(item) }.getOrNull()?.let { fresh[id] = it }
        }
        if ((candidates.keys + fresh.keys).size > MAX_CANDIDATES) return status("candidate_limit", true)
        candidates.putAll(fresh)
        return result
    }
    /** Atomic replace, including [] to clear. On any invalid ID the prior selection stays unchanged. */
    @Synchronized fun select(idsJson: String): String {
        if (idsJson.toByteArray(Charsets.UTF_8).size > 12 * 1024) return status("invalid_arguments", true)
        val values = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(idsJson) as? kotlinx.serialization.json.JsonArray }.getOrNull()
            ?: return status("invalid_arguments", true)
        if (values.size > AiCurveCard.MAX_CARDS_PER_TURN) return status("card_limit", true)
        val ids = values.map { value ->
            val id = (value as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content
            if (id == null || id.length !in 1..80) return status("invalid_arguments", true)
            id
        }
        if (ids.distinct().size != ids.size) return status("invalid_arguments", true)
        val next = ArrayList<AiCurveCard>(ids.size)
        for (id in ids) {
            val candidate = candidates[id] ?: return status("unknown_curve", true)
            val current = runCatching { resolve(id) }.getOrNull()
            if (!candidate.matches(current)) return status("curve_changed", true)
            next += candidate
        }
        selected = next.toList()
        return status("selected", false)
    }
    private fun status(value: String, unchanged: Boolean): String = JSONObject().put("status", value)
        .put("selectedCount", selected.size).put("selectionUnchanged", unchanged)
        .put("limit", AiCurveCard.MAX_CARDS_PER_TURN).put("candidateLimit", MAX_CANDIDATES).put("dataOnly", true).put("writes", false).toString()
    @Synchronized fun snapshot(): List<AiCurveCard> = selected.toList()
}
