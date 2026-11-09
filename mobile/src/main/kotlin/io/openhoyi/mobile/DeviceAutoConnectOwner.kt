package io.openhoyi.mobile

import android.Manifest
import android.app.KeyguardManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import android.provider.Settings
import io.openhoyi.session.*

/** Process owner exists even when Bluetooth was off before any service was created. */
internal class DeviceAutoConnectOwner(private val app:MobileApplication) {
    private val prefs=app.getSharedPreferences("devices",Context.MODE_PRIVATE)
    private val handler=Handler(Looper.getMainLooper())
    private fun stamp()=DeviceUseSession.Stamp(SystemClock.elapsedRealtime(),System.currentTimeMillis(),
        Settings.Global.getInt(app.contentResolver,Settings.Global.BOOT_COUNT,-1))
    private val session=DeviceUseSession(if(prefs.contains("useSessionId"))DeviceUseSession.Saved(
        prefs.getLong("useSessionId",0),DeviceUseSession.Stamp(prefs.getLong("useElapsed",0),prefs.getLong("useWall",0),prefs.getInt("useBoot",-2)),
        prefs.getBoolean("coffeeAutoSuppressed",false),prefs.getBoolean("scaleAutoSuppressed",false)) else null)
    var onChanged:(()->Unit)?=null
    private var initialized=false
    private var lastVisible=false
    private val heartbeat=object:Runnable {
        override fun run(){refresh(notify=false);if(lastVisible)handler.postDelayed(this,15_000)}
    }
    private val receiver=object:BroadcastReceiver(){override fun onReceive(context:Context,intent:Intent){refresh()}}
    fun close(){app.visibility.unobserve("device-use-session");handler.removeCallbacks(heartbeat);app.unregisterReceiver(receiver)}
    init {
        val filter=IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED);addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON);addAction(Intent.ACTION_USER_PRESENT)
        }
        // Bluetooth may originate from a privileged non-system UID. Payloads authorize nothing;
        // every event only rechecks actual OS state and persisted intent.
        if(Build.VERSION.SDK_INT>=33)app.registerReceiver(receiver,filter,Context.RECEIVER_EXPORTED)
        else app.registerReceiver(receiver,filter)
        app.visibility.observe("device-use-session"){refresh()}
    }
    fun enabled(role:DeviceRole)=prefs.getBoolean(if(role==DeviceRole.COFFEE)"coffeeAutoEnabled" else "scaleAutoEnabled",true)
    fun paused(role:DeviceRole)=prefs.getBoolean(if(role==DeviceRole.COFFEE)"coffeeAutoSuppressed" else "scaleAutoSuppressed",false)
    fun pause(role:DeviceRole,value:Boolean){
        session.pause(role,value)
        check(prefs.edit().putBoolean(if(role==DeviceRole.COFFEE)"coffeeAutoSuppressed" else "scaleAutoSuppressed",value).commit())
    }
    fun refresh(notify:Boolean=true){
        // Mock has no BLE devices, and the session bookkeeping below rewrites the "devices"
        // preferences on every visibility callback. That pollutes the lifecycle/language
        // instrumentation checks (preserved prefs are compared after Activity recreate) and has
        // no runtime value in Mock; read-only enabled()/paused() stay available for the UI.
        // Real devices keep the original behaviour.
        if(BuildConfig.MOCK_MODE)return
        val interactive=app.getSystemService(PowerManager::class.java).isInteractive &&
            !app.getSystemService(KeyguardManager::class.java).isKeyguardLocked
        app.visibility.interactive(interactive)
        val visible=app.visibility.visible
        if(!initialized && !visible)return
        val legacyCoffee=paused(DeviceRole.COFFEE);val legacyScale=paused(DeviceRole.BOOKOO)
        val first=session.saved==null
        if(session.visibility(visible,stamp()))app.logs.record("device.use_session",mapOf("id" to session.saved!!.id.toString()))
        // Legacy disconnect flags become this session's pause, never permanent opt-out.
        if(first){session.pause(DeviceRole.COFFEE,legacyCoffee);session.pause(DeviceRole.BOOKOO,legacyScale)}
        session.saved?.let { saved ->
            check(prefs.edit().putLong("useSessionId",saved.id).putLong("useElapsed",saved.stamp.elapsed)
                .putLong("useWall",saved.stamp.wall).putInt("useBoot",saved.stamp.boot)
                .putBoolean("coffeeAutoSuppressed",saved.coffeePaused).putBoolean("scaleAutoSuppressed",saved.scalePaused).commit())
        }
        initialized=true
        if(notify)onChanged?.invoke()
        val changed=lastVisible!=visible;lastVisible=visible
        if(changed){handler.removeCallbacks(heartbeat);if(visible)handler.postDelayed(heartbeat,15_000)}
        if(BuildConfig.MOCK_MODE || !visible || (!notify && !changed))return
        val permissions=if(Build.VERSION.SDK_INT>=31)listOf(Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT) else listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if(permissions.any {app.checkSelfPermission(it)!=PackageManager.PERMISSION_GRANTED})return
        val coffee=enabled(DeviceRole.COFFEE)&&!paused(DeviceRole.COFFEE)&&MobileService.hasRememberedCoffee(app)
        val scale=enabled(DeviceRole.BOOKOO)&&!paused(DeviceRole.BOOKOO)&&ScaleSelectionPolicy.remembered(prefs.getString("scale",null),prefs.getString("scaleProtocol",null))!=null
        if(!coffee && !scale)return
        if(runCatching {app.getSystemService(BluetoothManager::class.java).adapter?.isEnabled==true}.getOrDefault(false))
            runCatching {app.startForegroundService(Intent(app,MobileService::class.java).setAction(MobileService.AUTO_KNOWN_DEVICES))}
                .onFailure {app.logs.record("device.auto_start_deferred",mapOf("type" to it.javaClass.simpleName))}
    }
}
