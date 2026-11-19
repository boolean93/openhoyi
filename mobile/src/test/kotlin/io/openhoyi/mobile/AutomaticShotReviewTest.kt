package io.openhoyi.mobile

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

class AutomaticShotReviewTest {
    private val config = ShotReviewConfig(4, 2, AppLanguage.CHINESE)
    private fun point(at: Long, brewing: Boolean = true) =
        ShotPoint(at, 87, 22, 180, 9210, null, brewing = brewing)
    private val points = listOf(point(500), point(1250))

    @Test fun `payload contains selected actual data with units and explicit unknowns only`() {
        val brewing = (1..200).map { point(it * 100L) }.toMutableList().also {
            it[12] = it[12].copy(pressureTenthsBar = 93)
        }
        val series = listOf(point(0L, false)) + brewing + point(30_000L, false)
        val root = Json.parseToJsonElement(ShotReviewWire.request(series, null, AppLanguage.CHINESE)!!).jsonObject
        val body = Json.parseToJsonElement(root.getValue("messages").jsonArray[1].jsonObject
            .getValue("content").jsonPrimitive.content).jsonObject
        assertFalse("tools" in root)
        assertEquals(AiModelPolicy.FLASH, root.getValue("model").jsonPrimitive.content)
        assertEquals(JsonNull, body["machineDurationSeconds"])
        assertEquals(JsonPrimitive(9.3), body["peakObservedPressureBar"])
        assertEquals(JsonPrimitive(19900L), body["observedBrewSampleSpanMs"])
        assertFalse("shotId" in body)
        val samples = body.getValue("observations").jsonArray
        assertEquals(96, samples.size)
        assertEquals(JsonPrimitive(100L), samples.first().jsonObject["elapsedMs"])
        assertEquals(JsonPrimitive(20_000L), samples.last().jsonObject["elapsedMs"])
        assertEquals(JsonPrimitive(2.2), samples.first().jsonObject["machineFlowMlPerSecond"])
        assertEquals(JsonNull, samples.first().jsonObject["estimatedScaleFlowGramsPerSecond"])
        assertNull(ShotReviewWire.request(series.filter { it.brewing == false }, null, AppLanguage.ENGLISH))
    }

    @Test fun `staged missing scale cup requires grounded three line rubric without false target or yield`() {
        // A measured stable 3–5s interval and rapid pressure drop in the next
        // interval, not named recipe stages or proof of grind/distribution.
        val staged = listOf(3000L to 88, 4000L to 88, 5000L to 87, 6000L to 68, 7000L to 56)
            .map { (at, pressure) -> point(at).copy(pressureTenthsBar = pressure) }
        val messages = Json.parseToJsonElement(ShotReviewWire.request(staged, 8, AppLanguage.CHINESE)!!)
            .jsonObject.getValue("messages").jsonArray
        val prompt = messages[0].jsonObject.getValue("content").jsonPrimitive.content
        listOf("exactly three string keys: good, deviation, nextCup", "what went well", "observed time interval",
            "what deviated", "no clear anomaly", "never label a stage as",
            "next cup", "exactly one measurement", "exactly one change to one variable",
            "unverified hypotheses, not diagnoses", "never prescribe changing both",
            "mL/s", "scale mass flow in g/s", "never state beverage grams", "never state a brew ratio",
            "never state scale-derived g/s").forEach { assertTrue(it, prompt.contains(it)) }
        val data = Json.parseToJsonElement(messages[1].jsonObject.getValue("content").jsonPrimitive.content).jsonObject
        val evidence = data.getValue("evidence").jsonObject
        for (key in listOf("immutableStageTargetsAvailable", "doseGramsAvailable",
            "beverageWeightAvailable", "estimatedScaleFlowAvailable")) assertFalse(evidence.getValue(key).jsonPrimitive.boolean)
        val rows = data.getValue("observations").jsonArray.map { it.jsonObject }
        assertEquals(listOf(8.8, 8.8, 8.7, 6.8, 5.6), rows.map { it.getValue("pressureBar").jsonPrimitive.double })
        assertTrue(rows.all { it["beverageGrams"] == JsonNull && it["estimatedScaleFlowGramsPerSecond"] == JsonNull })
        assertEquals("表现：3–5 秒，压力较稳。\n变化：5–7 秒，压力明显下降。\n下杯：只补测杯中出液重量。",
            ShotReviewWire.answer(response(reviewContent("表现：3–5 秒，压力较稳。",
                "变化：5–7 秒，压力明显下降。", "下杯：只补测杯中出液重量。"))))
    }

