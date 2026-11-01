package io.openhoyi.bean

import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

private class MemoryStorage : InventoryStorage {
    var bytes: ByteArray? = null
    var fail = false
    override fun read(): ByteArray? = bytes?.clone()
    override fun write(bytes: ByteArray) {
        if (fail) error("disk full")
        this.bytes = bytes.clone()
    }
}
private fun rejects(block: () -> Unit) {
    check(runCatching(block).isFailure) { "Expected rejection" }
}
private fun batch(id: String = "batch") = BeanBatch(id, Bean("bean", "Ethiopia"), 250_000,
    LocalDate.of(2026, 10, 1), LocalDate.of(2026, 9, 28), null)

fun main() {
    val storage = MemoryStorage()
    val inventory = BeanInventory(storage)
    val added = inventory.addBatch("add", batch())
    check(added.balanceMg == 250_000L)
    check(inventory.batches().single().batch == batch())
    val used = inventory.consume("brew", "batch", 18_000)
    check(used.balanceMg == 232_000L)
    check(inventory.consume("brew", "batch", 18_000) == used)
    rejects { inventory.consume("brew", "batch", 19_000) }
    rejects { inventory.adjust("brew", "batch", -18_000, "Different kind") }
    rejects { inventory.addBatch("add", batch().copy(initialMg = 100_000)) }
    rejects { inventory.addBatch("another", batch("other").copy(bean = Bean("bean", "Different"))) }
    rejects { inventory.consume("missing", "missing", 1) }
    rejects { inventory.consume("too-much", "batch", 999_999) }
    rejects { inventory.consume("zero", "batch", 0) }
    rejects { inventory.addBatch("duplicate-batch", batch()) }
    check(inventory.adjust("correction", "batch", -2_000, "Measured loss").balanceMg == 230_000L)
    check(inventory.adjust("restock", "batch", 5_000, "Weighed again").balanceMg == 235_000L)
    rejects { inventory.adjust("invalid", "batch", -999_999, "Loss") }
    rejects { inventory.adjust("no-reason", "batch", 1, " ") }
    rejects { inventory.adjust("zero-adjust", "batch", 0, "Correction") }
    check(inventory.adjust("correction", "batch", -2_000, "Measured loss").balanceMg == 230_000L)
    rejects { inventory.adjust("correction", "batch", -2_000, "Different reason") }
    check(inventory.events().sumOf { it.deltaMg } == inventory.batches().single().balanceMg)
    val restored = BeanInventory(storage)
    check(restored.batches() == inventory.batches())
    check(restored.events() == inventory.events())
    check(restored.consume("brew", "batch", 18_000) == used)
    storage.fail = true
    rejects { restored.consume("failed", "batch", 10_000) }
    check(restored.batches().single().balanceMg == 235_000L)
    check(restored.events().none { it.eventId == "failed" })
    storage.fail = false
    check(restored.consume("failed", "batch", 10_000).balanceMg == 225_000L)

    val concurrent = BeanInventory(MemoryStorage())
    concurrent.addBatch("add", batch())
    val start = CountDownLatch(1)
    val failures = java.util.concurrent.ConcurrentLinkedQueue<Throwable>()
    val workers = (1..24).map {
        thread { start.await(); runCatching { concurrent.consume("same", "batch", 18_000) }.exceptionOrNull()?.let(failures::add) }
    }
    start.countDown(); workers.forEach { it.join() }
    check(failures.isEmpty())
    check(concurrent.batches().single().balanceMg == 232_000L)
    check(concurrent.events().size == 2)

    val unique = (1..24).map { index -> thread { concurrent.consume("unique-$index", "batch", 1_000) } }
    unique.forEach { it.join() }
    check(concurrent.batches().single().balanceMg == 208_000L)
    check(concurrent.events().sumOf { it.deltaMg } == 208_000L)
    listOf(byteArrayOf(), byteArrayOf(1, 2, 3), storage.bytes!!.dropLast(1).toByteArray(), storage.bytes!! + 0).forEach { corrupt ->
        val damaged = MemoryStorage().apply { bytes = corrupt }
        rejects { BeanInventory(damaged) }
        check(damaged.bytes!!.contentEquals(corrupt))
    }
    val payload = storage.bytes!!.copyOfRange(0, storage.bytes!!.size - 32)
    fun damagedPayload(change: (ByteArray) -> Unit) {
        val changed = payload.clone().also(change)
        val bytes = changed + java.security.MessageDigest.getInstance("SHA-256").digest(changed)
        val damaged = MemoryStorage().apply { this.bytes = bytes }
        rejects { BeanInventory(damaged) }
        check(damaged.bytes!!.contentEquals(bytes))
    }
    damagedPayload { it[7] = 2 } // Unknown version, valid checksum
    damagedPayload { it[12] = 99 } // Unknown event kind, valid checksum
    damagedPayload { it[11] = 100 } // Event count inconsistent with document
    damagedPayload { bytes ->
        val stream = java.io.ByteArrayInputStream(bytes)
        val input = java.io.DataInputStream(stream)
        input.readInt(); input.readInt(); input.readInt(); input.readByte()
        input.readUTF(); input.readUTF(); input.readLong()
        val balanceOffset = bytes.size - stream.available()
        bytes[balanceOffset + 7] = (bytes[balanceOffset + 7].toInt() xor 1).toByte()
    } // Recorded balance disagrees with replay, valid checksum
    val overflow = BeanInventory(MemoryStorage())
    overflow.addBatch("max", batch().copy(initialMg = Long.MAX_VALUE))
    rejects { overflow.adjust("overflow", "batch", 1, "Correction") }
    check(overflow.batches().single().balanceMg == Long.MAX_VALUE)
    println("Inventory checks passed: persistence, idempotency, concurrency, adjustments, write failure, corruption, overflow")
}
