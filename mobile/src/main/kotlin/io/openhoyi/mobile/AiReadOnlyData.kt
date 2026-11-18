package io.openhoyi.mobile

import android.content.Context
import io.openhoyi.protocol.ExtractionTelemetry
import io.openhoyi.protocol.IdleTelemetry
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** This is the entire capability surface given to the model: no controller or mutable store. */
internal interface AiReadOnlyData {
    fun currentStatus(): String
    fun curves(query: String = "", offset: Int = 0, limit: Int = 10): String
    fun history(curveId: String? = null, offset: Int = 0, limit: Int = 10): String
    fun samples(shotId: String, offset: Int = 0, limit: Int = 100): String = "{\"status\":\"unavailable\"}"
}

/** Read only on demand. The caller supplies a main-thread-safe snapshot supplier, not a service. */
internal class LocalAiReadOnlyData(context: Context, private val snapshot: () -> MobileSnapshot?) : AiReadOnlyData {
    private val context = context.applicationContext

    override fun currentStatus(): String = safe {
        val state = snapshot() ?: return@safe unavailable()
        val now = MobileMonotonicClock.elapsedRealtime()
        val machine = LiveTelemetry.machine(state.coffee, state.coffeeState, state.coffeeAt, now)
        val scale = LiveTelemetry.scale(state.scaleObservation, state.scaleState, now)
        val machineJson = JSONObject().put("connectionState", state.coffeeState.name)
            .put("ageMs", age(state.coffeeAt, now)).put("fresh", machine != null)
            .put("observedMode", when (machine) {
                is IdleTelemetry -> "idle"
                is ExtractionTelemetry -> "extraction"
                else -> "unknown"
            })
        when (machine) {
            is IdleTelemetry -> machineJson
                .put("brewTemperatureC", machine.brewTemperatureHundredthsC / 100.0)
                .put("brewPressureBar", machine.brewPressureTenthsBar / 10.0)
                .put("steamTemperatureC", machine.steamTemperatureHundredthsC / 100.0)
                .put("steamPressureBar", machine.steamPressureTenthsBar / 10.0)
            is ExtractionTelemetry -> machineJson
                .put("brewTemperatureC", machine.brewTemperatureHundredthsC / 100.0)
                .put("brewPressureBar", machine.pressureTenthsBar / 10.0)
            else -> Unit
        }
        JSONObject().put("status", "available").put("capturedAtEpochMs", System.currentTimeMillis())
            .put("machine", machineJson)
            .put("scale", JSONObject().put("connectionState", state.scaleState.name)
                .put("ageMs", age(state.scaleObservation?.receivedAtMs, now))
                .put("fresh", scale != null)
                .put("weightGrams", scale?.hundredthsGram?.div(100.0) ?: JSONObject.NULL))
    }

    override fun curves(query: String, offset: Int, limit: Int): String {
        if (!AiReadOnlyQuery.validQuery(query)) return AiReadOnlyQuery.invalidArguments()
        return safe {
            // No application lazy stores: querying must not initialize a recovering writer.
            val factory = context.assets.open("factory_curves_v3.tsv").use(FactoryCurveCatalog::load)
            val customText = readBounded(File(context.filesDir, "custom_curves_v1.json"))
            val custom = CustomCurveStore(object : CustomCurveStore.Storage {
                override fun read() = customText
                override fun write(value: String) { error("Read-only capability") }
            }).list()
            val rows = custom.map(AiReadOnlyQuery::fromDocument) + factory.map(AiReadOnlyQuery::fromFactory)
            AiReadOnlyQuery.curves(rows, query, offset, limit)
        }
    }

    override fun history(curveId: String?, offset: Int, limit: Int): String {
        if (!AiReadOnlyQuery.validCurveId(curveId)) return AiReadOnlyQuery.invalidArguments()
        return safe {
            val document = readBounded(File(context.filesDir, "brew_journal_v1.json"))
            val observations = document?.let(BrewJournal::readOnlyObservations).orEmpty()
            AiReadOnlyQuery.history(observations, curveId, offset, limit)
        }
    }

    override fun samples(shotId: String, offset: Int, limit: Int): String =
        AiExtractionSamples.read(shotId, offset, limit,
            observations = {
                readBounded(File(context.filesDir, "brew_journal_v1.json"))
                    ?.let(BrewJournal::readOnlyObservations).orEmpty()
            }, load = { ShotSamplesStore(File(context.filesDir, "shot_samples")).load(it) }).toString()

