package io.openhoyi.mobile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.SocketTimeoutException
import java.net.URL
import java.security.cert.Certificate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLPeerUnverifiedException

class AiDocumentUploadTest {
    private val key = "fake-sentinel-key-never-real"
    private val now = 1800000000L
    private val bytes = "%PDF-1.7\nprivate-document-sentinel".toByteArray()
    private fun attachment(size: Int = bytes.size, mime: String = "application/pdf") =
        AiAttachment(UUID.randomUUID().toString(), "private-local-name.pdf", mime, size, AiAttachmentKind.DOCUMENT)
    private fun response(a: AiAttachment) = """{"id":"file-api-test_123456789","object":"file","bytes":${a.size},"purpose":"rich_layout_recognition","created_at":$now,"expires_at":${now + 86400}}"""
    private fun uploader(c: UploadConnection, current: () -> Boolean = { true }) = AiDocumentUpload({ key }, current, { c }, { now })

    @Test fun multipartPurposePrecedesFileWithExpiryAndNoFilenameLeak() = runBlocking {
        val a = attachment(); val c = UploadConnection(answer = response(a))
        val result = uploader(c).upload(a, bytes)
        assertEquals(a.id, result.localAttachmentId); assertEquals("file-api-test_123456789", result.fileId)
        assertEquals(now + 86400, result.expiresAt)
        assertEquals("POST", c.requestMethod); assertFalse(c.instanceFollowRedirects); assertFalse(c.useCaches)
        assertEquals("Bearer $key", c.headers["Authorization"])
        assertEquals("application/json", c.headers["Accept"])
        assertEquals(c.fixedLengthStreamingMode, c.sent.size())
        val boundary = c.headers["Content-Type"]!!.substringAfter("boundary=")
        val body = c.sent.toString("UTF-8")
        assertTrue(body.startsWith("--$boundary\r\n")); assertTrue(body.endsWith("\r\n--$boundary--\r\n"))
        val purpose = body.indexOf("name=\"purpose\"")
        val file = body.indexOf("name=\"file\"")
        assertTrue(purpose >= 0 && purpose < file)
        assertTrue(body.contains("\r\n\r\nrich_layout_recognition\r\n"))
        assertTrue(body.contains("name=\"expires_after[anchor]\"\r\n\r\ncreated_at\r\n"))
        assertTrue(body.contains("name=\"expires_after[seconds]\"\r\n\r\n86400\r\n"))
        assertEquals(listOf("purpose", "expires_after[anchor]", "expires_after[seconds]", "file"),
            Regex("Content-Disposition: form-data; name=\"([^\"]+)\"").findAll(body).map { it.groupValues[1] }.toList())
        assertTrue(AiDocumentUpload.EXPIRY_SECONDS in 3600L..2592000L)
        assertTrue(body.contains("filename=\"document.pdf\"")); assertTrue(body.contains(String(bytes)))
        assertFalse(body.contains(a.name)); assertFalse(body.contains(key)); assertTrue(c.disconnected.get())
        assertEquals("AiUploadedDocument", result.toString())
        assertTrue(bytes.any { it != 0.toByte() }) // Caller, not transport, owns this buffer.
    }

    @Test fun successfulIdMappingIsReusedAndNeverAutomaticallyReuploadedAfterExpiry() = runBlocking {
        val a = attachment(); var opens = 0; var time = now
        val c = UploadConnection(answer = response(a))
        val t = AiDocumentUpload({ key }, { true }, { opens++; c }, { time })
        val first = t.upload(a, bytes)
        assertSame(first, t.upload(a, bytes)); assertEquals(1, opens)
        time += 86401
        val f = runCatching { t.upload(a, bytes) }.exceptionOrNull() as AiDocumentUploadFailure
        assertEquals(AiDocumentUploadProblem.EXPIRED, f.problem); assertEquals(1, opens)
        val changed = bytes.copyOf().also { it[it.lastIndex] = 0 }
        assertEquals(AiDocumentUploadProblem.INVALID, (runCatching { t.upload(a, changed) }.exceptionOrNull() as AiDocumentUploadFailure).problem)
        assertEquals(1, opens)
    }

