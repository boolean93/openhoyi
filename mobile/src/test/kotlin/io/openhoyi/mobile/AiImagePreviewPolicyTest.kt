package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class AiImagePreviewPolicyTest {
    private val epoch = "00000000-0000-0000-0000-000000000001"
    private fun image(id: String = epoch, name: String = "image.jpg", size: Int = 3) =
        AiAttachment(id, name, "image/jpeg", size, AiAttachmentKind.IMAGE)
    private fun key(item: AiAttachment = image(), owner: String = epoch) = AiImagePreviewKey.of(owner, item)
    @Test fun sampledDecodeIsBoundedEvenForExtremeAspectRatios() {
        listOf(3200 to 3200, 3200 to 100, 100 to 3200, 1 to 100000, 100000 to 1, 50 to 30).forEach { (w, h) ->
            val sample = AiImagePreviewPolicy.sample(w, h)
            assertTrue((maxOf(w, h).toLong() + sample - 1) / sample <= 512)
            assertEquals(0, sample and (sample - 1))
        }
        assertTrue(runCatching { AiImagePreviewPolicy.sample(0, 20) }.isFailure)
        assertTrue(runCatching { AiImagePreviewPolicy.sample(Int.MAX_VALUE, Int.MAX_VALUE) }.isFailure)
    }
    @Test fun fitPreservesLandscapePortraitSquareAndDoesNotUpscale() {
        assertEquals(240 to 60, AiImagePreviewPolicy.fit(1200, 300, 240, 180))
        assertEquals(45 to 180, AiImagePreviewPolicy.fit(300, 1200, 240, 180))
        assertEquals(180 to 180, AiImagePreviewPolicy.fit(800, 800, 240, 180))
        assertEquals(20 to 10, AiImagePreviewPolicy.fit(20, 10, 240, 180))
    }
    @Test fun fullMetadataAndOwnerArePartOfCacheIdentity() {
        val cache = AiImagePreviewCache<String>(10)
        val key = key(); cache.begin("session", setOf(key)); cache.put(key, "pixels", 4)
        assertNotNull(cache[key(image())])
        assertNull(cache[key(image(name = "renamed.jpg"))])
        assertNull(cache[key(image(size = 4))])
        assertNull(cache[key(owner = "other-owner")])
    }
    @Test fun switchesRemovalAndCloseInvalidateCachedPixels() {
        val cache = AiImagePreviewCache<String>(10); val key = key()
        cache.begin("s1", setOf(key)); cache.put(key, "pixels", 4)
        cache.begin("s1", setOf(key)); assertNotNull(cache[key])
        cache.begin("s2", setOf(key)); assertNull(cache[key]); assertEquals(0, cache.bytes)
        cache.put(key, "pixels", 4); cache.begin("s2", emptySet()); assertNull(cache[key])
        cache.put(key, "pixels", 4); cache.clear(); assertEquals(0, cache.bytes)
    }
    @Test fun cacheIsBoundedLruAndMissingHistoryIsCachedWithoutRetryLoop() {
        val cache = AiImagePreviewCache<String>(6)
        val a = key(); val b = key(image(id = "00000000-0000-0000-0000-000000000002"))
        cache.begin("s", setOf(a, b)); cache.put(a, "first", 4); cache.put(b, "next", 4)
        assertNull(cache[a]); assertEquals("next", cache[b]?.value); assertEquals(4, cache.bytes)
        cache.put(a, null, 0); assertNotNull(cache[a]); assertNull(cache[a]?.value)
        cache.put(a, "oversized", 7); assertNull(cache[a]); assertTrue(cache.bytes <= 6)
    }
    @Test fun missingHistoryAndFailedDecodeBecomePlaceholdersAndWipeBytes() {
        val item = image()
        assertNull(AiImagePreviewPolicy.read<String>(item, { throw AiAttachmentFailure(AiAttachmentProblem.UNAVAILABLE) }, { error("must not decode") }))
        val bytes = byteArrayOf(1, 2, 3)
        assertNull(AiImagePreviewPolicy.read<String>(item, { AiPreparedAttachment(it, bytes) }, { error("corrupt image") }))
        assertTrue(bytes.all { it == 0.toByte() })
        val good = byteArrayOf(1, 2, 3)
        assertEquals("decoded", AiImagePreviewPolicy.read(item, { AiPreparedAttachment(it, good) }, { "decoded" }))
        assertTrue(good.all { it == 0.toByte() })
        val cancelled = byteArrayOf(1, 2, 3)
        assertTrue(runCatching { AiImagePreviewPolicy.read<String>(item, { AiPreparedAttachment(it, cancelled) },
            { throw kotlinx.coroutines.CancellationException() }) }.exceptionOrNull() is kotlinx.coroutines.CancellationException)
        assertTrue(cancelled.all { it == 0.toByte() })
    }
    @Test fun imageNamesAreNotDuplicatedButFilePresentationIsUnchanged() {
        val file = AiAttachment("00000000-0000-0000-0000-000000000002", "notes.txt", "text/plain", 3, AiAttachmentKind.TEXT)
        assertEquals("question\n[notes.txt]", AiImagePreviewPolicy.visibleText("question", listOf(image(), file)))
        assertEquals("", AiImagePreviewPolicy.visibleText("", listOf(image())))
    }
}
