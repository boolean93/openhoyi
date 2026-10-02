package io.openhoyi.mobile
import org.junit.Assert.*
import org.junit.Test
class BrewFeedbackDeliveryTest {
    private fun result(id:String)=BrewFeedbackResult(id,30,0,100,400,1.5,BrewFeedbackClips.Level.LOW_FLOW)
    @Test fun oneCupHasOneClaimAcrossPageOwnersAndRepublishing() {
        val delivery=BrewFeedbackDelivery(); val cup=result("cup")
        delivery.publish(cup,true); assertEquals(cup,delivery.claim(true)); assertNull(delivery.claim(true))
        delivery.publish(cup,true); assertNull(delivery.claim(true))
    }
    @Test fun disabledCupIsNeverBackfilledWhenEnabledLater() {
        val delivery=BrewFeedbackDelivery(); val cup=result("cup")
        delivery.publish(cup,false); assertNull(delivery.claim(true))
        delivery.publish(cup,true); assertNull(delivery.claim(true))
        delivery.publish(result("next"),true); assertNotNull(delivery.claim(true))
    }
    @Test fun clearOrDisableDropsPendingCupWithoutResurrectingIt() {
        val delivery=BrewFeedbackDelivery(); val cup=result("cup")
        delivery.publish(cup,true); delivery.clear(); delivery.publish(cup,true); assertNull(delivery.claim(true))
        delivery.publish(result("next"),true); assertNull(delivery.claim(false)); assertNull(delivery.claim(true))
    }
    @Test fun newerCupReplacesPendingPreviousCup() {
        val delivery=BrewFeedbackDelivery(); delivery.publish(result("old"),true)
        delivery.publish(result("new"),true); assertEquals("new",delivery.claim(true)!!.shotId)
    }
}
