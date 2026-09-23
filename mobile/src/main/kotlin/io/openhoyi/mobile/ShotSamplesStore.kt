package io.openhoyi.mobile

import java.io.File
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
                writer.appendLine(HEADER)
                points.forEach { point ->
                    writer.appendLine(listOf(point.elapsedMs, point.pressureTenthsBar,
                        point.machineFlowTenthsMlPerSecond, point.waterTenthsMl,
                        point.temperatureHundredthsC, point.weightHundredthsGram ?: "").joinToString("\t"))
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
        if (!source.isFile) return emptyList()
        if (source.length() > 512L * 1024) return emptyList()
        val lines = source.readLines(Charsets.UTF_8)
        if (lines.firstOrNull() != HEADER) return emptyList()
        val points = lines.drop(1).take(ShotSeries.MAX_POINTS).mapNotNull { line ->
            val fields = line.split('\t')
            if (fields.size != 6) return@mapNotNull null
            runCatching {
                ShotPoint(fields[0].toLong(), fields[1].toInt(), fields[2].toInt(), fields[3].toInt(),
                    fields[4].toInt(), fields[5].takeIf(String::isNotEmpty)?.toInt())
            }.getOrNull()
        }
        return if (points.all { it.elapsedMs >= 0 } &&
            points.zipWithNext().all { (a, b) -> a.elapsedMs < b.elapsedMs }) points else emptyList()
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
        private const val HEADER = "# openhoyi-shot-points-v1"
        private val ID_PATTERN = Regex("[A-Za-z0-9-]{1,64}")
        private val FILE_PATTERN = Regex("samples-[A-Za-z0-9-]{1,64}\\.tsv")
    }
}

/** BLE callbacks enqueue disk work; an in-memory pending copy keeps new history immediately readable. */
class ShotSamplesRepository(directory: File, private val onError: (Throwable) -> Unit = {}) {
    private val store = ShotSamplesStore(directory)
    private val pending = ConcurrentHashMap<String, List<ShotPoint>>()
    private val writer = Executors.newSingleThreadExecutor { task ->
        Thread(task, "shot-samples").apply { isDaemon = true }
    }
    fun save(id: String, points: List<ShotPoint>) {
        val copied = points.toList()
        pending[id] = copied
        writer.execute {
            try {
                store.save(id, copied)
                pending.remove(id, copied)
            } catch (error: Throwable) { onError(error) }
        }
    }
    fun load(id: String): List<ShotPoint> = pending[id] ?: store.load(id)
    fun prune(retainedIds: Set<String>) {
        writer.execute { try { store.prune(retainedIds) } catch (error: Throwable) { onError(error) } }
    }
}
