package io.openhoyi.mobile

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException

/** Nonexported, fixed synthetic bytes only; same fenced preview process, not a SAF provider. */
class AiStorageProbeProvider : ContentProvider() {
    // Providers attach before Application.onCreate. No owner/storage work here.
    override fun onCreate(): Boolean = true
    private fun ready() = context?.let { c ->
        BuildConfig.MOCK_MODE && c.packageName == "io.openhoyi.mobile.mock" &&
            (c.applicationContext as? MobileApplication)?.offlinePreviewProcessReady == true
    } == true
    private fun checkUri(uri: Uri) {
        if (!ready() || uri.scheme != "content" || !AiStorageProbeScope.accepts(uri.authority, uri.path, uri.query, uri.fragment)) throw FileNotFoundException("Unavailable synthetic fixture")
    }
    override fun getType(uri: Uri): String { checkUri(uri); return when(uri.path) { "/text", "/short", "/wrong-size", "/cancel" -> "text/plain"; "/pdf" -> "application/pdf"; else -> "image/png" } }
    private fun bytes(uri: Uri): ByteArray {
        checkUri(uri)
        return when (uri.path) {
            "/text", "/short", "/wrong-size", "/cancel" -> "Fixed synthetic coffee document.\nNo user data.\n".toByteArray()
            "/pdf" -> "%PDF-1.4\n% Fixed synthetic fixture, not a full PDF rendering test.\n%%EOF\n".toByteArray()
            else -> ByteArrayOutputStream().use { out ->
                val b = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
                try { b.eraseColor(0xff008577.toInt()); check(b.compress(Bitmap.CompressFormat.PNG, 100, out)); out.toByteArray() }
                finally { b.recycle() }
            }
        }
    }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        checkUri(uri); require(selection == null && selectionArgs == null && sortOrder == null)
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        require(columns.all { it in setOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE) })
        val data = bytes(uri)
        return MatrixCursor(columns).apply { addRow(columns.map { if(it == OpenableColumns.SIZE) when(uri.path) { "/wrong-size" -> AiAttachmentPolicy.MAX_TEXT_BYTES + 1; else -> data.size } else when(uri.path) { "/text" -> "probe.txt"; "/pdf" -> "probe.pdf"; else -> "probe.png" } }) }
    }
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        checkUri(uri); if(mode != "r") throw FileNotFoundException("Read only")
        val data = bytes(uri); val pipe = ParcelFileDescriptor.createPipe()
        Thread {
            try { ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { it.write(data, 0, if (uri.path == "/short") data.size / 2 else data.size) } }
            catch (_: Exception) { /* Consumer cancellation; never log URI or content. */ }
            finally { data.fill(0) }
        }.start()
        return pipe[0]
    }
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
}
