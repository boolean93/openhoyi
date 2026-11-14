package io.openhoyi.mobile

import org.json.JSONArray
import org.json.JSONObject

/** One writer; the document, archive and invalidated shortcut references share one atomic commit. */
class CustomCurveStore(private val storage: Storage) {
    interface Storage { fun read(): String?; fun write(value: String) }
    enum class ImportStatus { SAVED, ALREADY_EXISTS, CONFLICT }
    data class ImportResult(val status: ImportStatus, val document: CustomCurveDocument)
    @ConsistentCopyVisibility
    data class Deletion internal constructor(val document: CustomCurveDocument, internal val token: String,
                                             internal val bindings: Map<Int, String>)
    private data class State(val documents: LinkedHashMap<String, CustomCurveDocument> = linkedMapOf(),
                             val archived: Map<String, String> = emptyMap(),
                             val clearedBindings: Map<Int, String> = emptyMap())
    var revision = 0L
        private set
    private var state = storage.read()?.let(::decode) ?: State()

    @Synchronized fun list(): List<CustomCurveDocument> = state.documents.values.filter { it.id !in state.archived }
    @Synchronized fun find(id: String): CustomCurveDocument? = state.documents[id]?.takeUnless { id in state.archived }
    /** History can still resolve the retained name, without making archived parameters executable. */
    @Synchronized fun historicalName(id: String): String? = state.documents[id]?.name
    @Synchronized fun items(): List<CurveLibraryItem> = list().map {
        CurveLibraryItem(it.id, it.name, "local-draft", "", null, customDocument = it)
    }
    @Synchronized fun presetBinding(slot: Int, stored: String?): String? =
        if (stored != null && state.clearedBindings[slot] == stored) PresetSlots.EMPTY else stored

    @Synchronized fun save(document: CustomCurveDocument, expected: CustomCurveDocument? = null) {
        val safe = frozen(document)
        check(safe.id !in state.archived) { "Curve was deleted" }
        if (expected != null) check(state.documents[safe.id] == expected) { "Curve changed; reopen editor" }
        if (state.documents[safe.id] == safe) return
        commit(state.copy(documents = LinkedHashMap(state.documents).apply { put(safe.id, safe) }))
    }
    @Synchronized fun delete(id: String, bindings: Map<Int, String>, expected: CustomCurveDocument? = null, permitted: () -> Boolean = { true }): Deletion {
        val document = requireNotNull(find(id)) { "Curve unavailable" }
        if (expected != null) check(expected == document) { "Curve changed" }
        check(permitted()) { "Curve in use" }
        val affected = bindings.filter { (slot, raw) -> PresetSlots.curveId(slot, raw) == id && presetBinding(slot, raw) != PresetSlots.EMPTY }
        val deletion = Deletion(document, java.util.UUID.randomUUID().toString(), affected.toMap())
        commit(state.copy(archived = state.archived + (id to deletion.token),
            clearedBindings = state.clearedBindings + affected))
        return deletion
    }
    @Synchronized fun undo(deletion: Deletion): Boolean {
        val id = deletion.document.id
        if (state.archived[id] != deletion.token || state.documents[id] != deletion.document) return false
        val cleared = state.clearedBindings.toMutableMap()
        deletion.bindings.forEach { (slot, raw) -> if (cleared[slot] == raw) cleared.remove(slot) }
        commit(state.copy(archived = state.archived - id, clearedBindings = cleared))
        return true
    }
    @Synchronized fun importDocument(document: CustomCurveDocument): ImportResult {
        val safe = frozen(document)
        state.documents[safe.id]?.let {
            if (it != safe) return ImportResult(ImportStatus.CONFLICT, it)
            if (safe.id !in state.archived) return ImportResult(ImportStatus.ALREADY_EXISTS, it)
            // Explicit same-content reimport restores the document, but leaves cleared slots empty.
            commit(state.copy(archived = state.archived - safe.id))
            return ImportResult(ImportStatus.SAVED, it)
        }
        save(safe)
        return ImportResult(ImportStatus.SAVED, requireNotNull(find(safe.id)))
    }
    @Synchronized fun saveCopy(document: CustomCurveDocument): CustomCurveDocument {
        var id: String
        do { id = CustomCurveDocument.newId() } while (state.documents.containsKey(id))
        val copy = frozen(document.copy(id = id))
        save(copy)
        return requireNotNull(find(id))
    }
    private fun frozen(document: CustomCurveDocument): CustomCurveDocument {
        document.validate()
        return document.copy(stages = java.util.Collections.unmodifiableList(document.stages.toList()),
            configuration = document.configuration?.let { it.copy(inactiveStages = java.util.Collections.unmodifiableList(it.inactiveStages.toList())) })
    }
    private fun commit(next: State) {
        val documents = JSONArray().apply { next.documents.values.forEach { put(it.toJson()) } }
        val archived = JSONObject().apply { next.archived.forEach { (id, token) -> put(id, token) } }
        val cleared = JSONObject().apply { next.clearedBindings.forEach { (slot, raw) -> put(slot.toString(), raw) } }
        val version = if (next.archived.isEmpty() && next.clearedBindings.isEmpty()) 1 else 2
        val payload = if (version == 1) documents else JSONArray().put(documents).put(archived).put(cleared)
        val root = JSONObject().put("storeVersion", version).put("documents", documents)
        if (version == 2) root.put("archived", archived).put("clearedBindings", cleared)
        storage.write(root.put("checksum", CurveDocumentJson.checksum(payload)).toString())
        state = next
        revision++
    }
    private fun decode(value: String): State {
        try {
            val root = CurveDocumentJson.objectFrom(value)
            val version = CurveDocumentJson.number(root, "storeVersion")
            require(version in 1..2)
            CurveDocumentJson.keys(root, if (version == 1) setOf("storeVersion", "documents", "checksum")
                else setOf("storeVersion", "documents", "archived", "clearedBindings", "checksum"))
            val array = root.getJSONArray("documents")
            val archived = if (version == 2) root.getJSONObject("archived") else JSONObject()
            val cleared = if (version == 2) root.getJSONObject("clearedBindings") else JSONObject()
            val payload = if (version == 1) array else JSONArray().put(array).put(archived).put(cleared)
            require(CurveDocumentJson.string(root, "checksum") == CurveDocumentJson.checksum(payload))
            val documents = linkedMapOf<String, CustomCurveDocument>()
            for (index in 0 until array.length()) {
                val document = frozen(CustomCurveDocument.fromJson(array.getJSONObject(index)))
                require(!documents.containsKey(document.id)) { "Duplicate stored draft" }
                documents[document.id] = document
            }
            val archivedIds = archived.keys().asSequence().associateWith { id ->
                require(id in documents)
                CurveDocumentJson.string(archived, id).also { require(it.isNotBlank()) }
            }
            val clearedSlots = cleared.keys().asSequence().associate { key ->
                val slot = key.toInt().also { PresetSlots.requireSlot(it); require(it.toString() == key) }
                slot to CurveDocumentJson.string(cleared, key).also { require(PresetSlots.curveId(slot, it).startsWith("draft-")) }
            }
            return State(documents, archivedIds, clearedSlots)
        } catch (e: Exception) { throw IllegalStateException("Custom curve data unavailable; original file retained", e) }
    }
}
