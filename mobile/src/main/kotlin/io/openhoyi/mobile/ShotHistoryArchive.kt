package io.openhoyi.mobile

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Portable Alpha history. File names use ordinals so stored IDs never become ZIP paths. */
object ShotHistoryArchive {
    data class Report(val records: Int, val sampleFiles: Int, val unavailable: Int)

    fun write(entries: List<ShotHistory.Entry>, loadPoints: (String) -> List<ShotPoint>, output: OutputStream): Report {
        var sampleFiles = 0
        var unavailable = 0
        val rows = StringBuilder("id,curveId,startedAtMs,endedAtMs,elapsedMs,status,reason,weightHundredthsGram,slot,samplesFile,samplesState\n")
        ZipOutputStream(output).use { zip ->
            zip.text("README.txt", "OpenHOYI Alpha 萃取历史 v1\n" +
                "时间为毫秒；重量为 0.01 g，压力为 0.1 bar，水量为 0.1 ml，机器水流为 0.1 ml/s，温度为 0.01 °C。开始/结束时间是 Unix 时间戳。\n" +
                "UNKNOWN 表示结果未确认；对应采样文件可能只是中断前保存的部分曲线。\n" +
                "samplesState=none 表示读取结果无采样；unavailable 表示读取失败或数据无效。\n")
            entries.forEachIndexed { index, entry ->
                val loaded = runCatching { loadPoints(entry.id) }
                val points = loaded.getOrNull()
                val valid = points != null && points.size <= ShotSeries.MAX_POINTS &&
                    points.all { it.elapsedMs >= 0 } &&
                    points.zipWithNext().all { (a, b) -> a.elapsedMs < b.elapsedMs }
                val state = when {
                    !valid -> { unavailable++; "unavailable" }
                    points!!.isEmpty() -> "none"
                    else -> "available"
                }
                val path = if (state == "available") "shots/%04d.tsv".format(index + 1) else ""
                if (path.isNotEmpty()) {
                    val body = buildString {
                        append("elapsedMs\tpressureTenthsBar\tmachineFlowTenthsMlPerSecond\twaterTenthsMl\ttemperatureHundredthsC\tweightHundredthsGram\n")
                        points!!.forEach { point ->
                            append(listOf(point.elapsedMs, point.pressureTenthsBar,
                                point.machineFlowTenthsMlPerSecond, point.waterTenthsMl,
                                point.temperatureHundredthsC, point.weightHundredthsGram ?: "").joinToString("\t"))
                            append('\n')
                        }
                    }
                    zip.text(path, body)
                    sampleFiles++
                }
                rows.append(listOf(entry.id, entry.curveId, entry.startedAtMs,
                    entry.endedAtMs, entry.elapsedMs, entry.status.name, entry.reason,
                    entry.weightHundredthsGram, entry.slot, path, state).joinToString(",", transform = ::csv))
                rows.append('\n')
            }
            zip.text("history.csv", rows.toString())
        }
        return Report(entries.size, sampleFiles, unavailable)
    }

    private fun csv(value: Any?): String {
        val raw = value?.toString().orEmpty()
        return if (raw.any { it == ',' || it == '"' || it == '\n' || it == '\r' })
            "\"${raw.replace("\"", "\"\"")}\"" else raw
    }
    private fun ZipOutputStream.text(path: String, content: String) {
        putNextEntry(ZipEntry(path))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }
}
