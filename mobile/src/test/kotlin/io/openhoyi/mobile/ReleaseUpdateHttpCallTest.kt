package io.openhoyi.mobile

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.SocketTimeoutException
import java.net.URL
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ReleaseUpdateHttpCallTest {
    private fun fixture(code: Int = 94) = JSONObject(mapOf(
        "schemaVersion" to 1, "channel" to "develop-test", "repository" to "boolean93/openhoyi",
        "packageName" to "io.openhoyi.mobile", "versionCode" to code, "versionName" to "0.2.0-dev.$code",
        "apkSha256" to "a".repeat(64), "signerCertificateSha256" to "b".repeat(64),
        "declaredSourceCommit" to "c".repeat(40), "sourceCommitVerified" to false, "releaseTag" to "dev-$code",
        "publishedAt" to "2026-11-12T11:40:15Z", "apkFile" to "OpenHOYI.apk", "apkSize" to 100,
        "releaseUrl" to "https://github.com/boolean93/openhoyi/releases/tag/dev-$code",
        "downloadUrl" to "https://github.com/boolean93/openhoyi/releases/download/dev-$code/OpenHOYI.apk",
        "distributionUrl" to "https://github.com/boolean93/openhoyi/releases/download/dev-$code/distribution.json",
        "checksumsUrl" to "https://github.com/boolean93/openhoyi/releases/download/dev-$code/SHA256SUMS.txt",
    )).toString().toByteArray()
    private class Connection(val bytes: ByteArray, val code: Int = 200, val length: Long = -1,
        val headers: Map<String,String> = emptyMap()) : HttpsURLConnection(URL(ReleaseUpdateIndex.URL)) {
        var disconnected = false
        var inputClosed = false
        var stream: InputStream = object : ByteArrayInputStream(bytes) {
            override fun close() { inputClosed = true; super.close() }
        }
        override fun getResponseCode() = code
        override fun getHeaderField(name: String?) = headers[name]
        override fun getContentLengthLong() = length
        override fun getInputStream() = stream
        override fun disconnect() { disconnected = true }
        override fun connect() = Unit
        override fun usingProxy() = false
        override fun getCipherSuite() = "test"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
    }
    private fun fails(block: () -> Unit) {
        try { block(); fail("Invalid HTTP response accepted") } catch (_: Exception) { }
    }
    @Test fun unpublishedMirrorIsDisabledByDefault() {
        val urls = mutableListOf<String>()
        ReleaseUpdateHttpCall { url -> urls += url.toString(); Connection(fixture()) }.execute()
        assertEquals(listOf(ReleaseUpdateIndex.URL), urls)
    }
    @Test fun mismatchedMirrorSignerCannotMaskCanonicalRelease() {
        val urls = mutableListOf<String>()
        val result = ReleaseUpdateHttpCall(mirrorEnabled = true, installedVersionCode = 93,
            installedSignerSha256 = "b".repeat(64)) { url ->
            urls += url.toString()
            if (urls.size == 1) Connection(String(fixture(96)).replace("b".repeat(64), "d".repeat(64)).toByteArray())
            else Connection(fixture(95))
        }.execute()
        assertEquals(95L, result.versionCode)
        assertEquals(listOf(ReleaseUpdateIndex.MIRROR_URL, ReleaseUpdateIndex.URL), urls)
    }
    @Test fun domesticFirstAnonymousBoundedRequest() {
        val c = Connection(fixture()); val urls = mutableListOf<String>()
        val result = ReleaseUpdateHttpCall(mirrorEnabled = true) { url -> urls += url.toString(); c }.execute()
        assertEquals(94L, result.versionCode); assertEquals(listOf(ReleaseUpdateIndex.MIRROR_URL), urls)
        assertFalse(c.instanceFollowRedirects); assertFalse(c.useCaches)
        assertEquals(2000, c.connectTimeout); assertEquals(2000, c.readTimeout)
        assertEquals("GET", c.requestMethod); assertNull(c.getRequestProperty("Authorization"))
        assertTrue(c.inputClosed); assertTrue(c.disconnected)
    }
    @Test fun domesticRawRedirectIsExactAndNeverLeaksIntoGithub() {
        val raw = "https://raw.giteeusercontent.com/boolean93/openhoyi/raw/main/updates/dev.json"
        val urls = mutableListOf<String>()
        val result = ReleaseUpdateHttpCall(mirrorEnabled = true) { url -> urls += url.toString()
            if (urls.size == 1) Connection(byteArrayOf(), 302, headers=mapOf("Location" to raw)) else Connection(fixture())
        }.execute()
        assertEquals(94L, result.versionCode); assertEquals(listOf(ReleaseUpdateIndex.MIRROR_URL, raw), urls)
        for (bad in listOf("https://evil.test/updates", raw + "?token=secret", raw.replace("openhoyi", "other"), raw.replace("https", "http"))) {
            val attempts=mutableListOf<String>()
            assertEquals(94L, ReleaseUpdateHttpCall(mirrorEnabled = true) { url -> attempts+=url.toString()
                if(attempts.size==1) Connection(byteArrayOf(),302,headers=mapOf("Location" to bad)) else Connection(fixture())
            }.execute().versionCode)
            assertEquals(listOf(ReleaseUpdateIndex.MIRROR_URL,ReleaseUpdateIndex.URL),attempts)
        }
    }
    @Test fun networkHttpAndMalformedMirrorFallBackToGithub() {
        for (bad in listOf(Connection(fixture(),404),Connection(byteArrayOf(0xc3.toByte(),0x28)),
            Connection("not JSON".toByteArray()),Connection(fixture(),length=ReleaseUpdateIndex.MAX_BYTES+1L),
            Connection(ByteArray(ReleaseUpdateIndex.MAX_BYTES+1){32}),Connection(fixture()).apply {
                stream=object:InputStream(){override fun read():Int=throw SocketTimeoutException("secret URL")}
            })) {
            val urls=mutableListOf<String>(); val good=Connection(fixture())
            assertEquals(94L,ReleaseUpdateHttpCall(mirrorEnabled = true) { url -> urls+=url.toString();if(urls.size==1)bad else good }.execute().versionCode)
            assertEquals(listOf(ReleaseUpdateIndex.MIRROR_URL,ReleaseUpdateIndex.URL),urls)
            assertTrue(bad.disconnected);assertTrue(good.inputClosed);assertTrue(good.disconnected)
        }
    }
    @Test fun noRetryLoopAndPreserveCooldownIfAllSourcesFail() {
        var calls=0
        try { ReleaseUpdateHttpCall(mirrorEnabled = true) { calls++;Connection(byteArrayOf(),if(calls==1)429 else 500,
            headers=mapOf("Retry-After" to "120")) }.execute();fail("failure expected") }
        catch(error:ReleaseUpdateHttpException){assertEquals(429,error.code);assertEquals("120",error.retryAfter)}
        assertEquals(2,calls)
        fails { ReleaseUpdateHttpCall(mirrorEnabled = true) { Connection(byteArrayOf(),302) }.execute() }
    }
    @Test fun cancellationNeverStartsFallbackAndTotalDeadlineIsBounded() {
        var calls=0;val before=ReleaseUpdateHttpCall(mirrorEnabled = true) { calls++;Connection(fixture()) };before.cancel()
        fails{before.execute()};assertEquals(0,calls)
        lateinit var active:ReleaseUpdateHttpCall
        val c=Connection(fixture()).apply{stream=object:ByteArrayInputStream(fixture()){
            override fun read(b:ByteArray,off:Int,len:Int):Int{active.cancel();return super.read(b,off,len)}
        }}
        active=ReleaseUpdateHttpCall(mirrorEnabled = true) { calls++;c };fails{active.execute()};assertEquals(1,calls);assertTrue(c.disconnected)
        var time=0L;calls=0
        fails{ReleaseUpdateHttpCall(mirrorEnabled = true, clock={time}) { calls++;time=11_000_000_000;Connection(fixture()) }.execute()}
        assertEquals(1,calls)
    }
    @Test fun staleMirrorChecksGithubButKeepsHealthyMirrorWhenGithubFails() {
        var calls=0
        assertEquals(95L,ReleaseUpdateHttpCall(mirrorEnabled = true, installedVersionCode=94){calls++;Connection(fixture(if(calls==1)94 else 95))}.execute().versionCode)
        assertEquals(2,calls);calls=0
        assertEquals(94L,ReleaseUpdateHttpCall(mirrorEnabled = true, installedVersionCode=94){calls++;Connection(fixture(),if(calls==1)200 else 500)}.execute().versionCode)
        assertEquals(2,calls);calls=0
        assertEquals(95L,ReleaseUpdateHttpCall(mirrorEnabled = true, installedVersionCode=94){calls++;Connection(fixture(95))}.execute().versionCode)
        assertEquals(1,calls)
    }
}
