package io.openhoyi.mobile

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URL
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ReleaseUpdateHttpCallTest {
    private fun fixture() = JSONObject(mapOf(
        "schemaVersion" to 1, "channel" to "develop-test", "repository" to "boolean93/openhoyi",
        "packageName" to "io.openhoyi.mobile", "versionCode" to 94, "versionName" to "0.2.0-dev.94",
        "apkSha256" to "a".repeat(64), "signerCertificateSha256" to "b".repeat(64),
        "declaredSourceCommit" to "c".repeat(40), "sourceCommitVerified" to false, "releaseTag" to "dev-94",
        "publishedAt" to "2026-11-12T11:40:15Z", "apkFile" to "OpenHOYI.apk", "apkSize" to 100,
        "releaseUrl" to "https://github.com/boolean93/openhoyi/releases/tag/dev-94",
        "downloadUrl" to "https://github.com/boolean93/openhoyi/releases/download/dev-94/OpenHOYI.apk",
        "distributionUrl" to "https://github.com/boolean93/openhoyi/releases/download/dev-94/distribution.json",
        "checksumsUrl" to "https://github.com/boolean93/openhoyi/releases/download/dev-94/SHA256SUMS.txt",
    )).toString().toByteArray()
    private class Connection(val bytes: ByteArray, val code: Int = 200, val length: Long = -1, val retry: String? = null) : HttpsURLConnection(URL(ReleaseUpdateIndex.URL)) {
        var disconnects = 0
        var inputClosed = false
        var stream: InputStream = object : ByteArrayInputStream(bytes) {
            override fun close() { inputClosed = true; super.close() }
        }
        override fun getResponseCode() = code
        override fun getHeaderField(name: String?) = if (name == "Retry-After") retry else null
        override fun getContentLengthLong() = length
        override fun getInputStream() = stream
        override fun disconnect() { disconnects++ }
        override fun connect() { }
        override fun usingProxy() = false
        override fun getCipherSuite() = "test"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
    }
    private fun fails(block: () -> Unit) {
        try { block(); fail("Invalid HTTP response accepted") } catch (_: Exception) { }
    }
    @Test fun oneAnonymousFixedHttpsRequestWithTimeoutsAndNoRedirects() {
        val connection = Connection(fixture())
        var opens = 0
        val result = ReleaseUpdateHttpCall { url ->
            assertEquals(ReleaseUpdateIndex.URL, url.toExternalForm()); opens++; connection
        }.execute()
        assertEquals(94L, result.versionCode)
        assertEquals(1, opens)
        assertFalse(connection.instanceFollowRedirects)
        assertFalse(connection.useCaches)
        assertEquals(5000, connection.connectTimeout)
        assertEquals(5000, connection.readTimeout)
        assertEquals("GET", connection.requestMethod)
        assertNull(connection.getRequestProperty("Authorization"))
        assertTrue(connection.inputClosed)
        assertEquals(1, connection.disconnects)
    }
    @Test fun redirectsHttpErrorsAndDeclaredOrStreamedOversizeAreRejected() {
        for (code in listOf(301, 302, 307, 308, 404, 500)) {
            val connection = Connection(fixture(), code)
            fails { ReleaseUpdateHttpCall { connection }.execute() }
            assertEquals(1, connection.disconnects)
        }
        val declared = Connection(fixture(), length = ReleaseUpdateIndex.MAX_BYTES + 1L)
        fails { ReleaseUpdateHttpCall { declared }.execute() }
        assertEquals(1, declared.disconnects)
        val streamed = Connection(ByteArray(ReleaseUpdateIndex.MAX_BYTES + 1) { ' '.code.toByte() })
        fails { ReleaseUpdateHttpCall { streamed }.execute() }
        assertTrue(streamed.inputClosed)
        assertEquals(1, streamed.disconnects)
    }
    @Test fun invalidUtf8JsonAndTimeoutCloseConnection() {
        for (bytes in listOf(byteArrayOf(0xc3.toByte(), 0x28), "not JSON".toByteArray())) {
            val connection = Connection(bytes)
            fails { ReleaseUpdateHttpCall { connection }.execute() }
            assertTrue(connection.inputClosed)
            assertEquals(1, connection.disconnects)
        }
        val timeout = Connection(fixture()).apply { stream = object : InputStream() {
            override fun read(): Int = throw SocketTimeoutException()
        } }
        fails { ReleaseUpdateHttpCall { timeout }.execute() }
        assertEquals(1, timeout.disconnects)
    }
    @Test fun cancellationBeforeStartNeverConnectsAndDuringReadNeverPublishes() {
        var opens = 0
        val before = ReleaseUpdateHttpCall { opens++; Connection(fixture()) }
        before.cancel()
        fails { before.execute() }
        assertEquals(0, opens)
        val connection = Connection(fixture())
        lateinit var active: ReleaseUpdateHttpCall
        connection.stream = object : ByteArrayInputStream(fixture()) {
            override fun read(bytes: ByteArray, off: Int, len: Int): Int {
                active.cancel()
                return super.read(bytes, off, len)
            }
        }
        active = ReleaseUpdateHttpCall { connection }
        fails { active.execute() }
        assertTrue(connection.disconnects >= 1)
    }
    @Test fun throttlingMetadataIsPreservedWithoutReadingAnErrorBody() {
        for (code in listOf(429, 503)) {
            val connection = Connection(fixture(), code, retry = "120")
            try { ReleaseUpdateHttpCall { connection }.execute(); fail("Expected response failure") }
            catch (error: ReleaseUpdateHttpException) {
                assertEquals(code, error.code); assertEquals("120", error.retryAfter)
            }
            assertFalse(connection.inputClosed); assertEquals(1, connection.disconnects)
        }
    }

}
