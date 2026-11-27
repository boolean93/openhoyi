package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class AiConsentPolicyTest {
    @Test fun onlyCurrentDisclosureIsAccepted() {
        assertFalse(AiConsentPolicy.accepts(0)) // New user or declined: no version saved.
        assertFalse(AiConsentPolicy.accepts(1)) // Earlier disclosure cannot authorize more searches.
        assertTrue(AiConsentPolicy.accepts(AiConsentPolicy.CURRENT_VERSION))
        assertFalse(AiConsentPolicy.accepts(AiConsentPolicy.CURRENT_VERSION + 1))
    }

    @Test fun ordinaryChatAndCompletionShareTheConsentGateAndSearchBudget() {
        assertEquals(4, AiChatPolicy.SEARCH_CALLS)
        for (locale in listOf("values", "values-en")) {
            val xml = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(File("src/main/res/$locale/ai_chat.xml"))
            val strings = xml.getElementsByTagName("string")
            val body = (0 until strings.length).map { strings.item(it) }
                .single { it.attributes.getNamedItem("name").nodeValue == "ai_consent_body" }.textContent
            assertTrue(body.contains("%1\$d"))
            assertTrue(body.format(AiChatPolicy.SEARCH_CALLS).contains("4"))
        }
        val activity = File("src/main/kotlin/io/openhoyi/mobile/AiChatActivity.kt").readText()
        assertTrue(activity.contains("getString(R.string.ai_consent_body, AiChatPolicy.SEARCH_CALLS)"))
        assertFalse(activity.contains("deepseek_v4_compact"))
        val ordinarySend = activity.substringAfter("private fun sendOrConfirm()").substringBefore("private fun withChatConsent")
        assertFalse(ordinarySend.contains("confirmOcr("))
        assertTrue(ordinarySend.contains("withChatConsent { sendAfterConsent(text) }"))
        val completionSend = activity.substringAfter("override fun onPostResume()").substringBefore("private fun abandonCompletionReview")
        assertTrue(completionSend.indexOf("withChatConsent(") in 0 until completionSend.indexOf("completionReviewAccess.consume("))
        val gate = activity.substringAfter("private fun withChatConsent").substringBefore("private fun sendAfterConsent")
        assertTrue(gate.contains("if (consented()) { onAuthorized(); return }"))
        assertTrue(gate.contains("binding.start()\n                agreed = true\n                onAuthorized()"))
        val continuation = activity.substringAfter("private fun sendAfterConsent").substringBefore("private fun begin")
        assertTrue(continuation.contains("confirmOcr(document)"))
        assertTrue(continuation.contains("confirmAttachments(text)"))
    }
    @Test fun completionRequiresConfirmationOnlyForAttachmentsInActiveHistory() {
        val activity = File("src/main/kotlin/io/openhoyi/mobile/AiChatActivity.kt").readText()
        val completion = activity.substringAfter("override fun onPostResume()").substringBefore("private fun abandonCompletionReview")
        assertTrue(completion.indexOf("confirmAttachments(emptyList())") in 0 until completion.indexOf("begin(review.line("))
        val confirmation = activity.substringAfter("private fun confirmAttachments(").substringBefore("private fun pickAttachment")
        assertTrue(confirmation.contains("history.messages.any { AiAttachmentConversation.items(it).isNotEmpty() }"))
        assertTrue(confirmation.contains("if (outgoing.isEmpty() && !active) { onConfirmed(); return }"))
    }
}
