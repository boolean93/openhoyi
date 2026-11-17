package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class AiMarkdownTest {
    @Test fun commonBlocksAndInlineStyles() {
        val rendered = AiMarkdown.parse("# Brew\n- **fine** grind\n1. `25 s`\n```\nx < y\n```")
        assertEquals("Brew\n• fine grind\n1. 25 s\nx < y", rendered.text)
        assertTrue(rendered.marks.any { it.kind == AiMarkdown.Kind.HEADING && it.start == 0 })
        assertTrue(rendered.marks.any { it.kind == AiMarkdown.Kind.BOLD && rendered.text.substring(it.start, it.end) == "fine" })
        assertTrue(rendered.marks.any { it.kind == AiMarkdown.Kind.CODE && rendered.text.substring(it.start, it.end) == "25 s" })
        assertTrue(rendered.marks.any { it.kind == AiMarkdown.Kind.CODE_BLOCK && rendered.text.substring(it.start, it.end) == "x < y" })
    }

    @Test fun adjacentLinksKeepLabelOnlyAndExactOffsets() {
        val rendered = AiMarkdown.parse("[First](https://example.org/a) / [Second](https://example.org/b)")
        assertEquals("First / Second", rendered.text)
        val links = rendered.marks.filter { it.kind == AiMarkdown.Kind.LINK }
        assertEquals(listOf("First", "Second"), links.map { rendered.text.substring(it.start, it.end) })
        assertEquals(listOf("https://example.org/a", "https://example.org/b"), links.map { it.value })
    }

    @Test fun linksShowLabelAndRejectUnsafeSchemes() {
        val rendered = AiMarkdown.parse("[Guide](https://example.org/coffee#grind) [bad](javascript:alert) [auth](https://user@example.org/) ![photo](https://example.org/p.png)")
        assertTrue(rendered.text.startsWith("Guide [bad]"))
        assertFalse(rendered.text.contains("coffee#grind"))
        assertEquals(listOf("https://example.org/coffee#grind"), rendered.marks.filter { it.kind == AiMarkdown.Kind.LINK }.map { it.value })
        assertTrue(rendered.text.contains("[bad](javascript:alert)"))
        assertTrue(rendered.text.contains("[auth](https://user@example.org/)"))
        assertTrue(rendered.text.contains("![photo](https://example.org/p.png)"))
        assertFalse(AiMarkdown.safeHttpUrl("https://example.org/a\nsecret"))
        assertFalse(AiMarkdown.safeHttpUrl("file:///etc/passwd"))
        assertFalse(AiMarkdown.safeHttpUrl("http://localhost:8080/"))
        assertFalse(AiMarkdown.safeHttpUrl("http://192.168.1.1/"))
        assertFalse(AiMarkdown.safeHttpUrl("http://[::1]/"))
    }

    @Test fun rawHtmlAndLongTextStayLiteral() {
        val html = AiMarkdown.parse("<script>alert(1)</script><img src='https://example.org/a.png'>")
        assertEquals("<script>alert(1)</script><img src='https://example.org/a.png'>", html.text)
        assertTrue(html.marks.isEmpty())
        val source = "<script>alert(1)</script>\n\n" + "a".repeat(12_001)
        assertEquals(source, AiMarkdown.parse(source).text)
        assertTrue(AiMarkdown.parse(source).marks.isEmpty())
    }

    @Test fun unsupportedAndUnclosedFencesNeverHideContent() {
        assertEquals("```hello```", AiMarkdown.parse("```hello```").text)
        assertEquals("```kotlin\nval x = 1", AiMarkdown.parse("```kotlin\nval x = 1").text)
        val fenced = AiMarkdown.parse("```kotlin\n```suffix\nval x = 1\n```")
        assertEquals("```suffix\nval x = 1", fenced.text)
        assertTrue(fenced.marks.any { it.kind == AiMarkdown.Kind.CODE_BLOCK &&
            fenced.text.substring(it.start, it.end) == "```suffix" })
    }

    @Test fun pipeTableBecomesStackedLabelsWithInlineStylesAndSafeLinks() {
        val table = AiMarkdown.parse("| Parameter | Value |\n| :--- | ---: |\n| Grind | **fine** |\n| Guide | [Source](https://example.org/guide) |")
        assertEquals("• Parameter: Grind\n  Value: fine\n• Parameter: Guide\n  Value: Source", table.text)
        assertTrue(table.marks.any { it.kind == AiMarkdown.Kind.BOLD && table.text.substring(it.start, it.end) == "Parameter" })
        assertTrue(table.marks.any { it.kind == AiMarkdown.Kind.BOLD && table.text.substring(it.start, it.end) == "fine" })
        assertEquals(listOf("https://example.org/guide"), table.marks.filter { it.kind == AiMarkdown.Kind.LINK }.map { it.value })
        assertFalse(table.text.contains('|'))
    }

    @Test fun incompleteOrFencedTablesRemainLiteralAndEscapedPipesStayInCells() {
        val malformed = "| A | B |\n| -- | --- |\n| x | y |"
        assertEquals(malformed, AiMarkdown.parse(malformed).text)
        val fenced = "```\n| A | B |\n| --- | --- |\n| x | y |\n```"
        assertEquals("| A | B |\n| --- | --- |\n| x | y |", AiMarkdown.parse(fenced).text)
        assertEquals("• Name: A|B\n  Note: a|b", AiMarkdown.parse("Name | Note\n--- | ---\nA\\|B | `a|b`").text)
        val unsafe = AiMarkdown.parse("A | B\n--- | ---\n![x](https://example.org/a.png) | <script>hi</script>")
        assertTrue(unsafe.text.contains("![x](https://example.org/a.png)"))
        assertTrue(unsafe.text.contains("<script>hi</script>"))
        assertTrue(unsafe.marks.none { it.kind == AiMarkdown.Kind.LINK })
        val emptyHeader = "| | Value |\n| --- | --- |\n| a | b |"
        assertEquals(emptyHeader, AiMarkdown.parse(emptyHeader).text)
        val hugeHeaders = "${"a".repeat(2_000)} | ${"b".repeat(2_000)}"
        val oversizedExpansion = "$hugeHeaders\n--- | ---\n" + "x | y\n".repeat(7).trimEnd()
        assertEquals(oversizedExpansion, AiMarkdown.parse(oversizedExpansion).text)
    }
    @Test fun longAnswerKeepsHeadingsLinksAndFencedJsonBeyondTwelveThousand() {
        val json = "{\"notes\":\"" + "x".repeat(14_000) + "\"}"
        val source = "# Intro\n" + "a".repeat(12_001) + "\n[Source](https://example.org/guide)\n```json\n$json\n```\n## Finish"
        val doc = AiMarkdown.parse(source)
        assertTrue(doc.text.contains(json)); assertTrue(doc.text.endsWith("Finish"))
        assertEquals(2, doc.marks.count { it.kind == AiMarkdown.Kind.HEADING })
        assertTrue(doc.marks.any { it.kind == AiMarkdown.Kind.CODE_BLOCK && doc.text.substring(it.start, it.end) == json })
        assertEquals(listOf("https://example.org/guide"), doc.marks.filter { it.kind == AiMarkdown.Kind.LINK }.map { it.value })
    }
    @Test fun spanBudgetFallsBackLocallyAndNeverDropsTheTail() {
        val source = "# Start\n" + "**x**\n".repeat(3_000) + "**last-visible**"
        val doc = AiMarkdown.parse(source)
        assertTrue(doc.marks.isNotEmpty()); assertTrue(doc.marks.size <= AiMarkdown.MAX_MARKS)
        assertTrue(doc.text.endsWith("**last-visible**"))
        assertTrue(doc.marks.all { it.start >= 0 && it.start < it.end && it.end <= doc.text.length })
    }
    @Test fun hugeTableIsLiteralOnlyInsideItsBlockWhileAdjacentSectionsStayFormatted() {
        val table = "${"a".repeat(2_000)} | ${"b".repeat(2_000)}\n--- | ---\n" + "x | y\n".repeat(7).trimEnd()
        val doc = AiMarkdown.parse("# Before\n" + "padding\n".repeat(2_000) + table + "\n\n## After\n[Safe](https://example.org/ok)")
        assertTrue(doc.text.contains(table))
        assertEquals(2, doc.marks.count { it.kind == AiMarkdown.Kind.HEADING })
        assertTrue(doc.marks.any { it.kind == AiMarkdown.Kind.LINK && it.value == "https://example.org/ok" })
    }
    @Test fun displayWorkBoundaryPreservesUnicodeAndLiteralSuffix() {
        val source = "# Heading\n" + "a".repeat(AiMarkdown.MAX_PARSE_CHARS) + "😀\n**literal-tail**"
        val doc = AiMarkdown.parse(source)
        assertTrue(doc.marks.any { it.kind == AiMarkdown.Kind.HEADING })
        assertTrue(doc.text.endsWith("😀\n**literal-tail**"))
        val atSurrogate = "a".repeat(AiMarkdown.MAX_PARSE_CHARS - 1) + "😀tail"
        assertEquals(atSurrogate, AiMarkdown.parse(atSurrogate).text)
    }
    @Test(timeout = 3000) fun pathologicalLookaheadAndTableInputsStayBoundedWithoutHidingText() {
        val brackets = "[".repeat(60_000)
        assertEquals(brackets, AiMarkdown.parse(brackets).text)
        val table = "A | B\n--- | ---\n" + "--- | ---\n".repeat(5_000)
        val doc = AiMarkdown.parse(table)
        assertTrue(doc.text.length <= table.length + AiMarkdown.MAX_TABLE_EXPANSION)
        assertTrue(doc.text.endsWith("--- | ---\n"))
        assertTrue(doc.marks.size <= AiMarkdown.MAX_MARKS)
    }

    @Test fun multipleExpandingTablesCannotBorrowTheUnconsumedLongTailBudget() {
        val table = "${"a".repeat(100)} | ${"b".repeat(100)}\n--- | ---\n" + "x | y\n".repeat(90).trimEnd()
        val tail = "z".repeat(17_000) + "😀complete-tail"
        val source = table + "\n\n" + table + "\n\n" + tail
        val doc = AiMarkdown.parse(source)
        assertTrue(doc.text.length <= source.length + AiMarkdown.MAX_TABLE_EXPANSION)
        assertTrue(doc.text.endsWith(tail))
        assertTrue(doc.text.contains(table)) // Only the over-budget table stays literal.
        assertTrue(doc.marks.isNotEmpty()) // The earlier table keeps its formatting.
    }

}