    @Test fun redirectsAndErrorsNeverReadOrExposeProviderTextAndNeverRetry() = runBlocking {
        for (status in listOf(302, 400, 401, 402, 403, 408, 413, 429, 500, 504)) {
            val a = attachment(); val c = UploadConnection(status, key + " private-document-sentinel")
            var opens = 0
            val t = AiDocumentUpload({ key }, { true }, { u -> assertEquals(AiDocumentUpload.ENDPOINT, u.toString()); opens++; c }, { now })
            val f = runCatching { t.upload(a, bytes) }.exceptionOrNull() as AiDocumentUploadFailure
            assertNull(f.cause); assertEquals(f.problem.name, f.message); assertFalse(f.toString().contains(key))
            assertFalse(c.errorRead); assertTrue(c.errorClosed); assertFalse(c.instanceFollowRedirects)
            assertTrue(c.disconnected.get())
            assertEquals(AiDocumentUploadProblem.UNKNOWN, (runCatching { t.upload(a, bytes) }.exceptionOrNull() as AiDocumentUploadFailure).problem)
            assertEquals(1, opens)
        }
    }

    @Test fun timeoutIsUnknownNotNotUploadedAndHasNoAutomaticRetry() = runBlocking {
        val a = attachment(); var opens = 0
        val c = object : UploadConnection() {
            override fun getResponseCode(): Int { throw SocketTimeoutException(key + " private-document-sentinel") }
        }
        val t = AiDocumentUpload({ key }, { true }, { opens++; c }, { now })
        repeat(2) {
            val f = runCatching { t.upload(a, bytes) }.exceptionOrNull() as AiDocumentUploadFailure
            assertEquals(AiDocumentUploadProblem.UNKNOWN, f.problem); assertTrue(f.remoteMayExist); assertNull(f.cause)
            assertFalse(f.toString().contains(key))
        }
        assertEquals(1, opens)
    }

    @Test fun malformedMissingExpiryWrongPurposeAndOversizeSuccessAreUnknown() = runBlocking {
        val a = attachment()
        val valid = response(a)
        for (body in listOf("", "not-json-$key", valid.replace("file-api-test_123456789", "https://evil.invalid"),
            valid.replace("rich_layout_recognition", "text_recognition"), valid.replace("\"expires_at\":${now + 86400}", "\"expires_at\":null"),
            valid.replace("\"bytes\":${a.size}", "\"bytes\":0"), "x".repeat(AiDocumentUpload.MAX_RESPONSE_BYTES + 1))) {
            val c = UploadConnection(answer = body)
            val f = runCatching { uploader(c).upload(a, bytes) }.exceptionOrNull() as AiDocumentUploadFailure
            assertEquals(AiDocumentUploadProblem.UNKNOWN, f.problem); assertTrue(f.remoteMayExist); assertNull(f.cause)
            assertTrue(c.disconnected.get()); assertTrue(c.responseBytesRead <= AiDocumentUpload.MAX_RESPONSE_BYTES + 1)
        }
    }

    @Test fun sizeTypeAndKeyEpochRejectBeforeNetworkOrKeyRead() = runBlocking {
        var opens = 0; var reads = 0; var current = true
        val t = AiDocumentUpload({ reads++; key }, { current }, { opens++; UploadConnection() }, { now })
        val a = attachment()
        val f = runCatching { t.upload(a, ByteArray(AiAttachmentPolicy.MAX_DOCUMENT_BYTES + 1)) }.exceptionOrNull() as AiDocumentUploadFailure
        assertEquals(AiDocumentUploadProblem.INVALID, f.problem)
        val text = AiAttachment(UUID.randomUUID().toString(), "a.txt", "text/plain", 1, AiAttachmentKind.TEXT)
        assertEquals(AiDocumentUploadProblem.INVALID, (runCatching { t.upload(text, byteArrayOf(65)) }.exceptionOrNull() as AiDocumentUploadFailure).problem)
        current = false
        assertEquals(AiDocumentUploadProblem.KEY, (runCatching { t.upload(a, bytes) }.exceptionOrNull() as AiDocumentUploadFailure).problem)
        assertEquals(0, opens); assertEquals(0, reads)
    }

    @Test fun keyReadFailureCannotExposeItsExceptionOrSendAnything() = runBlocking {
        val c = UploadConnection()
        val t = AiDocumentUpload({ error("$key private-document-sentinel") }, { true }, { c }, { now })
        val f = runCatching { t.upload(attachment(), bytes) }.exceptionOrNull() as AiDocumentUploadFailure
        assertEquals(AiDocumentUploadProblem.KEY, f.problem); assertNull(f.cause); assertFalse(f.remoteMayExist)
        assertEquals(0, c.connects); assertEquals(0, c.sent.size()); assertNull(c.headers["Authorization"])
    }

