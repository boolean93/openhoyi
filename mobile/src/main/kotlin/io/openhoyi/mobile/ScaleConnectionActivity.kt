package io.openhoyi.mobile

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import io.openhoyi.session.DeviceRole

/** The existing service owns scanning and the single scale connection for all supported adapters. */
class ScaleConnectionActivity:ScaleOwnerActivity() {
    private lateinit var status:TextView
    private lateinit var scan:Button
    private lateinit var rows:LinearLayout
    private var renderedKey:String?=null
    private var scanPending=false
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val body=scaleBody(R.string.scale_connect,R.string.scale_connection_subtitle)
        status=HoyiUi.label(this,body,"",14,muted=true)
        scan=HoyiUi.button(this,body,getString(R.string.scale_scan),primary=true) {scanCandidates()}
        HoyiUi.button(this,body,getString(R.string.scale_connection_manager)) {startActivity(Intent(this,HomeActivity::class.java))}
        rows=HoyiUi.card(this,body)
        HoyiUi.label(this,body,getString(R.string.scale_offline_evidence),14,muted=true)
        renderScale()
    }
    private fun radioUsable():Boolean {
        val permissions=if(Build.VERSION.SDK_INT>=31) listOf(Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT)
            else listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if(permissions.any {checkSelfPermission(it)!=PackageManager.PERMISSION_GRANTED})return false
        if(Build.VERSION.SDK_INT<31) {
            val location=getSystemService(android.location.LocationManager::class.java)
            if(Build.VERSION.SDK_INT>=28) {if(!location.isLocationEnabled)return false}
            else if(!location.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) &&
                !location.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER))return false
        }
        return runCatching {getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled==true}.getOrDefault(false)
    }
    private fun scanCandidates() {
        if(BuildConfig.MOCK_MODE) {message(getString(R.string.scale_mock_notice));return}
        val service=owner
        if(service!=null && !changesAllowed()) {message(getString(R.string.scale_active_guard));return}
        if(!radioUsable()) {message(getString(R.string.scale_owner_required));startActivity(Intent(this,HomeActivity::class.java));return}
        if(service?.running==true) {service.scan();renderScale();return}
        // A user scan action may start the existing owner. No permission/enable prompt is bypassed.
        scanPending=true
        try {startForegroundService(Intent(this,MobileService::class.java));bindOwner()}
        catch(_:RuntimeException) {scanPending=false;message(getString(R.string.scale_owner_failed))}
    }
    override fun renderScale() {
        if(!::status.isInitialized)return
        if(scanPending && owner==null)bindOwner()
        val service=owner
        if(scanPending && service?.running==true) {
            scanPending=false
            if(changesAllowed() && radioUsable())service.scan()
        }
        status.text=when {
            BuildConfig.MOCK_MODE->getString(R.string.scale_mock_notice)
            service==null || !service.running->getString(R.string.scale_owner_failed)
            !changesAllowed()->getString(R.string.scale_active_guard)
            else->getString(R.string.scale_connection_state,DeviceStatusText.label(this,service.snapshot.scaleState))
        }
        scan.setText(if(service?.snapshot?.scanning==true)R.string.scale_scanning else R.string.scale_scan)
        scan.isEnabled=!BuildConfig.MOCK_MODE && service?.snapshot?.scanning!=true && (service==null || changesAllowed())
        val candidates=service?.snapshot?.candidates.orEmpty().filter {it.candidateRole==DeviceRole.BOOKOO}
        val key=candidates.joinToString {"${it.address}:${it.rssi}:${it.scaleProtocolId}"}+":"+changesAllowed()
        if(renderedKey==key)return
        renderedKey=key;rows.removeAllViews()
        if(candidates.isEmpty())HoyiUi.label(this,rows,getString(R.string.scale_scan_empty),14,muted=true)
        else candidates.forEach {candidate->
            if(candidate.scaleProtocolId==io.openhoyi.session.FelicitaReadOnlyScaleProtocolAdapter.id)
                HoyiUi.label(this,rows,getString(R.string.scale_read_only_live),14,muted=true)
            HoyiUi.button(this,rows,getString(R.string.scale_candidate_format,candidate.advertisedName,candidate.address,candidate.rssi)) {
                if(BuildConfig.MOCK_MODE || !changesAllowed()) {message(getString(R.string.scale_active_guard));return@button}
                if(!radioUsable()) {message(getString(R.string.scale_owner_required));return@button}
                candidate.scaleProtocolId?.let { owner?.connectScale(candidate.address,it) };renderScale()
            }.isEnabled=!BuildConfig.MOCK_MODE && changesAllowed()
        }
    }
}
