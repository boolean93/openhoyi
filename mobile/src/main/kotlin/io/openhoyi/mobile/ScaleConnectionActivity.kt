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
    private lateinit var candidateScroll:android.widget.ScrollView
    private var savedScrollY=0
    private val visual by lazy {ScaleVisualStyle(this)}
    private lateinit var candidateCount:TextView
    val productLayoutEvidence=org.json.JSONObject()
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        savedScrollY=savedInstanceState?.getInt("candidateScrollY") ?: 0
        val style=visual.palette
        val body=scaleBody(R.string.scale_connect,R.string.scale_connection_subtitle,visual=visual)
        val words=style.column("product/scale-connection/status-body")
        status=style.text("",TaskProductVisualStyle.body,"product/scale-connection/status");style.add(words,status)
        style.add(words,style.text(getString(R.string.scale_connection_subtitle),TaskProductVisualStyle.auxiliary,secondary=true),8)
        val tools=HistoryVisualFlow(this,style.dp(16),style.dp(8))
        val actions=style.row()
        // The native action callbacks and radio/permission checks below remain the only control path.
        val manager=HoyiUi.button(this,actions,getString(R.string.scale_connection_manager)) {startActivity(Intent(this,HomeActivity::class.java))}
        actions.removeView(manager);tools.addView(manager)
        style.button(manager);HomeVisualIcon.buttonIcon(manager,"chevron",atEnd=true);manager.tag="product/scale-connection/manager"
        scan=HoyiUi.button(this,actions,getString(R.string.scale_scan),primary=true) {scanCandidates()}
        actions.removeView(scan);tools.addView(scan)
        style.button(scan,primary=true);HomeVisualIcon.buttonIcon(scan,"search");scan.tag="product/scale-connection/scan"
        val statusCard=ScaleVisualTwoGroups(this,style,words,tools).apply {
            tag="product/scale-connection/status-card";setPadding(style.dp(16),style.dp(16),style.dp(16),style.dp(16))
            background=style.shape(style.surface,true,radius=16)
        }
        productLayoutEvidence.put("statusCard",statusCard.productLayoutEvidence)
        style.add(body,statusCard)
        val candidatesCard=style.column("product/scale-connection/candidates").apply {
            setPadding(style.dp(16),style.dp(16),style.dp(16),style.dp(16));background=style.shape(style.surface,true,radius=16)
        }
        body.addView(candidatesCard,LinearLayout.LayoutParams(-1,0,1f).apply {topMargin=style.dp(16)})
        val heading=HistoryVisualFlow(this,style.dp(16),style.dp(8))
        heading.addView(style.text(getString(R.string.m6_nearby_scales),TaskProductVisualStyle.section))
        candidateCount=style.text("",TaskProductVisualStyle.auxiliary,"product/scale-connection/count",true);heading.addView(candidateCount)
        style.add(candidatesCard,heading)
        candidatesCard.addView(android.view.View(this).apply {setBackgroundColor(style.border)},LinearLayout.LayoutParams(-1,style.dp(1)).apply {topMargin=style.dp(16)})
        val devices=android.widget.ScrollView(this).apply {isFillViewport=true;tag="product/scale-connection/candidate-scroll"}
        candidateScroll=devices
        rows=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;tag="product/scale-connection/candidate-rows";setPadding(0,style.dp(8),0,0)}
        devices.addView(rows)
        candidatesCard.addView(devices,LinearLayout.LayoutParams(-1,0,1f))
        style.add(body,style.text(getString(R.string.scale_offline_evidence),TaskProductVisualStyle.auxiliary,"product/scale-connection/capability-note",true),16)
        renderScale()
    }
    private fun rememberCandidatePosition() {
        if(::candidateScroll.isInitialized && !candidateScroll.isLayoutRequested &&
            rows.height>candidateScroll.height) savedScrollY=candidateScroll.scrollY
    }
    override fun onStop() {rememberCandidatePosition();super.onStop()}
    override fun onSaveInstanceState(outState:Bundle) {
        rememberCandidatePosition()
        outState.putInt("candidateScrollY",savedScrollY)
        super.onSaveInstanceState(outState)
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
        status.setIfChanged(when {
            service==null || !service.running->getString(R.string.scale_owner_failed)
            !changesAllowed()->getString(R.string.scale_active_guard)
            else->getString(R.string.scale_connection_state,DeviceStatusText.label(this,service.snapshot.scaleState))
        })
        scan.setIfChanged(getString(if(service?.snapshot?.scanning==true)R.string.scale_scanning else R.string.scale_scan))
        scan.isEnabled=!BuildConfig.MOCK_MODE && service?.snapshot?.scanning!=true && (service==null || changesAllowed())
        val candidates=service?.snapshot?.candidates.orEmpty().filter {it.candidateRole==DeviceRole.BOOKOO}
        candidateCount.setIfChanged(getString(R.string.m6_scale_candidate_count,candidates.size))
        val key=candidates.joinToString {"${it.address}:${it.advertisedName}:${it.rssi}:${it.scaleProtocolId}"}+":"+changesAllowed()
        if(renderedKey==key)return
        rememberCandidatePosition()
        renderedKey=key;rows.removeAllViews()
        if(candidates.isEmpty())rows.addView(visual.palette.text(getString(R.string.scale_scan_empty),
            TaskProductVisualStyle.auxiliary,secondary=true))
        else candidates.forEach {candidate->
            val name=candidate.advertisedName
            val description=buildString {
                append(name).append('\n')
                append(getString(R.string.scale_candidate_metadata,candidate.address,candidate.rssi))
                if(candidate.scaleProtocolId==io.openhoyi.session.FelicitaReadOnlyScaleProtocolAdapter.id)
                    append('\n').append(getString(R.string.scale_read_only_live))
            }
            val style=visual.palette
            val row=style.row("product/scale-connection/candidate/${candidate.address}").apply {
                contentDescription=description;gravity=android.view.Gravity.CENTER_VERTICAL;minimumHeight=HoyiUi.dp(this@ScaleConnectionActivity,64)
                setPadding(style.dp(16),style.dp(8),style.dp(16),style.dp(8))
            }
            rows.addView(row,LinearLayout.LayoutParams(-1,-2))
            row.addView(android.widget.ImageView(this).apply {setImageDrawable(HomeVisualIcon.drawable(this@ScaleConnectionActivity,"scale",style.ink));importantForAccessibility=android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO},
                LinearLayout.LayoutParams(style.dp(24),style.dp(24)).apply {marginEnd=style.dp(16)})
            val identity=style.column()
            row.addView(identity,LinearLayout.LayoutParams(0,-2,1f).apply {marginEnd=style.dp(16)})
            style.add(identity,style.text(name,TaskProductVisualStyle.action,"product/scale-connection/candidate/${candidate.address}/name"))
            style.add(identity,style.text(candidate.address,TaskProductVisualStyle.auxiliary,"product/scale-connection/candidate/${candidate.address}/address",true))
            val metadata=HistoryVisualFlow(this,style.dp(16),style.dp(8))
            metadata.addView(style.text("${candidate.rssi} dBm",TaskProductVisualStyle.auxiliary,"product/scale-connection/candidate/${candidate.address}/rssi",true))
            if(candidate.scaleProtocolId==io.openhoyi.session.FelicitaReadOnlyScaleProtocolAdapter.id)
                metadata.addView(style.text(getString(R.string.scale_read_only_live),TaskProductVisualStyle.auxiliary,
                    "product/scale-connection/candidate/${candidate.address}/restriction").apply {setTextColor(visual.warning.warningInk)})
            style.add(identity,metadata,8)
            HoyiUi.button(this,row,getString(R.string.home_connect)) {
                if(BuildConfig.MOCK_MODE || !changesAllowed()) {message(getString(R.string.scale_active_guard));return@button}
                if(!radioUsable()) {message(getString(R.string.scale_owner_required));return@button}
                candidate.scaleProtocolId?.let { owner?.connectScale(candidate.address,it) };renderScale()
            }.apply {
                style.button(this,outline=true)
                tag="product/scale-connection/candidate/${candidate.address}/connect";contentDescription=description
                isEnabled=!BuildConfig.MOCK_MODE && changesAllowed()
                layoutParams=LinearLayout.LayoutParams(-2,-2)
            }
            rows.addView(android.view.View(this).apply {setBackgroundColor(style.border)},LinearLayout.LayoutParams(-1,style.dp(1)))
        }
        candidateScroll.post {if(!isFinishing && !isDestroyed)candidateScroll.scrollTo(0,savedScrollY)}
    }
    private fun TextView.setIfChanged(value:String) { if(text.toString()!=value) text=value }
}
