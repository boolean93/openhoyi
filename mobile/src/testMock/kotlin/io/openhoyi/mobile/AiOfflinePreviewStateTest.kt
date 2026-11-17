package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class AiOfflinePreviewStateTest {
    @Test fun previewProcessFenceCannotDisableFormalOrNormalMockInitialization() {
        assertTrue(aiMockOwnerInitializationAllowed(false,"io.openhoyi.mobile",null))
        assertTrue(aiMockOwnerInitializationAllowed(true,"io.openhoyi.mobile.updatefixture","io.openhoyi.mobile.updatefixture"))
        assertFalse(aiMockOwnerInitializationAllowed(true,"io.openhoyi.mobile.updatefixture","io.openhoyi.mobile.updatefixture:ai_offline_preview"))
        assertFalse(aiMockOwnerInitializationAllowed(true,"",null))
        assertTrue(aiMockOwnerInitializationAllowed(true,"io.openhoyi.mobile.mock","io.openhoyi.mobile.mock"))
        assertFalse(aiMockOwnerInitializationAllowed(true,"io.openhoyi.mobile.mock",null))
        assertFalse(aiMockOwnerInitializationAllowed(true,"io.openhoyi.mobile.mock","foreign:ai_offline_preview"))
        assertFalse(aiMockOwnerInitializationAllowed(true,"io.openhoyi.mobile.mock","io.openhoyi.mobile.mock:unknown"))
        assertFalse(aiMockOwnerInitializationAllowed(true,"io.openhoyi.mobile.mock","io.openhoyi.mobile.mock:ai_offline_preview"))
        assertTrue(aiOfflinePreviewProcess(true,"io.openhoyi.mobile.mock","io.openhoyi.mobile.mock:ai_offline_preview"))
        assertFalse(aiOfflinePreviewProcess(false,"io.openhoyi.mobile.mock","io.openhoyi.mobile.mock:ai_offline_preview"))
        assertFalse(aiOfflinePreviewProcess(true,"io.openhoyi.mobile","io.openhoyi.mobile:ai_offline_preview"))
        assertFalse(aiOfflinePreviewProcess(true,"io.openhoyi.mobile.mock","io.openhoyi.mobile.mock"))
        assertFalse(aiOfflinePreviewProcess(true,"io.openhoyi.mobile.mock",null))
        assertFalse(aiOfflinePreviewProcess(true,"io.openhoyi.mobile.mock","foreign:ai_offline_preview"))
    }
    @Test fun fixedLongDraftAndIndependentModelSlotsUseProductionRecoveryCodec() {
        val s=AiOfflinePreviewState();s.fixedDraft();val draft=s.session.draft
        assertTrue(draft.length>4000);assertTrue(AiChatPolicy.accepts(s.session.lines,draft))
        assertTrue(s.add(AiAttachmentKind.IMAGE));s.switch(AiModelPolicy.PRO)
        assertTrue(s.session.draftAttachments.isEmpty());assertFalse(s.add(AiAttachmentKind.IMAGE))
        val restored=AiOfflinePreviewState(s.snapshot());restored.switch(AiModelPolicy.FLASH)
        assertEquals(draft,restored.session.draft);assertEquals(1,restored.session.draftAttachments.size)
        assertTrue(AiContextPolicy.matches(restored.session.protocol,restored.session.lines))
    }
    @Test fun twoStageOcrNeedsAnotherExplicitSendAndRecoveryNeverRetriesUnknownAttempt() {
        val s=AiOfflinePreviewState();assertTrue(s.add(AiAttachmentKind.DOCUMENT))
        assertFalse(s.sendSynthetic());assertTrue(s.beginOcr());assertFalse(s.beginOcr())
        val restored=AiOfflinePreviewState(s.snapshot())
        assertEquals(AiOcrStatus.UNKNOWN.name,restored.documentStage());assertFalse(restored.beginOcr());assertFalse(restored.sendSynthetic())
        s.finishOcr();assertEquals(AiOcrStatus.READY.name,s.documentStage());assertEquals(2,s.session.lines.size)
        assertTrue(s.sendSynthetic());assertEquals(4,s.session.lines.size)
        assertEquals(AiAttachmentKind.OCR_TEXT,s.session.lines[2].attachments.single().kind)
    }
    @Test fun cancellationAndLengthAreVisibleAndNeverBecomeSendableDocuments() {
        for(incomplete in listOf(false,true)) {
            val s=AiOfflinePreviewState();s.add(AiAttachmentKind.DOCUMENT);s.beginOcr()
            s.finishOcr(unknown=!incomplete,incomplete=incomplete)
            assertEquals(if(incomplete) AiOcrStatus.INCOMPLETE.name else AiOcrStatus.UNKNOWN.name,s.documentStage())
            assertFalse(s.sendSynthetic());assertFalse(s.beginOcr());assertEquals(2,s.session.lines.size)
        }
    }
    @Test fun compactionIsOnlySyntheticStateAndKeepsVisibleOldHistory() {
        val s=AiOfflinePreviewState();s.compactFixture();s.restoreRoundTrip()
        assertEquals(4,s.session.lines.size);assertEquals(2,s.session.protocol.coveredLines)
        assertEquals(2,s.session.protocol.messages.size);assertTrue(AiContextPolicy.matches(s.session.protocol,s.session.lines))
        assertTrue(s.sendSynthetic());assertEquals(6,s.session.lines.size)
    }
}
