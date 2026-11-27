package io.openhoyi.mobile

import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.Proxy
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.security.cert.Certificate
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.HttpsURLConnection

/** Instrumentation-process-only URL guard. Never delegates HTTP(S) to the platform/network. */
internal class AiAndroidHttpSandbox private constructor() {
    class Reply internal constructor(internal val status: Int, internal val body: String, internal val blocked: Boolean) {
        val readStarted = CountDownLatch(1)
        internal val released = CountDownLatch(1)
        val disconnected = AtomicBoolean()
        val errorRead = AtomicBoolean()
        val authorizationPresent = AtomicBoolean()
    }

    private val pending = ConcurrentLinkedQueue<Reply>()
    private val requests = CopyOnWriteArrayList<FakeConnection>()
    val requestCount: Int get() = requests.size
    /** Test assertions only. Never include these strings in instrumentation results or logs. */
    val lastBodies: List<String> get() = requests.map { it.sent.toString("UTF-8") }
    fun enqueue(status: Int = 200, body: String = answer("ok"), blockedRead: Boolean = false): Reply =
        Reply(status, body, blockedRead).also { pending.add(it) }
    fun requireInstalled() { check(installed === this) { "AI HTTP sandbox unavailable" } }
    fun assertDrained() { check(pending.isEmpty()) { "AI fixture responses not consumed" } }

    private fun open(url: URL): URLConnection {
        requireInstalled()
        // Exact match also excludes alternate ports, credentials, query strings and redirects.
        if (url.toExternalForm() != AiSafeTransport.ENDPOINT) throw IOException("AI sandbox rejected URL")
        val reply = pending.poll() ?: throw IOException("AI sandbox response missing")
        return FakeConnection(url, reply).also(requests::add)
    }

    private class FakeConnection(url: URL, private val reply: Reply) : HttpsURLConnection(url) {
        val sent = ByteArrayOutputStream()
        override fun setRequestProperty(key: String, value: String) {
            // Do not retain credentials, even in the in-memory request capture.
            if (key.equals("Authorization", ignoreCase = true)) reply.authorizationPresent.set(value.startsWith("Bearer "))
        }
        override fun getOutputStream() = sent
        override fun getResponseCode() = reply.status
        override fun getInputStream(): InputStream = if (!reply.blocked) ByteArrayInputStream(reply.body.toByteArray(Charsets.UTF_8))
        else object : InputStream() {
            override fun read(): Int {
                reply.readStarted.countDown()
                if (!reply.released.await(5, TimeUnit.SECONDS)) throw IOException("AI sandbox read timeout")
                return -1
            }
            override fun close() { reply.released.countDown() }
        }
        override fun getErrorStream(): InputStream = object : ByteArrayInputStream(reply.body.toByteArray(Charsets.UTF_8)) {
            override fun read(): Int { reply.errorRead.set(true); return super.read() }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                reply.errorRead.set(true); return super.read(buffer, offset, length)
            }
        }
        override fun connect() = Unit
        override fun disconnect() { reply.disconnected.set(true); reply.released.countDown() }
        override fun usingProxy() = false
        override fun getCipherSuite() = "fixture"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
    }

    companion object {
        @Volatile private var installed: AiAndroidHttpSandbox? = null
        @Volatile private var installationFailed = false

        /** Call before any send or Activity launch; a failed singleton install is terminal. */
        @Synchronized fun install(): AiAndroidHttpSandbox {
            installed?.let { return it }
            check(!installationFailed) { "AI HTTP sandbox installation failed" }
            val sandbox = AiAndroidHttpSandbox()
            try {
                URL.setURLStreamHandlerFactory { protocol ->
                    if (protocol == "http" || protocol == "https") object : URLStreamHandler() {
                        override fun openConnection(url: URL): URLConnection = sandbox.open(url)
                        override fun openConnection(url: URL, proxy: Proxy): URLConnection = sandbox.open(url)
                    } else null
                }
                installed = sandbox
                return sandbox
            } catch (_: Throwable) {
                installationFailed = true
                // No original cause, endpoint, credential or body can escape in test diagnostics.
                throw IllegalStateException("AI HTTP sandbox installation failed")
            }
        }

        fun answer(text: String, finishReason: String = "stop"): String =
            stream(""""content":${JSONObject.quote(text)}""", finishReason)

        val toolAnswer: String get() = toolAnswer("current_status", "{}")

        fun toolAnswer(name: String, arguments: String): String = stream(
            """"tool_calls":[{"index":0,"id":"call1","type":"function","function":{"name":${JSONObject.quote(name)},"arguments":${JSONObject.quote(arguments)}}}]""",
            "tool_calls",
        )

        private fun stream(delta: String, finishReason: String): String {
            val header = """"id":"fixture","system_fingerprint":"fixture","object":"chat.completion.chunk","created":1,"model":"deepseek-flash""""
            return listOf(
                """{$header,"choices":[{"index":0,"delta":{"role":"assistant","reasoning_content":"offline fixture reasoning",$delta},"finish_reason":null}]}""",
                """{$header,"choices":[{"index":0,"delta":{},"finish_reason":${JSONObject.quote(finishReason)}}]}""",
                """{$header,"choices":[],"usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}""",
                "[DONE]",
            ).joinToString(separator = "", transform = { "data: $it\n\n" })
        }
    }
}
