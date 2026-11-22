package io.openhoyi.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveAxisHeadersTest {
    @Test fun keepsAlreadySeparatePairsOnTheTickCenters() {
        val captions = listOf(18f, 64f, 246f, 292f).map { LiveAxisHeaders.Caption(it, 24f) }
        assertEquals(captions.map { LiveAxisHeaders.Placement(it.center, 0) },
            LiveAxisHeaders.pack(captions, 6f, 314f, 6f))
    }

    @Test fun drawingAreaCanExtendBelowTitlesWithoutHeaderCollision() {
        val captions = listOf(
            LiveAxisHeaders.Caption(16f, 42f), LiveAxisHeaders.Caption(36f, 34f),
            LiveAxisHeaders.Caption(265f, 36f), LiveAxisHeaders.Caption(286f, 72f)
        )
        val placements = LiveAxisHeaders.pack(captions, 6f, 300f, 6f)
        verify(captions, placements, 6f, 300f, 6f)
        assertEquals(listOf(0, 0, 0, 0), placements.map { it.band })
    }

    @Test fun foldsWholePairsInOrderInANarrowWindowWithoutChangingTheirWidth() {
        val captions = listOf(90f, 60f, 38f, 107f, 86f).mapIndexed { index, width ->
            LiveAxisHeaders.Caption(index * 40f, width)
        }
        val placements = LiveAxisHeaders.pack(captions, 6f, 270f, 6f)
        verify(captions, placements, 6f, 270f, 6f)
        assertEquals(listOf(0, 0, 0, 1, 1), placements.map { it.band })
    }

    private fun verify(captions: List<LiveAxisHeaders.Caption>, placements: List<LiveAxisHeaders.Placement>,
        left: Float, right: Float, gap: Float) {
        assertEquals(captions.size, placements.size)
        val rectangles = captions.zip(placements).map { (caption, p) ->
            Triple(p.center - caption.width / 2, p.center + caption.width / 2, p.band)
        }
        assertTrue(rectangles.all { (start, end, _) -> start >= left && end <= right })
        rectangles.groupBy { it.third }.values.forEach { row ->
            assertTrue(row.zipWithNext().all { (a, b) -> a.second + gap <= b.first })
        }
    }
}
