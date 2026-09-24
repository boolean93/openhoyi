package io.openhoyi.mobile

import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** One bounded file per shot. This contains decoded observations, never raw BLE frames. */
class ShotSamplesStore(private val directory: File) {
    fun save(id: String, points: List<ShotPoint>) {
        val target = file(id)
        require(points.size <= ShotSeries.MAX_POINTS)
        require(points.all { it.elapsedMs >= 0 })
        require(points.zipWithNext().all { (a, b) -> a.elapsedMs < b.elapsedMs })
        if (!directory.isDirectory && !directory.mkdirs()) error("Cannot create sample directory")
        val temporary = File.createTempFile("samples-", ".tmp", directory)
        try {
            temporary.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.appendLine(HEADER_V2)
                points.forEach { point ->
                    writer.appendLine(listOf(point.elapsedMs, point.pressureTenthsBar,
                        point.machineFlowTenthsMlPerSecond, point.waterTenthsMl,
                        point.temperatureHundredthsC, point.weightHundredthsGram ?: "",
                        point.scaleFlowHundredths ?: "").joinToString("\t"))
                }
            }
            try {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally { temporary.delete() }
    }

    fun load(id: String): List<ShotPoint> {
        val source = file(id)
        if (!source.exists()) return emptyList()
        if (!source.isFile || source.length() > 512L * 1024) throw IOException("Invalid shot samples file")
        val lines = source.readLines(Charsets.UTF_8)
        val fieldCount = when (lines.firstOrNull()) {
            HEADER_V1 -> 6
            HEADER_V2 -> 7
            else -> throw IOException("Invalid shot samples header")
        }
        if (lines.size > ShotSeries.MAX_POINTS + 1)
            throw IOException("Invalid shot samples header or size")
        val points = lines.drop(1).map { line ->
            val fields = line.split('\t')
            if (fields.size != fieldCount) throw IOException("Invalid shot sample row")
            val numbers = fields.take(5).map { it.toLongOrNull() ?: throw IOException("Invalid shot sample value") }
            val weight = fields[5].takeIf(String::isNotEmpty)?.toIntOrNull()
            if (fields[5].isNotEmpty() && weight == null) throw IOException("Invalid shot sample weight")
            val scaleFlow = fields.getOrNull(6)?.takeIf(String::isNotEmpty)?.toIntOrNull()
            if (fieldCount == 7 && fields[6].isNotEmpty() && scaleFlow == null)
                throw IOException("Invalid shot sample scale flow")
            try {
                ShotPoint(numbers[0], Math.toIntExact(numbers[1]), Math.toIntExact(numbers[2]),
                    Math.toIntExact(numbers[3]), Math.toIntExact(numbers[4]), weight, scaleFlow)
            } catch (_: ArithmeticException) { throw IOException("Shot sample value outside integer range") }
        }
        if (points.any { it.elapsedMs < 0 } ||
            points.zipWithNext().any { (a, b) -> a.elapsedMs >= b.elapsedMs })
            throw IOException("Invalid shot sample times")
        return points
    }

    fun prune(retainedIds: Set<String>) {
        if (!directory.isDirectory) return
        directory.listFiles()?.filter { it.isFile && it.name.matches(FILE_PATTERN) }?.forEach { file ->
            val id = file.name.removePrefix("samples-").removeSuffix(".tsv")
            if (id !in retainedIds) check(file.delete()) { "Cannot prune samples" }
        }
        directory.listFiles()?.filter { it.isFile && it.name.startsWith("samples-") && it.name.endsWith(".tmp") }
            ?.forEach { check(it.delete()) { "Cannot prune temporary sample file" } }
    }

    private fun file(id: String): File {
        require(id.matches(ID_PATTERN)) { "Invalid shot ID" }
        return File(directory, "samples-$id.tsv")
    }
    companion object {
        private const val HEADER_V1 = "# openhoyi-shot-points-v1"
        private const val HEADER_V2 = "# openhoyi-shot-points-v2"
        private val ID_PATTERN = Regex("[A-Za-z0-9-]{1,64}")
        private val FILE_PATTERN = Regex("samples-[A-Za-z0-9-]{1,64}\\.tsv")
    }
}

/** BLE callbacks enqueue disk work; an in-memory pending copy keeps new history immediately readable. */
class ShotSamplesRepository(directory: File, private val onError: (Throwable) -> Unit = {}) {
    private val store = ShotSamplesStore(directory)
    private class Pending(val points: List<ShotPoint>)
    private val pending = ConcurrentHashMap<String, Pending>()
    private val writer = Executors.newSingleThreadExecutor { task ->
        Thread(task, "shot-samples").apply { isDaemon = true }
    }
    fun save(id: String, points: List<ShotPoint>) {
        val latest = Pending(points.toList())
        pending[id] = latest
        writer.execute {
            try {
                store.save(id, latest.points)
                pending.remove(id, latest)
            } catch (error: Throwable) {
                pending.remove(id, latest)
                onError(error)
            }
        }
    }
    fun load(id: String): List<ShotPoint> = pending[id]?.points ?: store.load(id)
    fun prune(retainedIds: Set<String>) {
        writer.execute { try { store.prune(retainedIds) } catch (error: Throwable) { onError(error) } }
    }
}
