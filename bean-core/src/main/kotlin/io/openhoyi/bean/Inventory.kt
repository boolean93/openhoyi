package io.openhoyi.bean

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.time.LocalDate

/** One live inventory writer per store. write must atomically replace or leave the old bytes intact. */
interface InventoryStorage {
    fun read(): ByteArray?
    fun write(bytes: ByteArray)
}

data class Bean(val id: String, val name: String)
data class BeanBatch(
    val id: String,
    val bean: Bean,
    val initialMg: Long,
    val purchasedOn: LocalDate? = null,
    val roastedOn: LocalDate? = null,
    val openedOn: LocalDate? = null,
)
data class BatchBalance(val batch: BeanBatch, val balanceMg: Long)
enum class InventoryEventKind { ADD, CONSUME, ADJUST }
data class InventoryEvent(
    val eventId: String,
    val batchId: String,
    val deltaMg: Long,
    val balanceMg: Long,
    val kind: InventoryEventKind,
    val reason: String? = null,
)
class InventoryCorruptionException(cause: Exception) : IllegalStateException("Inventory data is damaged or unsupported", cause)

/** Persistence succeeds before any in-memory change. All public operations share this instance's lock. */
class BeanInventory(private val storage: InventoryStorage) {
    private data class Command(
        val eventId: String,
        val batchId: String,
        val kind: InventoryEventKind,
        val deltaMg: Long,
        val batch: BeanBatch? = null,
        val reason: String? = null,
    )
    private data class Entry(val command: Command, val result: InventoryEvent)
    private var balances = linkedMapOf<String, BatchBalance>()
    private var entries = linkedMapOf<String, Entry>()

    init {
        storage.read()?.let { bytes ->
            try {
                decode(bytes).forEach { entry ->
                    require(!entries.containsKey(entry.command.eventId)) { "Duplicate stored event" }
                    val next = apply(entry.command, balances)
                    require(next.second == entry.result) { "Inconsistent stored balance" }
                    balances = next.first
                    entries[entry.command.eventId] = entry
                }
            } catch (e: Exception) {
                throw InventoryCorruptionException(e)
            }
        }
    }

    @Synchronized
    fun addBatch(eventId: String, batch: BeanBatch): InventoryEvent = commit(
        Command(eventId, batch.id, InventoryEventKind.ADD, batch.initialMg, batch = batch),
    )

    @Synchronized
    fun consume(eventId: String, batchId: String, amountMg: Long): InventoryEvent {
        require(amountMg > 0) { "Consumption must be positive mg" }
        return commit(Command(eventId, batchId, InventoryEventKind.CONSUME, -amountMg))
    }

    @Synchronized
    fun adjust(eventId: String, batchId: String, deltaMg: Long, reason: String): InventoryEvent = commit(
        Command(eventId, batchId, InventoryEventKind.ADJUST, deltaMg, reason = reason),
    )

    @Synchronized
    fun batches(): List<BatchBalance> = balances.values.toList()

    @Synchronized
    fun events(): List<InventoryEvent> = entries.values.map { it.result }

    private fun commit(command: Command): InventoryEvent {
        entries[command.eventId]?.let {
            require(it.command == command) { "Event ID has different parameters" }
            return it.result
        }
        val (nextBalances, result) = apply(command, balances)
        val nextEntries = LinkedHashMap(entries).apply { put(command.eventId, Entry(command, result)) }
        storage.write(encode(nextEntries.values.toList()))
        balances = nextBalances
        entries = nextEntries
        return result
    }

