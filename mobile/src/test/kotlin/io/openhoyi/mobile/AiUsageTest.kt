package io.openhoyi.mobile

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.io.InputStream
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class AiUsageTest {
    @Test fun tokensUseTopLevelCountsNotSubtotals() {
        val parsed = AiUsagePolicy.reported("""{"model":"deepseek-flash","usage":{"prompt_tokens":100,"completion_tokens":20,"total_tokens":120,"prompt_cache_hit_tokens":80,"completion_tokens_details":{"reasoning_tokens":10,"safety_tokens":2}}}""")!!
        assertEquals(100L, parsed.input); assertEquals(20L, parsed.output)
        assertNull(AiUsagePolicy.reported("""{"usage":{"prompt_tokens":-1,"completion_tokens":20}}"""))
        assertNull(AiUsagePolicy.reported("""{"usage":{"prompt_tokens":"10","completion_tokens":20}}"""))
        assertNull(AiUsagePolicy.reported("""{"usage":{"prompt_tokens":10,"completion_tokens":20,"total_tokens":40}}"""))
        assertNull(AiUsagePolicy.reported("{}"))
    }
    @Test fun eachRequestCountsAndCallbacksAreIdempotent() {
        val ledger = AiUsageLedger()
        ledger.begin("one", AiUsageKind.CHAT); ledger.begin("one", AiUsageKind.CHAT)
        ledger.finish("one", AiReportedUsage("deepseek-flash", 100, 10))
        ledger.finish("one", AiReportedUsage("deepseek-flash", 100, 10)); ledger.begin("one", AiUsageKind.CHAT)
        ledger.begin("retry", AiUsageKind.CHAT); ledger.finish("retry")
        assertEquals(AiModelUsage("deepseek-flash", 2, 1, 100, 10), ledger.snapshot().models.single())
        assertEquals(1L, ledger.snapshot().unknownChat)
    }
    @Test fun searchCountsRawNonemptyEvenIfDisplayWouldRejectLinks() {
        assertEquals(AiSearchOutcome.NONEMPTY, AiUsagePolicy.searchOutcome("""{"results":[{"url":"file:///bad"}]}"""))
        val ledger = AiUsageLedger()
        listOf(AiSearchOutcome.NONEMPTY, AiSearchOutcome.EMPTY, AiSearchOutcome.FAILED, AiSearchOutcome.UNKNOWN).forEachIndexed { index, result ->
            ledger.begin(index.toString(), AiUsageKind.SEARCH); ledger.finish(index.toString(), search = result)
        }
        val s = ledger.snapshot(); assertEquals(4L, s.searches); assertEquals(1L, s.searchNonempty)
        assertEquals(1L, s.searchEmpty); assertEquals(1L, s.searchFailed); assertEquals(1L, s.unknownSearch)
        assertTrue(s.models.isEmpty())
    }
    @Test fun restartRetainsUnfinishedAsUnknownAndStorageFailureDoesNotThrow() {
        val old = AiUsageLedger(); old.begin("pending", AiUsageKind.CHAT)
        val restored = AiUsageLedger(old.snapshot())
        restored.finish("pending", AiReportedUsage("deepseek-flash", 10, 2))
        assertEquals(1L, restored.snapshot().unknownChat)
        val failed = AiUsageLedger(save = { throw IllegalStateException("disk") })
        failed.begin("one", AiUsageKind.CHAT); failed.finish("one", AiReportedUsage("deepseek-flash", 10, 2))
        assertTrue(failed.snapshot().incomplete); assertEquals(10L, failed.snapshot().models.single().input)
    }
    @Test fun actualModelMovesOnlyThatRequestAndModelNameIsBounded() {
        val ledger = AiUsageLedger(); ledger.begin("1", AiUsageKind.CHAT); ledger.begin("2", AiUsageKind.CHAT)
        ledger.finish("1", AiReportedUsage("deepseek-v4-pro", 10, 2)); ledger.finish("2")
        assertEquals(2L, ledger.snapshot().models.sumOf { it.requests })
        assertEquals(1L, ledger.snapshot().unknownChat)
        assertNull(AiUsagePolicy.model("secret\nhello")); assertNull(AiUsagePolicy.model("x".repeat(81)))
    }
    @Test fun codecRoundTripsOnlyCountersAndRejectsFutureOrCorruptState() {
        val value = AiUsageSnapshot(models = listOf(AiModelUsage("deepseek-flash", 2, 1, 100, 10)), searches = 1)
        assertEquals(value, AiUsageCodec.decode(AiUsageCodec.encode(value)))
        assertTrue(runCatching { AiUsageCodec.decode(AiUsageCodec.encode(value).replace("\"schema\":1", "\"schema\":2")) }.isFailure)
        assertTrue(runCatching { AiUsageCodec.decode("{}") }.isFailure)
        assertFalse(AiUsageCodec.encode(value).contains("query"))
    }
    @Test fun refusedAnswerStillHasUsageButPreflightFailureDoesNotCount() = runBlocking {
        val ledger = AiUsageLedger(); val key = "fake-sentinel-key-never-real"
        val refused = AiSafeTransport({ key }, { true }, AiRequestBudget(), {
            FakeAiConnection(answer = aiAnswer("ok").replace("\"stop\"", "\"content_filter\""))
        }, usage = AiUsageRecorder(listOf(ledger)))
        assertTrue(runCatching { refused.post("chat/completions", "{}", String::class, String::class) }.isFailure)
        assertEquals(1L, ledger.snapshot().models.single().reported)
        val unavailable = AiSafeTransport({ key }, { false }, AiRequestBudget(), usage = AiUsageRecorder(listOf(ledger)))
        assertTrue(runCatching { unavailable.post("chat/completions", "{}", String::class, String::class) }.isFailure)
        assertEquals(1L, ledger.snapshot().models.single().requests)
    }
    @Test fun missingUsageIsNotDisplayedAsMeasuredZero() {
        assertFalse(AiModelUsage("deepseek-flash", requests = 1).hasReportedUsage)
        assertTrue(AiModelUsage("deepseek-flash", requests = 2, reported = 1).hasReportedUsage)
    }
    @Test fun oldSessionCompletionDoesNotPolluteNewSession() {
        val device = AiUsageLedger(); val oldSession = AiUsageLedger(); val newSession = AiUsageLedger()
        val oldRecorder = AiUsageRecorder(listOf(device, oldSession))
        val newRecorder = AiUsageRecorder(listOf(device, newSession))
        val old = oldRecorder.begin(AiUsageKind.CHAT)
        val newer = newRecorder.begin(AiUsageKind.CHAT)
        newRecorder.finish(newer, AiReportedUsage("deepseek-flash", 20, 2))
        oldRecorder.finish(old, AiReportedUsage("deepseek-flash", 10, 1))
        assertEquals(30L, device.snapshot().models.single().input)
        assertEquals(20L, newSession.snapshot().models.single().input)
        assertEquals(10L, oldSession.snapshot().models.single().input)
    }
    @Test fun transportCancellationAndTimeoutRemainUnknown() = runBlocking {
        val key = "fake-sentinel-key-never-real"
        val ledger = AiUsageLedger()
        val began = CountDownLatch(1); val released = CountDownLatch(1)
        val connection = FakeAiConnection().apply { responseStream = object : InputStream() {
            override fun read(): Int { began.countDown(); released.await(2, TimeUnit.SECONDS); return -1 }
            override fun close() { released.countDown() }
        } }
        val cancelled = AiSafeTransport({ key }, { true }, AiRequestBudget(), { connection }, usage = AiUsageRecorder(listOf(ledger)))
        val job = async(Dispatchers.Default) { cancelled.post("chat/completions", "{}", String::class, String::class) }
        withTimeout(1000) { while (began.count > 0) delay(1) }
        withTimeout(1000) { job.cancelAndJoin() }
        cancelled.close()
        assertEquals(1L, ledger.snapshot().unknownChat)
        val timeout = AiSafeTransport({ key }, { true }, AiRequestBudget(), {
            FakeAiConnection().apply { responseStream = object : InputStream() {
                override fun read(): Int = throw SocketTimeoutException()
            } }
        }, usage = AiUsageRecorder(listOf(ledger)))
        assertTrue(runCatching { timeout.post("chat/completions", "{}", String::class, String::class) }.isFailure)
        timeout.close()
        assertEquals(2L, ledger.snapshot().unknownChat)
        assertEquals(2L, ledger.snapshot().models.single().requests)
        assertEquals(0L, ledger.snapshot().models.single().reported)
    }
    @Test fun savingCompletionFailureDoesNotFailChat() = runBlocking {
        var writes = 0
        val ledger = AiUsageLedger(save = { ++writes == 1 })
        val key = "fake-sentinel-key-never-real"
        val transport = AiSafeTransport({ key }, { true }, AiRequestBudget(), { FakeAiConnection() }, usage = AiUsageRecorder(listOf(ledger)))
        assertEquals(aiAnswer("ok"), transport.post("chat/completions", "{\"model\":\"deepseek-flash\"}", String::class, String::class))
        assertEquals(10L, ledger.snapshot().models.single().input)
        assertEquals(1L, ledger.snapshot().models.single().reported)
        assertTrue(ledger.snapshot().incomplete)
        assertEquals(2, writes)
    }
    @Test fun cacheRateUsesWeightedReportedTokensAndTracksCoverage() {
        val ledger = AiUsageLedger()
        ledger.begin("one", AiUsageKind.CHAT); ledger.finish("one", AiReportedUsage("deepseek-flash", 100, 5, 100, 0))
        ledger.begin("two", AiUsageKind.CHAT); ledger.finish("two", AiReportedUsage("deepseek-v4-pro", 900, 10, 0, 900))
        ledger.begin("missing", AiUsageKind.CHAT); ledger.finish("missing", AiReportedUsage("deepseek-flash", 200, 5))
        val state = ledger.snapshot()
        assertEquals(0.1, state.cacheHitRate!!, 0.00001)
        assertEquals(2L, state.cacheReported); assertEquals(3L, state.requests)
        assertEquals(1200L, state.models.sumOf { it.input }) // Cache is a subset, not extra input.
        assertEquals(state, AiUsageCodec.decode(AiUsageCodec.encode(state)))
    }
    @Test fun missingInvalidAndZeroCacheHaveNoInventedRate() {
        fun response(cache: String) = AiUsagePolicy.reported("""{"usage":{"prompt_tokens":10,"completion_tokens":2,$cache}}""")!!
        assertNull(response("\"prompt_cache_hit_tokens\":5").cacheHit)
        assertNull(response("\"prompt_cache_hit_tokens\":5,\"prompt_cache_miss_tokens\":6").cacheHit)
        assertNull(response("\"prompt_cache_hit_tokens\":-1,\"prompt_cache_miss_tokens\":11").cacheHit)
        val valid = response("\"prompt_cache_hit_tokens\":4,\"prompt_cache_miss_tokens\":6")
        assertEquals(4L, valid.cacheHit); assertEquals(6L, valid.cacheMiss)
        assertNull(AiUsageSnapshot().cacheHitRate)
        assertNull(AiUsageSnapshot(models = listOf(AiModelUsage("deepseek-flash", 1, 1, cacheReported = 1))).cacheHitRate)
        val old = """{"schema":1,"since":1,"incomplete":false,"searches":0,"searchNonempty":0,"searchEmpty":0,"searchFailed":0,"models":[{"model":"deepseek-flash","requests":2,"reported":2,"input":100,"output":5}]}"""
        val migrated = AiUsageCodec.decode(old)
        assertEquals(100L, migrated.models.single().input); assertEquals(0L, migrated.cacheReported); assertNull(migrated.cacheHitRate)
    }
    @Test fun transportRecordsBeforeSdkValidationAndFailureKeepsEarlierUsage() = runBlocking {
        val ledger = AiUsageLedger(); val recorder = AiUsageRecorder(listOf(ledger))
        val key = "fake-sentinel-key-never-real"
        var call = 0
        val transport = AiSafeTransport({ key }, { true }, AiRequestBudget(), {
            if (call++ == 0) FakeAiConnection(answer = aiAnswer("ok")) else FakeAiConnection(400, "{}")
        }, usage = recorder)
        transport.post("chat/completions", "{\"model\":\"deepseek-flash\"}", String::class, String::class)
        runCatching { transport.post("chat/completions", "{\"model\":\"deepseek-flash\"}", String::class, String::class) }
        assertEquals(2L, ledger.snapshot().models.single().requests)
        assertEquals(10L, ledger.snapshot().models.single().input); assertEquals(1L, ledger.snapshot().unknownChat)
    }
    @Test fun directSearchAttemptsClassifyResponsesWithoutInferringBillingOrRetrying() = runBlocking {
        val ledger = AiUsageLedger(); var opens = 0
        val scenarios = listOf(
            200 to "{\"results\":[{\"url\":\"file:///filtered\"}]}",
            200 to "{\"results\":[]}",
            500 to "private error body",
            200 to "{\"error\":{\"message\":\"private business error\"},\"results\":[]}",
            -1 to "client read timeout")
        for ((code, raw) in scenarios) {
            val safe = AiSafeTransport({ "fake-search-usage-key" }, { true }, AiRequestBudget(), { url ->
                assertEquals(AiSafeTransport.SEARCH_ENDPOINT, url.toString()); opens++
                object : FakeAiConnection(if (code == -1) 200 else code, raw, url) {
                    override fun getResponseCode(): Int = if (code == -1) throw SocketTimeoutException("synthetic client timeout") else super.getResponseCode()
                }
            }, usage = AiUsageRecorder(listOf(ledger)))
            val displayed = AiWebSearch.execute("synthetic coffee", safe::search)
            if (code == 200 && raw.contains("filtered")) assertEquals(JsonPrimitive("unavailable"), Json.parseToJsonElement(displayed).jsonObject["status"])
            safe.close()
        }
        val state = ledger.snapshot()
        assertEquals(5, opens); assertEquals(5L, state.searches)
        assertEquals(1L, state.searchNonempty); assertEquals(1L, state.searchEmpty)
        assertEquals(1L, state.searchFailed); assertEquals(2L, state.unknownSearch)
        assertTrue(state.models.isEmpty())
        val noRequest = AiSafeTransport({ error("Invalid input must not read a key") }, { true }, AiRequestBudget(),
            { error("Invalid input must not connect") }, usage = AiUsageRecorder(listOf(ledger)))
        assertEquals(AiWebSearch.status("invalid_arguments"), AiWebSearch.execute(" ", noRequest::search))
        assertEquals(state, ledger.snapshot())
        assertEquals(state, AiUsageCodec.decode(AiUsageCodec.encode(state)))
    }

}
