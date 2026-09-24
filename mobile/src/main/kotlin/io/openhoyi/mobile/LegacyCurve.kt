package io.openhoyi.mobile

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Imported curve data is deliberately separate from CurveLibrary and BLE frame authorization. */
data class LegacyCurve(val index: Int, val name: String, val category: String, val factory: Boolean,
    val temperatureC: Int?, val waterMl: Int?, val weightTenthsGram: Int?, val segments: Int?,
    val rawJson: String)
data class LegacyCurveBundle(val factoryVersion: Int?, val categoryLabels: Map<String, String>,
    val curves: List<LegacyCurve>)

object LegacyCurveCodec {
    const val MAX_BYTES = 16 * 1024 * 1024
    private const val MAX_CURVES = 2000

    fun parse(bytes: ByteArray): LegacyCurveBundle {
        if (bytes.size > MAX_BYTES) throw IOException("Legacy curves too large")
        val text = strictUtf8(bytes)
        val root = try { JSONObject(text) } catch (_: Exception) { throw IOException("Invalid legacy curves JSON") }
        if (root.optString("format") != "openhoyi-legacy-curves-v1") throw IOException("Unsupported curves format")
        val items = root.optJSONArray("items") ?: throw IOException("Missing curve list")
        if (items.length() !in 1..MAX_CURVES) throw IOException("Invalid curve count")
        val labels = mutableMapOf<String, String>()
        root.optJSONObject("categoryConfig")?.optJSONObject("labels")?.let { source ->
            val keys = source.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val label = source.opt(key) as? String ?: continue
                if (key.length <= 100 && label.length <= 200) labels[key] = label
            }
        }
        fun number(row: JSONObject, key: String): Int? = (row.opt(key) as? Number)?.toDouble()
            ?.takeIf { it.isFinite() && it % 1.0 == 0.0 && it in -100000.0..100000.0 }?.toInt()
        val curves = (0 until items.length()).map { index ->
            if (items.isNull(index)) return@map LegacyCurve(index, "空槽位 ${index + 1}", "", false,
                null, null, null, null, "null")
            val row = items.optJSONObject(index) ?: throw IOException("Invalid curve row $index")
            val originalName = row.opt("name")
            if (originalName != null && originalName != JSONObject.NULL && originalName !is String)
                throw IOException("Invalid curve name $index")
            val name = (originalName as? String).orEmpty().trim()
            if (name.length > 500) throw IOException("Curve name too long")
            val category = (row.opt("category") as? String).orEmpty().take(100)
            LegacyCurve(index, name.ifBlank { "未命名曲线 ${index + 1}" }, category,
                row.opt("factory") == true, number(row, "temp"), number(row, "flow"),
                number(row, "weight"), number(row, "seg"), row.toString())
        }
        val version = (root.opt("factoryVersion") as? Number)?.toInt()
        return LegacyCurveBundle(version, labels, curves)
    }

    private fun strictUtf8(bytes: ByteArray): String = try {
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    } catch (_: Exception) { throw IOException("Invalid UTF-8 curves file") }
}

class LegacyCurveStore(private val file: File) {
    data class ImportReport(val changed: Boolean, val count: Int)

    @Synchronized fun load(): LegacyCurveBundle? {
        if (!file.exists()) return null
        if (!file.isFile || file.length() > LegacyCurveCodec.MAX_BYTES) throw IOException("Invalid curves store")
        return LegacyCurveCodec.parse(file.readBytes())
    }

    @Synchronized fun import(input: InputStream): ImportReport {
        val bytes = input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count)
                if (output.size() > LegacyCurveCodec.MAX_BYTES) throw IOException("Curves file too large")
            }
            output.toByteArray()
        }
        val parsed = LegacyCurveCodec.parse(bytes)
        if (file.isFile && file.length() <= LegacyCurveCodec.MAX_BYTES &&
            file.readBytes().contentEquals(bytes)) return ImportReport(false, parsed.curves.size)
        val directory = file.parentFile ?: throw IOException("No curves store directory")
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create curves store directory")
        val temporary = File.createTempFile("legacy-curves-", ".tmp", directory)
        try {
            temporary.writeBytes(bytes)
            try { Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE) }
            catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally { temporary.delete() }
        return ImportReport(true, parsed.curves.size)
    }
}
