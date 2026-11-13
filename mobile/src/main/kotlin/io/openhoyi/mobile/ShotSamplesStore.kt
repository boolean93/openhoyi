package io.openhoyi.mobile

import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** Legacy raw samples stay unchanged; optional proven beverage readings live in a bound sidecar. */
class ShotSamplesStore(private val directory: File, private val now: () -> Long = System::currentTimeMillis) {
    fun save(id: String, points: List<ShotPoint>) {
        val target = file(id)
        require(points.size <= ShotSeries.MAX_POINTS)
        require(points.all { it.elapsedMs >= 0 && (it.statusBits == null || it.statusBits in 0..255) })
        require(points.all {
            (it.beverageHundredthsGram == null) == (it.beverageSource == null) &&
                (it.beverageHundredthsGram == null) == (it.beverageSegmentId == null) &&
                (it.beverageHundredthsGram == null || (it.beverageHundredthsGram >= 0 &&
                    it.beverageSegmentId!! >= 0 && it.weightHundredthsGram != null))
        })
        require(points.zipWithNext().all { (a, b) -> a.elapsedMs < b.elapsedMs })
        if (!directory.isDirectory && !directory.mkdirs()) error("Cannot create sample directory")
        val temporary = File.createTempFile("samples-", ".tmp", directory)
        try {
            temporary.bufferedWriter(Charsets.UTF_8).use { writer ->
                val withStatus = points.any { it.statusBits != null }
                writer.appendLine(if (withStatus) HEADER_V4 else HEADER_V3)
                points.forEach { point ->
                    val fields = listOf(point.elapsedMs, point.pressureTenthsBar,
                        point.machineFlowTenthsMlPerSecond, point.waterTenthsMl,
                        point.temperatureHundredthsC, point.weightHundredthsGram ?: "",
                        point.scaleFlowHundredths ?: "", point.brewing?.toString() ?: "")
                    writer.appendLine((if (withStatus) fields + (point.statusBits ?: "") else fields).joinToString("\t"))
                }
            }
            val digest = sha256(temporary.readBytes())
            // A same-raw overwrite may revoke net values. Invalidate before changing raw, so a
            // crash cannot resurrect an old sidecar whose raw hash happens to be unchanged.
            val sidecar = beverageFile(id)
            val canWriteBeverage = !sidecar.exists() || isOwnedBeverageFile(sidecar)
            if (sidecar.exists() && canWriteBeverage)
                check(sidecar.delete()) { "Cannot invalidate beverage samples" }
            // Optional unknown/corrupt evidence must not block saving authoritative raw data.
            // Preserve it for recovery/future readers, and do not replace it with a new sidecar.
            replace(temporary, target)
            if (canWriteBeverage && points.any { it.beverageHundredthsGram != null })
                saveBeverage(sidecar, digest, points)
        } finally { temporary.delete() }
    }

    fun load(id: String): List<ShotPoint> {
        val source = file(id)
        if (!source.exists()) return emptyList()
        if (!source.isFile || source.length() > 512L * 1024) throw IOException("Invalid shot samples file")
        val rawBytes = readBounded(source)
        val lines = rawBytes.inputStream().bufferedReader(Charsets.UTF_8).use { it.readLines() }
        val fieldCount = when (lines.firstOrNull()) {
            HEADER_V1 -> 6
            HEADER_V2 -> 7
            HEADER_V3 -> 8
            HEADER_V4 -> 9
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
            if (fieldCount >= 7 && fields[6].isNotEmpty() && scaleFlow == null)
                throw IOException("Invalid shot sample scale flow")
            val brewing = when(fields.getOrNull(7)) {
                null, "" -> null
                "true" -> true
                "false" -> false
                else -> throw IOException("Invalid shot sample brewing evidence")
            }
            val status = fields.getOrNull(8)?.takeIf(String::isNotEmpty)?.let {
                it.toIntOrNull()?.takeIf { value -> value in 0..255 }
                    ?: throw IOException("Invalid shot sample status bits")
            }
            try {
                ShotPoint(numbers[0], Math.toIntExact(numbers[1]), Math.toIntExact(numbers[2]),
                    Math.toIntExact(numbers[3]), Math.toIntExact(numbers[4]), weight, scaleFlow, brewing, status)
            } catch (_: ArithmeticException) { throw IOException("Shot sample value outside integer range") }
        }
        if (points.any { it.elapsedMs < 0 } ||
            points.zipWithNext().any { (a, b) -> a.elapsedMs >= b.elapsedMs })
            throw IOException("Invalid shot sample times")
        return withBeverage(id, rawBytes, points)
    }

