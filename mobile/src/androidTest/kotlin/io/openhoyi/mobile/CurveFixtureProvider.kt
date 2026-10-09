package io.openhoyi.mobile

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID

/** Test APK only. Signature permission plus exact registered UUID/predefined paths; no arbitrary files. */
class CurveFixtureProvider : ContentProvider() {
    companion object {
        const val AUTHORITY = "io.openhoyi.mobile.mock.test.curve-fixtures"
        const val PERMISSION = "io.openhoyi.mobile.mock.test.permission.CURVE_FIXTURE"
        const val MAX_FIXTURE_BYTES = 64 * 1024
        private val names = setOf("output.json", "valid.json", "malformed.json", "oversize.json")
        fun uri(session: String, name: String): Uri = Uri.Builder().scheme("content").authority(AUTHORITY)
            .appendPath("fixtures").appendPath(session).appendPath(name).build()
    }
    private val readCounts = mutableMapOf<String, Int>()
    private val writeCounts = mutableMapOf<String, Int>()
    private val base get() = File(requireNotNull(context).filesDir, "curve-contract-fixtures")
    override fun onCreate(): Boolean = requireNotNull(context).packageName == "io.openhoyi.mobile.mock.test"
    private fun requireCaller() {
        val app = requireNotNull(context)
        check(app.packageManager.checkSignatures(Binder.getCallingUid(), app.applicationInfo.uid) == PackageManager.SIGNATURE_MATCH) {
            "Fixture caller signature mismatch"
        }
        val packages = app.packageManager.getPackagesForUid(Binder.getCallingUid()).orEmpty()
        check(packages.any { it == "io.openhoyi.mobile.mock" || it == "io.openhoyi.mobile.mock.test" }) {
            "Only the isolated Mock or test package may use fixtures"
        }
    }
    private fun session(value: String?): String {
        val id = requireNotNull(value)
        require(UUID.fromString(id).toString() == id) { "Invalid fixture UUID" }
        return id
    }
    private fun directory(id: String, registered: Boolean = true): File = File(base, session(id)).also {
        if (registered) require(File(it, ".registered").isFile) { "Unknown fixture session" }
    }
    private fun file(uri: Uri): File {
        require(uri.scheme == "content" && uri.authority == AUTHORITY && uri.query == null && uri.fragment == null)
        val parts = uri.pathSegments
        require(parts.size == 3 && parts[0] == "fixtures" && parts[2] in names) { "Unknown fixture path" }
        return File(directory(parts[1]), parts[2])
    }
    @Synchronized
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        requireCaller()
        val id = session(arg)
        return when (method) {
            "create" -> {
                val json = requireNotNull(extras?.getString("document"))
                require(json.toByteArray(Charsets.UTF_8).size <= MAX_FIXTURE_BYTES)
                require(org.json.JSONObject(json).getString("kind") == "openhoyi.local-curve")
                val folder = directory(id, registered = false)
                require(!folder.exists()) { "Fixture session already exists" }
                check(folder.mkdirs())
                File(folder, "valid.json").writeBytes(json.toByteArray(Charsets.UTF_8))
                File(folder, "malformed.json").writeBytes(byteArrayOf(0xC3.toByte(), 0x28))
                File(folder, "oversize.json").writeBytes(ByteArray(MAX_FIXTURE_BYTES + 1) { 'x'.code.toByte() })
                File(folder, ".registered").writeText("curve-contract-v1")
                Bundle().apply { putString("session", id) }
            }
            "stats" -> {
                directory(id)
                Bundle().apply {
                    names.forEach { name ->
                        putInt("read:$name", readCounts["$id/$name"] ?: 0)
                        putInt("write:$name", writeCounts["$id/$name"] ?: 0)
                    }
                }
            }
            "cleanup" -> {
                val folder = directory(id)
                check(folder.deleteRecursively()) { "Cannot remove owned fixture session" }
                names.forEach { readCounts.remove("$id/$it"); writeCounts.remove("$id/$it") }
                Bundle()
            }
            else -> error("Unknown fixture operation")
        }
    }
    @Synchronized
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        requireCaller()
        val target = file(uri)
        val key = "${uri.pathSegments[1]}/${uri.lastPathSegment}"
        val flags = when (mode) {
            "r" -> {
                if (!target.isFile) throw FileNotFoundException("Fixture not written")
                readCounts[key] = (readCounts[key] ?: 0) + 1
                ParcelFileDescriptor.MODE_READ_ONLY
            }
            "w", "wt" -> {
                require(uri.lastPathSegment == "output.json") { "Input fixtures are read-only" }
                writeCounts[key] = (writeCounts[key] ?: 0) + 1
                ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE
            }
            else -> throw FileNotFoundException("Unsupported fixture mode")
        }
        return ParcelFileDescriptor.open(target, flags)
    }
    override fun getType(uri: Uri): String { requireCaller(); file(uri); return "application/json" }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        requireCaller()
        require(selection == null && selectionArgs == null && sortOrder == null)
        val target = file(uri)
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        require(columns.all { it == OpenableColumns.DISPLAY_NAME || it == OpenableColumns.SIZE })
        return MatrixCursor(columns).apply { addRow(columns.map { if (it == OpenableColumns.DISPLAY_NAME) target.name else target.length() }) }
    }
    override fun insert(uri: Uri, values: ContentValues?): Uri = throw UnsupportedOperationException("Fixture insertion is unavailable")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException("Fixture deletion is unavailable")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException("Fixture update is unavailable")
}
