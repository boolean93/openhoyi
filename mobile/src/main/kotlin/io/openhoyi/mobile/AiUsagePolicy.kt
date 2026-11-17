package io.openhoyi.mobile

import kotlinx.serialization.json.*
import java.util.UUID

internal enum class AiUsageKind { CHAT, SEARCH }
internal enum class AiSearchOutcome { NONEMPTY, EMPTY, FAILED, UNKNOWN }
internal data class AiModelUsage(val model: String, val requests: Long = 0, val reported: Long = 0,
    val input: Long = 0, val output: Long = 0, val cacheHit: Long = 0, val cacheMiss: Long = 0, val cacheReported: Long = 0) {
    val hasReportedUsage get() = reported > 0
}
internal data class AiUsageSnapshot(val schema: Int = 1, val since: Long = System.currentTimeMillis(),
    val models: List<AiModelUsage> = emptyList(), val searches: Long = 0, val searchNonempty: Long = 0,
    val searchEmpty: Long = 0, val searchFailed: Long = 0, val incomplete: Boolean = false) {
    val cacheHit get() = models.sumOf { it.cacheHit }
    val cacheMiss get() = models.sumOf { it.cacheMiss }
    val cacheReported get() = models.sumOf { it.cacheReported }
    val requests get() = models.sumOf { it.requests }
    val cacheHitRate: Double? get() = (cacheHit + cacheMiss).takeIf { it > 0 }?.let { cacheHit.toDouble() / it }
    val unknownChat get() = models.sumOf { it.requests - it.reported }
    val unknownSearch get() = searches - searchNonempty - searchEmpty - searchFailed
}
internal data class AiReportedUsage(val model: String?, val input: Long, val output: Long, val cacheHit: Long? = null, val cacheMiss: Long? = null)
internal object AiUsagePolicy {
    private fun count(value: JsonElement?): Long? = (value as? JsonPrimitive)?.takeUnless { it.isString }
        ?.longOrNull?.takeIf { it in 0..1_000_000_000L }
    fun model(value: String?): String? = value?.takeIf { it in setOf("deepseek-flash", "deepseek-v4-pro", "deepseek-chat", "deepseek-reasoner", "unknown", "other") }
    fun reported(raw: String): AiReportedUsage? = try {
        val root = Json.parseToJsonElement(raw).jsonObject
        val usage = root["usage"] as? JsonObject
        val input = count(usage?.get("prompt_tokens"))
        val output = count(usage?.get("completion_tokens"))
        val total = count(usage?.get("total_tokens"))
        if (input == null || output == null || (usage?.containsKey("total_tokens") == true && (total == null || total != input + output))) null
        else {
            val hit = count(usage?.get("prompt_cache_hit_tokens"))
            val miss = count(usage?.get("prompt_cache_miss_tokens"))
            val validCache = hit != null && miss != null && hit + miss == input
            AiReportedUsage(model((root["model"] as? JsonPrimitive)?.takeIf { it.isString }?.content) ?: "unknown", input, output,
                hit.takeIf { validCache }, miss.takeIf { validCache })
        }
    } catch (_: Exception) { null }
    fun searchOutcome(raw: String): AiSearchOutcome = try {
        val root = Json.parseToJsonElement(raw).jsonObject
        val results = root["results"] as? JsonArray
        val error = root["error"]?.let { it != JsonNull } == true
        when { error || results == null -> AiSearchOutcome.UNKNOWN; results.isEmpty() -> AiSearchOutcome.EMPTY; else -> AiSearchOutcome.NONEMPTY }
    } catch (_: Exception) { AiSearchOutcome.UNKNOWN }
}

