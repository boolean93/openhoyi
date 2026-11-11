package io.openhoyi.session

/** Immutable connection-local endpoint selection. Never probes by writing. */
internal object DiFluidGattBinding {
    fun resolve(adapter:DiFluidProtocolAdapter, discovered:List<CharacteristicInfo>):ScaleProtocolAdapter? {
        val suffix="-0000-1000-8000-00805f9b34fb"
        val services=setOf("000000ee$suffix","000000dd$suffix")
        val aa01="0000aa01$suffix"
        val aa02="0000aa02$suffix"
        val known=discovered.filter {it.endpoint.service in services}
        // Android addresses characteristics by UUID, not instance. Check before properties.
        if(known.groupBy {it.endpoint}.any {it.value.size!=1})return null
        val candidates=services.mapNotNull {service ->
            val chars=known.filter {it.endpoint.service==service}
            val notify=chars.singleOrNull {it.endpoint.characteristic==aa01 && (it.notify||it.indicate)}
                ?: return@mapNotNull null
            val write=chars.singleOrNull {it.endpoint.characteristic==aa02 && (it.write||it.writeWithoutResponse)}
                ?: chars.singleOrNull {it.endpoint.characteristic==aa01 && (it.write||it.writeWithoutResponse)}
                ?: return@mapNotNull null
            object:ScaleProtocolAdapter by adapter {
                override val writeEndpoint=write.endpoint
                override val notifyEndpoint=notify.endpoint
                override fun permitsWrite(operation:GattOperation.Write)=operation.endpoint==writeEndpoint &&
                    adapter.permitsWrite(GattOperation.Write(adapter.writeEndpoint,operation.bytes,operation.withResponse))
            }
        }
        return candidates.singleOrNull()
    }
}
