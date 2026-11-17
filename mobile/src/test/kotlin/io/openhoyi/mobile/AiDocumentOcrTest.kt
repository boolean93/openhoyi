package io.openhoyi.mobile

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URL
import java.security.cert.Certificate
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.HttpsURLConnection

class AiDocumentOcrTest {
    private val bytes = "%PDF-1.7\nprivate-document".toByteArray()
    private val epoch = "00000000-0000-0000-0000-000000000001"
    private fun original() = AiAttachment(UUID.randomUUID().toString(), "private-name.pdf", "application/pdf", bytes.size, AiAttachmentKind.DOCUMENT)
    private fun response(text: String = "actual text", reason: String = "stop") = buildJsonObject {
        put("id", "ocr-fake_123"); put("object", "ocr.result"); put("created", 1800000000); put("purpose", "text_recognition"); put("page_count", 1)
        put("pages", buildJsonArray { add(buildJsonObject { put("index", 0); put("text", text); put("finish_reason", reason) }) })
        put("usage", buildJsonObject { put("input_tokens", 1500); put("output_tokens", 500); put("total_tokens", 2000) })
    }.toString()
    private open class Connection(val answer: ByteArray, val code: Int = 200) : HttpsURLConnection(URL(AiDocumentOcr.ENDPOINT)) {
        val sent = ByteArrayOutputStream(); val headers = mutableMapOf<String,String>(); var errorRead = false; val disconnected = AtomicBoolean()
        override fun setRequestProperty(k:String,v:String) { headers[k] = v }
        override fun connect() {}
        override fun disconnect() { disconnected.set(true) }
        override fun usingProxy() = false
        override fun getOutputStream() = sent
        override fun getInputStream(): java.io.InputStream = ByteArrayInputStream(answer)
        override fun getResponseCode() = code
        override fun getErrorStream() = object : ByteArrayInputStream("private error".toByteArray()) { override fun read():Int { errorRead=true; return super.read() } }
        override fun getCipherSuite() = "fake"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
    }
    @Test fun multipartIsOcrOnlyAndNeverContainsFileExpiryOrPrivateFilename() = runBlocking {
        val c = Connection(("\n\n" + response()).toByteArray()); var opens = 0
        val client = AiDocumentOcr({ "fake-ocr-key" }, { true }, { u -> assertEquals(AiDocumentOcr.ENDPOINT,u.toString()); opens++; c })
        val result = client.recognize(original(), bytes)
        assertEquals(1, opens); assertEquals(1, result.pages); assertFalse(result.incomplete); assertEquals(1500L,result.referenceInput)
        val body = c.sent.toString("UTF-8")
        assertEquals(listOf("purpose", "file"),
            Regex("Content-Disposition: form-data; name=\"([^\"]+)\"").findAll(body).map { it.groupValues[1] }.toList())
        assertTrue(body.contains("text_recognition")); assertTrue(body.indexOf("name=\"purpose\"") < body.indexOf("name=\"file\""))
        assertFalse(body.contains("expires_after")); assertFalse(body.contains("private-name")); assertFalse(body.contains("fake-ocr-key"))
        assertFalse(c.instanceFollowRedirects); assertTrue(c.disconnected.get()); assertEquals("AiOcrResult", result.toString())
        assertTrue(runCatching { client.recognize(original(), bytes) }.isFailure); assertEquals(1,opens)
    }
    @Test fun strictPageAndUsageSchemaRejectsHttp200ErrorsAndDamagedOutput() {
        val base = Json.parseToJsonElement(response()).jsonObject
        val variants = listOf(
            "{\"error\":{\"message\":\"private error\"}}",
            JsonObject(base + ("page_count" to JsonPrimitive(2))).toString(),
            JsonObject(base + ("purpose" to JsonPrimitive("rich_layout_recognition"))).toString(),
            JsonObject(base + ("pages" to buildJsonArray { add(buildJsonObject { put("index",1);put("text","x");put("finish_reason","stop") }) })).toString(),
            response(reason="unknown"),
            JsonObject(base + ("usage" to buildJsonObject { put("input_tokens",2);put("output_tokens",3);put("total_tokens",8) })).toString())
        variants.forEach { raw -> val f = runCatching { AiDocumentOcr.parse(raw) }.exceptionOrNull() as AiOcrFailure
            assertTrue(f.mayHaveCharged); assertNull(f.cause); assertFalse(f.toString().contains("private")) }
    }
    @Test fun lengthIsRetainedAndMarkedIncompleteNeverPretendsToBeFullDocument() {
        val result = AiDocumentOcr.parse(response("partial text","length"))
        assertTrue(result.incomplete); assertTrue(result.text.contains("INCOMPLETE")); assertTrue(result.text.contains("partial text"))
    }
    @Test fun actualExtractedTextBudgetIsIndependentOfOcrReferenceTokensAndOriginalBytes() {
        val result = AiDocumentOcr.parse(response("中".repeat(6000)))
        val text = result.text.toByteArray()
        val a = AiAttachment(UUID.randomUUID().toString(),"paper-OCR.txt","text/plain",text.size,AiAttachmentKind.OCR_TEXT)
        assertTrue(AiAttachmentConversation.tokens(listOf(AiAttachmentConversation.message("q",listOf(a)))) > 18000)
        assertEquals(500L,result.referenceOutput)
        val wire = AiAttachmentConversation.wire(listOf(AiAttachmentConversation.message("q",listOf(a)))) { AiPreparedAttachment(it,text.copyOf()) }
        assertTrue(wire.single()["content"]!!.jsonArray.last().jsonObject["text"]!!.jsonPrimitive.content.contains("中".repeat(6000)))
    }
    @Test fun attemptsAndSuccessfulMetadataRecoverInOriginalModelSlotWithoutNetworkReplay() {
        val document = original(); val attempt = AiOcrRecord(document,AiOcrStatus.IN_FLIGHT)
        val pending = AiSession(epoch,emptyList(),AiProtocolHistory.empty(),"draft",AiUsageSnapshot(),listOf(document),ocr=listOf(attempt))
        val restored = AiSessionCodec.decode(AiSessionCodec.encode(AiModelSessions.switch(pending,AiModelPolicy.PRO)))
        assertEquals(AiModelPolicy.FLASH,restored.inactive.single().modelId)
        assertEquals(AiOcrStatus.UNKNOWN,restored.inactive.single().ocr.single().status)
        assertEquals(document.id,restored.inactive.single().draftAttachments.single().id)
        val text=AiAttachment(UUID.randomUUID().toString(),"paper-OCR.txt","text/plain",20,AiAttachmentKind.OCR_TEXT)
        val ready=AiOcrRecord(document,AiOcrStatus.READY,text,2,100L,50L)
        val session=AiSession(epoch,emptyList(),AiProtocolHistory.empty(),"draft",AiUsageSnapshot(),listOf(document),ocr=listOf(ready))
        val back=AiModelSessions.switch(AiSessionCodec.decode(AiSessionCodec.encode(AiModelSessions.switch(session,AiModelPolicy.PRO))),AiModelPolicy.FLASH)
        assertEquals(text.id,back.ocr.single().extracted!!.id);assertEquals(2,back.ocr.single().pages);assertEquals(0L,back.usage.requests)
    }
    @Test fun nonSuccessAndKeyMutationAfterConnectAreConservativelyPossiblyBilledAndNotRetried() = runBlocking {
        for(code in listOf(302,401,429,504,200)) {
            var current=true;var opens=0
            val c=object:Connection(if(code==200) "{\"error\":{}}".toByteArray() else response().toByteArray(),code) {
                override fun connect() { if(code==401) current=false }
            }
            val client=AiDocumentOcr({"fake-ocr-key"},{current},{opens++;c})
            val f=runCatching{client.recognize(original(),bytes)}.exceptionOrNull() as AiOcrFailure
            assertTrue(f.mayHaveCharged);assertFalse(c.errorRead);assertNull(f.cause)
            assertTrue(runCatching{client.recognize(original(),bytes)}.isFailure);assertEquals(1,opens)
        }
    }
    @Test fun timeoutAndCancellationNeverStartAnotherRecognition() = runBlocking {
        var opens = 0
        val timedOut = object : Connection(response().toByteArray()) {
            override fun getResponseCode(): Int = throw java.net.SocketTimeoutException("private timeout")
        }
        val client = AiDocumentOcr({ "fake-ocr-key" }, { true }, { opens++; timedOut })
        val failure = runCatching { client.recognize(original(), bytes) }.exceptionOrNull() as AiOcrFailure
        assertEquals(AiOcrProblem.UNKNOWN, failure.problem); assertTrue(failure.mayHaveCharged)
        assertTrue(runCatching { client.recognize(original(), bytes) }.isFailure); assertEquals(1, opens)

        val began = java.util.concurrent.CountDownLatch(1)
        val released = java.util.concurrent.CountDownLatch(1)
        val blocked = object : Connection(response().toByteArray()) {
            override fun getInputStream() = object : java.io.InputStream() {
                override fun read(): Int { began.countDown(); released.await(2, java.util.concurrent.TimeUnit.SECONDS); return -1 }
            }
            override fun disconnect() { super.disconnect(); released.countDown() }
        }
        var cancelledOpens = 0
        val cancelled = AiDocumentOcr({ "fake-ocr-key" }, { true }, { cancelledOpens++; blocked })
        val job = async(Dispatchers.Default) { cancelled.recognize(original(), bytes) }
        withTimeout(1000) { while (began.count > 0) delay(1) }
        withTimeout(1000) { job.cancelAndJoin() }
        assertTrue(blocked.disconnected.get()); assertTrue(job.isCancelled)
        assertTrue(runCatching { cancelled.recognize(original(), bytes) }.isFailure)
        assertEquals(1, cancelledOpens)
    }
    @Test fun malformedUtf8AndOverlargeResultsAreRejectedWithoutChatOrRetry() = runBlocking {
        val raw=response().toByteArray()+byteArrayOf(0xc3.toByte(),0x28)
        val client=AiDocumentOcr({"fake-ocr-key"},{true},{Connection(raw)})
        assertTrue((runCatching{client.recognize(original(),bytes)}.exceptionOrNull() as AiOcrFailure).mayHaveCharged)
        val f=runCatching{AiDocumentOcr.parse(response("x".repeat(AiAttachmentPolicy.MAX_EXTRACTED_BYTES+1)))}.exceptionOrNull() as AiOcrFailure
        assertEquals(AiOcrProblem.LIMIT,f.problem)
    }
}