    @Test fun `scale flow and beverage evidence are independent and never invent a shot target or dose`() {
        val massFlowOnly = points.first().copy(scaleFlowHundredths = 130)
        val payload = ShotReviewWire.request(listOf(massFlowOnly, points.last()), 8, AppLanguage.ENGLISH)!!
        val content = Json.parseToJsonElement(payload).jsonObject.getValue("messages").jsonArray[1]
            .jsonObject.getValue("content").jsonPrimitive.content
        val data = Json.parseToJsonElement(content).jsonObject
        val evidence = data.getValue("evidence").jsonObject
        assertFalse(evidence.getValue("beverageWeightAvailable").jsonPrimitive.boolean)
        assertTrue(evidence.getValue("estimatedScaleFlowAvailable").jsonPrimitive.boolean)
        assertFalse(evidence.getValue("immutableStageTargetsAvailable").jsonPrimitive.boolean)
        assertFalse(evidence.getValue("doseGramsAvailable").jsonPrimitive.boolean)
        val measured = data.getValue("observations").jsonArray.first().jsonObject
        assertEquals(JsonPrimitive(1.3), measured["estimatedScaleFlowGramsPerSecond"])
        assertEquals(JsonPrimitive(2.2), measured["machineFlowMlPerSecond"])
    }

    @Test fun `wire adapter sends one protected request with no tools to a fake provider`() = runBlocking {
        val fake = FakeAiConnection(answer = aiAnswer(reviewContent("pressure stable")))
        var count = 0
        val transport = AiSafeTransport({ "fake-shot-token-never-real" }, { true }, AiRequestBudget(), {
            assertEquals(AiSafeTransport.ENDPOINT, it.toString())
            count++
            fake
        })
        try {
            val request = requireNotNull(ShotReviewWire.request(points, 18, AppLanguage.ENGLISH))
            assertEquals("pressure stable", ShotReviewWire.answer(ShotReviewWire.send(transport, request))?.lineSequence()?.first())
            assertEquals(1, count)
            val wire = Json.parseToJsonElement(fake.sent.toString("UTF-8")).jsonObject
            assertTrue(wire.getValue("stream").jsonPrimitive.boolean)
            assertFalse("tools" in wire)
            assertFalse("tool_choice" in wire)
            assertFalse(fake.instanceFollowRedirects)
        } finally { transport.close() }
    }

    @Test fun `send once after persistence and isolate two cups from late responses`() = runBlocking {
        val owner = CoroutineScope(coroutineContext + Job())
        try {
            val rows = Ledger()
            var calls = 0
            val last = CompletableDeferred<String>()
            val review = AutomaticShotReview(owner, { it == config }, rows, { _, body ->
                calls++
                if (body.contains("12345")) last.await() else response(reviewContent("second cup"))
            })
            val firstPoints = listOf(point(500), point(12345))
            review.afterPersisted("cup-one", config, firstPoints, null)
            review.afterPersisted("cup-one", config, firstPoints, null)
            review.afterPersisted("cup-two", config, points, 30)
            yield()
            assertEquals(2, calls)
            assertEquals(ShotReviewStatus.PENDING, review.result("cup-one")?.status)
            assertEquals("second cup", review.result("cup-two")?.text?.lineSequence()?.first())
            last.complete(response(reviewContent("first cup")))
            yield()
            assertEquals("first cup", review.result("cup-one")?.text?.lineSequence()?.first())
            review.afterPersisted("cup-one", config, points, 30)
            yield()
            assertEquals(2, calls)
        } finally { owner.coroutineContext[Job]?.cancel() }
    }

    @Test fun `obsolete credentials discard late data and lost ack never replays`() = runBlocking {
        val owner = CoroutineScope(coroutineContext + Job())
        try {
            val ledger = Ledger()
            var valid = false
            var calls = 0
            val release = CompletableDeferred<String>()
            val review = AutomaticShotReview(owner, { valid }, ledger, { _, _ -> calls++; release.await() })
            review.afterPersisted("cup", config, points, null)
            yield()
            assertNull(review.result("cup"))
            valid = true
            review.afterPersisted("cup", config, points, null)
            yield()
            valid = false
            release.complete(response("must not publish"))
            yield()
            assertEquals(ShotReviewStatus.UNKNOWN, review.result("cup")?.status)
            valid = true
            review.afterPersisted("cup", config.copy(keyRevision = 3), points, null)
            yield()
            assertEquals(1, calls)
        } finally { owner.coroutineContext[Job]?.cancel() }
    }

