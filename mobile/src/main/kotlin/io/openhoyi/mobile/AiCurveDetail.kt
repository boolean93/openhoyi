package io.openhoyi.mobile

import org.json.JSONObject

/** One exact trusted local document, never ten paged copies or factory/raw parameters. */
internal object AiCurveDetail {
    fun result(id: String, document: CustomCurveDocument?): String {
        if (!validId(id)) return status("invalid_arguments")
        if (document == null || document.id != id || !document.isEditable) return status("unavailable")
        return try {
            val encoded = document.encode()
            if (encoded.toByteArray(Charsets.UTF_8).size > AiCurveWriteCoordinator.MAX_ARGUMENT_BYTES) return status("unavailable")
            JSONObject().put("status", "available").put("origin", "local")
                .put("document", JSONObject(encoded)).put("dataOnly", true).toString()
        } catch (_: Exception) { status("unavailable") }
    }
    fun factoryResult(id: String, item: CurveLibraryItem?): String = runCatching {
        val trusted = requireNotNull(item)
        require(validFactoryId(id) && trusted.id == id && trusted.factoryCurve != null && trusted.customDocument == null)
        val source = AiCurveCard.recommendation(trusted)
        val template = JSONObject(CustomCurveDocument.fromLibraryItem(trusted).encode()).also { it.remove("id") }
        JSONObject().put("status", "available").put("origin", "factory").put("readOnly", true)
            .put("sourceId", id).put("sourceFingerprint", source.fingerprint).put("copyTemplate", template)
            .put("dataOnly", true).toString()
    }.getOrElse { status("unavailable") }
    fun validFactoryId(id: String) = Regex("factory-v3-(00[1-9]|0[1-9][0-9]|100)").matches(id)
    fun validId(id: String): Boolean = id.startsWith("draft-") && runCatching {
        java.util.UUID.fromString(id.removePrefix("draft-")).toString() == id.removePrefix("draft-")
    }.getOrDefault(false)
    private fun status(status: String) = JSONObject().put("status", status).toString()
}
