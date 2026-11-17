package io.openhoyi.mobile

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.Level
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

/** Executes real SDK/transport paths; host runner separately checks this process's logcat. */
internal object AiAndroidLogChecks {
    private const val USER = "PRIVATE_USER_SENTINEL"
    private const val TOOL = "PRIVATE_TOOL_SENTINEL"
    private const val ERROR = "PRIVATE_ERROR_SENTINEL"

    /** Invoke on instrumentation worker, not main. Key callback is used only by the send boundary. */
    fun run(sandbox: AiAndroidHttpSandbox, key: () -> String) {
        sandbox.requireInstalled()
        val property = "kotlin-logging-to-android-native"
        val old = System.getProperty(property)
        try {
            System.setProperty(property, "true")
            AiSdkLogging.install()
            var evaluated = false
            val logger = KotlinLogging.logger("ai.koog.android.fixture")
            for (level in Level.entries) {
                check(!logger.isLoggingEnabledFor(level)) { "AI logging unexpectedly enabled" }
                logger.at(level) { evaluated = true; message = "$USER $TOOL $ERROR" }
            }
            logger.error(IllegalStateException("PRIVATE_THROWABLE_SENTINEL")) { evaluated = true; USER }
            check(!evaluated) { "AI lazy log payload evaluated" }
            check(org.slf4j.LoggerFactory.getILoggerFactory().javaClass.name == "org.slf4j.helpers.NOPLoggerFactory") {
                "AI SLF4J backend is not silent"
            }
            runBlocking { exercise(sandbox, key) }
        } finally {
            if (old == null) System.clearProperty(property) else System.setProperty(property, old)
        }
    }

    private suspend fun exercise(sandbox: AiAndroidHttpSandbox, key: () -> String) {
        val reads = AtomicInteger()
        val keyReads = AtomicInteger()
        val guardedKey = { keyReads.incrementAndGet(); key() }
        val data = object : AiReadOnlyData {
            override fun currentStatus(): String {
                reads.incrementAndGet()
                return "{\"status\":\"unknown\",\"sentinel\":\"$TOOL\"}"
            }
            override fun curves(query: String, offset: Int, limit: Int) = "{\"items\":[]}"
            override fun history(curveId: String?, offset: Int, limit: Int) = "{\"items\":[]}"
        }
        // Verify rejection before touching the SDK. Neither URL ever delegates to real networking.
        val before = sandbox.requestCount
        check(runCatching { URL("http://api.deepseek.com/chat/completions").openConnection() }.isFailure) { "AI HTTP not rejected" }
        check(runCatching { URL("https://outside.invalid/chat/completions").openConnection() }.isFailure) { "AI external host not rejected" }
        check(sandbox.requestCount == before && keyReads.get() == 0) { "AI sandbox rejection changed state" }

        suspend fun reply(): String {
            sandbox.requireInstalled()
            val budget = AiRequestBudget()
            val transport = AiSafeTransport(guardedKey, { true }, budget)
            return AiChatSession(data, transport, budget, Dispatchers.Unconfined).reply(emptyList(), USER)
        }
        val tool = sandbox.enqueue(body = AiAndroidHttpSandbox.toolAnswer)
        val answer = sandbox.enqueue(body = AiAndroidHttpSandbox.answer("fixture complete"))
        check(reply() == "fixture complete") { "AI tool reply mismatch" }
        check(sandbox.requestCount == before + 2 && reads.get() == 1 && keyReads.get() == 2) { "AI tool accounting mismatch" }
        check(tool.authorizationPresent.get() && answer.authorizationPresent.get()) { "AI authorization missing" }
        val bodies = sandbox.lastBodies.takeLast(2)
        check(bodies.first().contains(USER) && bodies.last().contains(TOOL)) { "AI private fixture absent from request" }
        for (body in bodies) {
            val json = JSONObject(body)
            check(json.getString("model") == "deepseek-flash") { "AI model mismatch" }
            check(json.getJSONObject("thinking").getString("type") == "enabled") { "AI thinking mode mismatch" }
            check(json.getJSONObject("safety_check").getString("level") == "high") { "AI safety mode mismatch" }
        }

        val denied = sandbox.enqueue(401, "$ERROR PRIVATE_KEY_SENTINEL $USER")
        val auth = runCatching { reply() }.exceptionOrNull()
        check(auth is AiFailure && auth.problem == AiProblem.AUTH && auth.cause == null && auth.message == "AUTH") {
            "AI authentication error was not sanitized"
        }
        check(!denied.errorRead.get() && denied.disconnected.get()) { "AI error response consumed or left connected" }
        sandbox.enqueue(body = AiAndroidHttpSandbox.answer("", "content_filter"))
        val refusal = runCatching { reply() }.exceptionOrNull()
        check(refusal is AiFailure && refusal.problem == AiProblem.REFUSED && refusal.cause == null) {
            "AI content filter not preserved"
        }
        check(reads.get() == 1) { "AI unexpected tool read" }

        val blocked = sandbox.enqueue(blockedRead = true)
        val budget = AiRequestBudget()
        val transport = AiSafeTransport(guardedKey, { true }, budget)
        kotlinx.coroutines.coroutineScope {
            val request = async(Dispatchers.Default) { transport.post("chat/completions", "{}", String::class, String::class) }
            try {
                withTimeout(2000) { while (blocked.readStarted.count > 0) delay(5) }
                withTimeout(2000) { request.cancelAndJoin() }
                check(request.isCancelled && blocked.disconnected.get()) { "AI blocked read was not cancelled" }
            } finally { transport.close(); request.cancelAndJoin() }
        }
        check(keyReads.get() == 5) { "AI key callback was not send-scoped" }
        sandbox.assertDrained()
    }
}
