package io.openhoyi.session

enum class CoffeeDeliveryKind {
    AUTHENTICATE, INITIAL_SETTINGS_SYNC, START, STOP, SETTING, BOILER, CUP_RESET, SCHEDULE_FIRST, SCHEDULE_SECOND,
    SLEEP, PREHEAT, PREHEAT_CANCEL, STORED_RECIPE, SETTINGS_REFRESH,
}

/** Transport evidence only. TRANSPORT_CALLBACK_OK never means that the machine applied a command. */
enum class CoffeeDeliveryPhase { QUEUED, DRIVER_HANDOFF, TRANSPORT_CALLBACK_OK, NOT_SENT, OUTCOME_UNKNOWN }

data class CoffeeDelivery(
    val generation: Long,
    val operationId: Long,
    val kind: CoffeeDeliveryKind,
    val phase: CoffeeDeliveryPhase,
)

/** Owner-thread local projection. No observers, payload, callbacks, command replay, or disk IO. */
internal class CoffeeDeliveryProjection {
    private var generation = -1L
    private val latest = mutableMapOf<CoffeeDeliveryKind, CoffeeDelivery>()
    fun record(delivery: CoffeeDelivery) {
        if (delivery.generation < generation) return
        if (delivery.generation != generation) {
            generation = delivery.generation
            latest.clear()
        }
        if (delivery.operationId >= (latest[delivery.kind]?.operationId ?: -1))
            latest[delivery.kind] = delivery
    }
    fun read(generation: Long): List<CoffeeDelivery> =
        if (this.generation == generation) latest.values.sortedBy { it.operationId } else emptyList()
}
