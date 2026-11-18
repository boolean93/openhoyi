package io.openhoyi.mobile

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class AiHoyiScreenshotSessionTest {
    private val epoch = "00000000-0000-0000-0000-000000000001"
    private val image = AiAttachment("00000000-0000-0000-0000-000000000002", "curve.jpg", "image/jpeg", 4, AiAttachmentKind.IMAGE)
    private fun state(draft: String = "", model: String = AiModelPolicy.FLASH) = AiSession(epoch, emptyList(), AiProtocolHistory.empty(model), draft,
        AiUsageSnapshot(), listOf(image))
    // Build the actual legacy field explicitly. The new encoder intentionally never writes an active mode.
    private fun legacy(value: JsonElement = JsonPrimitive(image.id)): ByteArray {
        val root = Json.parseToJsonElement(AiSessionCodec.encode(state()).toString(Charsets.UTF_8)).jsonObject
        return JsonObject(root + ("hoyiScreenshotSourceId" to value)).toString().toByteArray()
    }
    @Test fun legacySchema6RemainsReadableWithoutRevivingModeOrDeletingImages() {
        val restored = AiSessionCodec.decode(legacy())
        assertEquals(image.id, restored.draftAttachments.single().id)
        val saved = Json.parseToJsonElement(AiSessionCodec.encode(restored).toString(Charsets.UTF_8)).jsonObject
        assertEquals(JsonNull, saved["hoyiScreenshotSourceId"])
        val pro = AiModelSessions.switch(restored, AiModelPolicy.PRO)
        val returned = AiModelSessions.switch(AiSessionCodec.decode(AiSessionCodec.encode(pro)), AiModelPolicy.FLASH)
        assertEquals(image.id, returned.draftAttachments.single().id)
        val old = JsonObject((saved - "hoyiScreenshotSourceId") + ("schema" to JsonPrimitive(5)))
        assertEquals(image.id, AiSessionCodec.decode(old.toString().toByteArray()).draftAttachments.single().id)
    }
    @Test fun malformedLegacyBindingRemainsStrictInsteadOfSilentlyDiscardingCorruption() {
        for (bad in listOf<JsonElement>(JsonPrimitive("unknown"), JsonPrimitive(1), JsonPrimitive(true), JsonObject(emptyMap()))) {
            assertTrue(runCatching { AiSessionCodec.decode(legacy(bad)) }.isFailure)
        }
    }
    @Test fun failedCommitCannotReplaceSnapshotOrLoseDraftOnLaterUsageSave() {
        val lock = Any(); val original = state("original")
        var snapshot = original; var commits = 0
        val candidate = state("changed")
        assertFalse(AiSessionTransactions.commit(lock, candidate, save = { false }, committed = { snapshot = it; commits++ }))
        assertSame(original, snapshot); assertEquals(0, commits)
        assertTrue(AiSessionTransactions.commit(lock, snapshot.withUsage(AiUsageSnapshot()), save = {
            assertEquals("original", AiSessionCodec.decode(AiSessionCodec.encode(it)).draft); true
        }, committed = { snapshot = it; commits++ }))
        assertEquals("original", snapshot.draft)
        assertTrue(AiSessionTransactions.commit(lock, candidate, save = { true }, committed = { snapshot = it; commits++ }))
        assertEquals("changed", snapshot.draft); assertEquals(2, commits)
    }
}
