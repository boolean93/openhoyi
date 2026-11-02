package io.openhoyi.session

import org.junit.Assert.*
import org.junit.Test

class SingleScaleConnectionSlotTest {
    private val bookoo=ScaleSelection("AA:BB:CC:DD:EE:01",BookooScaleProtocolAdapter.id)
    private val readOnly=bookoo.copy(protocolId=FelicitaReadOnlyScaleProtocolAdapter.id)
    private class Owner(val closing:()->Unit={}):AutoCloseable {override fun close()=closing()}
    @Test fun closesAndRevokesOldOwnerBeforeCreatingDifferentProtocolAtSameAddress() {
        val slot=SingleScaleConnectionSlot<Owner>();val order=mutableListOf<String>();var ticket=0L
        slot.replace(bookoo) {token->ticket=token;Owner {assertFalse(slot.owns(ticket));order+="close-old"}}
        val next=slot.replace(readOnly) {order+="create-new";Owner()}
        assertEquals(listOf("close-old","create-new"),order)
        assertFalse(slot.owns(ticket));assertTrue(slot.owns(next.ticket));assertEquals(readOnly,slot.current!!.selection)
    }
    @Test fun closeFailureBlocksAllNewOwnersAndRetainsOldCallbackRevocation() {
        val slot=SingleScaleConnectionSlot<Owner>();var creates=0
        val old=slot.replace(bookoo) {Owner {error("close outcome not confirmed")}}
        assertThrows(IllegalStateException::class.java) {slot.replace(readOnly) {creates++;Owner()}}
        assertTrue(slot.blocked);assertNull(slot.current);assertFalse(slot.owns(old.ticket));assertEquals(0,creates)
        assertThrows(IllegalStateException::class.java) {slot.replace(bookoo) {creates++;Owner()}}
        assertEquals(0,creates)
    }
    @Test fun reentrantCloseCannotCreateASecondOwnerWhileTransitionInProgress() {
        val slot=SingleScaleConnectionSlot<Owner>();var nestedCreates=0
        slot.replace(bookoo) {Owner {
            assertThrows(IllegalStateException::class.java) {slot.replace(bookoo) {nestedCreates++;Owner()}}
        }}
        val next=slot.replace(readOnly) {Owner()}
        assertEquals(0,nestedCreates);assertTrue(slot.owns(next.ticket))
    }
    @Test fun oldCallbacksStayInvalidAfterFinalClose() {
        val slot=SingleScaleConnectionSlot<Owner>();val first=slot.replace(bookoo) {Owner()}
        slot.close();assertNull(slot.current);assertFalse(slot.owns(first.ticket))
    }
    @Test fun legacyAddressMigratesOnlyWhenProtocolKeyAbsent() {
        assertEquals(bookoo,ScaleSelectionPolicy.remembered(bookoo.address,null))
        assertNull(ScaleSelectionPolicy.remembered(bookoo.address,"unknown-protocol"))
        assertNull(ScaleSelectionPolicy.remembered(bookoo.address,""))
        assertNull(ScaleSelectionPolicy.remembered("invalid",null))
        assertNull(ScaleSelectionPolicy.remembered(null,BookooScaleProtocolAdapter.id))
        assertEquals(readOnly,ScaleSelectionPolicy.remembered(bookoo.address,readOnly.protocolId))
        assertSame(BookooScaleProtocolAdapter,ScaleSelectionPolicy.adapter(bookoo.protocolId))
        assertSame(FelicitaReadOnlyScaleProtocolAdapter,ScaleSelectionPolicy.adapter(readOnly.protocolId))
        assertNull(ScaleSelectionPolicy.adapter("felicita"))
        assertNull(ScaleSelectionPolicy.adapter("unknown"))
    }
    @Test fun dynamicControlUsesCurrentCapabilitiesButKeepsUnresolvedTareAcrossOwners() {
        var owner:ScaleControl?=null;var unresolved=false
        val proxy=DynamicScaleControl({owner},{unresolved})
        assertFalse(proxy.ready);assertFalse(proxy.capabilities.weight);assertTrue(proxy.startAllowed)
        owner=object:ScaleControl {
            override val ready=true
            override val startAllowed=true
            override val capabilities=FelicitaReadOnlyScaleProtocolAdapter.capabilities
            override fun tare(beforeDispatch:()->Boolean,done:(OperationResult)->Unit)=done(OperationResult.Failed("read only"))
        }
        assertTrue(proxy.ready);assertFalse(proxy.capabilities.tare);assertFalse(proxy.capabilities.validatedWeightControl)
        unresolved=true;owner=null;assertFalse(proxy.startAllowed)
    }
    @Test fun dynamicTareRejectsReentryAndNeverReturnsOldOwnerSuccess() {
        var owner:ScaleControl?=null;var guard:(()->Boolean)?=null;var completion:((OperationResult)->Unit)?=null
        val proxy=DynamicScaleControl({owner},{false})
        owner=object:ScaleControl {
            override val ready=true
            override val startAllowed=true
            override fun tare(beforeDispatch:()->Boolean,done:(OperationResult)->Unit){guard=beforeDispatch;completion=done}
        }
        var result:OperationResult?=null
        proxy.tare({owner=null;true}){result=it}
        assertFalse(guard!!())
        completion!!(OperationResult.Success())
        assertTrue(result is OperationResult.Unknown)
    }

    @Test fun finalCloseDuringFactoryCannotPublishOrLeakNewOwner() {
        val slot=SingleScaleConnectionSlot<Owner>();var closes=0
        assertThrows(IllegalStateException::class.java) {
            slot.replace(bookoo) {slot.close();Owner {closes++}}
        }
        assertNull(slot.current);assertEquals(1,closes)
    }

}
