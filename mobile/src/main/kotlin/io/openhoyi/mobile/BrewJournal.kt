package io.openhoyi.mobile

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.security.MessageDigest

/** Permanent metadata/notes, deliberately independent of the bounded history and sample cache. */
internal class BrewJournal(private val storage: Storage) {
    interface Storage { fun read(): String?; fun write(value: String) }
    data class Observation(
        val id: String,
        val curveId: String,
        val startedAtMs: Long,
        val endedAtMs: Long?,
        val elapsedMs: Long?,
        val status: String,
        val reason: String?,
        val weightHundredthsGram: Int?,
    )
    data class Notes(
        val beanId: String? = null,
        val beanName: String? = null,
        val doseMg: Long? = null,
        val grind: String = "",
        val curveExpectation: String = "",
        val taste: String = "",
        val nextAdjustment: String = "",
    )
    data class Entry(val observation: Observation, val notes: Notes)
    private var records: LinkedHashMap<String, Entry> = storage.read()?.let(::decode) ?: linkedMapOf()

    init {
        // A prior process's unresolved observation does not prove a current running or ended machine.
        val recovered = LinkedHashMap(records)
        records.forEach { (id, entry) ->
            if (entry.observation.status in setOf("STARTING", "RUNNING", "STOP_REQUESTED"))
                recovered[id] = entry.copy(observation = entry.observation.copy(status = "UNKNOWN"))
        }
        if (recovered != records) commit(recovered)
    }

    @Synchronized
    fun observe(value: Observation) {
        validate(value)
        val previous = records[value.id]
        previous?.let { sameIdentity(it.observation, value) }
        val entry = Entry(value, previous?.notes ?: Notes())
        if (entry == previous) return
        commit(LinkedHashMap(records).apply { put(value.id, entry) })
    }

    @Synchronized
    fun edit(id: String, notes: Notes) {
        validate(notes)
        val previous = requireNotNull(records[id]) { "Cannot create a shot through notes" }
        if (previous.notes == notes) return
        commit(LinkedHashMap(records).apply { put(id, previous.copy(notes = notes)) })
    }

    @Synchronized
    fun find(id: String): Entry? = records[id]

    @Synchronized
    fun entries(): List<Entry> = records.values.sortedWith(compareByDescending<Entry> { it.observation.startedAtMs }.thenBy { it.observation.id })

    /** Explicit migration never overwrites a newer observation or notes already in the journal. */
    @Synchronized
    fun importRecent(values: List<Observation>): Int {
        val next = LinkedHashMap(records)
        var added = 0
        values.forEach { value ->
            validate(value)
            val previous = next[value.id]
            if (previous == null) { next[value.id] = Entry(value, Notes()); added++ }
            else sameIdentity(previous.observation, value)
        }
        if (added > 0) commit(next)
        return added
    }

    /** Replay the persisted recent history after a missed journal write. Keep known terminal
     * observations and all user notes; never infer an end from interruption or a command callback. */
    @Synchronized
    fun reconcileRecent(values: List<Observation>) {
        val next=LinkedHashMap(records)
        values.forEach {value ->
            validate(value)
            val previous=next[value.id]
            if(previous==null) next[value.id]=Entry(value,Notes())
            else {
                sameIdentity(previous.observation,value)
                if(previous.observation.status !in setOf("ENDED","NOT_STARTED") &&
                    value.status in setOf("ENDED","NOT_STARTED"))
                    next[value.id]=previous.copy(observation=value)
            }
        }
        if(next!=records) commit(next)
    }

    @Synchronized
    fun exportJson(): String = encode(records)

