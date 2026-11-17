package io.openhoyi.mobile

import java.net.URI

/** Small, bounded display grammar. Never interprets HTML or fetches referenced resources. */
internal object AiMarkdown {
    const val MAX_PARSE_CHARS = 64_000
    const val MAX_MARKS = 2048
    const val MAX_TABLE_EXPANSION = 24_000
    private class WorkBudget {
        private var remaining = MAX_PARSE_CHARS * 8L
        fun search(length: Int): Boolean {
            if (length > remaining) return false
            remaining -= length
            return true
        }
    }
    private val openingFence = Regex("```[A-Za-z0-9_+.-]*\\s*")
    private val tableDivider = Regex(":?-{3,}:?")
    enum class Kind { HEADING, BOLD, CODE, CODE_BLOCK, LINK }
    data class Mark(val start: Int, val end: Int, val kind: Kind, val value: String? = null)
    data class Document(val text: String, val marks: List<Mark>)

    fun parse(source: String): Document {
        // Parse a bounded display prefix, preserving the remaining source literally.
        // Never slice protocol data or hide visible content at a display-work boundary.
        var parseEnd = minOf(source.length, MAX_PARSE_CHARS)
        if (parseEnd < source.length && parseEnd > 0 && Character.isHighSurrogate(source[parseEnd - 1]) &&
            Character.isLowSurrogate(source[parseEnd])) parseEnd--
        val input = source.substring(0, parseEnd)
        val budget = WorkBudget()
        val output = StringBuilder(input.length)
        val marks = mutableListOf<Mark>()
        var fenced = false
        var wroteLine = false
        var skipUntil = 0
        var tableExpansionUsed = 0L
        val lines = input.split('\n')
        val closingAhead = BooleanArray(lines.size + 1)
        for (index in lines.indices.reversed()) closingAhead[index] = closingAhead[index + 1] || lines[index].trim() == "```"
        lines.forEachIndexed { index, raw ->
            if (index < skipUntil) return@forEachIndexed
            if (marks.size >= MAX_MARKS) {
                if (wroteLine) output.append('\n')
                wroteLine = true
                output.append(raw)
                return@forEachIndexed
            }
            val trimmed = raw.trimStart()
            val closing = trimmed.trimEnd() == "```"
            val opening = trimmed.matches(openingFence)
            if (fenced && closing) {
                fenced = false
                return@forEachIndexed
            }
            if (!fenced && opening && closingAhead[index + 1]) {
                fenced = true
                return@forEachIndexed
            }
            // A small-screen table is easier to read as stacked labelled values than a
            // horizontally clipped grid. Require a complete header, divider and data row.
            if (!fenced && index + 2 < lines.size) {
                val headers = tableCells(raw)
                val divider = tableCells(lines[index + 1])
                if (headers != null && headers.all { it.isNotBlank() } && divider?.size == headers.size &&
                    divider.all { it.matches(tableDivider) }) {
                    val rows = mutableListOf<List<String>>()
                    val headerCost = headers.sumOf { it.length }.toLong() + headers.size * 5L
                    var expansion = 0L
                    var originalLength = raw.length.toLong() + lines[index + 1].length + 1
                    var canExpand = true
                    var next = index + 2
                    while (next < lines.size) {
                        val cells = tableCells(lines[next])?.takeIf { it.size == headers.size } ?: break
                        expansion += headerCost + cells.sumOf { it.length }
                        originalLength += lines[next].length + 1
                        val extra = (expansion - originalLength).coerceAtLeast(0)
                        if (expansion > MAX_TABLE_EXPANSION || tableExpansionUsed + extra > MAX_TABLE_EXPANSION) {
                            canExpand = false
                            rows.clear() // Do not retain a huge table just to render it literally.
                        }
                        if (canExpand) rows += cells
                        next++
                    }
                    if (next > index + 2 && !canExpand) {
                        // Degrade this complete table block only, then resume normal formatting.
                        for (row in index until next) {
                            if (wroteLine) output.append('\n')
                            wroteLine = true
                            output.append(lines[row])
                        }
                        skipUntil = next
                        return@forEachIndexed
                    }
                    if (rows.isNotEmpty()) {
                        // Charge expansion independently of any source text still to come.
                        tableExpansionUsed += (expansion - originalLength).coerceAtLeast(0)
                        rows.forEach { cells ->
                            if (wroteLine) output.append('\n')
                            wroteLine = true
                            cells.forEachIndexed { column, cell ->
                                if (column > 0) output.append('\n')
                                output.append(if (column == 0) "• " else "  ")
                                val labelStart = output.length
                                inline(headers[column], output, marks, budget)
                                if (output.length > labelStart && marks.size < MAX_MARKS)
                                    marks += Mark(labelStart, output.length, Kind.BOLD)
                                output.append(": ")
                                inline(cell, output, marks, budget)
                            }
                        }
                        skipUntil = next
                        return@forEachIndexed
                    }
                }
            }
            if (wroteLine) output.append('\n')
            wroteLine = true
            if (fenced) {
                val start = output.length
                output.append(raw)
                if (output.length > start && marks.size < MAX_MARKS) marks += Mark(start, output.length, Kind.CODE_BLOCK)
                return@forEachIndexed
            }
            if (trimmed.startsWith("```")) {
                output.append(raw) // Unsupported or unclosed fence is visible literal text.
                return@forEachIndexed
            }
            var line = raw
            var heading = 0
            while (heading < line.length && heading < 3 && line[heading] == '#') heading++
            if (heading > 0 && heading < line.length && line[heading] == ' ') {
                line = line.drop(heading + 1)
            } else heading = 0
            if (line.startsWith("- ") || line.startsWith("* ")) line = "• " + line.drop(2)
            val start = output.length
            inline(line, output, marks, budget)
            if (heading > 0 && output.length > start && marks.size < MAX_MARKS) marks += Mark(start, output.length, Kind.HEADING, heading.toString())
        }
        output.append(source, parseEnd, source.length)
        return Document(output.toString(), marks)
    }

