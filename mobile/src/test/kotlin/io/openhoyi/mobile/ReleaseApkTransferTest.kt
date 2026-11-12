package io.openhoyi.mobile

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.File
import java.net.URL
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection
import org.junit.Assert.*
import org.junit.Test

class ReleaseApkTransferTest {
    private val url = "https://github.com/boolean93/openhoyi/releases/download/dev-94/update.apk"
    private fun target(size: Long) = ReleaseUpdate(94, "fixture", "a".repeat(64), "b".repeat(64), url, "", apkSize = size)
    private class Connection(address: String, val bytes: ByteArray, val code: Int = 200,
        val headers: Map<String,String> = emptyMap(), val length: Long = -1) : HttpsURLConnection(URL(address)) {
        var closed = false
        var disconnected = false
        var stream: InputStream = object : ByteArrayInputStream(bytes) { override fun close() { closed = true; super.close() } }
        override fun getResponseCode() = code
        override fun getContentLengthLong() = length
        override fun getHeaderField(name: String?) = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
        override fun getInputStream() = stream
        override fun disconnect() { disconnected = true }
        override fun connect() = Unit
        override fun usingProxy() = false
        override fun getCipherSuite() = "test"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
    }
    private fun file() = kotlin.io.path.createTempDirectory().toFile().resolve("attempt.part")
    private fun reject(action: () -> Unit) {
        try { action(); fail("Unsafe or invalid download accepted") } catch (_: Exception) { }
    }
    @Test fun actualBytesDriveProgressWithNoCredentialsRangeOrAutomaticRedirects() {
        val bytes = ByteArray(128000) { 7 }; val connection = Connection(url, bytes, length = bytes.size.toLong())
        val part = file(); val progress = mutableListOf<Long>()
        try {
            ReleaseApkTransfer({ connection }, { System.nanoTime() }).download(target(bytes.size.toLong()), part, progress::add)
            assertArrayEquals(bytes, part.readBytes()); assertEquals(0L, progress.first()); assertEquals(bytes.size.toLong(), progress.last())
            assertTrue(progress.zipWithNext().all { it.first <= it.second })
            assertFalse(connection.instanceFollowRedirects); assertEquals("identity", connection.getRequestProperty("Accept-Encoding"))
            assertNull(connection.getRequestProperty("Authorization")); assertNull(connection.getRequestProperty("Cookie"))
            assertNull(connection.getRequestProperty("Range")); assertTrue(connection.closed); assertTrue(connection.disconnected)
        } finally { part.parentFile!!.deleteRecursively() }
    }
    @Test fun githubToExactReleaseAssetCdnIsAllowedButUntrustedRedirectsNeverConnect() {
        val cdn = "https://release-assets.githubusercontent.com/github-production-release-asset/123/file?token=private"
        val opened = mutableListOf<String>(); val part = file()
        try {
            ReleaseApkTransfer({ u -> opened += u.toString(); if (u.toString() == url)
                Connection(url, byteArrayOf(), 302, mapOf("Location" to cdn)) else Connection(cdn, byteArrayOf(1)) })
                .download(target(1), part) { }
            assertEquals(listOf(url, cdn), opened)
        } finally { part.parentFile!!.deleteRecursively() }
        for (bad in listOf("http://release-assets.githubusercontent.com/github-production-release-asset/a",
            "https://evil.example/a", "https://release-assets.githubusercontent.com.evil.example/a",
            "https://user:pass@release-assets.githubusercontent.com/github-production-release-asset/a",
            "https://release-assets.githubusercontent.com:444/github-production-release-asset/a",
            "https://github.com/other/repo/a.apk", "https://objects.githubusercontent.com/a")) {
            var opens = 0; val output = file()
            try {
                reject { ReleaseApkTransfer({ opens++; Connection(url, byteArrayOf(), 302, mapOf("Location" to bad)) })
                    .download(target(1), output) { } }
                assertEquals(1, opens); assertFalse(output.exists())
            } finally { output.parentFile!!.deleteRecursively() }
        }
    }
    @Test fun declaredShortOverflowEncodedAndRedirectLoopsFailAndOwnPartIsRemoved() {
        for (connection in listOf(Connection(url, byteArrayOf(1), length = 3),
            Connection(url, byteArrayOf(1)), Connection(url, byteArrayOf(1,2,3)),
            Connection(url, byteArrayOf(1,2), headers = mapOf("Content-Encoding" to "gzip")))) {
            val output = file()
            try { reject { ReleaseApkTransfer({ connection }).download(target(2), output) { } }; assertFalse(output.exists()) }
            finally { output.parentFile!!.deleteRecursively() }
        }
        var redirects = 0; val output = file()
        try {
            reject { ReleaseApkTransfer({ redirects++; Connection(url, byteArrayOf(), 302, mapOf("Location" to url)) })
                .download(target(1), output) { } }
            assertEquals(4, redirects)
        } finally { output.parentFile!!.deleteRecursively() }
    }
    @Test fun preexistingPartIsNeverDeletedAndCancellationNeverPublishesACompletedFile() {
        val output = file(); output.writeText("existing")
        try {
            reject { ReleaseApkTransfer({ Connection(url, byteArrayOf(1)) }).download(target(1), output) { } }
            assertEquals("existing", output.readText())
            output.delete()
            lateinit var call: ReleaseApkTransfer
            val connection = Connection(url, byteArrayOf(1)).apply { stream = object : ByteArrayInputStream(byteArrayOf(1)) {
                override fun read(b: ByteArray, off: Int, len: Int): Int { call.cancel(); return super.read(b,off,len) }
            } }
            call = ReleaseApkTransfer({ connection })
            reject { call.download(target(1), output) { } }; assertFalse(output.exists())
        } finally { output.parentFile!!.deleteRecursively() }
    }
}
