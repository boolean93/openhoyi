package io.openhoyi.mobile

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

/** Durable local transmission journal, never evidence of the machine's saved/current recipe.
 * One writer owns this store. Storage must synchronously and atomically persist the whole value.
 * Previous SENT snapshots and acknowledged UNKNOWN attempts are retained, not overwritten.
 */
class MachineLeverRecipeStore(private val storage: Storage) {
    interface Storage { fun read(): String?; fun write(value: String): Boolean }
    enum class State { PENDING, SENT, UNKNOWN, FAILED }
    data class Snapshot(val address: String, val curveId: String, val name: String,
        val frameHex: String, val createdAtMs: Long, val firmware: String)
    data class Record(val token: String, val snapshot: Snapshot, val state: State,
        val acknowledged: Boolean = false)

    private var usable = true
    private var records: List<Record> = try {
        storage.read()?.let(::decode).orEmpty().map {
            // A process restart cannot establish whether an in-flight write reached the machine.
            if (it.state == State.PENDING) it.copy(state = State.UNKNOWN) else it
        }
    } catch (_: Exception) { usable = false; emptyList() }

    val available: Boolean @Synchronized get() = usable
    val blockingAttempt: Record? @Synchronized get() = unresolved()
    @Synchronized fun pending(token: String): Boolean = usable && records.any { it.token == token && it.state == State.PENDING }
    val active: Boolean @Synchronized get() = records.any { it.state == State.PENDING }
    val blocked: Boolean @Synchronized get() = !usable || records.any {
        it.state == State.PENDING || (it.state == State.UNKNOWN && !it.acknowledged)
    }

    /** Last successful transport callback, not a machine readback. Never returns a pending draft. */
    @Synchronized fun recordFor(address: String?): Record? = normalizeOrNull(address)?.let { key ->
        records.lastOrNull { it.snapshot.address == key && it.state == State.SENT }
    }
    @Synchronized fun attemptFor(address: String?): Record? = normalizeOrNull(address)?.let { key ->
        records.lastOrNull { it.snapshot.address == key }
    }
    @Synchronized fun unresolved(): Record? = records.firstOrNull {
        it.state == State.PENDING || (it.state == State.UNKNOWN && !it.acknowledged)
    }

    /** Persist PENDING before the caller may dispatch. Null must mean DO NOT SEND. */
    @Synchronized fun begin(snapshot: Snapshot): String? {
        if (blocked || records.size >= MAX_RECORDS) return null
        val safe = try { validate(snapshot) } catch (_: Exception) { return null }
        val token = UUID.randomUUID().toString()
        val next = records + Record(token, safe, State.PENDING)
        return if (commit(next)) token else null
    }

    /** A terminal result only owns the currently pending token; late/duplicate callbacks do nothing.
     * SENT means GATT write completed, not durable save confirmed. No transition ever verifies a recipe.
     */
    @Synchronized fun complete(token: String, state: State): Boolean {
        if (!usable || state == State.PENDING) return false
        val index = records.indexOfFirst { it.token == token && it.state == State.PENDING }
        if (index < 0) return false
        val next = records.toMutableList().apply { set(index, get(index).copy(state = state)) }
        if (commit(next)) return true
        // If persistence failed after a physical dispatch, do not continue displaying PENDING/SENT.
        records = records.toMutableList().apply { set(index, get(index).copy(state = State.UNKNOWN)) }
        return false
    }

    /** Explicit human acknowledgement only releases the control block; it does not verify or erase. */
    @Synchronized fun acknowledge(token: String): Boolean {
        if (!usable) return false
        val index = records.indexOfFirst { it.token == token && it.state == State.UNKNOWN && !it.acknowledged }
        if (index < 0) return false
        return commit(records.toMutableList().apply { set(index, get(index).copy(acknowledged = true)) })
    }

    private fun commit(next: List<Record>): Boolean {
        val written = try { storage.write(encode(next)) } catch (_: Exception) { false }
        if (!written) { usable = false; return false }
        records = next.toList()
        return true
    }
    private fun encode(values: List<Record>): String {
        val rows = JSONArray()
        values.forEach { r -> rows.put(JSONObject().put("token", r.token).put("state", r.state.name)
            .put("acknowledged", r.acknowledged).put("address", r.snapshot.address)
            .put("curveId", r.snapshot.curveId).put("name", r.snapshot.name)
            .put("frameHex", r.snapshot.frameHex).put("createdAtMs", r.snapshot.createdAtMs)
            .put("firmware", r.snapshot.firmware)) }
        return JSONObject().put("version", 1).put("records", rows)
            .put("checksum", CurveDocumentJson.checksum(rows)).toString()
    }
    private fun decode(value: String): List<Record> {
        require(value.length <= MAX_STORE_CHARS)
        val root = CurveDocumentJson.objectFrom(value)
        CurveDocumentJson.keys(root, setOf("version", "records", "checksum"))
        require(root.get("version") == 1)
        val rows = root.getJSONArray("records")
        require(rows.length() <= MAX_RECORDS &&
            root.get("checksum") == CurveDocumentJson.checksum(rows))
        val tokens = mutableSetOf<String>()
        val result = (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            CurveDocumentJson.keys(row, setOf("token", "state", "acknowledged", "address", "curveId", "name",
                "frameHex", "createdAtMs", "firmware"))
            fun string(key: String) = (row.get(key) as? String) ?: error("Invalid snapshot field")
            val token = string("token")
            require(UUID.fromString(token).toString() == token && tokens.add(token))
            val state = State.valueOf(string("state"))
            val ack = row.get("acknowledged") as? Boolean ?: error("Invalid acknowledgement")
            require(!ack || state == State.UNKNOWN)
            val created = row.get("createdAtMs")
            require(created is Long || created is Int)
            val snapshot = validate(Snapshot(string("address"), string("curveId"), string("name"),
                string("frameHex"), (created as Number).toLong(), string("firmware")))
            Record(token, snapshot, state, ack)
        }
        require(result.count { it.state == State.PENDING } <= 1)
        return result
    }
    companion object {
        private const val MAX_RECORDS = 4096 // Never silently discard unknown outcomes to make room.
        private const val MAX_STORE_CHARS = 8 * 1024 * 1024
        fun normalizeAddress(address: String): String {
            val key = address.trim().uppercase(Locale.ROOT)
            require(key.length in 1..128 && key.all { it in 'A'..'Z' || it in '0'..'9' || it in ":._-" })
            return key
        }
        private fun normalizeOrNull(address: String?): String? = try {
            address?.let(::normalizeAddress)
        } catch (_: Exception) { null }
        private fun validate(snapshot: Snapshot): Snapshot {
            require(snapshot.curveId.length in 1..256 && snapshot.name.isNotBlank() && snapshot.name.length <= 512)
            require(snapshot.firmware.isNotBlank() && snapshot.firmware.length <= 128 && snapshot.createdAtMs >= 0)
            val hex = snapshot.frameHex.lowercase(Locale.ROOT)
            require(hex.length == 40 && hex.all { it in '0'..'9' || it in 'a'..'f' })
            val b = hex.chunked(2).map { it.toInt(16) }
            require(b[0] == 8 && (b[1] and 15) in 1..4 && b[1] and 0x30 == 0)
            require(b.fold(0) { a, v -> a xor v } == 0)
            return snapshot.copy(address = normalizeAddress(snapshot.address), frameHex = hex)
        }
    }
}
