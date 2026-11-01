# Bean inventory core

Pure Kotlin/JVM (Java 17), no Android or device dependencies. Quantities are integer **mg** (`Long`). UI owns grams parsing/formatting.

## API

```kotlin
val inventory = BeanInventory(storage)
val batch = BeanBatch(
    id = "batch-1", bean = Bean("bean-1", "Ethiopia"), initialMg = 250_000,
    purchasedOn = LocalDate.of(2026, 10, 9), roastedOn = null, openedOn = null,
)
inventory.addBatch("add-batch-1", batch)
inventory.consume("dose-shot-1", "batch-1", 18_000)
inventory.adjust("correction-1", "batch-1", -2_000, "Measured loss")
val batches: List<BatchBalance> = inventory.batches()
val ledger: List<InventoryEvent> = inventory.events()
```

`BeanBatch` includes immutable bean identity/name and optional `LocalDate` purchase/roast/open dates. `BatchBalance` contains `batch` and `balanceMg`. Ledger results contain event ID, batch ID, kind, signed delta, resulting balance and optional adjustment reason. Ledger order is commit order. Query results are snapshots.

All mutation IDs share one namespace. Exact same ID + parameters returns its original event result, even after restart or later mutations. Reusing an ID with different parameters throws `IllegalArgumentException`. Initial stock/consumption must be positive; adjustments must be nonzero with a nonblank reason. Negative inventory and arithmetic overflow are rejected. A batch ID cannot be added twice, and one bean ID cannot name different beans.

## Storage contract

Implement `InventoryStorage.read(): ByteArray?` (`null` only when no store exists) and `write(bytes: ByteArray)` in the platform adapter. **Write must atomically replace the full document, or throw without replacing the old document.** Use an atomic file/transaction, not a truncate-then-write file operation. Keep exactly one live `BeanInventory` writer per storage namespace; synchronized access protects threads on that instance, not multiple processes/instances. Reader/adapter exceptions propagate.

Persistence has a versioned binary header, complete event inputs and recorded balances, plus SHA-256 checksum. Loading validates integrity and replays every event. Unknown versions, damaged or inconsistent records reject construction with `InventoryCorruptionException`; nothing is cleared or rewritten. A failed save does not update memory or reserve the event ID. Data has no account/cloud/automatic device behavior. A hardware start failure never automatically adds stock back; use an explicit adjustment event.

Run `./gradlew :bean-core:test` (or `:bean-core:verify`) for executable behavior checks.