    @Test fun `timeout and stale pending preserve fence without retry`() = runBlocking {
        val ledger = Ledger()
        assertTrue(ledger.reserve("stale"))
        var calls = 0
        val review = AutomaticShotReview(this, { true }, ledger, { _, _ -> calls++; awaitCancellation() }, timeoutMs = 5)
        assertEquals(ShotReviewStatus.UNKNOWN, review.result("stale")?.status)
        review.afterPersisted("stale", config, points, null)
        review.afterPersisted("fresh", config, points, null)
        kotlinx.coroutines.withTimeout(5000) {
            while (review.result("fresh")?.status != ShotReviewStatus.UNKNOWN) kotlinx.coroutines.delay(2)
        }
        review.afterPersisted("fresh", config, points, null)
        yield()
        assertEquals(1, calls)
    }

    @Test fun `no request on an unwritable attempt fence or insufficient samples`() = runBlocking {
        var calls = 0
        val broken = object : ShotReviewLedger {
            override fun reserve(id: String) = false
            override fun read(id: String): ShotReviewResult? = null
            override fun finish(id: String, result: ShotReviewResult) = error("not reserved")
        }
        val blocked = AutomaticShotReview(this, { true }, broken, { _, _ -> calls++; response("never") })
        blocked.afterPersisted("blocked", config, points, null)
        val ledger = Ledger()
        val thin = AutomaticShotReview(this, { true }, ledger, { _, _ -> calls++; response("never") })
        thin.afterPersisted("thin", config, points.take(1), null)
        yield()
        assertEquals(0, calls)
        assertNull(ledger.read("thin"))
    }

    @Test fun `ready output requires three bounded slots without truncating the advice`() {
        assertNull(ShotReviewWire.answer("""{"choices":[{"finish_reason":"tool_calls","message":{"content":"hi"}}]}"""))
        assertNull(ShotReviewWire.answer("""{"choices":[{"finish_reason":"length","message":{"content":"hi"}}]}"""))
        assertNull(ShotReviewWire.answer(response("one arbitrary line")))
        assertNull(ShotReviewWire.answer(response("""{"good":"well","deviation":"slowed"}""")))
        assertNull(ShotReviewWire.answer(response("""{"good":"well","deviation":"slowed","nextCup":"measure","extra":"ignore"}""")))
        assertNull(ShotReviewWire.answer(response(reviewContent("well", "slowed", " "))))
        assertNull(ShotReviewWire.answer(response(reviewContent("well", "slowed", "before\nafter"))))
        assertNull(ShotReviewWire.answer(response(reviewContent("well", "slowed", "x".repeat(116)))))
        assertEquals("well\nslowed\nmeasure", ShotReviewWire.answer(response(reviewContent("well", "slowed", "measure"))))
    }

    @Test fun `default denial and changing consent or credential changes the revision fence`() {
        var saved: String? = null
        var grant = false
        val consent = ShotReviewPreferences({ saved }, { saved = it; true }, { grant },
            { grant = false; true }, { grant = true; true })
        assertNull(consent.revision("key1"))
        assertTrue(consent.set("key1"))
        val first = requireNotNull(consent.revision("key1"))
        assertNull(consent.revision("key2"))
        assertTrue(consent.set(null))
        assertFalse(consent.publishForRevision("key1", first) { error("must not publish") })
        assertNull(consent.revision("key1"))
        assertTrue(consent.set("key1"))
        assertNotEquals(first, consent.revision("key1"))
    }

    @Test fun `failed preference revocation still denies a fresh process after keystore withdrawal`() {
        var saved: String? = null
        var grant = false
        var prefsWritable = true
        var grantWritable = true
        fun fresh() = ShotReviewPreferences({ saved }, { next -> if (prefsWritable) { saved = next; true } else false },
            { grant }, { if (grantWritable) { grant = false; true } else false }, { grant = true; true })
        val consent = fresh()
        assertTrue(consent.set("key1"))
        prefsWritable = false
        assertTrue(consent.set(null))
        assertEquals("key1", saved)
        assertNull(fresh().revision("key1"))
        prefsWritable = true
        assertTrue(consent.set("key1"))
        grantWritable = false
        assertTrue(consent.set(null))
        assertTrue(grant)
        assertNull(fresh().revision("key1"))
        assertTrue(consent.set("key1"))
        prefsWritable = false
        assertFalse(consent.set(null))
        assertTrue(consent.revocationUnconfirmed)
        assertNull(consent.revision("key1")) // Fail closed now; expose unresolved durability.
        assertNotNull(fresh().revision("key1"))
        assertNull(fresh().revision("key2")) // Replacing/deleting the old key invalidates its saved grant.
    }

