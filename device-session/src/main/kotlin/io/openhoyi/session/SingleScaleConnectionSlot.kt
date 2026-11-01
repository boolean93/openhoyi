package io.openhoyi.session

import io.openhoyi.protocol.ScaleCapabilities

/** Explicit address + protocol selection; never infer a protocol from a device name. */
data class ScaleSelection(val address:String,val protocolId:String)
object ScaleSelectionPolicy {
    fun adapter(protocolId:String):ScaleProtocolAdapter? =
        (ScaleProtocolRegistry.connectable + ScaleProtocolRegistry.readOnlyCandidates).singleOrNull { it.id==protocolId }
    fun remembered(address:String?,protocolId:String?):ScaleSelection? {
        if(address==null || !Regex("([0-9A-F]{2}:){5}[0-9A-F]{2}").matches(address))return null
        val id=protocolId ?: BookooScaleProtocolAdapter.id
        return if(adapter(id)!=null)ScaleSelection(address,id) else null
    }
}

/** Revoke callbacks before closing. An uncertain close permanently blocks replacement. */
class SingleScaleConnectionSlot<T:AutoCloseable>:AutoCloseable {
    data class Lease<T>(val selection:ScaleSelection,val ticket:Long,val owner:T)
    var current:Lease<T>?=null;private set
    var blocked=false;private set
    private var serial=0L
    private var transitioning=false
    private var closed=false
    private var stranded:T?=null
    fun owns(ticket:Long):Boolean=!closed && !transitioning && current?.ticket==ticket
    fun replace(selection:ScaleSelection,create:(Long)->T):Lease<T> {
        check(!closed && !blocked && !transitioning){"scale owner replacement unavailable"}
        transitioning=true
        val old=current;current=null
        try {
            try {old?.owner?.close()} catch(error:Exception) {
                stranded=old?.owner;blocked=true;throw error
            }
            check(!closed){"scale slot closed during replacement"}
            val ticket=++serial
            val owner=create(ticket)
            if(closed) {
                try {owner.close()} catch(error:Exception) {stranded=owner;blocked=true;throw error}
                error("scale slot closed during creation")
            }
            return Lease(selection,ticket,owner).also {current=it}
        } finally {transitioning=false}
    }
    override fun close() {
        closed=true
        val owner=current?.owner ?: stranded
        current=null;stranded=null
        try {owner?.close()} catch(error:Exception) {stranded=owner;blocked=true;throw error}
    }
}

/** Keep the extraction controller stable while delegating to the current single owner. */
class DynamicScaleControl(private val current:()->ScaleControl?,private val unresolved:()->Boolean):ScaleControl {
    override val ready get()=current()?.ready==true
    override val startAllowed get()=!unresolved() && (current()?.startAllowed ?: true)
    override val capabilities get()=current()?.capabilities ?: ScaleCapabilities(weight=false)
    override fun tare(beforeDispatch:()->Boolean,done:(OperationResult)->Unit) {
        val owner=current()
        if(owner==null){done(OperationResult.Failed("scale not ready"));return}
        owner.tare({current()===owner && beforeDispatch() && current()===owner}) {result->
            done(if(current()===owner)result else OperationResult.Unknown("scale owner changed during tare"))
        }
    }
}
