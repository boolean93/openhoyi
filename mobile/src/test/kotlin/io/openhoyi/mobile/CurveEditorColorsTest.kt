package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.pow

class CurveEditorColorsTest {
    private fun luminance(color: Int): Double = listOf(16, 8, 0).map { shift ->
        val s = ((color ushr shift) and 255) / 255.0
        if (s <= .04045) s / 12.92 else ((s + .055) / 1.055).pow(2.4)
    }.let { .2126 * it[0] + .7152 * it[1] + .0722 * it[2] }
    private fun contrast(a: Int, b: Int): Double {
        val x = luminance(a); val y = luminance(b)
        return (maxOf(x, y) + .05) / (minOf(x, y) + .05)
    }
    @Test fun semanticColorsAreDistinctAndRetainReadableTextInBothThemes() {
        for (dark in listOf(false, true)) {
            val palette = CurveEditorColors(dark)
            assertNotEquals(palette.pressureFill, palette.flowFill)
            assertNotEquals(palette.pressureInk, palette.flowInk)
            for (flow in listOf(false, true)) {
                assertTrue("selected text dark=$dark flow=$flow", contrast(-1, palette.fill(flow)) >= 4.5)
                assertTrue("badge text dark=$dark flow=$flow", contrast(palette.ink(flow), palette.tint(flow)) >= 4.5)
            }
        }
    }
}
