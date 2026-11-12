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
        val rows = StringBuilder("id,curveId,startedAtMs,endedAtMs,elapsedMs,status,reason,weightHundredthsGram,slot,samplesFile,samplesState,observedMachineElapsedSeconds,timingEvidenceVersion,observedSampleCoverageMs\n")
        ZipOutputStream(output).use { zip ->
            zip.text("README.txt", "OpenHOYI 萃取历史 v4\n" +
                "除 observedMachineElapsedSeconds 为机器遥测整数秒外，时间为毫秒；elapsedMs 是包含结束确认的记录历时，timingEvidenceVersion=0 为旧口径、1 为新证据（机器值空缺不推算）；UNKNOWN 机器计时仅表示中断前观察值，不是完整时长。observedSampleCoverageMs 是最早/最晚有效萃取采样之差（至少两个不同时间点），只代表采样覆盖，不补齐断头断尾；中间断采不重置起点。图表时间是本地单调采样时钟。重量为 0.01 g，压力为 0.1 bar，水量为 0.1 ml，机器水流为 0.1 ml/s，秤报告的咖啡流速为设备值的 0.01 倍（物理单位待实机校准），温度为 0.01 °C。开始/结束时间是 Unix 时间戳。\n" +
                "秤读数超过 1.5 秒未更新时，重量和咖啡流速均留空；旧版采样文件没有咖啡流速字段。\n" +
                "brewing=true 表示实际阀门开启且非预热等待；false 表示明确非萃取阶段；空值表示旧版未知阶段，不补猜。预热原始采样保留。\n" +
                "curveId=manual、slot=6 表示机器拨杆发起的手动萃取，App 仅被动记录。\n" +
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
                        append("elapsedMs\tpressureTenthsBar\tmachineFlowTenthsMlPerSecond\twaterTenthsMl\ttemperatureHundredthsC\tweightHundredthsGram\tscaleFlowHundredths\tbrewing\n")
                        points!!.forEach { point ->
                            append(listOf(point.elapsedMs, point.pressureTenthsBar,
                                point.machineFlowTenthsMlPerSecond, point.waterTenthsMl,
                                point.temperatureHundredthsC, point.weightHundredthsGram ?: "",
                                point.scaleFlowHundredths ?: "", point.brewing?.toString() ?: "").joinToString("\t"))
                            append('\n')
                        }
                    }
                    zip.text(path, body)
                    sampleFiles++
                }
                rows.append(listOf(entry.id, entry.curveId, entry.startedAtMs,
                    entry.endedAtMs, entry.elapsedMs, entry.status.name, entry.reason,
                    entry.weightHundredthsGram, entry.slot, path, state, entry.observedMachineElapsedSeconds, entry.timingEvidenceVersion, entry.observedSampleCoverageMs).joinToString(",", transform = ::csv))
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
