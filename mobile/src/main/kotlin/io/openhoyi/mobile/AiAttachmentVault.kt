package io.openhoyi.mobile

import android.content.Context
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

/** Ciphertext-only test seam. A new write must never replace an existing id. */
internal interface AiAttachmentStorage {
    /** Reentrant exclusive transaction; file storage also fences other app processes. */
    fun <T> transaction(action: () -> T): T = action()
    fun hasAny(): Boolean
    fun writeNew(id: String, ciphertext: ByteArray)
    fun read(id: String): ByteArray
    fun remove(id: String)
}
internal interface AiAttachmentAddressableStorage : AiAttachmentStorage {
    /** True for any reserved name, including incomplete or corrupt entries. Never follows links. */
    fun exists(id: String): Boolean
}
/** Owns app-created attachment copies only; new images use private v2, other/legacy data stays encrypted. */
internal class AiAttachmentVault(private val storage: AiAttachmentStorage, private val keys: AiKeySource, private val ownerEpoch: String, private val trace: AiAttachmentTrace = AiAttachmentTrace(), private val images: AiAttachmentAddressableStorage? = null) {
    constructor(context: Context, ownerEpoch: String, trace: AiAttachmentTrace = AiAttachmentTrace()) : this(AndroidAiAttachmentStorage(context, trace), AndroidAiKeySource("io.openhoyi.mobile.ai_attachments.v1"), ownerEpoch, trace, AndroidAiPrivateImageStorage(context, trace))
    init { require(ownerEpoch.matches(Regex("[a-f0-9-]{36}")) && java.util.UUID.fromString(ownerEpoch).toString() == ownerEpoch) }
    private val imageVault get() = images?.let { AiPrivateImageVault(it, ownerEpoch, trace) }
    fun save(prepared: AiPreparedAttachment) = synchronized(lock) {
        try {
            val imageStorage = images
            if (imageStorage == null) saveLegacy(prepared)
            else imageStorage.transaction {
                storage.transaction {
                    val addressable = storage as? AiAttachmentAddressableStorage ?: error("Unavailable")
                    check(!imageStorage.exists(prepared.attachment.id) && !addressable.exists(prepared.attachment.id))
                    if (prepared.attachment.kind == AiAttachmentKind.IMAGE) imageVault!!.save(prepared) else saveLegacy(prepared)
                }
            }
        } catch (failure: AiAttachmentFailure) { throw failure }
        catch (failure: Exception) { trace.failed(AiAttachmentProblem.UNAVAILABLE, failure); throw AiAttachmentFailure(AiAttachmentProblem.UNAVAILABLE) }
    }
    fun load(expected: AiAttachment): AiPreparedAttachment = synchronized(lock) {
        val imageStorage = images
        if (imageStorage == null) loadLegacy(expected)
        else try { imageStorage.transaction {
            if (imageStorage.exists(expected.id)) imageVault!!.load(expected) else loadLegacy(expected)
        } } catch (_: Exception) { throw AiAttachmentFailure(AiAttachmentProblem.UNAVAILABLE) }
    }
    fun remove(attachment: AiAttachment): Boolean = synchronized(lock) {
        val imageStorage = images
        if (imageStorage == null) removeLegacy(attachment)
        else runCatching { imageStorage.transaction {
            if (imageStorage.exists(attachment.id)) imageVault!!.remove(attachment) else removeLegacy(attachment)
        } }.getOrDefault(false)
    }
    private fun saveLegacy(prepared: AiPreparedAttachment) = synchronized(lock) {
        var plain: ByteArray? = null
        try { storage.transaction {
            val a = prepared.attachment
            check(prepared.bytes.size == a.size)
            trace.event(AiAttachmentStage.STORE_ENCODE)
            plain = encode(prepared)
            // Missing/lost aliases with existing ciphertext never license implicit replacement.
            trace.event(AiAttachmentStage.STORE_KEY_LOOKUP)
            val key = keys.existing() ?: if (!storage.hasAny()) {
                trace.event(AiAttachmentStage.STORE_KEY_CREATE); keys.create()
            } else { trace.event(AiAttachmentStage.STORE_KEY_LOOKUP); error("Unavailable") }
            trace.event(AiAttachmentStage.STORE_ENCRYPT)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key); cipher.updateAAD(aad(a.id))
            check(cipher.iv.size == 12)
            val ciphertext = byteArrayOf(1) + cipher.iv + cipher.doFinal(plain)
            trace.event(AiAttachmentStage.STORE_WRITE)
            storage.writeNew(a.id, ciphertext)
        } } catch (failure: AiAttachmentFailure) { trace.failed(failure.problem, failure); throw failure }
        catch (failure: Exception) { trace.failed(AiAttachmentProblem.UNAVAILABLE, failure); throw AiAttachmentFailure(AiAttachmentProblem.UNAVAILABLE) }
        finally { plain?.fill(0) }
    }
    private fun loadLegacy(expected: AiAttachment): AiPreparedAttachment = synchronized(lock) {
        var plain: ByteArray? = null
        var content: ByteArray? = null
        try {
            val loaded = storage.transaction {
            val sealed = storage.read(expected.id)
            check(sealed.size in 30..MAX_CIPHER_BYTES && sealed[0] == 1.toByte())
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, keys.existing() ?: error("Unavailable"), GCMParameterSpec(128, sealed, 1, 12))
            cipher.updateAAD(aad(expected.id)); plain = cipher.doFinal(sealed, 13, sealed.size - 13)
            DataInputStream(ByteArrayInputStream(plain)).use { input ->
                check(input.readInt() == 1)
                check(input.readUTF() == expected.id && input.readUTF() == expected.name && input.readUTF() == expected.mimeType)
                check(input.readInt() == expected.size && input.readUTF() == expected.kind.name)
                content = ByteArray(expected.size); input.readFully(content); check(input.read() == -1)
            }
            if (expected.kind == AiAttachmentKind.TEXT || expected.kind == AiAttachmentKind.OCR_TEXT) AiAttachmentPolicy.validateText(content!!, if (expected.kind == AiAttachmentKind.OCR_TEXT) AiAttachmentPolicy.MAX_EXTRACTED_BYTES else AiAttachmentPolicy.MAX_TEXT_BYTES)
            else if (expected.kind == AiAttachmentKind.IMAGE) check(AiAttachmentPolicy.imageSignature(content!!, "image/jpeg"))
            else AiAttachmentDocuments.validate(content!!, expected.mimeType)
            AiPreparedAttachment(expected, content!!)
            }
            // Transfer plaintext ownership only after lock release/close has also succeeded.
            content = null
            loaded
        } catch (_: Exception) { throw AiAttachmentFailure(AiAttachmentProblem.UNAVAILABLE) }
        finally { plain?.fill(0); content?.fill(0) }
    }
    /** Authenticate epoch/id and exact metadata before deleting; unknown/corrupt copies stay intact. */
    private fun removeLegacy(attachment: AiAttachment): Boolean = synchronized(lock) {
        runCatching { storage.transaction {
            loadLegacy(attachment).use { /* Authentication only; close wipes the temporary plaintext. */ }
            storage.remove(attachment.id)
            true
        } }.getOrDefault(false)
    }
    private fun encode(prepared: AiPreparedAttachment): ByteArray {
        val out = AiAttachmentBuffer()
        return try {
            DataOutputStream(out).use { data ->
                val a = prepared.attachment
                data.writeInt(1); data.writeUTF(a.id); data.writeUTF(a.name); data.writeUTF(a.mimeType)
                data.writeInt(a.size); data.writeUTF(a.kind.name); data.write(prepared.bytes)
            }
            out.toByteArray()
        } finally { out.wipe() }
    }
    private fun aad(id: String) = "openhoyi:ai-attachment:v1:$ownerEpoch:$id".toByteArray(Charsets.US_ASCII)
    companion object {
        private val lock = Any()
        const val MAX_CIPHER_BYTES = AiAttachmentPolicy.MAX_DOCUMENT_BYTES + 1024
    }
}
/** Wipes superseded growth buffers too; ByteArrayOutputStream.reset alone does not erase bytes. */
internal class AiAttachmentBuffer : ByteArrayOutputStream() {
    private fun capacity(required: Int) {
        if (required <= buf.size) return
        val old = buf
        buf = old.copyOf(maxOf(required, old.size * 2))
        old.fill(0)
    }
    @Synchronized override fun write(value: Int) { capacity(count + 1); buf[count++] = value.toByte() }
    @Synchronized override fun write(bytes: ByteArray, offset: Int, length: Int) {
        require(offset >= 0 && length >= 0 && offset <= bytes.size - length)
        capacity(count + length); bytes.copyInto(buf, count, offset, offset + length); count += length
    }
    @Synchronized fun wipe() { buf.fill(0); reset() }
}
