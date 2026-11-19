package io.openhoyi.mobile

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.*
import java.util.concurrent.ConcurrentHashMap

/** A closed, one-shot workflow. No chat context, tools, uploads, retries, or machine controls. */
internal object ShotReviewWire {
    const val RESPONSE_CHARS = 360
    private const val SLOT_CHARS = 115
    private val reviewSlots = listOf("good", "deviation", "nextCup")
    private const val MAX_POINTS = 96

    fun eligible(entry: ShotHistory.Entry, passiveConfirmed: Boolean, appOutcomeUnknown: Boolean): Boolean =
        entry.status == ShotHistory.Status.ENDED && entry.endedAtMs != null && entry.machineTimingInterrupted != true &&
            if (entry.curveId == "manual") passiveConfirmed else entry.observedRunning && !appOutcomeUnknown

    fun request(points: List<ShotPoint>, machineSeconds: Int?, language: AppLanguage): String? {
        val brewing = points.filter { it.brewing == true }
        if (brewing.size < 2 || brewing.last().elapsedMs <= brewing.first().elapsedMs) return null
        val count = minOf(brewing.size, MAX_POINTS)
        val selected = (0 until count).map { index -> brewing[index * brewing.lastIndex / (count - 1)] }
        val measurements = buildJsonObject {
            put("machineDurationSeconds", machineSeconds?.let(::JsonPrimitive) ?: JsonNull)
            put("savedBrewingObservationCount", brewing.size)
            put("observedBrewSampleSpanMs", brewing.last().elapsedMs - brewing.first().elapsedMs)
            put("peakObservedPressureBar", brewing.maxOf { it.pressureTenthsBar } / 10.0)
            put("evidence", buildJsonObject {
                put("immutableStageTargetsAvailable", false)
                put("doseGramsAvailable", false)
                put("beverageWeightAvailable", selected.any { it.beverageHundredthsGram != null })
                put("estimatedScaleFlowAvailable", selected.any { it.scaleFlowHundredths != null })
            })
            put("observations", buildJsonArray {
                selected.forEach { p -> add(buildJsonObject {
                    put("elapsedMs", p.elapsedMs)
                    put("pressureBar", p.pressureTenthsBar / 10.0)
                    put("machineFlowMlPerSecond", p.machineFlowTenthsMlPerSecond / 10.0)
                    put("machineWaterMl", p.waterTenthsMl / 10.0)
                    put("machineTemperatureC", p.temperatureHundredthsC / 100.0)
                    put("estimatedScaleFlowGramsPerSecond", p.scaleFlowHundredths?.let { JsonPrimitive(it / 100.0) } ?: JsonNull)
                    put("beverageGrams", p.beverageHundredthsGram?.let { JsonPrimitive(it / 100.0) } ?: JsonNull)
                    put("beverageSource", p.beverageSource?.name?.let(::JsonPrimitive) ?: JsonNull)
                    put("beverageSegment", p.beverageSegmentId?.let(::JsonPrimitive) ?: JsonNull)
                }) }
            })
        }
        return buildJsonObject {
            put("model", AiModelPolicy.FLASH)
            put("max_tokens", 350)
            put("thinking", buildJsonObject { put("type", "disabled") })
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", "Review this espresso extraction in ${language.nativeName} (${language.tag}). " +
                        "Return only a JSON object with exactly three string keys: good, deviation, nextCup. " +
                        "Keep each value to one plain-text line of at most 110 characters, including a localized label. " +
                        "For good (what went well): give an observed time interval and the pressure or flow evidence that supports " +
                        "a favorable or stable phase. If the data do not support one, say so. " +
                        "For deviation (what deviated): give the observed time interval and measured departure from the local trend, " +
                        "or state that no clear anomaly is identifiable. No immutable stage boundaries or recipe targets " +
                        "are supplied, so distinguish a series anomaly from target deviation: never label a stage as " +
                        "passing/failing a target, and don't invent named recipe stages. " +
                        "For nextCup (next cup): propose exactly one change to one variable, keeping all others constant; if the " +
                        "evidence cannot support a change, ask for exactly one measurement instead. Grind and puck " +
                        "distribution are separate, unverified hypotheses, not diagnoses; never prescribe changing both. " +
                        "Do not claim channeling, a root cause, or taste. " +
                        "Use only the observations. Null means unavailable. The series is thinned without interpolation; " +
                        "elapsedMs is relative to sample-series start, not machine extraction time. " +
                        "Machine flow is volumetric in mL/s and machine water is mL; neither is beverage weight or " +
                        "the estimated scale mass flow in g/s. With no beverage measurement, never state beverage grams. " +
                        "With no scale flow, never state scale-derived g/s. The dose is absent: never state a brew ratio. " +
                        "Even when present, label estimated measurements and do not join beverage segments or treat the " +
                        "last sample as guaranteed final yield. No machine commands, long data recaps, links, or markdown.")
                })
                add(buildJsonObject {
                    put("role", "user")
                    put("content", measurements.toString())
                })
            })
        }.toString()
    }

    suspend fun send(transport: AiSafeTransport, request: String): String =
        transport.post("chat/completions", request, String::class, String::class, emptyMap(), emptyMap())

    fun answer(raw: String): String? = runCatching {
        val choice = Json.parseToJsonElement(raw).jsonObject["choices"]?.jsonArray?.singleOrNull()?.jsonObject
            ?: return null
        if (choice["finish_reason"] != JsonPrimitive("stop")) return null
        val message = choice["message"]?.jsonObject ?: return null
        if ((message["tool_calls"] as? JsonArray)?.isNotEmpty() == true) return null
        val text = (message["content"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
        val slots = Json.parseToJsonElement(text) as? JsonObject ?: return null
        if (slots.keys != reviewSlots.toSet()) return null
        val lines = reviewSlots.map { slot ->
            val value = (slots[slot] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
            if (value.any { it.isISOControl() || Character.getType(it) in setOf(
                    Character.FORMAT.toInt(), Character.LINE_SEPARATOR.toInt(), Character.PARAGRAPH_SEPARATOR.toInt()) }) return null
            value.trim().takeIf { it.length in 1..SLOT_CHARS } ?: return null
        }
        lines.joinToString("\n").takeIf { it.length <= RESPONSE_CHARS }
    }.getOrNull()
}

internal enum class ShotReviewStatus { PENDING, READY, FAILED, UNKNOWN }
internal data class ShotReviewResult(val status: ShotReviewStatus, val text: String? = null)
internal data class ShotReviewConfig(val consentRevision: Long, val keyRevision: Long, val language: AppLanguage)

internal interface ShotReviewLedger {
    /** Synchronously persists the attempt fence before the HTTP boundary. */
    fun reserve(id: String): Boolean
    fun finish(id: String, result: ShotReviewResult)
    fun read(id: String): ShotReviewResult?
}

internal class AutomaticShotReview(
    private val scope: CoroutineScope,
    private val current: (ShotReviewConfig) -> Boolean,
    private val ledger: ShotReviewLedger,
    private val send: suspend (ShotReviewConfig, String) -> String,
    private val timeoutMs: Long = 45_000,
    private val publishIfCurrent: (ShotReviewConfig, () -> Unit) -> Boolean = { cfg, publish ->
        if (!current(cfg)) false else { publish(); true }
    },
) {
    private val active = ConcurrentHashMap.newKeySet<String>()
    private val revision = kotlinx.coroutines.flow.MutableStateFlow(0L)
    val changes: kotlinx.coroutines.flow.StateFlow<Long> = revision

    fun result(id: String): ShotReviewResult? = runCatching { ledger.read(id) }.getOrNull()?.let {
        // Process died or the response could not be stored after dispatch. Never replay.
        if (it.status == ShotReviewStatus.PENDING && id !in active) ShotReviewResult(ShotReviewStatus.UNKNOWN) else it
    }

    fun afterPersisted(id: String, config: ShotReviewConfig, points: List<ShotPoint>, machineSeconds: Int?) {
        if (!AiExtractionSamples.validId(id) || !current(config) || !active.add(id)) return
        scope.launch {
            var reserved = false
            var outcome = ShotReviewResult(ShotReviewStatus.UNKNOWN)
            try {
                val request = ShotReviewWire.request(points, machineSeconds, config.language) ?: return@launch
                if (!current(config) || !ledger.reserve(id)) return@launch
                reserved = true
                signal()
                if (!current(config)) { outcome = ShotReviewResult(ShotReviewStatus.FAILED); return@launch }
                val response = withTimeoutOrNull(timeoutMs) { send(config, request) } ?: return@launch
                val text = ShotReviewWire.answer(response)
                outcome = if (text != null) ShotReviewResult(ShotReviewStatus.READY, text)
                    else ShotReviewResult(ShotReviewStatus.FAILED)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: AiFailure) {
                val diag = failure.diagnostic
                val ambiguous = diag?.stage in setOf(AiStage.WRITE, AiStage.RESPONSE_HEADERS, AiStage.RESPONSE_BODY) &&
                    (diag?.httpStatus == null || diag.httpStatus >= 500 ||
                        diag.kind in setOf(AiFailureKind.IO, AiFailureKind.IO_TIMEOUT, AiFailureKind.READ_TIMEOUT))
                outcome = ShotReviewResult(if (ambiguous) ShotReviewStatus.UNKNOWN else ShotReviewStatus.FAILED)
            } catch (_: Exception) {
                // Unknown write boundary: safest to surface uncertainty rather than replay.
            } finally {
                try {
                    if (reserved) runCatching {
                        if (!publishIfCurrent(config) { ledger.finish(id, outcome) })
                            ledger.finish(id, ShotReviewResult(ShotReviewStatus.UNKNOWN))
                    }
                } finally {
                    active.remove(id)
                    if (reserved) signal()
                }
            }
        }
    }

    private fun signal() {
        while (true) {
            val old = revision.value
            if (revision.compareAndSet(old, old + 1)) return
        }
    }
}

/** Check two independently persistent grants. When disabling, either successful revocation is enough. */
internal class ShotReviewPreferences(
    private val loadEpoch: () -> String?,
    private val persistEpoch: (String?) -> Boolean,
    private val grantExists: () -> Boolean,
    private val revokeGrant: () -> Boolean,
    private val createGrant: () -> Boolean,
) {
    private data class State(val epoch: String?, val revision: Long)
    private fun load() = runCatching { if (grantExists()) loadEpoch() else null }.getOrNull()
    @Volatile private var state = State(load(), 0)
    @Volatile var revocationUnconfirmed = false
        private set
    fun enabledRevision() = state.let { if (it.epoch != null) it.revision else null }
    fun revision(epoch: String?): Long? = state.let { if (epoch != null && epoch == it.epoch) it.revision else null }

    @Synchronized fun publishForRevision(epoch: String, expected: Long, publish: () -> Unit): Boolean {
        if (revision(epoch) != expected) return false
        publish()
        return true
    }

    @Synchronized fun set(epoch: String?): Boolean {
        // Fence sends and delivery immediately even when durable storage cannot be reached.
        val next = State(null, state.revision + 1)
        state = next
        if (epoch == null) {
            val revoked = runCatching(revokeGrant).getOrDefault(false)
            val cleared = runCatching { persistEpoch(null) }.getOrDefault(false)
            revocationUnconfirmed = !revoked && !cleared
            return !revocationUnconfirmed
        }
        val saved = runCatching { persistEpoch(epoch) && createGrant() }.getOrDefault(false)
        if (saved) {
            state = next.copy(epoch = epoch)
            revocationUnconfirmed = false
        }
        return saved
    }
}
