package io.openhoyi.mobile

import android.content.Context
import android.util.AtomicFile
import kotlinx.serialization.json.*
import java.io.File
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Never stringify session contents: protocol includes private tool and reasoning data. */
internal class AiSession(val epoch: String, val lines: List<AiChatLine>, val protocol: AiProtocolHistory,
    val draft: String, val usage: AiUsageSnapshot, val draftAttachments: List<AiAttachment> = emptyList(), val inactive: List<AiSession> = emptyList(), val ocr: List<AiOcrRecord> = emptyList()) {
    val modelId get() = protocol.modelId
    fun withUsage(next: AiUsageSnapshot) = AiSession(epoch, lines, protocol, draft, next, draftAttachments, inactive, ocr)
    override fun toString() = "AiSession"
}
internal object AiSessionCodec {
    const val MAX_BYTES = 32 * 1024 * 1024
    // Replace (rather than double-count) the active working protocol; inactive model slots stay included.
    const val TURN_RESERVE_BYTES = AiProtocolHistory.MAX_BYTES + AiChatPolicy.OUTPUT_CHARS * 6 +
        AiContextPolicy.SUMMARY_CHARS * 6 + (AiCurveCard.MAX_CARDS_PER_TURN + 1) * AiCurveCard.MAX_ENCODED_BYTES + 65536
    fun hasTurnHeadroomBytes(currentBytes: Int, currentProtocolBytes: Int = 0): Boolean =
        currentBytes in 0..MAX_BYTES && currentProtocolBytes in 0..minOf(currentBytes, AiProtocolHistory.MAX_BYTES) &&
            currentBytes.toLong() - currentProtocolBytes + TURN_RESERVE_BYTES <= MAX_BYTES
    fun hasTurnHeadroom(session: AiSession): Boolean = runCatching {
        val bytes = encode(session)
        val protocolBytes = JsonArray(session.protocol.messages).toString().toByteArray(Charsets.UTF_8)
        try { hasTurnHeadroomBytes(bytes.size, protocolBytes.size) }
        finally { bytes.fill(0); protocolBytes.fill(0) }
    }.getOrDefault(false)
    fun encode(s: AiSession): ByteArray {
        val raw = encodeObject(s).toString().toByteArray(Charsets.UTF_8)
        decode(raw)
        return raw
    }
    private fun encodeObject(s: AiSession): JsonObject = buildJsonObject {
            put("schema", 5); put("model", s.modelId);
            require(s.inactive.size <= 1 && s.inactive.all { it.inactive.isEmpty() && it.epoch == s.epoch && it.modelId != s.modelId })
            put("inactive", JsonArray(s.inactive.map(::encodeObject))); put("ocr", AiOcrRecords.encode(s.ocr)); put("epoch", s.epoch); put("draft", s.draft)
            put("draftAttachments", AiAttachmentConversation.encode(s.draftAttachments))
            put("lines", buildJsonArray { s.lines.forEach { add(buildJsonObject { put("user", it.user); put("text", it.text); put("attachments", AiAttachmentConversation.encode(it.attachments)); put("curveCards", JsonArray(it.curveCards.map { card -> Json.parseToJsonElement(card.encode().toString()) })) }) } })
            put("context", buildJsonObject {
                put("summary", s.protocol.summary?.let(::JsonPrimitive) ?: JsonNull)
                put("coveredLines", s.protocol.coveredLines)
                put("reportedInput", s.protocol.reportedInput?.let(::JsonPrimitive) ?: JsonNull)
                put("reportedOutput", s.protocol.reportedOutput)
            })
            put("protocol", JsonArray(s.protocol.messages)); put("usage", Json.parseToJsonElement(AiUsageCodec.encode(s.usage)))
        }
    fun decode(raw: ByteArray): AiSession {
        require(raw.size <= MAX_BYTES)
        val text = Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(raw)).toString()
        return decodeObject(Json.parseToJsonElement(text).jsonObject, true)
    }
    private fun decodeObject(o: JsonObject, allowInactive: Boolean): AiSession {
        val schema = o["schema"]?.jsonPrimitive?.int
        require(schema in 1..5)
        require(o.keys == setOf("schema", "epoch", "draft", "lines", "protocol", "usage") + (if (schema != 1) setOf("context") else emptySet()) + (if (schema!! >= 3) setOf("draftAttachments") else emptySet()) + (if (schema!! >= 4) setOf("model", "inactive", "ocr") else emptySet()))
        fun string(obj: JsonObject, name: String) = (obj[name] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: error("Invalid session")
        val epoch = string(o, "epoch"); require(epoch.matches(Regex("[a-f0-9-]{36}")))
        val modelId = if (schema!! >= 4) string(o, "model") else AiModelPolicy.FLASH
        AiModelPolicy.known(modelId)
        val inactive = if (schema!! >= 4) o.getValue("inactive").jsonArray.let { values ->
            require(values.size <= if (allowInactive) 1 else 0)
            values.map { decodeObject(it.jsonObject, false) }.also { slots -> require(slots.all { it.epoch == epoch && it.modelId != modelId }) }
        } else emptyList()
        val draft = string(o, "draft"); require(draft.length <= AiChatPolicy.INPUT_CHARS)
        val lines = o.getValue("lines").jsonArray.map { element ->
            val line = element.jsonObject; require(line.keys == setOf("user", "text") + (if (schema!! >= 3) setOf("attachments") else emptySet()) + (if (schema >= 5) setOf("curveCards") else emptySet()))
            val user = (line["user"] as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull ?: error("Invalid session")
            val cards = if (schema!! >= 5) line.getValue("curveCards").jsonArray.let { values ->
                require(values.size <= AiCurveCard.MAX_CARDS_PER_TURN + 1)
                values.map { AiCurveCard.decode(org.json.JSONObject(it.toString())) }
            } else emptyList()
            require(!user || cards.isEmpty())
            require(cards.count { it.kind != AiCurveCard.Kind.RECOMMENDATION } <= 1)
            val references = cards.filter { it.kind == AiCurveCard.Kind.RECOMMENDATION }
            require(references.size <= AiCurveCard.MAX_CARDS_PER_TURN && references.map { it.id }.distinct().size == references.size)
            AiChatLine(user, string(line, "text"), AiAttachmentConversation.decode(line["attachments"]).also { require(user || it.isEmpty()) }, cards)
        }
        require(lines.size % 2 == 0 && lines.size <= 20000)
        require(lines.withIndex().all { (i, l) -> l.user == (i % 2 == 0) && l.text.isNotBlank() && l.text.length <= if (l.user) AiChatPolicy.INPUT_CHARS else AiChatPolicy.OUTPUT_CHARS })
        require(lines.sumOf { it.text.length.toLong() } <= AiContextPolicy.TRANSCRIPT_CHARS)
        val context = o["context"] as? JsonObject
        if (schema != 1) require(context != null && context.keys == setOf("summary", "coveredLines", "reportedInput", "reportedOutput"))
        val protocol = AiProtocolHistory.checked(o.getValue("protocol").jsonArray.map { it.jsonObject },
            summary = context?.get("summary")?.takeUnless { it == JsonNull }?.let { (it as JsonPrimitive).also { p -> require(p.isString) }.content },
            coveredLines = context?.get("coveredLines")?.jsonPrimitive?.int ?: 0,
            reportedInput = context?.get("reportedInput")?.takeUnless { it == JsonNull }?.jsonPrimitive?.long,
            reportedOutput = context?.get("reportedOutput")?.jsonPrimitive?.long ?: 0, modelId = modelId)
        require(AiContextPolicy.matches(protocol, lines))
        val usage = o.getValue("usage").jsonObject
        require(usage.keys == setOf("schema", "since", "incomplete", "searches", "searchNonempty", "searchEmpty", "searchFailed", "models"))
        require(usage.getValue("models").jsonArray.all { it.jsonObject.keys == setOf("model", "requests", "reported", "input", "output", "cacheHit", "cacheMiss", "cacheReported") })
        return AiSession(epoch, lines, protocol, draft, AiUsageCodec.decode(usage.toString()), AiAttachmentConversation.decode(o["draftAttachments"]), inactive, if (schema!! >= 4) AiOcrRecords.decode(o["ocr"]) else emptyList())
    }
}
internal interface AiSessionStorage {
    fun read(): ByteArray?
    /** Atomically retain either the previous record or a complete replacement, never partial bytes.
     * A thrown failure means completion could not be confirmed, not necessarily rollback.
     */
    fun write(bytes: ByteArray)
    /** Preserve exact ciphertext in a separate no-backup file and verify it before allowing recovery. */
    fun preserveUnreadable(): Boolean = false
}
internal object AiSessionCipher {
    private val aad = "openhoyi:ai-session:v1".toByteArray(Charsets.UTF_8)
    fun encrypt(key: SecretKey, plain: ByteArray): ByteArray {
        require(plain.size <= AiSessionCodec.MAX_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key); cipher.updateAAD(aad)
        require(cipher.iv.size == 12)
        return byteArrayOf(1) + cipher.iv + cipher.doFinal(plain)
    }
    fun decrypt(key: SecretKey, bytes: ByteArray): ByteArray {
        require(bytes.size in 29..AiSessionCodec.MAX_BYTES + 29 && bytes[0] == 1.toByte())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
        cipher.updateAAD(aad)
        return cipher.doFinal(bytes.copyOfRange(13, bytes.size))
    }
}
internal sealed class AiSessionLoad {
    object Empty : AiSessionLoad()
    object Unavailable : AiSessionLoad()
    class Ready(val session: AiSession) : AiSessionLoad()
}
/** A failed load fences writes. Only an explicit new conversation can replace unreadable data. */
internal class AiSessionStore(private val storage: AiSessionStorage, private val keys: AiKeySource) {
    constructor(context: Context) : this(AndroidAiSessionStorage(context), AndroidAiKeySource("io.openhoyi.mobile.ai_session.v1"))
    private var writable = false
    @Synchronized fun needsRecovery(): Boolean = !writable
    private var hasBytes = false
    @Synchronized fun load(epoch: String): AiSessionLoad = try {
        hasBytes = true // An unreadable file must never be mistaken for a first save.
        val bytes = storage.read(); hasBytes = bytes != null
        if (bytes == null) { writable = true; AiSessionLoad.Empty }
        else {
            val plain = AiSessionCipher.decrypt(keys.existing() ?: error("Missing session key"), bytes)
            val session = try { AiSessionCodec.decode(plain) } finally { plain.fill(0) }
            writable = true
            if (session.epoch == epoch) AiSessionLoad.Ready(session) else AiSessionLoad.Empty
        }
    } catch (_: Exception) { writable = false; AiSessionLoad.Unavailable }
    @Synchronized fun save(session: AiSession): Boolean {
        if (!writable) return false
        return runCatching {
            val plain = AiSessionCodec.encode(session)
            try {
                // Existing ciphertext never licenses silently creating a missing alias.
                val key = keys.existing() ?: if (!hasBytes) keys.create() else error("Missing session key")
                storage.write(AiSessionCipher.encrypt(key, plain))
            } finally { plain.fill(0) }
            hasBytes = true; writable = true
        }.isSuccess
    }
    /** Only after the owner confirms preservation and a new conversation, never an automatic retry. */
    @Synchronized fun recover(session: AiSession): Boolean {
        if (writable) return false
        return runCatching {
            val plain = AiSessionCodec.encode(session)
            try {
                // No alias creation and no active-file write before verified preservation.
                check(storage.preserveUnreadable())
                val key = keys.existing() ?: keys.create()
                storage.write(AiSessionCipher.encrypt(key, plain))
            } finally { plain.fill(0) }
            hasBytes = true; writable = true
        }.isSuccess
    }
}
internal class AndroidAiSessionStorage(private val directory: File) : AiSessionStorage {
    constructor(context: Context) : this(context.applicationContext.noBackupFilesDir)
    private val file by lazy { AtomicFile(File(directory, "ai_current_session_v1")) }
    override fun preserveUnreadable(): Boolean = runCatching {
        val bytes = read() ?: return false
        val archive = AtomicFile(File(directory, "ai_session_quarantine_" + java.util.UUID.randomUUID()))
        val stream = archive.startWrite()
        try { stream.write(bytes); stream.fd.sync(); archive.finishWrite(stream) }
        catch (e: Exception) { archive.failWrite(stream); throw e }
        archive.openRead().use { input ->
            // Only ciphertext is buffered. Bound the verification read independently too.
            val verified = ByteArray(bytes.size)
            var offset = 0
            while (offset < verified.size) {
                val count = input.read(verified, offset, verified.size - offset)
                check(count > 0); offset += count
            }
            check(input.read() == -1 && verified.contentEquals(bytes))
        }
        true
    }.getOrDefault(false)
    override fun read(): ByteArray? {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return null
        return file.openRead().use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (true) {
                val count = input.read(buffer)
                if (count == -1) break
                require(output.size() + count <= AiSessionCodec.MAX_BYTES + 29)
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    }
    override fun write(bytes: ByteArray) {
        val stream = file.startWrite()
        try {
            stream.write(bytes); stream.fd.sync(); file.finishWrite(stream)
            // AtomicFile may log rather than throw on a failed rename; do not report that as saved.
            check(read()?.contentEquals(bytes) == true)
        }
        catch (e: Exception) { file.failWrite(stream); throw e }
    }
}
