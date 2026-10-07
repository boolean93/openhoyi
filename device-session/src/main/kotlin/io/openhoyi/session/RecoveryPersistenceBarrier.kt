package io.openhoyi.session

/** Keeps the complete intent authoritative when a record commit clears only cached preferences. */
class RecoveryPersistenceBarrier<T>(private val record:RecordStorage<T>,private val marker:Marker<T>,
    private val pending:(T)->Boolean) {
    interface RecordStorage<T> { fun read():T; fun write(value:T):Boolean }
    interface Marker<T> { fun exists():Boolean; fun read():T?; fun write(value:T):Boolean; fun clear():Boolean }
    fun read():T=marker.read() ?: record.read()
    fun write(value:T):Boolean=runCatching {
        val isPending=pending(value)
        if(isPending) {
            if(!marker.write(value))return@runCatching false
        } else if(!marker.exists()) {
            val previous=record.read()
            if(pending(previous) && !marker.write(previous))return@runCatching false
        }
        record.write(value) && (isPending || marker.clear())
    }.getOrDefault(false)
}
