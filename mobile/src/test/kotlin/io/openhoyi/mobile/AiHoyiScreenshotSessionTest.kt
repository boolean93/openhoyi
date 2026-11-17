package io.openhoyi.mobile

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class AiHoyiScreenshotSessionTest {
    private val epoch = "00000000-0000-0000-0000-000000000001"
    private val image = AiAttachment("00000000-0000-0000-0000-000000000002", "curve.jpg", "image/jpeg", 4, AiAttachmentKind.IMAGE)
    private fun state(items: List<AiAttachment> = listOf(image), id: String? = image.id, model: String = AiModelPolicy.FLASH,
        inactive: List<AiSession> = emptyList()) = AiSession(epoch, emptyList(), AiProtocolHistory.empty(model), "", AiUsageSnapshot(),
        items, inactive, hoyiScreenshotSourceId = id)
    private fun changed(s: AiSession, value: JsonElement): ByteArray {
        val o = Json.parseToJsonElement(AiSessionCodec.encode(s).toString(Charsets.UTF_8)).jsonObject
        return JsonObject(o + ("hoyiScreenshotSourceId" to value)).toString().toByteArray()
    }
    @Test fun explicitBindingRoundTripsAndUsageUpdatesPreserveItWhileLegacyCannotInventIt() {
        val restored = AiSessionCodec.decode(AiSessionCodec.encode(state()))
        assertEquals(image.id, restored.hoyiScreenshotSourceId); assertEquals(image.name, restored.hoyiScreenshotSource!!.name)
        assertEquals(image.id, restored.withUsage(AiUsageSnapshot()).hoyiScreenshotSourceId)
        val root = Json.parseToJsonElement(AiSessionCodec.encode(state()).toString(Charsets.UTF_8)).jsonObject
        val old = JsonObject((root - "hoyiScreenshotSourceId") + ("schema" to JsonPrimitive(5)))
        assertNull(AiSessionCodec.decode(old.toString().toByteArray()).hoyiScreenshotSource)
        assertNull(AiSessionCodec.decode(AiSessionCodec.encode(state(id = null))).hoyiScreenshotSource)
    }
    @Test fun unknownNonImageMalformedAndConflictingMetadataNeverDowngradeToUnbound() {
        for (bad in listOf<JsonElement>(JsonPrimitive("unknown"), JsonPrimitive(1), JsonPrimitive(true), JsonObject(emptyMap()))) {
            assertTrue(runCatching { AiSessionCodec.decode(changed(state(), bad)) }.isFailure)
        }
        val text = AiAttachment(image.id, "curve.txt", "text/plain", 4, AiAttachmentKind.TEXT)
        assertTrue(runCatching { AiSessionCodec.encode(state(listOf(text))) }.isFailure)
        val lines = listOf(AiChatLine(true, "question", listOf(image)), AiChatLine(false, "answer"))
        val protocol = AiProtocolHistory.checked(AiContextPolicy.lines(lines))
        val consistent = AiSession(epoch, lines, protocol, "", AiUsageSnapshot(), listOf(image), hoyiScreenshotSourceId = image.id)
        assertNotNull(AiSessionCodec.decode(AiSessionCodec.encode(consistent)).hoyiScreenshotSource)
        val different = AiAttachment(image.id, "different.jpg", "image/jpeg", 4, AiAttachmentKind.IMAGE)
        val conflicting = AiSession(epoch, lines, protocol, "", AiUsageSnapshot(), listOf(different), hoyiScreenshotSourceId = image.id)
        assertTrue(runCatching { AiSessionCodec.encode(conflicting) }.isFailure)
    }
    @Test fun inactiveModelBindingIsIsolatedAndCannotProvideActiveSource() {
        val inactive = state()
        val active = state(items = emptyList(), id = null, model = AiModelPolicy.PRO, inactive = listOf(inactive))
        val restored = AiSessionCodec.decode(AiSessionCodec.encode(active))
        assertNull(restored.hoyiScreenshotSource); assertEquals(image.id, restored.inactive.single().hoyiScreenshotSourceId)
        assertTrue(runCatching { AiSessionCodec.encode(state(items = emptyList(), model = AiModelPolicy.PRO, inactive = listOf(inactive))) }.isFailure)
        val switched = AiModelSessions.switch(state(), AiModelPolicy.PRO)
        assertNull(switched.hoyiScreenshotSource)
        assertTrue(AiModelPolicy.accepts(switched.modelId, switched.draftAttachments))
        assertEquals("switch must retain the inactive source", image.id, switched.inactive.single().hoyiScreenshotSourceId)
        val switchedRoundTrip = AiSessionCodec.decode(AiSessionCodec.encode(switched))
        assertEquals("codec must retain the inactive source", image.id, switchedRoundTrip.inactive.single().hoyiScreenshotSourceId)
        val returned = AiModelSessions.switch(switchedRoundTrip, AiModelPolicy.FLASH)
        assertEquals(image.id, returned.hoyiScreenshotSourceId)
        assertEquals(image.name, returned.hoyiScreenshotSource!!.name)
    }
    @Test fun failedCancellationCannotReplaceSnapshotOrLoseBindingOnLaterUsageSave() {
        val lock = Any()
        val original = state()
        var snapshot = original
        val cancelled = state(id = null)
        var commits = 0
        assertFalse(AiSessionTransactions.commit(lock, cancelled, save = { false }, committed = {
            snapshot = it; commits++
        }))
        assertSame(original, snapshot); assertEquals(0, commits)
        assertEquals(image.id, snapshot.hoyiScreenshotSourceId)
        val usageUpdate = snapshot.withUsage(AiUsageSnapshot())
        assertTrue(AiSessionTransactions.commit(lock, usageUpdate, save = {
            assertEquals(image.id, AiSessionCodec.decode(AiSessionCodec.encode(it)).hoyiScreenshotSourceId)
            true
        }, committed = { snapshot = it; commits++ }))
        assertEquals(image.id, snapshot.hoyiScreenshotSourceId)
        assertTrue(AiSessionTransactions.commit(lock, cancelled, save = {
            assertNull(AiSessionCodec.decode(AiSessionCodec.encode(it)).hoyiScreenshotSourceId)
            true
        }, committed = { snapshot = it; commits++ }))
        assertNull(snapshot.hoyiScreenshotSource); assertEquals(2, commits)
    }
    @Test fun clearingCurrentConversationClearsNativeScreenshotBinding() {
        val memory = AiChatViewState().apply {
            hoyiScreenshotSource = image
            attachments += image
            draft = "missing values follow-up"
        }
        val generation = memory.contextGeneration
        memory.clearCurrent()
        assertNull(memory.hoyiScreenshotSource); assertTrue(memory.attachments.isEmpty())
        assertTrue(memory.draft.isEmpty()); assertTrue(memory.contextGeneration > generation)
    }

}
