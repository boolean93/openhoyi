package io.openhoyi.mobile

import io.openhoyi.session.MachineWriteRecoveryState
import io.openhoyi.session.SplitMachineWriteJournal
import org.json.JSONArray
import org.json.JSONObject

/** Wire-compatible candidate for the existing pending_machine_write.json marker; not wired. */
internal object SplitMachineWriteMarkerCodec {
    private const val EXTENSION = "ownership_v2"
    private const val VERSION = 1
    internal const val MAX_BYTES = 4096

    fun decode(raw: ByteArray): SplitMachineWriteJournal.Snapshot = runCatching {
        require(raw.size in 1..MAX_BYTES && raw.all { it.toInt() in 0..127 })
        decode(StrictMarkerJson(raw.toString(Charsets.US_ASCII)).read())
    }.getOrDefault(SplitMachineWriteJournal.Snapshot.Blocked)

    fun encode(entries: List<SplitMachineWriteJournal.Entry>): JSONObject {
        require(SplitMachineWriteJournal.valid(entries))
        return JSONObject().put("kind", MachineWriteRecoveryState.Kind.UNKNOWN.name)
            .put("address", JSONObject.NULL)
            .put(EXTENSION, JSONObject().put("version", VERSION).put("entries", JSONArray(entries.map {
                JSONObject().put("operation", it.kind.name).put("address", it.address)
            })))
    }

    /** An absent marker alone cannot prove Empty; the legacy preferences may still be pending. */
    fun decode(json: JSONObject): SplitMachineWriteJournal.Snapshot = runCatching {
        require(json.keys().asSequence().toSet() == setOf("kind", "address", EXTENSION))
        require(json.getString("kind") == MachineWriteRecoveryState.Kind.UNKNOWN.name &&
            json.has("address") && json.isNull("address"))
        val extension = json.getJSONObject(EXTENSION)
        require(extension.keys().asSequence().toSet() == setOf("version", "entries"))
        require(extension.get("version") == VERSION)
        val values = extension.getJSONArray("entries")
        require(values.length() in 1..SplitMachineWriteJournal.Kind.entries.size)
        val entries = List(values.length()) { index ->
            val entry = values.getJSONObject(index)
            require(entry.keys().asSequence().toSet() == setOf("operation", "address"))
            SplitMachineWriteJournal.Entry(SplitMachineWriteJournal.Kind.valueOf(entry.getString("operation")), entry.getString("address"))
        }
        require(SplitMachineWriteJournal.valid(entries))
        SplitMachineWriteJournal.Snapshot.Pending(entries)
    }.getOrDefault(SplitMachineWriteJournal.Snapshot.Blocked)

    /** Generated fields are printable ASCII; never give a permissive JSON parser ambiguous bytes. */
    private class StrictMarkerJson(private val text: String) {
        private var at = 0
        private fun peek(): Char? {
            while (text.getOrNull(at)?.let { it in " \r\n\t" } == true) at++
            return text.getOrNull(at)
        }
        private fun take(expected: Char) { require(peek() == expected); at++ }
        private fun quoted(): String {
            take('"')
            val start = at
            while (text.getOrNull(at) != '"') {
                val ch = text.getOrNull(at) ?: error("unterminated string")
                require(ch in ' '..'~' && ch != '\\')
                at++
            }
            return text.substring(start, at++)
        }
        private fun value(depth: Int): Any = when (peek()) {
            '{' -> obj(depth + 1)
            '[' -> {
                require(depth < 3); take('[')
                val items = JSONArray()
                if (peek() != ']') while (true) {
                    require(items.length() < SplitMachineWriteJournal.Kind.entries.size)
                    items.put(obj(depth + 1))
                    if (peek() != ',') break
                    take(',')
                }
                take(']'); items
            }
            '"' -> quoted()
            'n' -> { require(text.startsWith("null", at)); at += 4; JSONObject.NULL }
            '1' -> { at++; 1 }
            else -> error("invalid marker value")
        }
        private fun obj(depth: Int): JSONObject {
            require(depth <= 3); take('{')
            val fields = JSONObject()
            if (peek() != '}') while (true) {
                val key = quoted()
                require(fields.length() < 3 && !fields.has(key))
                take(':')
                fields.put(key, value(depth))
                if (peek() != ',') break
                take(',')
            }
            take('}')
            return fields
        }
        fun read(): JSONObject = obj(0).also { require(peek() == null) }
    }
}