    @Test fun `revocation at the send permit blocks credentials and provider connection`() = runBlocking {
        var connects = 0
        val fake = object : FakeAiConnection() {
            override fun connect() { connects++ }
        }
        val transport = AiSafeTransport({ "fake-shot-token-never-real" }, { true }, AiRequestBudget(), { fake },
            sendPermit = { false })
        val error = runCatching { ShotReviewWire.send(transport, requireNotNull(
            ShotReviewWire.request(points, 4, AppLanguage.CHINESE))) }.exceptionOrNull()
        assertEquals(AiProblem.KEY, (error as AiFailure).problem)
        assertEquals(0, connects)
        assertFalse("Authorization" in fake.requestHeaders)
        assertEquals(0, fake.sent.size())
        assertTrue(fake.disconnected.get())
    }

    @Test fun `only confirmed completed app or passive cups are eligible`() {
        val app = ShotHistory.Entry("shot-a", "factory-r7", 1, 9, null,
            ShotHistory.Status.ENDED, null, null, observedRunning = true)
        assertTrue(ShotReviewWire.eligible(app, false, false))
        assertFalse(ShotReviewWire.eligible(app, false, true))
        assertFalse(ShotReviewWire.eligible(app.copy(observedRunning = false), false, false))
        assertFalse(ShotReviewWire.eligible(app.copy(machineTimingInterrupted = true), false, false))
        assertFalse(ShotReviewWire.eligible(app.copy(status = ShotHistory.Status.UNKNOWN), false, false))
        assertFalse(ShotReviewWire.eligible(app.copy(endedAtMs = null), false, false))
        val manual = app.copy(curveId = "manual", observedRunning = false)
        assertTrue(ShotReviewWire.eligible(manual, true, false))
        assertFalse(ShotReviewWire.eligible(manual, false, false))
        assertFalse(ShotReviewWire.eligible(manual.copy(machineTimingInterrupted = true), true, false))
    }

    @Test fun `bounded durable ledger prunes only evicted local cup and rejects corrupt state`() {
        var disk: ByteArray? = null
        var writable = true
        fun fresh() = BoundedShotReviewLedger({ disk }, { data ->
            check(writable)
            disk = data
        })
        val ledger = fresh()
        ledger.publishRetained(setOf("cup1", "cup2"))
        assertTrue(ledger.reserve("cup1"))
        ledger.finish("cup1", ShotReviewResult(ShotReviewStatus.READY, "reviewed"))
        assertTrue(ledger.reserve("cup2"))
        val restarted = fresh()
        restarted.publishRetained(setOf("cup2", "cup3"))
        assertTrue(restarted.prune())
        assertNull(restarted.read("cup1"))
        assertEquals(ShotReviewStatus.PENDING, restarted.read("cup2")?.status)
        assertFalse(restarted.reserve("cup2"))
        assertFalse(restarted.reserve("cup1"))
        writable = false
        assertFalse(restarted.reserve("cup3"))
        assertNull(restarted.read("cup3"))
        writable = true
        disk = "not a ledger".toByteArray()
        val corrupt = fresh()
        corrupt.publishRetained(setOf("cup3"))
        assertFalse(corrupt.reserve("cup3"))
        assertFalse(corrupt.prune())
        assertEquals("not a ledger", disk!!.decodeToString())
    }

    private fun reviewContent(good: String, deviation: String = "no clear anomaly", next: String = "measure output") =
        buildJsonObject { put("good", good); put("deviation", deviation); put("nextCup", next) }.toString()

    private fun response(text: String) = """{"choices":[{"finish_reason":"stop","message":{"content":${JsonPrimitive(text)}}}]}"""

    private class Ledger : ShotReviewLedger {
        private val entries = ConcurrentHashMap<String, ShotReviewResult>()
        override fun reserve(id: String) = entries.putIfAbsent(id, ShotReviewResult(ShotReviewStatus.PENDING)) == null
        override fun finish(id: String, result: ShotReviewResult) { entries[id] = result }
        override fun read(id: String) = entries[id]
    }
}
