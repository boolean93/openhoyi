package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import kotlinx.serialization.json.*

class AiStorageProbeFixtureTest {
    private fun fixture(): AiSession {
        val pdf = AiAttachment(UUID.randomUUID().toString(), "probe.pdf", "application/pdf", 32, AiAttachmentKind.DOCUMENT)
        return AiStorageProbeFixture.session(UUID.randomUUID().toString(), listOf(pdf), "Fixed draft")
    }
    @Test fun codecRestoresBothModelSlotsCheckpointAndExactToolTransaction() {
        val initial = fixture()
        val bytes = AiSessionCodec.encode(initial)
        try {
            val restored = AiSessionCodec.decode(bytes)
            AiStorageProbeFixture.verify(restored)
            assertEquals(initial.protocol.messages, restored.protocol.messages)
            val pro = AiModelSessions.switch(restored, AiModelPolicy.PRO)
            assertEquals("Fixed Pro draft", pro.draft)
            val flash = AiModelSessions.switch(pro, AiModelPolicy.FLASH)
            assertEquals(restored.protocol.messages, flash.protocol.messages)
            assertEquals(restored.lines.map { it.text }, flash.lines.map { it.text })
            assertEquals("Fixed draft", flash.draft)
            assertEquals(AiOcrStatus.UNKNOWN, flash.ocr.single().status)
        } finally { bytes.fill(0) }
    }
    @Test fun repeatedDecodeKeepsInterruptedOcrUnknownWithoutExtractedPayload() {
        val first = AiSessionCodec.decode(AiSessionCodec.encode(fixture()))
        val second = AiSessionCodec.decode(AiSessionCodec.encode(first))
        assertEquals(AiOcrStatus.UNKNOWN, second.ocr.single().status)
        assertNull(second.ocr.single().extracted)
        assertNull(second.ocr.single().referenceInput)
        assertNull(second.ocr.single().pages)
    }
    @Test fun missingToolResultFailsClosedInsteadOfRepairingHistory() {
        val root = Json.parseToJsonElement(String(AiSessionCodec.encode(fixture()), Charsets.UTF_8)).jsonObject
        val broken = JsonObject(root + ("protocol" to JsonArray(root.getValue("protocol").jsonArray.filterNot {
            it.jsonObject["role"] == JsonPrimitive("tool")
        })))
        assertTrue(runCatching { AiSessionCodec.decode(broken.toString().toByteArray()) }.isFailure)
    }
    @Test fun faultFixturesAreOnlyFixedLocalReadPaths() {
        listOf("/short", "/wrong-size", "/cancel").forEach {
            assertTrue(AiStorageProbeScope.accepts(AiStorageProbeScope.AUTHORITY, it, null, null))
            assertFalse(AiStorageProbeScope.accepts(AiStorageProbeScope.AUTHORITY, it, "uri=private", null))
        }
        assertFalse(AiStorageProbeScope.accepts(AiStorageProbeScope.AUTHORITY, "/cancel/../text", null, null))
    }
}