    /** Parses only a bounded, single-line pipe row; malformed syntax stays visible verbatim. */
    private fun tableCells(raw: String): List<String>? {
        val line = raw.trim()
        if ('|' !in line) return null
        val first = if (line.startsWith('|')) 1 else 0
        val last = if (line.endsWith('|') && !line.endsWith("\\|")) line.length - 1 else line.length
        val cells = mutableListOf<String>()
        val cell = StringBuilder()
        var code = false
        var index = first
        while (index < last) {
            when {
                line[index] == '\\' && index + 1 < last && line[index + 1] == '|' -> {
                    cell.append('|'); index += 2
                }
                line[index] == '`' -> { code = !code; cell.append('`'); index++ }
                line[index] == '|' && !code -> {
                    cells += cell.toString().trim()
                    if (cells.size >= 8) return null // More than eight columns cannot be a supported table.
                    cell.clear(); index++
                }
                else -> { cell.append(line[index]); index++ }
            }
        }
        if (code) return null
        cells += cell.toString().trim()
        return cells.takeIf { it.size in 2..8 }
    }

    private fun inline(line: String, output: StringBuilder, marks: MutableList<Mark>, budget: WorkBudget) {
        var index = 0
        while (index < line.length) {
            if (marks.size >= MAX_MARKS ||
                (line[index] in "`[*_" && !budget.search(line.length - index))) {
                output.append(line, index, line.length)
                return
            }
            if (line[index] == '`') {
                val end = line.indexOf('`', index + 1)
                if (end > index + 1) {
                    val start = output.length
                    output.append(line, index + 1, end)
                    if (marks.size < MAX_MARKS) marks += Mark(start, output.length, Kind.CODE)
                    index = end + 1
                    continue
                }
            }
            val marker = when {
                line.startsWith("**", index) -> "**"
                line.startsWith("__", index) -> "__"
                else -> null
            }
            if (marker != null) {
                val end = line.indexOf(marker, index + 2)
                if (end > index + 2) {
                    val start = output.length
                    output.append(line, index + 2, end)
                    if (marks.size < MAX_MARKS) marks += Mark(start, output.length, Kind.BOLD)
                    index = end + 2
                    continue
                }
            }
            // ![alt](url) is intentionally literal: no image loader, preview or request.
            if (line[index] == '[' && (index == 0 || line[index - 1] != '!')) {
                val close = line.indexOf(']', index + 1)
                val openUrl = close + 1
                if (close > index + 1 && openUrl < line.length && line[openUrl] == '(') {
                    val end = line.indexOf(')', openUrl + 1)
                    if (end > openUrl + 1) {
                        val url = line.substring(openUrl + 1, end)
                        if (safeHttpUrl(url)) {
                            val start = output.length
                            output.append(line, index + 1, close)
                            if (marks.size < MAX_MARKS) marks += Mark(start, output.length, Kind.LINK, url)
                            index = end + 1
                            continue
                        }
                    }
                }
            }
            output.append(line[index])
            index++
        }
    }

    fun safeHttpUrl(value: String): Boolean {
        if (value.length !in 1..2048 || value.any {
            it.isWhitespace() || Character.isISOControl(it) || Character.getType(it) == Character.FORMAT.toInt()
        }) return false
        return try {
            val uri = URI(value)
            val host = uri.host?.lowercase()?.trimEnd('.') ?: return false
            uri.scheme?.lowercase() in setOf("https", "http") &&
                host.contains('.') && host != "localhost" && !host.endsWith(".localhost") &&
                !host.endsWith(".local") && !host.endsWith(".internal") &&
                !host.all { it.isDigit() || it == '.' } && ':' !in host &&
                uri.userInfo == null && uri.port in -1..65535
        } catch (_: Exception) { false }
    }
}