    private fun commit(next: LinkedHashMap<String, Entry>) {
        storage.write(encode(next))
        records = next
    }
    private fun sameIdentity(a: Observation, b: Observation) {
        require(a.curveId == b.curveId && a.startedAtMs == b.startedAtMs) { "Shot ID has different identity" }
    }
    private fun validate(value: Observation) {
        require(value.id.isNotBlank() && value.id.length <= 256)
        require(value.curveId.isNotBlank() && value.curveId.length <= 256)
        require(value.startedAtMs >= 0)
        require(value.endedAtMs == null || value.endedAtMs >= value.startedAtMs)
        require(value.elapsedMs == null || value.elapsedMs >= 0)
        require(value.status in setOf("STARTING", "RUNNING", "STOP_REQUESTED", "ENDED", "UNKNOWN", "NOT_STARTED"))
        require(value.reason == null || value.reason.length <= 16000)
    }
    private fun validate(notes: Notes) {
        require((notes.beanId == null) == (notes.beanName == null))
        require(notes.beanId == null || notes.beanId.isNotBlank() && notes.beanId.length <= 256)
        require(notes.beanName == null || notes.beanName.isNotBlank() && notes.beanName.length <= 4096)
        require(notes.doseMg == null || notes.doseMg > 0)
        require(listOf(notes.grind, notes.curveExpectation, notes.taste, notes.nextAdjustment).all { it.length <= 16000 })
    }
    private fun encode(values: Map<String, Entry>): String {
        val array = JSONArray()
        values.values.forEach { entry ->
            val shot = entry.observation
            val notes = entry.notes
            array.put(JSONObject().put("shot", JSONObject()
                .put("id", shot.id).put("curveId", shot.curveId).put("startedAtMs", shot.startedAtMs)
                .put("endedAtMs", shot.endedAtMs ?: JSONObject.NULL).put("elapsedMs", shot.elapsedMs ?: JSONObject.NULL)
                .put("status", shot.status).put("reason", shot.reason ?: JSONObject.NULL)
                .put("weightHundredthsGram", shot.weightHundredthsGram ?: JSONObject.NULL))
                .put("notes", JSONObject().put("beanId", notes.beanId ?: JSONObject.NULL).put("beanName", notes.beanName ?: JSONObject.NULL)
                    .put("doseMg", notes.doseMg ?: JSONObject.NULL).put("grind", notes.grind)
                    .put("curveExpectation", notes.curveExpectation).put("taste", notes.taste).put("nextAdjustment", notes.nextAdjustment)))
        }
        return JSONObject().put("schema", 1).put("entries", array).put("checksum", checksum(array)).toString()
    }
    private fun decode(value: String): LinkedHashMap<String, Entry> {
        try {
            val tokenizer = JSONTokener(value)
            val root = tokenizer.nextValue()
            require(root is JSONObject && tokenizer.nextClean() == '\u0000') { "Trailing or invalid journal input" }
            keys(root, setOf("schema", "entries", "checksum"))
            require(number(root, "schema") == 1L) { "Unknown journal schema" }
            val array = root.getJSONArray("entries")
            require(root.get("checksum") is String && root.getString("checksum") == checksum(array)) { "Journal checksum mismatch" }
            val result = linkedMapOf<String, Entry>()
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                keys(item, setOf("shot", "notes"))
                val shot = item.getJSONObject("shot")
                keys(shot, setOf("id", "curveId", "startedAtMs", "endedAtMs", "elapsedMs", "status", "reason", "weightHundredthsGram"))
                val weight = nullableNumber(shot, "weightHundredthsGram")
                require(weight == null || weight in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong())
                val observed = Observation(string(shot, "id"), string(shot, "curveId"), number(shot, "startedAtMs"),
                    nullableNumber(shot, "endedAtMs"), nullableNumber(shot, "elapsedMs"), string(shot, "status"), nullableString(shot, "reason"), weight?.toInt())
                val note = item.getJSONObject("notes")
                keys(note, setOf("beanId", "beanName", "doseMg", "grind", "curveExpectation", "taste", "nextAdjustment"))
                val notes = Notes(nullableString(note, "beanId"), nullableString(note, "beanName"), nullableNumber(note, "doseMg"),
                    string(note, "grind"), string(note, "curveExpectation"), string(note, "taste"), string(note, "nextAdjustment"))
                validate(observed); validate(notes)
                require(!result.containsKey(observed.id)) { "Duplicate journal shot" }
                result[observed.id] = Entry(observed, notes)
            }
            return result
        } catch (e: Exception) { throw IllegalStateException("Journal is damaged or unsupported; original document retained", e) }
    }
    private fun keys(value: JSONObject, expected: Set<String>) { require(value.keys().asSequence().toSet() == expected) }
    private fun number(value: JSONObject, key: String): Long {
        val raw = value.get(key)
        require(raw is Int || raw is Long)
        return (raw as Number).toLong()
    }
    private fun nullableNumber(value: JSONObject, key: String): Long? = if (value.isNull(key)) null else number(value, key)
    private fun string(value: JSONObject, key: String): String { val raw = value.get(key); require(raw is String); return raw }
    private fun nullableString(value: JSONObject, key: String): String? = if (value.isNull(key)) null else string(value, key)
    // Semantic canonicalization is independent of JSONObject key order on Android versus JVM.
    private fun canonical(value: Any?): String = when {
        value == null || value === JSONObject.NULL -> "z"
        value is JSONObject -> "{" + value.keys().asSequence().toList().sorted().joinToString("") { canonical(it) + canonical(value.get(it)) } + "}"
        value is JSONArray -> "[" + (0 until value.length()).joinToString("") { canonical(value.get(it)) } + "]"
        value is String -> "s${value.length}:$value"
        value is Number -> "n$value;"
        else -> error("Unsupported journal value")
    }
    private fun checksum(values: JSONArray): String = MessageDigest.getInstance("SHA-256").digest(canonical(values).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
