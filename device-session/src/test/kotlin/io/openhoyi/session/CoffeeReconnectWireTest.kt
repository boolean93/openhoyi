package io.openhoyi.session

import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class CoffeeReconnectWireTest {
    private fun hex(value:String)=value.chunked(2).map {it.toInt(16).toByte()}.toByteArray()

    @Test fun sameMachineLinkRecoveryOnlyAuthenticatesAndPublishesFreshTelemetry() {
        val calls=mutableListOf<Triple<Long,Long,GattOperation>>()
        val session=DeviceSession(DeviceRole.COFFEE,object:GattDriver {
            override fun execute(generation:Long,token:Long,operation:GattOperation):Boolean {
                calls+=Triple(generation,token,operation);return true
            }
            override fun close(generation:Long) {}
        },{0L})
        val auth=CoffeeAuthentication(LocalDateTime.of(2026,10,26,12,0),"123456")
        val reconnect=CoffeeReconnectPolicy().apply { foreground(0) }
        var firstGeneration:Long?=null
        repeat(2) { attempt ->
            if(attempt==0)session.connect("AA:BB:CC:DD:EE:01",auth)
            else assertEquals(CoffeeReconnectPolicy.Dispatch.STARTED,
                reconnect.dispatch(100,true,true) { address ->
                    session.connect(address,auth);true
                })
            val generation=session.generation
            firstGeneration?.let {assertNotEquals(it,generation)}
            fun complete(result:OperationResult=OperationResult.Success()) {
                val(g,t,_)=calls.last();session.onComplete(g,t,result)
            }
            complete()
            complete(OperationResult.Success(listOf(
                CharacteristicInfo(KnownGatt.coffeeWrite,true,false,false,false),
                CharacteristicInfo(KnownGatt.coffeeNotify,false,false,true,false))))
            complete();complete()
            assertEquals(DeviceState.SYNCHRONIZING,session.state)
            session.onNotification(generation,KnownGatt.coffeeNotify,hex("830113FD5C007D0F350019006E"))
            assertEquals(DeviceState.READY,session.state)
            reconnect.rememberReady("AA:BB:CC:DD:EE:01",if(attempt==0)0 else 200)
            if(attempt==1) {
                session.onDisconnected(requireNotNull(firstGeneration),"stale old link callback")
                session.onNotification(requireNotNull(firstGeneration),KnownGatt.coffeeNotify,
                    hex("830113FD5C007D0F350019006E"))
                assertEquals(DeviceState.READY,session.state)
            }
            val writes=calls.filter {it.first==generation}.mapNotNull {(_,_,operation)->operation as? GattOperation.Write}
            assertEquals(1,writes.size)
            assertArrayEquals(auth.encode().frame.toByteArray(),writes.single().bytes)
            if(attempt==0) {
                firstGeneration=generation
                session.onDisconnected(generation,"unexpected link loss")
                assertEquals(DeviceState.FAILED,session.state)
                reconnect.connectionFailed(100)
            }
        }
    }
}
