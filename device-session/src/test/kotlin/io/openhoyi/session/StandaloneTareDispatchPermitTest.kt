package io.openhoyi.session

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class StandaloneTareDispatchPermitTest(private val name:String,private val original:String?,
    private val current:String?,private val hub:Boolean,private val ready:Boolean,
    private val manual:Boolean,private val shot:ExtractionState,private val expected:Boolean) {
    @Test fun authorizeManualTareBeforeDispatch() {
        assertEquals(name,expected,StandaloneTareDispatchPermit.allows(original,current,hub,ready,manual,shot))
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name="{0}") fun cases():List<Array<Any?>> {
            fun c(name:String,original:String?="aa:bb",current:String?="AA:BB",hub:Boolean=true,
                ready:Boolean=true,manual:Boolean=false,shot:ExtractionState=ExtractionState.IDLE,expected:Boolean=false)=
                arrayOf<Any?>(name,original,current,hub,ready,manual,shot,expected)
            return listOf(c("same address case insensitive",expected=true),c("missing original",original=null),
                c("missing current",current=null),c("empty original",original="",current=""),
                c("other scale",current="CC:DD"),c("other hub",hub=false),c("not ready",ready=false),
                c("manual shot",manual=true))+ExtractionState.entries.map {
                c("app state $it",shot=it,expected=DeviceConnectionGate.mayChangeScale(it))
            }
        }
    }
}
