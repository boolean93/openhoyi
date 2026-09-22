package io.openhoyi.bluetooth

import android.Manifest
import android.bluetooth.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import io.openhoyi.session.*
import java.util.UUID

/** One instance per device. Uses the main looper as the session owner, never an Activity reference. */
class AndroidGattDriver(context:Context, private val events:Events, private val trace:(WireTrace)->Unit={}):GattDriver {
    interface Events {
        fun complete(generation:Long,token:Long,result:OperationResult)
        fun disconnected(generation:Long,reason:String)
        fun notification(generation:Long,endpoint:Endpoint,bytes:ByteArray)
    }
    private val context=context.applicationContext
    private val handler=Handler(Looper.getMainLooper())
    private var gatt:BluetoothGatt?=null
    private var generation=0L
    private data class Pending(val token:Long,val op:GattOperation)
    private var pending:Pending?=null
    private fun owner()=check(Looper.myLooper()==Looper.getMainLooper()){"BLE access must use main looper"}
    private fun allowed()=Build.VERSION.SDK_INT<31||context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED
    private fun characteristic(ep:Endpoint)=gatt?.getService(UUID.fromString(ep.service))?.getCharacteristic(UUID.fromString(ep.characteristic))
    private fun record(kind:String,gen:Long,token:Long?=null,op:GattOperation?=null,detail:String?=null) {
        val ep=when(op){is GattOperation.Write->op.endpoint;is GattOperation.Subscribe->op.endpoint;else->null}
        val operation=when(op){is GattOperation.Connect->"connect";GattOperation.Discover->"discover";is GattOperation.Subscribe->"subscribe";is GattOperation.Write->"write";null->null}
        val description=listOfNotNull(operation,detail).joinToString(" ").ifEmpty { null }
        emitWireTrace(trace,wireTrace(kind,gen,token,ep,(op as? GattOperation.Write)?.bytes,description))
    }
    private fun notification(gen:Long,ep:Endpoint,bytes:ByteArray) {
        emitWireTrace(trace,wireTrace("notification",gen,endpoint=ep,bytes=bytes))
        events.notification(gen,ep,bytes)
    }
    override fun execute(generation:Long,token:Long,operation:GattOperation):Boolean {
        owner();record("request",generation,token,operation)
        if(!allowed()||pending!=null){record("rejected",generation,token,operation,"permission_or_busy");return false}
        if(operation !is GattOperation.Connect && (this.generation!=generation||gatt==null)){
            record("rejected",generation,token,operation,"inactive_generation");return false
        }
        this.generation=generation;pending=Pending(token,operation)
        val accepted=try {
            when(operation) {
                is GattOperation.Connect -> {
                    val adapter=(context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
                    if(adapter==null||!adapter.isEnabled)false else {
                        gatt=adapter.getRemoteDevice(operation.address).connectGatt(context,false,callback(generation),BluetoothDevice.TRANSPORT_LE)
                        gatt!=null
                    }
                }
                GattOperation.Discover -> gatt!!.discoverServices()
                is GattOperation.Subscribe -> {
                    val ch=characteristic(operation.endpoint)
                    val descriptor=ch?.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                    if(ch==null||descriptor==null||!gatt!!.setCharacteristicNotification(ch,true))false else {
                        val bytes=if(operation.indication)BluetoothGattDescriptor.ENABLE_INDICATION_VALUE else BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        if(Build.VERSION.SDK_INT>=33)gatt!!.writeDescriptor(descriptor,bytes)==BluetoothStatusCodes.SUCCESS
                        else { @Suppress("DEPRECATION") descriptor.value=bytes; @Suppress("DEPRECATION") gatt!!.writeDescriptor(descriptor) }
                    }
                }
                is GattOperation.Write -> {
                    val ch=characteristic(operation.endpoint)
                    // First-release controls fit default ATT payload. Larger protocols need explicit framing/MTU support.
                    if(ch==null||operation.bytes.size>20)false else {
                        val type=if(operation.withResponse)BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                        if(Build.VERSION.SDK_INT>=33)gatt!!.writeCharacteristic(ch,operation.bytes,type)==BluetoothStatusCodes.SUCCESS
                        else {ch.writeType=type; @Suppress("DEPRECATION") ch.value=operation.bytes; @Suppress("DEPRECATION") gatt!!.writeCharacteristic(ch)}
                    }
                }
            }
        }catch(_:SecurityException){false}catch(_:IllegalArgumentException){false}
        if(!accepted)pending=null
        record(if(accepted)"accepted" else "rejected",generation,token,operation)
        return accepted
    }
    override fun close(generation:Long) {
        owner();if(this.generation!=generation)return
        record("close",generation,pending?.token,pending?.op)
        val old=gatt;gatt=null;pending=null
        try {if(allowed())old?.disconnect()} catch(_:SecurityException) {} finally {old?.close()}
    }
    private fun endpoint(ch:BluetoothGattCharacteristic)=Endpoint(ch.service.uuid.toString(),ch.uuid.toString())
    private fun callback(gen:Long)=object:BluetoothGattCallback() {
        private fun dispatch(g:BluetoothGatt,block:()->Unit){handler.post {if(g===gatt&&gen==generation)block()}}
        private fun finish(status:Int,extra:List<CharacteristicInfo> = emptyList()) {
            val p=pending?:return;pending=null
            record("completed",gen,p.token,p.op,"status=$status")
            events.complete(gen,p.token,if(status==BluetoothGatt.GATT_SUCCESS)OperationResult.Success(extra)else OperationResult.Failed("GATT status $status"))
        }
        override fun onConnectionStateChange(g:BluetoothGatt,status:Int,newState:Int)=dispatch(g){
            if(status!=BluetoothGatt.GATT_SUCCESS||newState==BluetoothProfile.STATE_DISCONNECTED){
                record("disconnected",gen,pending?.token,pending?.op,"status=$status")
                events.disconnected(gen,"GATT disconnected: $status")
            }
            else if(newState==BluetoothProfile.STATE_CONNECTED&&pending?.op is GattOperation.Connect)finish(status)
        }
        override fun onServicesDiscovered(g:BluetoothGatt,status:Int)=dispatch(g){
            if(pending?.op!=GattOperation.Discover)return@dispatch
            val info=if(status==0)g.services.flatMap { s->s.characteristics.map { c->
                val p=c.properties
                CharacteristicInfo(endpoint(c),p and BluetoothGattCharacteristic.PROPERTY_WRITE!=0,p and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE!=0,p and BluetoothGattCharacteristic.PROPERTY_NOTIFY!=0,p and BluetoothGattCharacteristic.PROPERTY_INDICATE!=0)
            }}else emptyList()
            finish(status,info)
        }
        override fun onDescriptorWrite(g:BluetoothGatt,d:BluetoothGattDescriptor,status:Int)=dispatch(g){
            val op=pending?.op as? GattOperation.Subscribe?:return@dispatch
            if(endpoint(d.characteristic)==op.endpoint && d.uuid==UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))finish(status)
        }
        override fun onCharacteristicWrite(g:BluetoothGatt,ch:BluetoothGattCharacteristic,status:Int)=dispatch(g){
            val op=pending?.op as? GattOperation.Write?:return@dispatch
            if(endpoint(ch)==op.endpoint)finish(status)
        }
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g:BluetoothGatt,ch:BluetoothGattCharacteristic){
            if(Build.VERSION.SDK_INT<33){val bytes=ch.value?.copyOf()?:return;val ep=endpoint(ch);dispatch(g){notification(gen,ep,bytes)}}
        }
        override fun onCharacteristicChanged(g:BluetoothGatt,ch:BluetoothGattCharacteristic,value:ByteArray){
            val bytes=value.copyOf();val ep=endpoint(ch);dispatch(g){notification(gen,ep,bytes)}
        }
    }
}
