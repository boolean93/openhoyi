package io.openhoyi.mobile

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/** Local container validation only, not an Office/PDF renderer or a claim that parsing will succeed. */
internal object AiAttachmentDocuments {
    fun validate(bytes: ByteArray, mime: String) {
        if (bytes.isEmpty() || bytes.size > AiAttachmentPolicy.MAX_DOCUMENT_BYTES) throw AiAttachmentFailure(AiAttachmentProblem.SIZE)
        val valid = when (mime) {
            "application/pdf" -> bytes.size >= 8 && String(bytes, 0, 5, Charsets.US_ASCII) == "%PDF-" &&
                bytes[5] in '1'.code.toByte()..'2'.code.toByte() && bytes[6] == '.'.code.toByte() && bytes[7] in '0'.code.toByte()..'9'.code.toByte()
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> officeZip(bytes, "word/document.xml")
            "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> officeZip(bytes, "ppt/presentation.xml")
            else -> false
        }
        if (!valid) throw AiAttachmentFailure(AiAttachmentProblem.DOCUMENT)
    }
    private fun officeZip(bytes: ByteArray, expected: String): Boolean = try {
        var count = 0; var expanded = 0L
        val names = mutableSetOf<String>()
        val buffer = ByteArray(8192)
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (++count > 512 || entry.name.length > 256 || entry.name.any { Character.isISOControl(it) } || entry.name.startsWith('/') || '\\' in entry.name ||
                        entry.name.split('/').any { it == ".." } || !names.add(entry.name)) return false
                    while (true) {
                        val read = zip.read(buffer)
                        if (read == -1) break
                        expanded += read
                        if (expanded > 32L * 1024 * 1024 || expanded > maxOf(1024L * 1024, bytes.size.toLong() * 200)) return false
                    }
                    zip.closeEntry()
                }
            }
            "[Content_Types].xml" in names && expected in names &&
                (if (expected.startsWith("word/")) "ppt/presentation.xml" !in names else "word/document.xml" !in names)
        } finally { buffer.fill(0) }
    } catch (_: Exception) { false }
}
