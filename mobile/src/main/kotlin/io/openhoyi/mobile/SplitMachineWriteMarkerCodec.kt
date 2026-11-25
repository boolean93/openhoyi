package io.openhoyi.mobile

import io.openhoyi.session.MachineWriteRecoveryState
import io.openhoyi.protocol.SleepDay
import io.openhoyi.protocol.WeeklySleepDay
import java.util.Locale
import io.openhoyi.session.SplitMachineWriteJournal
import org.json.JSONArray
import org.json.JSONObject

/** Wire-compatible candidate for the existing pending_machine_write.json marker; not wired. */
internal object SplitMachineWriteMarkerCodec {
    private const val EXTENSION = "ownership_v2"
    private const val LEGACY_VERSION = 1
    private const val TYPED_VERSION = 2
    internal const val MAX_BYTES = 4096

    fun decode(raw: ByteArray): SplitMachineWriteJournal.Snapshot = runCatching {
        require(raw.size in 1..MAX_BYTES && raw.all { it.toInt() in 0..127 })
        decode(StrictMarkerJson(raw.toString(Charsets.US_ASCII)).read())
    }.getOrDefault(SplitMachineWriteJournal.Snapshot.Blocked)

    fun encode(entries: List<SplitMachineWriteJournal.Entry>): JSONObject {
        require(SplitMachineWriteJournal.valid(entries))
        val version=if(entries.all {it.target!=null}) TYPED_VERSION else LEGACY_VERSION
        return JSONObject().put("kind", MachineWriteRecoveryState.Kind.UNKNOWN.name)
            .put("address", JSONObject.NULL)
            .put(EXTENSION, JSONObject().put("version", version).put("entries", JSONArray(entries.map { entry ->
                JSONObject().put("operation", entry.kind.name).put("address", entry.address).also { json ->
                    entry.target?.let { json.put("target",encodeTarget(it)) }
                }
            })))
    }

    /** An absent marker alone cannot prove Empty; the legacy preferences may still be pending. */
    fun decode(json: JSONObject): SplitMachineWriteJournal.Snapshot = runCatching {
        require(json.keys().asSequence().toSet() == setOf("kind", "address", EXTENSION))
        require(json.getString("kind") == MachineWriteRecoveryState.Kind.UNKNOWN.name &&
            json.has("address") && json.isNull("address"))
        val extension = json.getJSONObject(EXTENSION)
        require(extension.keys().asSequence().toSet() == setOf("version", "entries"))
        val version=extension.get("version")
        require(version==LEGACY_VERSION || version==TYPED_VERSION)
        val values = extension.getJSONArray("entries")
        require(values.length() in 1..SplitMachineWriteJournal.Kind.entries.size)
        val entries = List(values.length()) { index ->
            val entry = values.getJSONObject(index)
            val typed=version==TYPED_VERSION
            require(entry.keys().asSequence().toSet() == if(typed) setOf("operation","address","target") else setOf("operation","address"))
            SplitMachineWriteJournal.Entry(SplitMachineWriteJournal.Kind.valueOf(entry.getString("operation")),
                entry.getString("address"),if(typed) decodeTarget(entry.getString("target")) else null)
        }
        require(SplitMachineWriteJournal.valid(entries))
        SplitMachineWriteJournal.Snapshot.Pending(entries)
    }.getOrDefault(SplitMachineWriteJournal.Snapshot.Blocked)

    private fun encodeTarget(target:SplitMachineWriteJournal.Target):String = when(target) {
        is SplitMachineWriteJournal.Target.Boiler -> "B:${target.boiler.name}:${if(target.enabled) 1 else 0}"
        is SplitMachineWriteJournal.Target.Weekly -> "W:"+target.days.joinToString("") { day ->
            String.format(Locale.ROOT,"%d%02d%02d%02d%02d",if(day.enabled) 1 else 0,
                day.time.sleepHour,day.time.sleepMinute,day.time.wakeHour,day.time.wakeMinute)
        }
    }

    private fun decodeTarget(encoded:String):SplitMachineWriteJournal.Target {
        if(encoded.startsWith("B:")) {
            val parts=encoded.split(':')
            require(parts.size==3 && parts[2] in setOf("0","1"))
            return SplitMachineWriteJournal.Target.Boiler(
                SplitMachineWriteJournal.Target.BoilerId.valueOf(parts[1]),parts[2]=="1")
        }
        require(encoded.startsWith("W:") && encoded.length==65)
        val fields=encoded.substring(2)
        require(fields.all {it in '0'..'9'})
        return SplitMachineWriteJournal.Target.Weekly(fields.chunked(9).map { day ->
            require(day[0] in '0'..'1')
            WeeklySleepDay(day[0]=='1',SleepDay(day.substring(1,3).toInt(),day.substring(3,5).toInt(),
                day.substring(5,7).toInt(),day.substring(7,9).toInt()))
        })
    }

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
            '2' -> { at++; 2 }
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
