package io.openhoyi.mobile

import io.openhoyi.protocol.ByteFrame
import io.openhoyi.protocol.ExtractionTelemetry
import io.openhoyi.protocol.StartParameters
import io.openhoyi.session.DeviceState
import io.openhoyi.session.ExtractionState
import io.openhoyi.session.OperationResult
import org.junit.Assert.*
import org.junit.Test

class MockExtractionScenarioTest {
    private class Fixture(mock: Boolean = true) {
        val owner = Any()
        var clockOwner: Any? = owner
        var main = true
        var hubAbsent = true
        var now = 1000L
        var state = DeviceState.READY
        val frames = mutableListOf<ExtractionTelemetry>()
        val scenario = MockExtractionScenario(mock, { main }, { clockOwner === it }, { hubAbsent }, { now },
            { frame -> frames += frame as ExtractionTelemetry }, { state = it })
        val parameters = StartParameters(false,false,1,7,93,120,false,0,0,0,0,0,100,1,100,100,100)
        fun start(): Long {
            assertTrue(scenario.start(owner,parameters,0,0) { true })
            assertEquals(ExtractionState.STARTING,scenario.state)
            return requireNotNull(scenario.pendingStartId)
        }
        fun running() {
            val id=start()
            scenario.completeStart(owner,id,OperationResult.Success())
            assertEquals(ExtractionState.RUNNING,scenario.state)
        }
        fun frame(elapsed: Int, water: Int) = ExtractionTelemetry(8,elapsed,90,water,9300,100,64,0,ByteFrame(byteArrayOf()))
    }
    @Test fun delayedStartAndStopAreActualControllerStates() {
        val f=Fixture();f.scenario.install(f.owner)
        val start=f.start()
        repeat(3) { f.now+=100;f.scenario.tick(f.owner) }
        assertEquals(ExtractionState.STARTING,f.scenario.state)
        assertEquals(0L,f.scenario.offeredFrameSerial)
        assertTrue(f.frames.isEmpty())
        f.scenario.completeStart(f.owner,start,OperationResult.Success())
        assertEquals(ExtractionState.RUNNING,f.scenario.state)
        f.scenario.stop(f.owner)
        assertEquals(ExtractionState.STOP_REQUESTED,f.scenario.state)
        val stop=requireNotNull(f.scenario.pendingStopId)
        f.scenario.completeStop(f.owner,stop,OperationResult.Success())
        assertEquals(ExtractionState.STOP_REQUESTED,f.scenario.state)
        assertThrows(IllegalStateException::class.java) { f.scenario.completeStop(f.owner,stop,OperationResult.Success()) }
    }
    @Test fun disconnectedRunningBecomesUnknownWithoutInventingSamples() {
        val f=Fixture();f.scenario.install(f.owner);f.running()
        f.scenario.coffeeState(f.owner,DeviceState.DISCONNECTED)
        assertEquals(DeviceState.DISCONNECTED,f.state)
        assertEquals(ExtractionState.OUTCOME_UNKNOWN,f.scenario.state)
        assertEquals(0L,f.scenario.offeredFrameSerial)
        assertTrue(f.frames.isEmpty())
    }
    @Test fun onlyExplicitOrderedFramesAreDeliveredAndWaterCanReset() {
        val f=Fixture();f.scenario.install(f.owner);f.running()
        listOf(10 to 20,11 to 25,12 to 3).forEach { (elapsed,water) ->
            f.now+=1000
            f.scenario.offerCoffeeFrame(f.owner,f.frame(elapsed,water))
        }
        assertEquals(listOf(20,25,3),f.frames.map {it.totalWaterTenthsMl})
        assertEquals(3L,f.scenario.offeredFrameSerial)
        assertThrows(IllegalArgumentException::class.java) { f.scenario.offerCoffeeFrame(f.owner,f.frame(12,3)) }
        f.now+=1000;f.scenario.tick(f.owner)
        assertEquals(3,f.frames.size)
    }
    @Test fun manualFramesDoNotMoveTheAppShotController() {
        val f=Fixture();f.scenario.install(f.owner)
        f.scenario.offerCoffeeFrame(f.owner,f.frame(1,5))
        f.now+=1000;f.scenario.offerCoffeeFrame(f.owner,f.frame(2,10))
        assertEquals(ExtractionState.IDLE,f.scenario.state)
        assertNull(f.scenario.pendingStartId)
        assertEquals(2L,f.scenario.offeredFrameSerial)
        assertEquals(2,f.frames.size)
    }
    @Test fun ownerGuardsStaleCompletionAndDefaultPathAreClosed() {
        val f=Fixture()
        assertThrows(IllegalStateException::class.java) { f.scenario.start(f.owner,f.parameters,0,0) { true } }
        f.main=false
        assertThrows(IllegalStateException::class.java) { f.scenario.install(f.owner) }
        f.main=true;f.clockOwner=Any()
        assertThrows(IllegalStateException::class.java) { f.scenario.install(f.owner) }
        f.clockOwner=f.owner;f.hubAbsent=false
        assertThrows(IllegalStateException::class.java) { f.scenario.install(f.owner) }
        f.hubAbsent=true;f.scenario.install(f.owner)
        assertThrows(IllegalStateException::class.java) { f.scenario.install(f.owner) }
        val first=f.start()
        assertThrows(IllegalStateException::class.java) { f.scenario.completeStart(Any(),first,OperationResult.Success()) }
        assertThrows(IllegalStateException::class.java) { f.scenario.completeStart(f.owner,first+1,OperationResult.Success()) }
        f.scenario.coffeeState(f.owner,DeviceState.DISCONNECTED)
        assertThrows(IllegalStateException::class.java) { f.scenario.completeStart(f.owner,first,OperationResult.Success()) }
        f.scenario.clear(f.owner)
        assertThrows(IllegalStateException::class.java) { f.scenario.offerCoffeeFrame(f.owner,f.frame(1,1)) }
        val real=Fixture(mock=false)
        assertThrows(IllegalStateException::class.java) { real.scenario.install(real.owner) }
    }
}
