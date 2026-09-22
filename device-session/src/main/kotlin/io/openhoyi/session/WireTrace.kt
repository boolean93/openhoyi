package io.openhoyi.session

/** Immutable, already redacted transport observation. Never contains device addresses or exceptions. */
data class WireTrace(
    val kind:String,
    val generation:Long,
    val token:Long?=null,
    val endpoint:String?=null,
    val hex:String?=null,
    val size:Int?=null,
    val detail:String?=null,
)

fun wireTrace(kind:String,generation:Long,token:Long?=null,endpoint:Endpoint?=null,
    bytes:ByteArray?=null,detail:String?=null):WireTrace {
    val secret=endpoint?.let {
        it.service.equals(KnownGatt.coffeeWrite.service,true) &&
            it.characteristic.equals(KnownGatt.coffeeWrite.characteristic,true)
    }==true && bytes?.firstOrNull()?.toInt()?.and(0xff) in listOf(1,11)
    val hex=bytes?.let {
        if(secret) "[REDACTED]" else buildString {
            val digits="0123456789abcdef"
            for(index in 0 until minOf(it.size,512)) {
                val value=it[index].toInt() and 0xff
                append(digits[value ushr 4]);append(digits[value and 15])
            }
            if(it.size>512)append("[TRUNCATED]")
        }
    }
    return WireTrace(kind,generation,token,endpoint?.let { "${it.service}/${it.characteristic}" },hex,bytes?.size,detail)
}

/** Diagnostics must never change protocol execution, even when a host observer throws. */
fun emitWireTrace(observer:(WireTrace)->Unit,event:WireTrace) {
    try {observer(event)} catch(_:Throwable) { }
}
