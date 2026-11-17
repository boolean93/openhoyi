package io.openhoyi.mobile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import java.io.InputStream
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.util.Locale

/** System pickers only. No broad storage permission, URI persistence or upload on selection. */
internal object AiAttachmentPicker {
    fun imageIntent(): Intent = if (Build.VERSION.SDK_INT >= 33) Intent(MediaStore.ACTION_PICK_IMAGES).setType("image/*")
    else Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")
        .putExtra(Intent.EXTRA_MIME_TYPES, AiAttachmentPolicy.IMAGE_MIMES.toTypedArray())
    fun documentIntent(): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
        .putExtra(Intent.EXTRA_MIME_TYPES, (AiAttachmentPolicy.DOCUMENT_MIMES + AiAttachmentPolicy.TEXT_MIMES).toTypedArray())
}
/** The URI is used transiently here, never copied to metadata, saved state, exceptions or logs. */
internal class AndroidAiAttachmentSource(context: Context, private val trace: AiAttachmentTrace = AiAttachmentTrace(), private val preparer: AiAttachmentPreparer = AiAttachmentPreparer(AndroidAiAttachmentImages(trace), trace)) {
    private val resolver = context.applicationContext.contentResolver
    /** Call off the main thread. The owner must discard/close a result from an obsolete picker/session. */
    fun prepare(uri: Uri, existing: List<AiAttachment>, cancellation: CancellationSignal? = null,
        opened: (InputStream) -> Unit = {}): AiPreparedAttachment {
        try {
            if (uri.scheme != "content" || uri.authority.isNullOrBlank()) throw AiAttachmentFailure(AiAttachmentProblem.TYPE)
            if (existing.size >= AiAttachmentPolicy.MAX_COUNT) throw AiAttachmentFailure(AiAttachmentProblem.COUNT)
            cancellation?.throwIfCanceled()
            val mime = resolver.getType(uri)?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
                ?: throw AiAttachmentFailure(AiAttachmentProblem.TYPE)
            AiAttachmentPolicy.kind(mime) // Reject unsupported types before opening a stream.
            var name = "attachment"; var size: Long? = null
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null, cancellation)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val n = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (n >= 0 && !cursor.isNull(n)) name = AiAttachmentPolicy.safeName(cursor.getString(n))
                    val s = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (s >= 0 && !cursor.isNull(s)) size = cursor.getLong(s).takeIf { it >= 0 }
                }
            }
            cancellation?.throwIfCanceled()
            return preparer.prepare(name, mime, size, existing) {
                val descriptor = resolver.openFileDescriptor(uri, "r", cancellation) ?: throw AiAttachmentFailure(AiAttachmentProblem.UNAVAILABLE)
                val stream = ParcelFileDescriptor.AutoCloseInputStream(descriptor)
                try { opened(stream); cancellation?.throwIfCanceled(); stream }
                catch (e: Exception) { stream.close(); throw e }
            }
        } catch (failure: AiAttachmentFailure) { throw failure }
        catch (_: Exception) { throw AiAttachmentFailure(AiAttachmentProblem.UNAVAILABLE) }
    }
}
