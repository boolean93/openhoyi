package io.openhoyi.bluetooth

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import io.openhoyi.protocol.*
import io.openhoyi.session.*

/** Host owns lifecycle (e.g. service). Closing a screen must not close this object during extraction. */
class AndroidDevice(context:Context,role:DeviceRole,stateChanged:(DeviceState)->Unit={},
    coffeeFrame:(HoyiMessage,Long)->Unit={_,_->},weightFrame:(BookooSample,Long)->Unit={_,_->},diagnostic:(String)->Unit={},trace:(WireTrace)->Unit={}) : AutoCloseable {
    init {check(Looper.myLooper()==Looper.getMainLooper())}
    private val handler=Handler(Looper.getMainLooper())
    private var closed=false
    private val driver:AndroidGattDriver=AndroidGattDriver(context,object:AndroidGattDriver.Events {
        override fun complete(generation:Long,token:Long,result:OperationResult)=session.onComplete(generation,token,result)
        override fun disconnected(generation:Long,reason:String)=session.onDisconnected(generation,reason)
        override fun notification(generation:Long,endpoint:Endpoint,bytes:ByteArray)=session.onNotification(generation,endpoint,bytes)
    },trace)
    val session:DeviceSession=DeviceSession(role,driver,{SystemClock.elapsedRealtime()},stateChanged,coffeeFrame,weightFrame,diagnostic)
    private val ticker=object:Runnable {override fun run(){if(!closed){session.tick();handler.postDelayed(this,50)}}}
    init {handler.post(ticker)}
    override fun close(){check(Looper.myLooper()==Looper.getMainLooper());closed=true;handler.removeCallbacks(ticker);session.disconnect()}
}