    private fun age(at: Long?, now: Long): Any =
        if (at != null && at >= 0 && now >= at) now - at else JSONObject.NULL

    /** Read from one open descriptor and bound bytes even if an atomic replacement races this read. */
    private fun readBounded(file: File): String? {
        if (!file.exists()) return null
        require(file.length() <= MAX_DOCUMENT_BYTES)
        return file.inputStream().use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= MAX_DOCUMENT_BYTES)
                output.write(buffer, 0, count)
            }
            output.toByteArray().toString(Charsets.UTF_8)
        }
    }

    private fun safe(read: () -> JSONObject): String = try {
        val value = read().toString()
        if (value.length <= MAX_OUTPUT_CHARS) value else unavailable().toString()
    } catch (_: Exception) { unavailable().toString() }

    private fun unavailable() = JSONObject().put("status", "unavailable")

    private companion object {
        const val MAX_OUTPUT_CHARS = 6000
        const val MAX_DOCUMENT_BYTES = 2 * 1024 * 1024
    }
}

/** Pure projections, independently testable without Android, storage writers or a model. */
internal object AiReadOnlyQuery {
    data class Curve(val id: String, val name: String, val origin: String,
        val temperatureC: Int, val targetWeightGrams: Double?, val stageCount: Int,
        val recipe: Recipe? = null)
    data class RecipeStage(val controlMode: String, val target: Double?, val waterMl: Double)
    data class Recipe(val stages: List<RecipeStage>, val totalWaterLimitMl: Double,
        val waterLimitSource: String, val pressureLogic: Boolean?, val variableFlowLogic: Boolean?,
        val firstSegmentFlowMode: Boolean?, val preinfusionSeconds: Int?,
        val firstDurationSeconds: Int?, val weightCompensationGrams: Double?)

    fun fromDocument(document: CustomCurveDocument): Curve {
        val configuration = document.configuration
        val unknown = document.controlMode == CustomCurveDocument.ControlMode.FLOW_RAW
        val preview = CurveStageParameters.preview(document)
        val stages = document.stages.take(4).mapIndexed { index, stage ->
            val mode = when (preview.stages[index].unit) {
                CurveStageParameters.Unit.PRESSURE_BAR -> "pressure"
                CurveStageParameters.Unit.FLOW_ML_PER_SECOND -> "flow"
                CurveStageParameters.Unit.RAW_UNKNOWN -> "unknown"
            }
            RecipeStage(mode, if (mode == "unknown") null else stage.target / 10.0, stage.waterTenthsMl / 10.0)
        }
        return Curve(document.id, document.name, "local", document.temperatureC,
            document.targetHundredthsGram.takeIf { it > 0 }?.div(100.0), stages.size,
            Recipe(stages, document.totalWaterLimitMl?.toDouble() ?: stages.sumOf { it.waterMl },
                if (document.totalWaterLimitMl == null) "stage_sum" else "configured",
                if (unknown) null else configuration?.pressureLogic ?: false,
                if (unknown) null else configuration?.variableFlowLogic ?: false,
                if (unknown) null else configuration?.firstSegmentFlowMode ?: false,
                if (unknown) null else configuration?.preinfusionSeconds ?: 0,
                if (unknown) null else configuration?.firstDurationSeconds ?: 0,
                if (unknown) null else (configuration?.compensationHundredthsGram ?: 0) / 100.0))
    }

    fun fromFactory(curve: FactoryCurve): Curve {
        val stages = (0 until curve.segmentCount.coerceIn(0, 4)).map { index ->
            RecipeStage(if (curve.variableFlowLogic && index > 0) "flow" else "pressure",
                curve.targets[index] / 10.0, curve.segmentFlowMl[index].toDouble())
        }
        return Curve(curve.id, curve.name, "factory", curve.temperatureC,
            curve.weightTenthsGram.takeIf { it > 0 }?.div(10.0), stages.size,
            Recipe(stages, curve.flowMl.toDouble(), "configured", curve.pressureLogic,
                curve.variableFlowLogic, false, curve.preinfusionSeconds, curve.firstDurationSeconds,
                curve.weightCompensationTenthsGram / 10.0))
    }

