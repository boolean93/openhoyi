package io.openhoyi.mobile

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import kotlinx.coroutines.ensureActive
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext

/** Local format/size boundary. There is no sharing, upload, persistent URI or original file name. */
internal object BackgroundImageFile {
    suspend fun normalize(resolver: ContentResolver, uri: Uri, target: File): Bitmap {
        require(uri.scheme == ContentResolver.SCHEME_CONTENT)
        val cancellation = coroutineContext
        val bytes = resolver.openInputStream(uri)?.use { source ->
            ByteArrayOutputStream(128 * 1024).use { output ->
                val buffer = ByteArray(32 * 1024)
                var total = 0L
                while (true) {
                    cancellation.ensureActive()
                    val count = source.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= BackgroundImageLimits.MAX_SOURCE_BYTES)
                    output.write(buffer, 0, count)
                }
                require(total > 0)
                output.toByteArray()
            }
        } ?: error("No image input")
        var raw: Bitmap? = null
        var oriented: Bitmap? = null
        var normalized: Bitmap? = null
        try {
            cancellation.ensureActive()
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val mime = requireNotNull(bounds.outMimeType)
            require(mime in setOf("image/jpeg", "image/png", "image/webp"))
            val sample = BackgroundImageLimits.sample(bounds.outWidth, bounds.outHeight)
            // A pure bounded parser shared with local attachment decoding. No AI owner is initialized.
            val metadata = AiAttachmentImageMetadata.inspect(bytes, mime)
            val orientation = when (metadata.state) {
                AiAttachmentImageMetadata.OrientationState.KNOWN -> requireNotNull(metadata.value)
                AiAttachmentImageMetadata.OrientationState.ABSENT -> 1
                else -> error("Unsupported or invalid image metadata")
            }
            val decoded = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
                inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888
            }))
            raw = decoded
            require(BackgroundImageLimits.fits(decoded.width, decoded.height))
            cancellation.ensureActive()
            val rotated = if (orientation == 1) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height,
                Matrix().apply {
                    when (orientation) {
                        2 -> setScale(-1f, 1f)
                        3 -> setRotate(180f)
                        4 -> setScale(1f, -1f)
                        5 -> { setRotate(90f); postScale(-1f, 1f) }
                        6 -> setRotate(90f)
                        7 -> { setRotate(-90f); postScale(-1f, 1f) }
                        8 -> setRotate(-90f)
                    }
                }, true)
            // Re-encode pixels only, flatten transparency onto a neutral, mode-independent matte.
            oriented = rotated
            val pixels = if (rotated.hasAlpha()) Bitmap.createBitmap(rotated.width, rotated.height, Bitmap.Config.ARGB_8888).also {
                Canvas(it).apply { drawColor(Color.rgb(220, 220, 220)); drawBitmap(rotated, 0f, 0f, null) }
            } else rotated
            normalized = pixels
            FileOutputStream(target).use { output ->
                check(pixels.compress(Bitmap.CompressFormat.JPEG, 88, output))
                output.fd.sync()
            }
            cancellation.ensureActive()
        } finally {
            bytes.fill(0)
            listOfNotNull(normalized, oriented, raw).distinct().forEach { it.recycle() }
        }
        // The exact on-disk result is shared by preview and applied pages; intermediates are gone.
        return inspectNormalized(target)
    }

    fun inspectNormalized(file: File): Bitmap {
        require(file.isFile && file.length() in 1..BackgroundImageLimits.MAX_SOURCE_BYTES)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        require(options.outMimeType == "image/jpeg" && BackgroundImageLimits.fits(options.outWidth, options.outHeight))
        return requireNotNull(BitmapFactory.decodeFile(file.absolutePath))
    }
}
