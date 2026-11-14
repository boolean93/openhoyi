package io.openhoyi.mobile

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.SocketTimeoutException
import java.net.URL
import java.security.MessageDigest
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection
import org.junit.Assert.*
import org.junit.Test

class ReleaseApkDownloadTest {
    private val github="https://github.com/boolean93/openhoyi/releases/download/dev-94/update.apk"
    private val mirror="https://openhoyi.ios.dog/boolean93/openhoyi/releases/download/dev-94/update.apk"
    private val bytes=byteArrayOf(1,2,3,4)
    private fun target()=ReleaseUpdate(94,"fixture","a".repeat(64),
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it.toInt() and 255)},
        github,"",apkSize=bytes.size.toLong(),mirrorDownloadUrls=listOf("https://gitee.com/boolean93/openhoyi/attach_files/123/download/update.apk"))
    private class Connection(address:String,val bytes:ByteArray,val code:Int=200,val headers:Map<String,String> = emptyMap()):HttpsURLConnection(URL(address)) {
        var stream:InputStream=ByteArrayInputStream(bytes)
        override fun getResponseCode()=code
        override fun getInputStream()=stream
        override fun getHeaderField(name:String?)=headers[name]
        override fun disconnect()=Unit
        override fun connect()=Unit
        override fun usingProxy()=false
        override fun getCipherSuite()="fixture"
        override fun getLocalCertificates():Array<Certificate>?=null
        override fun getServerCertificates():Array<Certificate> = emptyArray()
    }
    private fun verify(file:File,target:ReleaseUpdate){require(releaseApkSha256(file)==target.apkSha256)}
    private fun output()=kotlin.io.path.createTempDirectory().toFile().resolve("one.part")
    private fun rejected(block:()->Unit){try{block();fail("expected rejection")}catch(_:Exception){}}

    @Test fun acceleratorVerifiesSameBytesEvenForPreviouslyCachedCanonicalIndex() {
        val opened = mutableListOf<String>(); val part = output()
        try {
            val call = ReleaseApkDownload(ReleaseApkTransfer({url ->
                opened += url.toString(); Connection(url.toString(), bytes)
            }), ::verify, pause={error("no retry expected")})
            call.download(target().copy(mirrorDownloadUrls=emptyList()), part){_,_->}
            assertEquals(listOf(mirror), opened); assertArrayEquals(bytes, part.readBytes())
        } finally { part.parentFile!!.deleteRecursively() }
    }
    @Test fun partialTransferRetriesWholeFileThenFallsBackWithBoundedAttempts() {
        val opened=mutableListOf<String>();val waits=mutableListOf<Long>();val part=output()
        try {
            val call=ReleaseApkDownload(ReleaseApkTransfer({url->opened+=url.toString()
                if(url.toString()==mirror)Connection(mirror,bytes).apply {stream=object:InputStream(){
                    var first=true
                    override fun read():Int=throw SocketTimeoutException("https://private.example/?token=secret")
                    override fun read(b:ByteArray,off:Int,len:Int):Int {
                        if(first){first=false;b[off]=1;return 1};throw SocketTimeoutException("secret")
                    }
                }} else Connection(github,bytes)
            }),::verify,pause=waits::add)
            call.download(target(),part){_,_->}
            assertEquals(listOf(mirror,mirror,github),opened);assertEquals(listOf(1000L),waits)
            assertArrayEquals(bytes,part.readBytes())
        }finally{part.parentFile!!.deleteRecursively()}
    }
    @Test fun corruptedMirrorNeverPublishesAndFallsBackWithoutRetryingBadBytes() {
        val opened=mutableListOf<String>();val events=mutableListOf<ReleaseTransferEvent>();val part=output()
        try {
            val call=ReleaseApkDownload(ReleaseApkTransfer({url->opened+=url.toString();Connection(url.toString(),
                if(url.toString()==mirror)byteArrayOf(9,9,9,9)else bytes)}),::verify,events::add,pause={error("no retry")})
            call.download(target(),part){_,_->}
            assertEquals(listOf(mirror,github),opened);assertArrayEquals(bytes,part.readBytes())
            assertEquals(ReleaseTransferStage.VERIFY,events.first().stage);assertEquals(ReleaseTransferOutcome.FAILED,events.first().outcome)
        }finally{part.parentFile!!.deleteRecursively()}
    }
    @Test fun rateLimitSkipsSameSourceAndSurvivesTotalFailure() {
        val part=output();val opened=mutableListOf<String>()
        try{
            val call=ReleaseApkDownload(ReleaseApkTransfer({url->opened+=url.toString();Connection(url.toString(),byteArrayOf(),
                if(url.toString()==mirror)429 else 500,if(url.toString()==mirror)mapOf("Retry-After" to "120")else emptyMap())}),::verify,pause={})
            try{call.download(target(),part){_,_->};fail("expected limit")}catch(error:ReleaseUpdateHttpException){assertEquals(429,error.code)}
            assertEquals(listOf(mirror,github,github),opened);assertFalse(part.exists())
        }finally{part.parentFile!!.deleteRecursively()}
    }
    @Test fun cancelDoesNotRetrySwitchSourceOrDeletePreexistingFile() {
        val part=output();var opens=0
        try{
            lateinit var call:ReleaseApkDownload
            call=ReleaseApkDownload(ReleaseApkTransfer({opens++;Connection(mirror,bytes)}),::verify,pause={error("no retry")})
            rejected{call.download(target(),part){_,_->call.cancel()}}
            assertEquals(1,opens);assertFalse(part.exists())
            part.writeText("existing")
            rejected{ReleaseApkDownload(verify=::verify).download(target(),part){_,_->}}
            assertEquals("existing",part.readText())
        }finally{part.parentFile!!.deleteRecursively()}
    }
    @Test fun untrustedMirrorRedirectsNeverConnectAndDiagnosticsContainNoRawMessage() {
        for(bad in listOf("http://foruda.gitee.com/attach_file/1/update.apk", "https://foruda.gitee.com.evil.test/attach_file/1/update.apk",
            "https://foruda.gitee.com/attach_file/1/other.apk", "https://gitee.com/other/openhoyi/attach_files/1/download/update.apk",
            "https://foruda.gitee.com/attach_file/1/update.apk#fragment",
            mirror + "?token=secret", mirror.replace("dev-94", "dev-93"), mirror.replace("update.apk", "other.apk"),
            mirror.replace("ios.dog/", "ios.dog:444/"), mirror.replace("https://", "https://user@"))) {
            val part=output();val opened=mutableListOf<String>()
            try {
                val transfer=ReleaseApkTransfer({url->opened+=url.toString();Connection(mirror,byteArrayOf(),302,mapOf("Location" to bad))})
                rejected{transfer.downloadFrom(target(),mirror,part){}}
                assertEquals(listOf(mirror),opened)
            }finally{part.parentFile!!.deleteRecursively()}
        }
        val fields=releaseTransferEvent(ReleaseTransferStage.APK,ReleaseSource.CLOUDFLARE,1,
            SocketTimeoutException("https://foruda.gitee.com/x?access_token=secret")).fields()
        assertEquals("TIMEOUT",fields["failure"]);assertFalse(fields.toString().contains("secret"));assertFalse(fields.toString().contains("http:"))
    }
}
