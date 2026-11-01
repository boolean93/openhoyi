package io.openhoyi.mobile

import org.json.JSONArray
import org.json.JSONObject

/** Exactly one writer per store; successful atomic persistence precedes publishing immutable snapshots. */
class CustomCurveStore(private val storage: Storage) {
    interface Storage { fun read(): String?; fun write(value: String) }
    enum class ImportStatus { SAVED, ALREADY_EXISTS, CONFLICT }
    data class ImportResult(val status: ImportStatus, val document: CustomCurveDocument)
    private var documents = storage.read()?.let(::decode) ?: linkedMapOf()

    @Synchronized fun list(): List<CustomCurveDocument> = documents.values.toList()
    @Synchronized fun find(id: String): CustomCurveDocument? = documents[id]
    @Synchronized fun items(): List<CurveLibraryItem> = documents.values.map {
        CurveLibraryItem(it.id, it.name, "local-draft", "", null, customDocument = it)
    }
    @Synchronized fun save(document: CustomCurveDocument) {
        val safe = frozen(document)
        if (documents[safe.id] == safe) return
        commit(LinkedHashMap(documents).apply { put(safe.id, safe) })
    }
    @Synchronized fun importDocument(document: CustomCurveDocument): ImportResult {
        val safe = frozen(document)
        documents[safe.id]?.let {
            return ImportResult(if (it == safe) ImportStatus.ALREADY_EXISTS else ImportStatus.CONFLICT, it)
        }
        save(safe)
        return ImportResult(ImportStatus.SAVED, requireNotNull(documents[safe.id]))
    }
    @Synchronized fun saveCopy(document: CustomCurveDocument): CustomCurveDocument {
        var id: String
        do { id = CustomCurveDocument.newId() } while (documents.containsKey(id))
        val copy = frozen(document.copy(id = id))
        save(copy)
        return requireNotNull(documents[id])
    }
    private fun frozen(document: CustomCurveDocument): CustomCurveDocument {
        document.validate()
        return document.copy(stages = java.util.Collections.unmodifiableList(document.stages.toList()))
    }
    private fun commit(next: LinkedHashMap<String, CustomCurveDocument>) {
        val array = JSONArray()
        next.values.forEach { array.put(it.toJson()) }
        storage.write(JSONObject().put("storeVersion", 1).put("documents", array).put("checksum", CurveDocumentJson.checksum(array)).toString())
        documents = next
    }
    private fun decode(value: String): LinkedHashMap<String, CustomCurveDocument> {
        try {
            val root = CurveDocumentJson.objectFrom(value)
            CurveDocumentJson.keys(root, setOf("storeVersion", "documents", "checksum"))
            require(CurveDocumentJson.number(root, "storeVersion") == 1)
            val array = root.getJSONArray("documents")
            require(CurveDocumentJson.string(root, "checksum") == CurveDocumentJson.checksum(array))
            val result = linkedMapOf<String, CustomCurveDocument>()
            for (index in 0 until array.length()) {
                val document = frozen(CustomCurveDocument.fromJson(array.getJSONObject(index)))
                require(!result.containsKey(document.id)) { "Duplicate stored draft" }
                result[document.id] = document
            }
            return result
        } catch (e: Exception) { throw IllegalStateException("Custom curve data unavailable; original file retained", e) }
    }
}
