package io.openhoyi.mobile

import kotlinx.serialization.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class AiSessionStoreTest {
    private val epoch = "00000000-0000-0000-0000-000000000001"
    private class Storage : AiSessionStorage {
        var bytes: ByteArray? = null
        var fail = false
        var readFailure = false
        var preserveSucceeds = true
        val archives = mutableListOf<ByteArray>()
        override fun preserveUnreadable(): Boolean {
            if (!preserveSucceeds || readFailure) return false
            archives.add(bytes?.clone() ?: return false)
            return archives.last().contentEquals(bytes)
        }
        override fun read(): ByteArray? { check(!readFailure); return bytes?.clone() }
        override fun write(bytes: ByteArray) { check(!fail); this.bytes = bytes.clone() }
    }
    private class Keys : AiKeySource {
        var key: SecretKey? = null
        var creates = 0
        override fun existing() = key
        override fun create(): SecretKey { creates++; return KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().also { key = it } }
    }
    private fun state(draft: String = "待发问题", usage: AiUsageSnapshot = AiUsageSnapshot()): AiSession {
        val messages = listOf(
            buildJsonObject { put("role", "user"); put("content", "问题") },
            buildJsonObject { put("role", "assistant"); put("content", "回答"); put("reasoning_content", "private reasoning marker") })
        return AiSession(epoch, listOf(AiChatLine(true, "问题"), AiChatLine(false, "回答")), AiProtocolHistory.checked(messages), draft, usage)
    }
    @Test fun restoresCompleteProtocolDraftAndCountersWithoutReplay() {
        val storage = Storage(); val keys = Keys(); val store = AiSessionStore(storage, keys)
        assertSame(AiSessionLoad.Empty, store.load(epoch)); assertEquals(0, keys.creates)
        val ledger = AiUsageLedger(); ledger.begin("unfinished", AiUsageKind.CHAT)
        val session = state(usage = ledger.snapshot())
        assertTrue(store.save(session))
        assertFalse(storage.bytes!!.toString(Charsets.UTF_8).contains("private reasoning marker"))
        val restored = (AiSessionStore(storage, keys).load(epoch) as AiSessionLoad.Ready).session
        assertEquals(session.protocol.messages, restored.protocol.messages)
        assertEquals("待发问题", restored.draft)
        val recovered = AiUsageLedger(restored.usage)
        recovered.finish("unfinished", AiReportedUsage("deepseek-flash", 10, 10))
        assertEquals(1L, recovered.snapshot().unknownChat)
        assertEquals(1L, recovered.snapshot().requests)
    }
    @Test fun credentialChangeIsolatesSession() {
        val storage = Storage(); val keys = Keys(); val store = AiSessionStore(storage, keys)
        store.load(epoch); assertTrue(store.save(state()))
        assertSame(AiSessionLoad.Empty, AiSessionStore(storage, keys).load("00000000-0000-0000-0000-000000000002"))
    }
    @Test fun corruptUnknownOrMissingKeyNeverOverwritesOriginal() {
        val storage = Storage(); val keys = Keys(); val store = AiSessionStore(storage, keys)
        store.load(epoch); assertTrue(store.save(state()))
        for (bad in listOf(byteArrayOf(9) + storage.bytes!!.drop(1), byteArrayOf(1, 2))) {
            storage.bytes = bad
            val reader = AiSessionStore(storage, keys)
            assertSame(AiSessionLoad.Unavailable, reader.load(epoch))
            assertFalse(reader.save(state())); assertArrayEquals(bad, storage.bytes)
        }
        keys.key = null
        val reader = AiSessionStore(storage, keys)
        assertSame(AiSessionLoad.Unavailable, reader.load(epoch))
        assertFalse(reader.save(state())); assertEquals(1, keys.creates)
    }
    @Test fun failedAtomicWriteLeavesPreviousRecoverable() {
        val storage = Storage(); val keys = Keys(); val store = AiSessionStore(storage, keys)
        store.load(epoch); assertTrue(store.save(state("original")))
        val previous = storage.bytes!!.clone(); storage.fail = true
        assertFalse(store.save(state("replacement"))); assertArrayEquals(previous, storage.bytes)
        assertEquals("original", (AiSessionStore(storage, keys).load(epoch) as AiSessionLoad.Ready).session.draft)
    }
    @Test fun strictSchemaAndVisibleProtocolConsistency() {
        val good = state()
        val mismatch = AiSession(epoch, emptyList(), good.protocol, "", good.usage)
        assertTrue(runCatching { AiSessionCodec.encode(mismatch) }.isFailure)
        val encoded = AiSessionCodec.encode(good).toString(Charsets.UTF_8)
        assertTrue(runCatching { AiSessionCodec.decode(encoded.replace("\"schema\":5", "\"schema\":6").toByteArray()) }.isFailure)
        assertTrue(runCatching { AiSessionCodec.decode(ByteArray(AiSessionCodec.MAX_BYTES + 1)) }.isFailure)
    }
    @Test fun unreadableStorageNeverCreatesReplacementKey() {
        val storage = Storage().apply { readFailure = true }; val keys = Keys()
        val store = AiSessionStore(storage, keys)
        assertSame(AiSessionLoad.Unavailable, store.load(epoch))
        assertFalse(store.recover(state())); assertEquals(0, keys.creates)
    }
    @Test fun nullableReasoningAndToolProtocolArePreservedOnlyInCiphertext() {
        val raw = """[{"role":"user","content":"问题"},{"role":"assistant","content":null,"reasoning_content":null,"tool_calls":[{"id":"c1","type":"function","function":{"name":"web_search","arguments":"private tool arguments"}}]},{"role":"tool","tool_call_id":"c1","content":"private tool result"},{"role":"assistant","content":"回答"}]"""
        val base = state()
        val protocol = AiProtocolHistory.checked(Json.parseToJsonElement(raw).jsonArray.map { it.jsonObject })
        val storage = Storage(); val keys = Keys(); val store = AiSessionStore(storage, keys)
        store.load(epoch)
        assertTrue(store.save(AiSession(epoch, base.lines, protocol, base.draft, base.usage)))
        val ciphertext = storage.bytes!!.toString(Charsets.UTF_8)
        assertFalse(ciphertext.contains("private tool"))
        val restored = (AiSessionStore(storage, keys).load(epoch) as AiSessionLoad.Ready).session
        assertEquals(protocol.messages, restored.protocol.messages)
        assertEquals(JsonNull, restored.protocol.messages[1]["reasoning_content"])
        assertFalse(restored.protocol.messages.last().containsKey("reasoning_content"))
    }
    @Test fun restoredProtocolIsSentExactlyOnNextTurnWithoutReplayingTools() = runBlocking {
        for (finalReason in listOf<JsonElement?>(null, JsonNull)) {
            val messages = Json.parseToJsonElement("""[{"role":"user","content":"问题"},{"role":"assistant","content":null,"reasoning_content":"private tool reasoning","tool_calls":[{"id":"saved1","type":"function","function":{"name":"current_status","arguments":" { } "}}]},{"role":"tool","tool_call_id":"saved1","content":"private tool result"},{"role":"assistant","content":"回答"}]""").jsonArray.map { it.jsonObject }.toMutableList()
            if (finalReason != null) messages[messages.lastIndex] = JsonObject(messages.last() + ("reasoning_content" to finalReason))
            val original = AiProtocolHistory.checked(messages)
            val base = state()
            val storage = Storage(); val keys = Keys(); val store = AiSessionStore(storage, keys)
            store.load(epoch)
            assertTrue(store.save(AiSession(epoch, base.lines, original, "", base.usage)))
            val restored = (AiSessionStore(storage, keys).load(epoch) as AiSessionLoad.Ready).session
            val calls = mutableListOf<FakeAiConnection>()
            val budget = AiRequestBudget()
            val safe = AiSafeTransport({ "fake-session-bridge-key" }, { true }, budget, {
                FakeAiConnection(answer = aiAnswer("next answer")).also(calls::add)
            })
            val noReplay = object : AiReadOnlyData {
                override fun currentStatus(): String = error("Saved tool must not be replayed")
                override fun curves(query: String, offset: Int, limit: Int): String = error("Saved tool must not be replayed")
                override fun history(curveId: String?, offset: Int, limit: Int): String = error("Saved tool must not be replayed")
            }
            val turn = AiProtocolTurn(restored.protocol, restored.lines, "next question", safe)
            val reply = AiChatSession(noReplay, turn, budget, Dispatchers.Unconfined).reply(restored.lines, "next question")
            assertEquals("next answer", turn.complete(reply).text)
            assertEquals(1, calls.size)
            val wire = Json.parseToJsonElement(calls.single().sent.toString("UTF-8")).jsonObject.getValue("messages").jsonArray
            assertEquals(messages, wire.drop(1).take(messages.size))
            assertEquals(JsonPrimitive("next question"), wire.last().jsonObject["content"])
            assertEquals("private tool reasoning", wire[2].jsonObject["reasoning_content"]!!.jsonPrimitive.content)
            assertEquals(finalReason != null, wire[4].jsonObject.containsKey("reasoning_content"))
        }
    }
    @Test fun explicitRecoveryPreservesCiphertextBeforeCreatingMissingSessionKey() {
        val storage = Storage(); val keys = Keys(); val store = AiSessionStore(storage, keys)
        store.load(epoch); assertTrue(store.save(state()))
        val old = storage.bytes!!.clone(); keys.key = null
        val reader = AiSessionStore(storage, keys)
        assertSame(AiSessionLoad.Unavailable, reader.load(epoch))
        storage.preserveSucceeds = false
        assertFalse(reader.recover(state("new")))
        assertEquals(1, keys.creates); assertArrayEquals(old, storage.bytes)
        storage.preserveSucceeds = true
        assertTrue(reader.recover(state("new")))
        assertEquals(2, keys.creates); assertArrayEquals(old, storage.archives.single())
        assertEquals("new", (AiSessionStore(storage, keys).load(epoch) as AiSessionLoad.Ready).session.draft)
    }
    @Test fun corruptRecordRecoveryKeepsExistingAliasAndNeverReplacesFailingAlias() {
        val storage = Storage().apply { bytes = byteArrayOf(9, 1, 2) }
        val keys = Keys(); val originalKey = keys.create()
        val store = AiSessionStore(storage, keys)
        assertSame(AiSessionLoad.Unavailable, store.load(epoch))
        assertTrue(store.recover(state()))
        assertSame(originalKey, keys.key); assertEquals(1, keys.creates)
        assertArrayEquals(byteArrayOf(9, 1, 2), storage.archives.single())
        val failing = object : AiKeySource {
            override fun existing(): SecretKey? = error("Unavailable provider")
            override fun create(): SecretKey = error("Must not replace an existing/unavailable alias")
        }
        val failed = AiSessionStore(storage, failing)
        val previous = storage.bytes!!.clone()
        assertSame(AiSessionLoad.Unavailable, failed.load(epoch))
        assertFalse(failed.recover(state())); assertArrayEquals(previous, storage.bytes)
        assertArrayEquals(previous, storage.archives.last())
    }
    @Test fun encryptionUsesFreshIvAndAuthenticatesVersion() {
        val key = Keys().create(); val plain = AiSessionCodec.encode(state())
        val a = AiSessionCipher.encrypt(key, plain); val b = AiSessionCipher.encrypt(key, plain)
        assertFalse(a.contentEquals(b)); assertArrayEquals(plain, AiSessionCipher.decrypt(key, a))
        a[a.lastIndex] = (a.last().toInt() xor 1).toByte()
        assertTrue(runCatching { AiSessionCipher.decrypt(key, a) }.isFailure)
    }
}