    @Test fun fullTenMiBDocumentIsAcceptedButTotalAboveTwentyMiBStopsBeforeOpening() = runBlocking {
        val large = ByteArray(AiAttachmentPolicy.MAX_DOCUMENT_BYTES)
        var next = attachment(large.size); var opens = 0
        val t = AiDocumentUpload({ key }, { true }, { opens++; UploadConnection(answer = response(next)) }, { now })
        repeat(2) { next = attachment(large.size); t.upload(next, large) }
        val f = runCatching { t.upload(attachment(), bytes) }.exceptionOrNull() as AiDocumentUploadFailure
        assertEquals(AiDocumentUploadProblem.LIMIT, f.problem); assertEquals(2, opens)
    }

    @Test fun injectedWrongHostDoesNotReceiveKeyOrData() = runBlocking {
        val c = UploadConnection(url = URL("https://other.invalid/files")); var keyReads = 0
        val t = AiDocumentUpload({ keyReads++; key }, { true }, { c }, { now })
        assertEquals(AiDocumentUploadProblem.INVALID, (runCatching { t.upload(attachment(), bytes) }.exceptionOrNull() as AiDocumentUploadFailure).problem)
        assertEquals(0, keyReads); assertTrue(c.headers.isEmpty()); assertEquals(0, c.sent.size())
    }

    @Test fun batchCountLimitNeverConnectsFourthDocument() = runBlocking {
        var opens = 0
        var next: AiAttachment? = null
        val t = AiDocumentUpload({ key }, { true }, { opens++; UploadConnection(answer = response(next!!)) }, { now })
        repeat(3) { next = attachment(); t.upload(next!!, bytes) }
        next = attachment()
        assertEquals(AiDocumentUploadProblem.LIMIT, (runCatching { t.upload(next!!, bytes) }.exceptionOrNull() as AiDocumentUploadFailure).problem)
        assertEquals(3, opens) // Connection object may be configured, but never connects/sends for the fourth.
    }

    @Test fun cancellationDisconnectsBlockedReadWithoutRetry() = runBlocking {
        val began = CountDownLatch(1); val released = CountDownLatch(1)
        val c = UploadConnection().apply { stream = object : InputStream() {
            override fun read(): Int { began.countDown(); released.await(2, TimeUnit.SECONDS); return -1 }
            override fun close() { released.countDown() }
        } }
        val t = uploader(c)
        val request = async(Dispatchers.Default) { t.upload(attachment(), bytes) }
        withTimeout(1000) { while (began.count > 0) delay(1) }
        withTimeout(1000) { request.cancelAndJoin() }
        assertTrue(c.disconnected.get()); assertTrue(request.isCancelled)
    }

    @Test fun credentialMutationStopsResponseAndDiscardsProviderBody() = runBlocking {
        val a = attachment(); var current = true
        val c = object : UploadConnection(answer = response(a)) {
            override fun getResponseCode(): Int { current = false; return 200 }
        }
        val f = runCatching { uploader(c) { current }.upload(a, bytes) }.exceptionOrNull() as AiDocumentUploadFailure
        assertEquals(AiDocumentUploadProblem.KEY, f.problem); assertTrue(f.remoteMayExist)
        assertTrue(c.disconnected.get()); assertEquals(0, c.responseBytesRead)
    }

    private open class UploadConnection(private val code: Int = 200, private val answer: String = "{}",
        url: URL = URL(AiDocumentUpload.ENDPOINT)) : HttpsURLConnection(url) {
        val sent = ByteArrayOutputStream()
        val headers = mutableMapOf<String, String>()
        val disconnected = AtomicBoolean()
        var errorRead = false; var errorClosed = false; var responseBytesRead = 0; var connects = 0
        var stream: InputStream? = null
        val fixedLengthStreamingMode get() = fixedContentLength
        override fun setRequestProperty(key: String, value: String) { headers[key] = value }
        override fun getOutputStream() = sent
        override fun getResponseCode() = code
        override fun getInputStream(): InputStream = stream ?: object : ByteArrayInputStream(answer.toByteArray()) {
            override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) responseBytesRead += it }
        }
        override fun getErrorStream(): InputStream = object : ByteArrayInputStream(answer.toByteArray()) {
            override fun read(): Int { errorRead = true; return super.read() }
            override fun read(b: ByteArray, off: Int, len: Int): Int { errorRead = true; return super.read(b, off, len) }
            override fun close() { errorClosed = true; super.close() }
        }
        override fun connect() { connects++ }
        override fun disconnect() { disconnected.set(true); stream?.close() }
        override fun usingProxy() = false
        override fun getCipherSuite() = "fake"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = throw SSLPeerUnverifiedException("fake")
    }
}
