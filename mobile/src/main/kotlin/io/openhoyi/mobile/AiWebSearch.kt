package io.openhoyi.mobile

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*
import java.net.URI

/** No fetch capability: URLs are citation data only, never visited by the client. */
internal object AiWebSearch {
    fun request(query: String): String? {
        if (query.isBlank() || query.codePointCount(0, query.length) !in 1..128 || query.any { it.isISOControl() }) return null
        return buildJsonObject { put("query", query); put("count", 3); put("need_content", false) }.toString()
    }
    fun safeUrl(value: String): Boolean = try {
        val uri = URI(value)
        val host = uri.host?.lowercase() ?: ""
        val labels = host.split('.')
        uri.scheme in setOf("https", "http") && uri.rawUserInfo == null && uri.port in setOf(-1, 80, 443) &&
            value.length <= 600 && !value.any { it.isISOControl() } && host.length <= 253 &&
            // Domain names only: reject IP literals, alternate numeric spellings and local names without DNS lookup.
            labels.size >= 2 && labels.all { it.matches(Regex("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?")) } &&
            labels.last().matches(Regex("[a-z]{2,63}")) &&
            labels.last() !in setOf("local", "localhost", "internal", "invalid", "test", "example", "lan", "home", "onion") &&
            !host.endsWith(".localhost")
    } catch (_: Exception) { false }
    fun results(raw: String): String {
        val root = try { Json.parseToJsonElement(raw) as? JsonObject } catch (_: Exception) { null }
        if (root?.get("error")?.let { it != JsonNull } == true) return status("unavailable")
        val entries = root?.get("results") as? JsonArray ?: return status("unavailable")
        val items = entries.take(3).mapNotNull { entry ->
            val obj = entry as? JsonObject ?: return@mapNotNull null
            fun field(name: String) = (obj[name] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: ""
            val url = field("url")
            if (!safeUrl(url)) return@mapNotNull null
            buildJsonObject {
                put("title", field("title").filterNot { it.isISOControl() }.take(100)); put("url", url)
                val text = field("summary").ifBlank { field("snippet") }.filterNot { it.isISOControl() }
                put("summary", text.take(350)); put("truncated", text.length > 350)
            }
        }
        return buildJsonObject {
            put("status", if (entries.isEmpty()) "empty" else if (items.isEmpty()) "unavailable" else "ok")
            put("untrusted_web_data", true); put("results", JsonArray(items))
        }.toString()
    }
    fun status(value: String) = buildJsonObject { put("status", value); put("results", JsonArray(emptyList())) }.toString()
    suspend fun execute(query: String, search: suspend (String) -> String): String {
        if (request(query) == null) return status("invalid_arguments")
        return try { results(search(query)) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: AiFailure) {
            // An over-budget search must not open a connection or discard the whole reply.
            // Only our closed search-budget reason is recoverable; other budgets stay hard stops.
            if (failure.problem == AiProblem.TOOL_LIMIT && failure.diagnostic?.kind == AiFailureKind.SEARCH_BUDGET)
                return status("search_budget_exhausted")
            when (failure.problem) {
                AiProblem.KEY, AiProblem.LIMIT, AiProblem.TOOL_LIMIT, AiProblem.REQUEST_LIMIT, AiProblem.RESPONSE_LIMIT -> throw failure
                AiProblem.AUTH -> status("authentication_failed")
                AiProblem.RATE_LIMIT -> status("rate_limited")
                else -> status("unavailable")
            }
        }
        catch (_: Exception) { status("unavailable") }
    }
}
