package io.openhoyi.bluetooth

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import io.openhoyi.session.DeviceRole

data class DiscoveredDevice(val address:String,val advertisedName:String,val rssi:Int,val candidateRole:DeviceRole,
    val scaleProtocolId:String?=if(candidateRole==DeviceRole.BOOKOO)io.openhoyi.session.BookooScaleProtocolAdapter.id else null)
/** A single application/service-owned coordinator scans both device roles. Names are candidates, never protocol proof. */
class ScanCoordinator(context:Context):AutoCloseable {
    private val context=context.applicationContext
    private val handler=Handler(Looper.getMainLooper())
    private var generation=0L
    private var running:ScanCallback?=null
    enum class Owner { MANUAL, AUTOMATIC }
    private var scanOwner:Owner?=null
    private var completed:((String?)->Unit)?=null
    val automaticScanning:Boolean get()=scanOwner==Owner.AUTOMATIC
    val manualScanning:Boolean get()=scanOwner==Owner.MANUAL
    private var stopTask:Runnable?=null
    fun cancel(owner:Owner){if(scanOwner==owner){val done=completed;close();done?.invoke("scan cancelled")}}
    private fun owner()=check(Looper.myLooper()==Looper.getMainLooper())
    fun missingPermissions():List<String> {
        val needed=if(Build.VERSION.SDK_INT>=31)listOf(Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT)else listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        return needed.filter{context.checkSelfPermission(it)!=PackageManager.PERMISSION_GRANTED}
    }
    fun start(durationMs:Long=5000, requestedOwner:Owner=Owner.MANUAL, remembered:io.openhoyi.session.ScaleSelection?=null,
        onDevice:(DiscoveredDevice)->Unit,onFinished:(String?)->Unit) {
        owner();require(durationMs in 1000..30_000)
        if(running!=null && requestedOwner==Owner.MANUAL && scanOwner==Owner.AUTOMATIC)cancel(Owner.AUTOMATIC)
        if(running!=null){onFinished("scan already running");return}
        if(missingPermissions().isNotEmpty()){onFinished("scan permissions required");return}
        if(Build.VERSION.SDK_INT<31){
            val location=context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val enabled=if(Build.VERSION.SDK_INT>=28)location.isLocationEnabled else location.isProviderEnabled(LocationManager.GPS_PROVIDER)||location.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            if(!enabled){onFinished("location services required on this Android version");return}
        }
        val adapter=(context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        val scanner=try {if(adapter?.isEnabled==true)adapter.bluetoothLeScanner else null}catch(_:SecurityException){null}
        if(scanner==null){onFinished("Bluetooth unavailable");return}
        val gen=++generation
        fun finish(error:String?){if(gen!=generation)return;close();onFinished(error)}
        val callback=object:ScanCallback(){
            override fun onScanResult(callbackType:Int,result:ScanResult){handler.post {
                if(gen!=generation||running==null)return@post
                try {
                    val address=result.device.address
                    val name=ScaleAdvertisementClassifier.discoveryName(result.device.name,result.scanRecord?.deviceName)
                    val known=remembered?.takeIf { it.address.equals(address,ignoreCase=true) }
                    val protocolId=known?.protocolId ?: ScaleAdvertisementClassifier.protocolId(name)
                    val role=when{known!=null->DeviceRole.BOOKOO;name.contains("HOYI",true)->DeviceRole.COFFEE;protocolId!=null->DeviceRole.BOOKOO;else->return@post }
                    onDevice(DiscoveredDevice(address,name,result.rssi,role,if(role==DeviceRole.BOOKOO)protocolId else null))
                }catch(_:SecurityException){finish("scan permission revoked")}
            }}
            override fun onBatchScanResults(results:MutableList<ScanResult>){
                results.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES,it) }
            }
            override fun onScanFailed(errorCode:Int){handler.post {finish("scan failed: $errorCode")}}
        }
        running=callback;scanOwner=requestedOwner;completed=onFinished
        val timeout=Runnable{finish(null)};stopTask=timeout
        try {scanner.startScan(null,ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),callback);handler.postDelayed(timeout,durationMs)}
        catch(_:SecurityException){finish("scan permission revoked")}
        catch(_:IllegalStateException){finish("Bluetooth became unavailable")}
    }
    fun environmentReady():Boolean=runCatching {
        missingPermissions().isEmpty() &&
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter?.isEnabled==true &&
            (Build.VERSION.SDK_INT>=31 || (context.getSystemService(Context.LOCATION_SERVICE) as LocationManager).let { location ->
                if(Build.VERSION.SDK_INT>=28)location.isLocationEnabled else location.isProviderEnabled(LocationManager.GPS_PROVIDER)||location.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            })
    }.getOrDefault(false)
    override fun close(){
        owner();generation++;stopTask?.let{handler.removeCallbacks(it)};stopTask=null
        val callback=running;running=null;scanOwner=null;completed=null
        if(callback!=null)try{(context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter?.bluetoothLeScanner?.stopScan(callback)}catch(_:SecurityException){}catch(_:IllegalStateException){}
    }
}
