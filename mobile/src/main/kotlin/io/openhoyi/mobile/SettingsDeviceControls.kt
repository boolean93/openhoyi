package io.openhoyi.mobile

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.os.Build
import android.view.View
import android.widget.*
import io.openhoyi.session.DeviceRole
import io.openhoyi.session.DeviceConnectionGate

/** Inline connection controls, with the MainActivity remaining the single service owner. */
internal class SettingsDeviceControls(private val host:MainActivity, private val visual:SettingsVisualStyle,
    private val requestPermissions:(Array<String>)->Unit, private val enableBluetooth:()->Unit) {
    private val style=visual.palette
    private var owner:MobileService?=null
    private lateinit var message:TextView
    private lateinit var candidates:LinearLayout
    private val disconnectActions=mutableListOf<Button>()
    private val states=mutableMapOf<DeviceRole,TextView>()
    private var coffeeRetry:Button?=null
    private var dialog:AlertDialog?=null
    private var localError:String?=null
    private var rendered=""
    private val selection=DeviceScanSelection()
    var errorChanged:(Boolean)->Unit={}
    private fun connectedActionAllowed()=owner!=null && allowed()
    private fun allowed()=owner?.let { !it.manualShotActive && DeviceConnectionGate.mayChangeScale(it.shotState) } ?: true
    fun create(body:LinearLayout) {
        val card=visual.card(body,host.getString(R.string.home_device_connection),0)
        card.removeViewAt(0)
        listOf(DeviceRole.COFFEE to R.string.home_coffee_disconnect,DeviceRole.BOOKOO to R.string.home_scale_disconnect).forEachIndexed { index,(role,label) ->
            if(index>0)visual.divider(card)
            val words=style.column()
            style.add(words,style.text(host.getString(if(role==DeviceRole.COFFEE)R.string.home_coffee else R.string.scale_title),TaskProductVisualStyle.body))
            states[role]=style.text("—",TaskProductVisualStyle.auxiliary,secondary=true).also {style.add(words,it,4)}
            val actions=LinearLayout(host).apply {orientation=LinearLayout.HORIZONTAL;isBaselineAligned=false}
            if(role==DeviceRole.COFFEE)coffeeRetry=HoyiUi.button(host,actions,host.getString(R.string.coffee_connection_retry)) {
                host.reconnectCoffee()
            }.apply {visual.action(this,outline=true);tag="product/devices/coffee/retry"}
            HoyiUi.button(host,actions,host.getString(R.string.home_visual_change)) {
                owner?.beginDeviceSelection();selection.showRole(role);rendered="";render(owner)
                candidates.post {candidates.requestRectangleOnScreen(android.graphics.Rect(0,0,candidates.width,minOf(candidates.height,style.dp(120))),false)}
            }.apply {visual.action(this,outline=true)}
            HoyiUi.button(host,actions,host.getString(label)) {
                if(connectedActionAllowed())owner?.disconnect(role) else error(host.getString(R.string.scale_active_guard))
            }.apply {visual.action(this,outline=true);disconnectActions+=this}
            (0 until actions.childCount).forEach { index ->
                (actions.getChildAt(index).layoutParams as LinearLayout.LayoutParams).apply {
                    width=android.view.ViewGroup.LayoutParams.WRAP_CONTENT;topMargin=0;bottomMargin=0;marginEnd=if(index<actions.childCount-1)style.dp(8) else 0
                }
            }
            style.add(card,visual.pair(words,actions,"settings/devices/${role.name}"))
        }
        visual.divider(card)
        val scan=HoyiUi.button(host,LinearLayout(host),host.getString(R.string.home_scan)) {scan()}.apply {visual.action(this,outline=true)}
        val scanWords=style.text(host.getString(R.string.home_device_connection),TaskProductVisualStyle.body)
        (scan.parent as android.view.ViewGroup).removeView(scan)
        style.add(card,visual.pair(scanWords,scan,"settings/devices/scan"))
        message=style.text("",TaskProductVisualStyle.auxiliary,"settings/devices/message",true).apply {accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE}
        style.add(card,message,8)
        candidates=style.column("settings/devices/candidates");style.add(card,candidates,8)
    }
    fun addShutdown(body:LinearLayout) {
        HoyiUi.button(host,body,host.getString(R.string.home_shutdown)) {
            val service=owner
            if(service==null || !allowed()){error(host.getString(R.string.home_shutdown_busy));return@button}
            service.shutdown()
            if(service.running)error(host.getString(R.string.home_shutdown_busy)) else host.releaseServiceBinding()
        }.apply {asTaskTextAction();minimumHeight=style.dp(48);disconnectActions+=this}
    }
    fun error(value:String) {errorChanged(true);localError=value;if(::message.isInitialized)message.text=value}
    /** The shared scan action always shows both device types, including after a scoped change. */
    fun scan() {
        selection.showAll();rendered="";render(owner)
        resumeScan()
    }
    /** Permission/Bluetooth callbacks resume the current intent, not a new unfiltered search. */
    fun resumeScan() {
        localError=null;errorChanged(false)
        if(!allowed()) {error(host.getString(R.string.scale_active_guard));return}
        val permissions=if(Build.VERSION.SDK_INT>=31)arrayOf(Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if(!BuildConfig.MOCK_MODE) {
            val missing=permissions.filter {host.checkSelfPermission(it)!=PackageManager.PERMISSION_GRANTED}
            if(missing.isNotEmpty()){requestPermissions(missing.toTypedArray());return}
            try {
                if(Build.VERSION.SDK_INT<31) {
                    val location=host.getSystemService(android.location.LocationManager::class.java)
                    val enabled=if(Build.VERSION.SDK_INT>=28)location.isLocationEnabled else location.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)||location.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
                    if(!enabled){error(host.getString(R.string.device_scan_enable_location));return}
                }
                val adapter=host.getSystemService(BluetoothManager::class.java)?.adapter
                if(adapter==null){error(host.getString(R.string.home_bluetooth_unsupported));return}
                if(!adapter.isEnabled){enableBluetooth();return}
            }catch(_:SecurityException){error(host.getString(R.string.home_permission_expired));return}
        }
        if(!host.ensureServiceForScan())error(host.getString(R.string.home_bind_failed))
    }
    fun render(service:MobileService?) {
        owner=service
        if(!::message.isInitialized)return
        disconnectActions.forEach {it.isEnabled=connectedActionAllowed()}
        val snapshot=service?.snapshot
        val coffeeState=snapshot?.coffeeState ?: io.openhoyi.session.DeviceState.DISCONNECTED
        coffeeRetry?.apply {
            visibility=if(coffeeState==io.openhoyi.session.DeviceState.READY)View.GONE else View.VISIBLE
            isEnabled=allowed() && !TaskShellDeviceStatus.connecting(coffeeState)
            text=host.getString(if(TaskShellDeviceStatus.connecting(coffeeState))R.string.task_shell_action_connecting else R.string.coffee_connection_retry)
        }
        val prefs=host.getSharedPreferences("devices",android.content.Context.MODE_PRIVATE)
        states.forEach {(role,text)->
            val state=if(role==DeviceRole.COFFEE)snapshot?.coffeeState else snapshot?.scaleState
            val identity=if(BuildConfig.MOCK_MODE)host.getString(R.string.application_mode_mock) else host.getString(R.string.device_remembered_identity,prefs.getString(if(role==DeviceRole.COFFEE)"coffee" else "scale","—"))
            val status=state?.let {if(role==DeviceRole.COFFEE)DeviceStatusText.coffeeLabel(host,it,snapshot?.coffeeFailure) else DeviceStatusText.label(host,it)} ?: "—"
            // Saved identity may belong to a different device until the new one reaches READY.
            val value=DeviceStatusText.settingsSummary(status,identity)
            if(text.text.toString()!=value)text.text=value
        }
        val display=localError ?: snapshot?.messageForDisplay {id,args->host.getString(id,*args)}.orEmpty()
        if(message.text.toString()!=display)message.text=display
        message.visibility=if(message.text.isNullOrEmpty())View.GONE else View.VISIBLE
        val values=snapshot?.candidates.orEmpty().filter {selection.includes(it.candidateRole)}.sortedBy {it.candidateRole.name}
        val key=values.toString()+allowed()
        if(key==rendered)return
        rendered=key;candidates.removeAllViews()
        values.forEach {candidate->
            val words=style.column()
            style.add(words,style.text(candidate.advertisedName,TaskProductVisualStyle.body))
            style.add(words,style.text(candidate.address,TaskProductVisualStyle.auxiliary,secondary=true),4)
            ScaleCandidateStatus.resource(candidate.scaleProtocolId)?.let { status ->
                style.add(words,style.text(host.getString(status),TaskProductVisualStyle.auxiliary,secondary=true),4)
            }
            val connect=HoyiUi.button(host,LinearLayout(host),host.getString(R.string.home_connect)) {
                if(!connectedActionAllowed()){error(host.getString(R.string.scale_active_guard));return@button}
                localError=null
                if(candidate.candidateRole==DeviceRole.BOOKOO) {
                    (candidate.scaleProtocolId ?: if(BuildConfig.MOCK_MODE)io.openhoyi.session.BookooScaleProtocolAdapter.id else null)?.let {owner?.connectScale(candidate.address,it)}
                } else if(owner?.connectRememberedCoffee(candidate.address)==RememberedCoffeeConnection.PASSWORD_REQUIRED) {
                    showPassword(candidate.address)
                }
            }.apply {visual.action(this,outline=true);isEnabled=connectedActionAllowed();tag="product/devices/candidate/${candidate.address}/connect"}
            (connect.parent as android.view.ViewGroup).removeView(connect)
            val actions=style.column()
            style.add(actions,connect)
            if(candidate.candidateRole==DeviceRole.COFFEE && !BuildConfig.MOCK_MODE) {
                HoyiUi.button(host,actions,host.getString(R.string.home_connect_with_password)) {
                    if(connectedActionAllowed())showPassword(candidate.address) else error(host.getString(R.string.scale_active_guard))
                }.apply {visual.action(this,outline=true);isEnabled=connectedActionAllowed();tag="product/devices/candidate/${candidate.address}/password"}
            }
            style.add(candidates,visual.pair(words,actions,"settings/devices/candidate/${candidate.address}"));visual.divider(candidates)
        }
    }
    private fun showPassword(address:String) {
        val input=EditText(host).apply {
            inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters=arrayOf(android.text.InputFilter.LengthFilter(6));hint=host.getString(R.string.home_password_hint)
            isSaveEnabled=false;importantForAutofill=View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }
        dialog=AlertDialog.Builder(host).setTitle(R.string.home_connect_coffee_title).setMessage(R.string.home_password_message)
            .setView(input).setPositiveButton(R.string.home_connect,null).setNegativeButton(android.R.string.cancel,null).create().also {window->
                window.setOnShowListener {window.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val password=input.text.toString()
                    if(!password.matches(Regex("[0-9]{6}")))input.error=host.getString(R.string.home_password_error)
                    else if(connectedActionAllowed()){owner?.connectCoffee(address,password);window.dismiss()}
                }}
                window.setOnDismissListener {input.text.clear()};AppDialog.show(window)
            }
    }
    fun close(){dialog?.dismiss();dialog=null;owner?.endDeviceSelection();owner=null}
}