    private fun apply(command: Command, current: Map<String, BatchBalance>): Pair<LinkedHashMap<String, BatchBalance>, InventoryEvent> {
        text(command.eventId, 256, "Event ID")
        text(command.batchId, 256, "Batch ID")
        val batch: BeanBatch
        val balance: Long
        when (command.kind) {
            InventoryEventKind.ADD -> {
                batch = requireNotNull(command.batch)
                require(batch.id == command.batchId && command.deltaMg == batch.initialMg && command.reason == null)
                text(batch.bean.id, 256, "Bean ID")
                text(batch.bean.name, 4096, "Bean name")
                require(batch.initialMg > 0) { "Initial stock must be positive mg" }
                require(!current.containsKey(batch.id)) { "Batch already exists" }
                require(current.values.none { it.batch.bean.id == batch.bean.id && it.batch.bean != batch.bean }) { "Bean ID has different name" }
                balance = batch.initialMg
            }
            InventoryEventKind.CONSUME, InventoryEventKind.ADJUST -> {
                require(command.batch == null)
                if (command.kind == InventoryEventKind.CONSUME) {
                    require(command.deltaMg < 0 && command.deltaMg != Long.MIN_VALUE && command.reason == null)
                } else {
                    require(command.deltaMg != 0L) { "Adjustment must be nonzero" }
                    text(requireNotNull(command.reason), 4096, "Adjustment reason")
                }
                val previous = requireNotNull(current[command.batchId]) { "Batch not found" }
                batch = previous.batch
                balance = try { Math.addExact(previous.balanceMg, command.deltaMg) }
                catch (e: ArithmeticException) { throw IllegalArgumentException("Stock overflow", e) }
                require(balance >= 0) { "Insufficient stock" }
            }
        }
        val next = LinkedHashMap(current).apply { put(batch.id, BatchBalance(batch, balance)) }
        return next to InventoryEvent(command.eventId, batch.id, command.deltaMg, balance, command.kind, command.reason)
    }

    private fun text(value: String, maxLength: Int, label: String) {
        require(value.isNotBlank() && value.length <= maxLength) { "$label must be nonblank and at most $maxLength characters" }
    }

    private fun encode(records: List<Entry>): ByteArray {
        val payload = ByteArrayOutputStream().also { stream ->
            DataOutputStream(stream).use { out ->
                out.writeInt(MAGIC)
                out.writeInt(VERSION)
                out.writeInt(records.size)
                records.forEach { entry ->
                    val command = entry.command
                    out.writeByte(command.kind.ordinal)
                    out.writeUTF(command.eventId)
                    out.writeUTF(command.batchId)
                    out.writeLong(command.deltaMg)
                    out.writeLong(entry.result.balanceMg)
                    when (command.kind) {
                        InventoryEventKind.ADD -> {
                            val batch = requireNotNull(command.batch)
                            out.writeUTF(batch.bean.id)
                            out.writeUTF(batch.bean.name)
                            out.date(batch.purchasedOn)
                            out.date(batch.roastedOn)
                            out.date(batch.openedOn)
                        }
                        InventoryEventKind.ADJUST -> out.writeUTF(requireNotNull(command.reason))
                        InventoryEventKind.CONSUME -> Unit
                    }
                }
            }
        }.toByteArray()
        return payload + hash(payload)
    }

    private fun decode(bytes: ByteArray): List<Entry> {
        require(bytes.size >= 44) { "Truncated inventory" }
        val payload = bytes.copyOfRange(0, bytes.size - 32)
        require(MessageDigest.isEqual(hash(payload), bytes.copyOfRange(bytes.size - 32, bytes.size))) { "Inventory checksum mismatch" }
        DataInputStream(ByteArrayInputStream(payload)).use { input ->
            require(input.readInt() == MAGIC && input.readInt() == VERSION) { "Unknown inventory format" }
            val count = input.readInt()
            require(count >= 0 && count <= payload.size / 23) { "Invalid event count" }
            val result = ArrayList<Entry>()
            repeat(count) {
                val kind = InventoryEventKind.entries.getOrNull(input.readUnsignedByte()) ?: error("Unknown event type")
                val eventId = input.readUTF()
                val batchId = input.readUTF()
                val delta = input.readLong()
                val balance = input.readLong()
                val batch = if (kind == InventoryEventKind.ADD) BeanBatch(
                    batchId, Bean(input.readUTF(), input.readUTF()), delta,
                    input.date(), input.date(), input.date(),
                ) else null
                val reason = if (kind == InventoryEventKind.ADJUST) input.readUTF() else null
                result += Entry(Command(eventId, batchId, kind, delta, batch, reason), InventoryEvent(eventId, batchId, delta, balance, kind, reason))
            }
            require(input.available() == 0) { "Trailing inventory data" }
            return result
        }
    }

    private fun DataOutputStream.date(value: LocalDate?) {
        writeByte(if (value == null) 0 else 1)
        value?.let { writeLong(it.toEpochDay()) }
    }
    private fun DataInputStream.date(): LocalDate? = when (readUnsignedByte()) {
        0 -> null
        1 -> LocalDate.ofEpochDay(readLong())
        else -> error("Invalid date marker")
    }
    private fun hash(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    private companion object {
        const val MAGIC = 0x484F5949
        const val VERSION = 1
    }
}
