package io.openhoyi.session

/** A durable marker prevents a failed record commit from exposing a cleared cached value. */
class TarePersistenceBarrier(private val record:StandaloneTare.Storage,private val marker:Marker):StandaloneTare.Storage {
    interface Marker { fun exists():Boolean; fun arm():Boolean; fun clear():Boolean }
    override fun read()=runCatching { marker.exists() || record.read() }.getOrDefault(true)
    override fun write(pending:Boolean)=runCatching {
        marker.arm() && record.write(pending) && (pending || marker.clear())
    }.getOrDefault(false)
}