    private fun saveBeverage(target: File, digest: String, points: List<ShotPoint>) {
        val temporary = File.createTempFile("beverage-", ".tmp", directory)
        try {
            temporary.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.appendLine(BEVERAGE_HEADER)
                writer.appendLine(BEVERAGE_SOURCE)
                writer.appendLine("# raw-sha256=$digest")
                points.forEach { writer.appendLine("${it.elapsedMs}\t${it.beverageHundredthsGram ?: ""}\t${it.beverageSource?.name ?: ""}\t${it.beverageSegmentId ?: ""}") }
            }
            replace(temporary, target)
        } finally { temporary.delete() }
    }

    /** Optional evidence must never prevent reading the authoritative legacy observations. */
    private fun withBeverage(id: String, raw: ByteArray, points: List<ShotPoint>): List<ShotPoint> {
        val sidecar = beverageFile(id)
        if (!sidecar.isFile || sidecar.length() > MAX_FILE_BYTES) return points
        return try {
            val bytes = readBounded(sidecar)
            val lines = bytes.inputStream().bufferedReader(Charsets.UTF_8).use { it.readLines() }
            if (lines.size != points.size + 3 || lines.size > ShotSeries.MAX_POINTS + 3 ||
                lines.getOrNull(0) != BEVERAGE_HEADER || lines.getOrNull(1) != BEVERAGE_SOURCE ||
                lines.getOrNull(2) != "# raw-sha256=${sha256(raw)}") return points
            val values = lines.drop(3).mapIndexed { index, line ->
                val fields = line.split('\t')
                if (fields.size != 4 || fields[0].toLongOrNull() != points[index].elapsedMs) return points
                val value = fields[1].takeIf(String::isNotEmpty)?.toIntOrNull()
                if (fields[1].isNotEmpty() && value == null) return points
                val source = fields[2].takeIf(String::isNotEmpty)?.let { name ->
                    BeverageWeightSource.entries.firstOrNull { it.name == name }
                }
                if (fields[2].isNotEmpty() && source == null) return points
                val segment = fields[3].takeIf(String::isNotEmpty)?.toIntOrNull()
                if (fields[3].isNotEmpty() && segment == null) return points
                if ((value == null) != (source == null) || (value == null) != (segment == null) ||
                    value != null && (value < 0 || segment!! < 0 || points[index].weightHundredthsGram == null)) return points
                Triple(value, source, segment)
            }
            points.mapIndexed { index, point -> point.copy(beverageHundredthsGram = values[index].first,
                beverageSource = values[index].second, beverageSegmentId = values[index].third) }
        } catch (_: IOException) { points }
        catch (_: SecurityException) { points }
    }

    private fun replace(source: File, target: File) {
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun readBounded(file: File): ByteArray = file.inputStream().use { input ->
        val bytes = ByteArray(MAX_FILE_BYTES + 1)
        var size = 0
        while (size < bytes.size) {
            val count = input.read(bytes, size, bytes.size - size)
            if (count < 0) break
            size += count
        }
        if (size > MAX_FILE_BYTES) throw IOException("Invalid sample file size")
        bytes.copyOf(size)
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun isOwnedBeverageFile(file: File): Boolean = try {
        file.isFile && file.length() <= MAX_FILE_BYTES &&
            readBounded(file).inputStream().bufferedReader(Charsets.UTF_8).use {
                it.readLine() == BEVERAGE_HEADER && it.readLine() == BEVERAGE_SOURCE
            }
    } catch (_: IOException) { false }
    catch (_: SecurityException) { false }

    fun prune(retainedIds: Set<String>) {
        if (!directory.isDirectory) return
        // A missing/corrupt history index must not erase recoverable recent samples.
        val cutoff = now() - 30L * 24 * 60 * 60 * 1000
        directory.listFiles()?.filter { it.isFile && it.name.matches(FILE_PATTERN) }?.forEach { file ->
            val id = file.name.removePrefix("samples-").removeSuffix(".tsv")
            val modifiedAt = file.lastModified()
            if (id !in retainedIds && modifiedAt > 0 && modifiedAt <= cutoff) {
                check(file.delete()) { "Cannot prune samples" }
                val sidecar = beverageFile(id)
                if (isOwnedBeverageFile(sidecar)) check(sidecar.delete()) { "Cannot prune beverage samples" }
            }
        }
        // Only old, orphaned files from our exact namespace/schema may be removed. Preserve
        // retained IDs, recent checkpoints, unknown future formats, and every raw sample file.
        directory.listFiles()?.filter { it.isFile && it.name.matches(BEVERAGE_PATTERN) }?.forEach { sidecar ->
            val id = sidecar.name.removePrefix("beverage-samples-").removeSuffix(".tsv")
            val modifiedAt = sidecar.lastModified()
            if (id !in retainedIds && !file(id).exists() && modifiedAt > 0 && modifiedAt <= cutoff &&
                isOwnedBeverageFile(sidecar)) check(sidecar.delete()) { "Cannot prune orphaned beverage samples" }
        }
        directory.listFiles()?.filter { it.isFile && it.name.startsWith("samples-") && it.name.endsWith(".tmp") }
            ?.forEach { check(it.delete()) { "Cannot prune temporary sample file" } }
    }

    private fun file(id: String): File {
        require(id.matches(ID_PATTERN)) { "Invalid shot ID" }
        return File(directory, "samples-$id.tsv")
    }
    private fun beverageFile(id: String): File {
        require(id.matches(ID_PATTERN)) { "Invalid shot ID" }
        return File(directory, "beverage-samples-$id.tsv")
    }
    companion object {
        private const val MAX_FILE_BYTES = 512 * 1024
        private const val BEVERAGE_HEADER = "# openhoyi-shot-beverage-v1"
        private const val BEVERAGE_SOURCE = "# source=app-shot-baseline-v1"
        private val BEVERAGE_PATTERN = Regex("beverage-samples-[A-Za-z0-9-]{1,64}\\.tsv")
        private const val HEADER_V1 = "# openhoyi-shot-points-v1"
        private const val HEADER_V2 = "# openhoyi-shot-points-v2"
        private const val HEADER_V3 = "# openhoyi-shot-points-v3"
        private const val HEADER_V4 = "# openhoyi-shot-points-v4"
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