    private fun recipeJson(recipe: Recipe): JSONObject {
        val stages = recipe.stages.take(4)
        val modes = stages.map { it.controlMode }.distinct()
        return JSONObject().put("valueKind", "recipe_settings_not_live_readings")
            .put("controlMode", when {
                modes.isEmpty() || "unknown" in modes -> "unknown"
                modes.size > 1 -> "mixed"
                else -> modes.single()
            }).put("totalWaterLimitMl", recipe.totalWaterLimitMl).put("waterLimitSource", recipe.waterLimitSource)
            .put("configuration", JSONObject()
                .put("pressureLogic", recipe.pressureLogic ?: JSONObject.NULL)
                .put("variableFlowLogic", recipe.variableFlowLogic ?: JSONObject.NULL)
                .put("firstSegmentFlowMode", recipe.firstSegmentFlowMode ?: JSONObject.NULL)
                .put("preinfusionSeconds", recipe.preinfusionSeconds ?: JSONObject.NULL)
                .put("firstDurationSeconds", recipe.firstDurationSeconds ?: JSONObject.NULL)
                .put("weightCompensationGrams", recipe.weightCompensationGrams ?: JSONObject.NULL))
            .put("stages", JSONArray(stages.mapIndexed { index, stage ->
                JSONObject().put("index", index + 1).put("controlMode", stage.controlMode)
                    .put("waterMl", stage.waterMl).also { json ->
                        if (stage.controlMode == "pressure") json.put("targetBar", stage.target ?: JSONObject.NULL)
                        if (stage.controlMode == "flow") json.put("targetMlPerSecond", stage.target ?: JSONObject.NULL)
                    }
            }))
    }

    fun validQuery(query: String) = query.length <= 100
    fun validCurveId(curveId: String?) = curveId == null || curveId.length <= 80
    fun invalidArguments() = JSONObject().put("status", "invalid_arguments").toString()

    fun curves(rows: List<Curve>, query: String, offset: Int, limit: Int): JSONObject {
        if (!validQuery(query)) return JSONObject(invalidArguments())
        val matching = rows.filter { it.name.contains(query, ignoreCase = true) || it.id.contains(query, ignoreCase = true) }
        return page(matching, offset, limit) { curve ->
            JSONObject().put("id", curve.id.take(80)).put("name", curve.name.take(100))
                .put("origin", curve.origin).put("temperatureC", curve.temperatureC)
                .put("targetWeightGrams", curve.targetWeightGrams ?: JSONObject.NULL)
                .put("stageCount", curve.stageCount).also { json ->
                    curve.recipe?.let { json.put("recipe", recipeJson(it)) }
                }
        }
    }

    fun history(rows: List<BrewJournal.Observation>, curveId: String?, offset: Int, limit: Int): JSONObject {
        if (!validCurveId(curveId)) return JSONObject(invalidArguments())
        val matching = rows.filter { curveId == null || it.curveId == curveId }
        return page(matching, offset, limit) { shot ->
            JSONObject().put("shotId", shot.id).put("startedAtEpochMs", shot.startedAtMs).put("status", shot.status)
                .put("curveId", shot.curveId.take(80))
                .put("observedDurationSeconds", shot.observedMachineElapsedSeconds
                    ?.takeUnless { shot.machineTimingInterrupted == true } ?: JSONObject.NULL)
                .put("preInterruptionMachineElapsedSeconds", shot.observedMachineElapsedSeconds
                    ?.takeIf { shot.machineTimingInterrupted == true } ?: JSONObject.NULL)
                .put("machineTimingInterrupted", shot.machineTimingInterrupted ?: JSONObject.NULL)
                .put("finalWeightGrams", shot.weightHundredthsGram?.div(100.0) ?: JSONObject.NULL)
        }
    }

    private fun <T> page(rows: List<T>, requestedOffset: Int, requestedLimit: Int,
        project: (T) -> JSONObject): JSONObject {
        val offset = requestedOffset.coerceIn(0, 500)
        val limit = requestedLimit.coerceIn(1, 10)
        val selected = rows.drop(offset).take(limit).map(project).toMutableList()
        fun result(): JSONObject {
            val end = offset + selected.size
            val more = end < rows.size
            return JSONObject().put("status", "available").put("capturedAtEpochMs", System.currentTimeMillis())
                .put("items", JSONArray(selected)).put("offset", offset).put("total", rows.size)
                .put("truncated", more)
                // A hard offset ceiling must not return a cursor which silently wraps back to 500.
                .put("nextOffset", if (more && selected.isNotEmpty() && end <= 500) end else JSONObject.NULL)
                .put("dataOnly", true)
        }
        var result = result()
        while (result.toString().length > 6000 && selected.isNotEmpty()) {
            selected.removeAt(selected.lastIndex)
            result = result()
        }
        return result
    }
}
