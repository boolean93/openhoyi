package io.openhoyi.session

/** Every DeviceSession write crosses this boundary immediately before its driver is invoked.
 * A rejected frame returns false without invoking transport. Non-write lifecycle operations and
 * accepted writes retain their original identity, tokens, response mode and completion behavior. */
class GuardedGattDriver(private val role:DeviceRole,private val delegate:GattDriver):GattDriver {
    override fun execute(generation:Long,token:Long,operation:GattOperation):Boolean =
        if(operation is GattOperation.Write && !OutboundWritePolicy.permits(role,operation))false
        else delegate.execute(generation,token,operation)
    override fun close(generation:Long)=delegate.close(generation)
}