/** Counters only. Pending ids are process-local: after restart unfinished requests remain unknown, never replayed. */
internal class AiUsageLedger(initial: AiUsageSnapshot = AiUsageSnapshot(), private val save: (AiUsageSnapshot) -> Boolean = { true }) {
    private var value = initial
    private val pending = mutableMapOf<String, Pair<AiUsageKind, String>>()
    private val completed = linkedSetOf<String>()
    @Synchronized fun snapshot(): AiUsageSnapshot = value
    @Synchronized fun begin(id: String, kind: AiUsageKind, model: String = "deepseek-flash") {
        if (pending.containsKey(id) || id in completed) return
        // At most one active production request; bound accidental/malicious callers too.
        if (pending.size >= 16) { update(value.copy(incomplete = true)); return }
        val name = bucket(AiUsagePolicy.model(model) ?: "unknown")
        pending[id] = kind to name
        update(if (kind == AiUsageKind.SEARCH) value.copy(searches = value.searches + 1)
            else changeModel(value, name) { it.copy(requests = it.requests + 1) })
    }
    @Synchronized fun finish(id: String, reported: AiReportedUsage? = null, search: AiSearchOutcome = AiSearchOutcome.UNKNOWN) {
        val (kind, requested) = pending.remove(id) ?: return
        completed.add(id)
        if (completed.size > 256) completed.remove(completed.first())
        if (kind == AiUsageKind.SEARCH) update(when (search) {
            AiSearchOutcome.NONEMPTY -> value.copy(searchNonempty = value.searchNonempty + 1)
            AiSearchOutcome.EMPTY -> value.copy(searchEmpty = value.searchEmpty + 1)
            AiSearchOutcome.FAILED -> value.copy(searchFailed = value.searchFailed + 1)
            AiSearchOutcome.UNKNOWN -> value
        }) else if (reported != null) {
            val actual = bucket(reported.model ?: requested)
            var next = value
            if (actual != requested) next = changeModel(next, requested) { it.copy(requests = it.requests - 1) }
            next = changeModel(next, actual) { it.copy(requests = it.requests + if (actual != requested) 1 else 0,
                reported = it.reported + 1, input = it.input + reported.input, output = it.output + reported.output,
                cacheHit = it.cacheHit + (reported.cacheHit ?: 0), cacheMiss = it.cacheMiss + (reported.cacheMiss ?: 0),
                cacheReported = it.cacheReported + if (reported.cacheHit != null && reported.cacheMiss != null) 1 else 0) }
            update(next.copy(models = next.models.filter { it.requests > 0 }))
        }
    }
    private fun bucket(name: String) = if (value.models.any { it.model == name } || value.models.size < 15) name else "other"
    private fun changeModel(state: AiUsageSnapshot, name: String, change: (AiModelUsage) -> AiModelUsage): AiUsageSnapshot {
        val old = state.models.firstOrNull { it.model == name } ?: AiModelUsage(name)
        return state.copy(models = state.models.filterNot { it.model == name } + change(old))
    }
    private fun update(next: AiUsageSnapshot) {
        value = next
        if (!runCatching { save(next) }.getOrDefault(false)) value = next.copy(incomplete = true)
    }
}

internal class AiUsageRecorder(private val ledgers: List<AiUsageLedger>) {
    fun begin(kind: AiUsageKind, model: String = AiModelPolicy.FLASH): String = UUID.randomUUID().toString().also { id -> ledgers.forEach { it.begin(id, kind, model) } }
    fun finish(id: String, reported: AiReportedUsage? = null, search: AiSearchOutcome = AiSearchOutcome.UNKNOWN) {
        ledgers.forEach { it.finish(id, reported, search) }
    }
}

internal object AiUsageCodec {
    fun encode(s: AiUsageSnapshot): String = buildJsonObject {
        put("schema", s.schema); put("since", s.since); put("incomplete", s.incomplete)
        put("searches", s.searches); put("searchNonempty", s.searchNonempty); put("searchEmpty", s.searchEmpty); put("searchFailed", s.searchFailed)
        put("models", buildJsonArray { s.models.forEach { m -> add(buildJsonObject {
            put("model", m.model); put("requests", m.requests); put("reported", m.reported); put("input", m.input); put("output", m.output)
            put("cacheHit", m.cacheHit); put("cacheMiss", m.cacheMiss); put("cacheReported", m.cacheReported)
        }) } })
    }.toString()
    fun decode(raw: String): AiUsageSnapshot {
        val o = Json.parseToJsonElement(raw).jsonObject
        fun number(obj: JsonObject, name: String): Long = (obj[name] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull
            ?.takeIf { it in 0..1_000_000_000_000_000L } ?: error("Invalid counter")
        require(number(o, "schema") == 1L)
        val models = (o["models"] as? JsonArray ?: error("Missing models")).map { element ->
            val m = element.jsonObject
            val name = (m["model"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            val hasCache = listOf("cacheHit", "cacheMiss", "cacheReported").any { it in m }
            AiModelUsage(AiUsagePolicy.model(name) ?: error("Invalid model"), number(m,"requests"), number(m,"reported"), number(m,"input"), number(m,"output"),
                if (hasCache) number(m,"cacheHit") else 0, if (hasCache) number(m,"cacheMiss") else 0, if (hasCache) number(m,"cacheReported") else 0)
        }
        val state = AiUsageSnapshot(since = number(o, "since"), models = models, searches = number(o,"searches"),
            searchNonempty = number(o,"searchNonempty"), searchEmpty = number(o,"searchEmpty"), searchFailed = number(o,"searchFailed"),
            incomplete = (o["incomplete"] as? JsonPrimitive)?.booleanOrNull ?: error("Missing completeness"))
        require(state.since > 0 && models.size <= 16 && models.map { it.model }.distinct().size == models.size)
        require(models.all { it.reported <= it.requests && it.cacheReported <= it.reported && it.cacheHit + it.cacheMiss <= it.input && (it.cacheReported > 0 || it.cacheHit + it.cacheMiss == 0L) } && state.unknownSearch >= 0)
        return state
    }
}
