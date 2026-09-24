package io.openhoyi.mobile

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Old UniApp history is kept separate: its points do not contain native water/temperature samples. */
data class LegacyPoint(val seconds: Double, val pressureBar: Double, val waterFlow: Double,
    val oldWFlow: Double, val oldWTrend: Double)
data class LegacyShot(val id: String, val createdAtMs: Long, val durationSec: Int,
    val chartSlot: Int?, val profileName: String, val snapshotJson: String?, val points: List<LegacyPoint>)

object LegacyHistoryCodec {
    const val MAX_BYTES = 16 * 1024 * 1024
    private const val MAX_SHOTS = 2000
    private const val MAX_POINTS = 2000

    fun parse(text: String): List<LegacyShot> {
        if (text.toByteArray(Charsets.UTF_8).size > MAX_BYTES) throw IOException("Legacy history too large")
        val root = try { JSONObject(text) } catch (_: Exception) { throw IOException("Invalid legacy JSON") }
        if (root.optInt("version", -1) != 1) throw IOException("Unsupported legacy history version")
        val items = root.optJSONArray("items") ?: throw IOException("Missing legacy items")
        if (items.length() > MAX_SHOTS) throw IOException("Too many legacy shots")
        val shots = (0 until items.length()).map { index -> parseShot(items.optJSONObject(index) ?:
            throw IOException("Invalid legacy item $index"), index) }
        if (shots.map(LegacyShot::id).toSet().size != shots.size) throw IOException("Duplicate legacy IDs")
        return shots
    }

    fun encode(shots: List<LegacyShot>): String {
        val items = JSONArray()
        shots.forEach { shot ->
            val points = JSONObject()
            fun values(block: (LegacyPoint) -> Double): JSONArray = JSONArray().also { array ->
                shot.points.forEach { array.put(block(it)) }
            }
            points.put("t", values(LegacyPoint::seconds))
            points.put("press", values(LegacyPoint::pressureBar))
            points.put("flow", values(LegacyPoint::waterFlow))
            points.put("wFlow", values(LegacyPoint::oldWFlow))
            points.put("wTrend", values(LegacyPoint::oldWTrend))
            items.put(JSONObject().put("id", shot.id).put("createdAt", shot.createdAtMs)
                .put("durationSec", shot.durationSec)
                .put("chartSlot", shot.chartSlot ?: JSONObject.NULL)
                .put("profileName", shot.profileName)
                .put("snapshot", shot.snapshotJson?.let(::JSONObject) ?: JSONObject.NULL)
                .put("points", points))
        }
        return JSONObject().put("version", 1).put("items", items).toString()
    }

    private fun parseShot(item: JSONObject, index: Int): LegacyShot {
        fun invalid(): Nothing = throw IOException("Invalid legacy shot $index")
        val id = item.opt("id") as? String ?: invalid()
        if (!id.matches(Regex("[A-Za-z0-9-]{1,64}"))) invalid()
        val createdAt = integer(item.opt("createdAt")) ?: invalid()
        val duration = integer(item.opt("durationSec")) ?: invalid()
        if (createdAt !in 946684800000L..4102444800000L || duration !in 15..7200) invalid()
        val slotNumber = if (item.isNull("chartSlot")) null else integer(item.opt("chartSlot")) ?: invalid()
        if (slotNumber != null && slotNumber !in 0..8) invalid()
        val slot = slotNumber?.toInt()
        val name = (item.opt("profileName") as? String ?: "").trim()
        if (name.length > 500) invalid()
        val snapshot = item.optJSONObject("snapshot")?.toString()
        val points = item.optJSONObject("points") ?: invalid()
        val keys = listOf("t", "press", "flow", "wFlow", "wTrend")
        val arrays = keys.map { points.optJSONArray(it) ?: invalid() }
        val count = arrays.first().length()
        if (count !in 1..MAX_POINTS || arrays.any { it.length() != count }) invalid()
        var previous = -1.0
        val parsed = (0 until count).map { pointIndex ->
            val values = arrays.map { numeric(it.opt(pointIndex)) ?: invalid() }
            val time = values[0]
            if (time < 0 || time > duration + 10 || time < previous ||
                values[1] !in 0.0..20.0 || values[2] !in 0.0..100.0 ||
                values[3] !in -1000.0..1000.0 || values[4] !in -1000.0..1000.0) invalid()
            previous = time
            LegacyPoint(time, values[1], values[2], values[3], values[4])
        }
        return LegacyShot(id, createdAt, duration.toInt(), slot, name, snapshot, parsed)
    }

    private fun numeric(value: Any?): Double? = (value as? Number)?.toDouble()?.takeIf(Double::isFinite)
    private fun integer(value: Any?): Long? = numeric(value)?.takeIf {
        it >= 0 && it <= Long.MAX_VALUE.toDouble() && it % 1.0 == 0.0
    }?.toLong()
}

class LegacyHistoryStore(private val file: File) {
    data class ImportReport(val added: Int, val total: Int)

    @Synchronized fun list(): List<LegacyShot> = if (!file.exists()) emptyList() else {
        if (!file.isFile || file.length() > LegacyHistoryCodec.MAX_BYTES) throw IOException("Invalid legacy store")
        LegacyHistoryCodec.parse(utf8(file.readBytes())).sortedByDescending(LegacyShot::createdAtMs)
    }

    @Synchronized fun import(input: InputStream): ImportReport {
        val bytes = input.use { stream ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                out.write(buffer, 0, count)
                if (out.size() > LegacyHistoryCodec.MAX_BYTES) throw IOException("Legacy file too large")
            }
            out.toByteArray()
        }
        if (bytes.size > LegacyHistoryCodec.MAX_BYTES) throw IOException("Legacy file too large")
        val incoming = LegacyHistoryCodec.parse(utf8(bytes))
        val existing = list()
        val merged = LinkedHashMap(existing.associateBy(LegacyShot::id))
        var added = 0
        incoming.forEach { shot ->
            val old = merged[shot.id]
            if (old != null && old != shot) throw IOException("Conflicting legacy shot ID")
            if (old == null) { merged[shot.id] = shot; added++ }
        }
        if (merged.size > 2000) throw IOException("Too many merged legacy shots")
        if (added == 0) return ImportReport(0, merged.size)
        val encoded = LegacyHistoryCodec.encode(merged.values.toList())
        if (encoded.toByteArray(Charsets.UTF_8).size > LegacyHistoryCodec.MAX_BYTES)
            throw IOException("Merged legacy history too large")
        val directory = file.parentFile ?: throw IOException("No legacy store directory")
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create legacy store directory")
        val temporary = File.createTempFile("legacy-history-", ".tmp", directory)
        try {
            temporary.writeText(encoded, Charsets.UTF_8)
            try { Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE) }
            catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally { temporary.delete() }
        return ImportReport(added, merged.size)
    }

    private fun utf8(bytes: ByteArray): String = try {
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    } catch (_: Exception) { throw IOException("Invalid UTF-8 legacy file") }
}
