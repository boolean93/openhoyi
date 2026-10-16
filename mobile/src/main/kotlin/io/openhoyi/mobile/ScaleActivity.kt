package io.openhoyi.mobile

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import io.openhoyi.protocol.ScaleEvidence
import io.openhoyi.session.DeviceConnectionGate
import io.openhoyi.session.DeviceState
import io.openhoyi.session.StandaloneTare
import java.util.Locale

/** Screens only bind the one existing service; none of them creates a BLE owner. */
abstract class ScaleOwnerActivity: ThemedActivity() {
    protected var owner:MobileService?=null
    private var bound=false
    private var visible=false
    private val handler=Handler(Looper.getMainLooper())
    private val connection=object:ServiceConnection {
        override fun onServiceConnected(name:ComponentName,binder:IBinder) {
            owner=(binder as MobileService.LocalBinder).service;renderScale()
        }
        override fun onServiceDisconnected(name:ComponentName) {owner=null;renderScale()}
        override fun onBindingDied(name:ComponentName) {releaseOwner();renderScale()}
    }
    private val refresh=object:Runnable {
        override fun run() {renderScale();if(visible)handler.postDelayed(this,250)}
    }
    protected fun bindOwner() {if(!bound)bound=bindService(Intent(this,MobileService::class.java),connection,0)}
    override fun onStart() {super.onStart();visible=true;bindOwner();handler.post(refresh)}
    override fun onStop() {visible=false;handler.removeCallbacks(refresh);releaseOwner();super.onStop()}
    private fun releaseOwner() {if(bound)unbindService(connection);bound=false;owner=null}
    protected abstract fun renderScale()
    protected fun changesAllowed():Boolean = owner?.let { !it.manualShotActive && DeviceConnectionGate.mayChangeScale(it.shotState) } == true
    protected fun message(value:String) {Toast.makeText(this,value,Toast.LENGTH_SHORT).show()}
    protected fun scaleBody(title:Int,subtitle:Int,scrollable:Boolean=false):LinearLayout {
        val root=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL;setBackgroundColor(getColor(R.color.mobile_background))
            setOnApplyWindowInsetsListener {view,insets->
                val area=if(Build.VERSION.SDK_INT>=30) {
                    val x=insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                    intArrayOf(x.left,x.top,x.right,x.bottom)
                } else {
                    @Suppress("DEPRECATION")
                    intArrayOf(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,insets.systemWindowInsetRight,insets.systemWindowInsetBottom)
                }
                view.setPadding(area[0],area[1],area[2],area[3]);insets
            }
        }
        val body=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            val padding=HoyiUi.dp(this@ScaleOwnerActivity,if(HoyiUi.wide(this@ScaleOwnerActivity))24 else 16)
            setPadding(padding,HoyiUi.dp(this@ScaleOwnerActivity,8),padding,HoyiUi.dp(this@ScaleOwnerActivity,8))
        }
        if (scrollable) root.addView(ScrollView(this).apply { addView(body) },LinearLayout.LayoutParams(-1,0,1f))
        else root.addView(body,LinearLayout.LayoutParams(-1,0,1f))
        HoyiUi.navigation(this,root,ScaleActivity::class.java);setContentView(root)
        HoyiUi.header(this,body,getString(title),getString(subtitle),back=true)
        return body
    }
}

class ScaleActivity:ScaleOwnerActivity() {
    companion object {
        const val EXTRA_CAPTURE_BEAN_DOSE="io.openhoyi.mobile.capture_bean_dose"
        const val EXTRA_BEAN_DOSE_HUNDREDTHS_GRAM="io.openhoyi.mobile.bean_dose_hundredths_gram"
    }
    private lateinit var weight:TextView
    private lateinit var freshness:TextView
    private lateinit var battery:TextView
    private lateinit var deviceStatus:TextView
    private lateinit var guard:TextView
    private lateinit var tareStatus:TextView
    private lateinit var tare:Button
    private lateinit var timerValue:TextView
    private lateinit var timerToggle:Button
    private lateinit var connectionButton:Button
    private var capture:Button?=null
    private var timer=LocalScaleTimer()
    private lateinit var trend: ScaleTrendPanel
    private fun stableDose(now:Long):Int? {
        return owner?.beanDoseCaptureAt(now)
    }
    override fun onStop() { if (::trend.isInitialized) trend.dismiss(); owner?.resetBeanDoseCapture();super.onStop()}
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        timer=LocalScaleTimer(LocalScaleTimer.State(savedInstanceState?.getLong("scale.timer.accumulated") ?: 0,
            savedInstanceState?.takeIf {it.containsKey("scale.timer.origin")}?.getLong("scale.timer.origin")))
        val body=scaleBody(R.string.scale_title,R.string.scale_subtitle)
        val info=LinearLayout(this).apply {orientation=LinearLayout.HORIZONTAL}
        body.addView(info)
        freshness=HoyiUi.label(this,info,"",14,muted=true).apply {layoutParams=LinearLayout.LayoutParams(0,-2,1f)}
        guard=HoyiUi.label(this,body,"",14,muted=true)
        val workspace=TaskWorkspaceLayout(this)
        body.addView(workspace,LinearLayout.LayoutParams(-1,0,1f))
        val primary=workspace.primaryBody
        val secondary=workspace.secondaryBody
        val reading=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL; gravity=android.view.Gravity.CENTER
            minimumHeight=HoyiUi.dp(this@ScaleActivity,128)
            background=HoyiUi.shape(this@ScaleActivity,android.R.color.transparent,0)
            setPadding(HoyiUi.dp(this@ScaleActivity,12),0,HoyiUi.dp(this@ScaleActivity,12),0)
        }
        primary.addView(reading,LinearLayout.LayoutParams(-1,0,1f).apply {topMargin=HoyiUi.dp(this@ScaleActivity,8)})
        HoyiUi.label(this,reading,getString(R.string.scale_current_weight),16,muted=true).apply {
            gravity=android.view.Gravity.CENTER
            layoutParams=LinearLayout.LayoutParams(-1,-2)
            setPadding(0,0,0,HoyiUi.dp(this@ScaleActivity,16))
        }
        weight=HoyiUi.label(this,reading,getString(R.string.scale_unavailable),if(HoyiUi.wide(this))64 else 48,true).apply {
            // Natural height preserves system font scaling and long/negative readings.
            layoutParams=LinearLayout.LayoutParams(-1,-2);gravity=android.view.Gravity.CENTER
            typeface=TaskTypography.face(this@ScaleActivity, 700)
        }
        tareStatus=HoyiUi.label(this,reading,"",14,muted=true).apply {setPadding(0,0,0,HoyiUi.dp(this@ScaleActivity,8))}
        tare=HoyiUi.button(this,primary,getString(R.string.scale_tare),
            primary=!intent.getBooleanExtra(EXTRA_CAPTURE_BEAN_DOSE,false)) {
            val service=owner ?: return@button
            if(BuildConfig.MOCK_MODE || !changesAllowed()) {message(getString(R.string.scale_active_guard));return@button}
            service.resetBeanDoseCapture()
            message(service.tareScale() ?: getString(R.string.scale_tare_requested));renderScale()
        }
        trend = ScaleTrendPanel(this, primary, secondary)
        workspace.onPrimarySpaceAvailable = { width, height ->
            fun natural(view: android.view.View): Int {
                view.measure(android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED))
                val params = view.layoutParams as LinearLayout.LayoutParams
                return view.measuredHeight + params.topMargin + params.bottomMargin
            }
            val buttonWidth = minOf(width, HoyiUi.dp(this,240)).coerceAtLeast(0)
            (tare.layoutParams as LinearLayout.LayoutParams).apply {
                this.width=buttonWidth; gravity=android.view.Gravity.CENTER_HORIZONTAL
            }
            tare.measure(android.view.View.MeasureSpec.makeMeasureSpec(buttonWidth, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED))
            val tareParams=tare.layoutParams as LinearLayout.LayoutParams
            val required = natural(reading) + tare.measuredHeight + tareParams.topMargin + tareParams.bottomMargin
            required + trend.fit(width, height - required)
        }
        // Only TaskWorkspace secondary content scrolls; weight, tare and capture stay fixed.
        val timerCard=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL}
        secondary.addView(timerCard)
        val timerHeading=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL}
        timerCard.addView(timerHeading,LinearLayout.LayoutParams(-1,-2).apply {topMargin=HoyiUi.dp(this@ScaleActivity,8)})
        HoyiUi.label(this,timerHeading,getString(R.string.scale_timer_title),20,true).apply {
            setPadding(0,0,0,HoyiUi.dp(this@ScaleActivity,16))
        }
        timerValue=HoyiUi.label(this,timerHeading,"",40,true).apply {
            typeface=TaskTypography.face(this@ScaleActivity,700)
            setPadding(0,0,0,HoyiUi.dp(this@ScaleActivity,8))
        }
        // The timer is explicitly local, not an unconfirmed scale command.
        HoyiUi.label(this,timerCard,getString(R.string.scale_timer_help),14,muted=true)
        val timerActions=LinearLayout(this).apply {orientation=LinearLayout.HORIZONTAL}
        timerCard.addView(timerActions)
        timerToggle=HoyiUi.button(this,timerActions,getString(R.string.scale_timer_start)) {
            if(timer.running)timer.pause(SystemClock.elapsedRealtime())else timer.start(SystemClock.elapsedRealtime());renderScale()
        }.apply {layoutParams=LinearLayout.LayoutParams(0,-2,1f).apply {topMargin=HoyiUi.dp(this@ScaleActivity,6);marginEnd=HoyiUi.dp(this@ScaleActivity,6)}}
        HoyiUi.button(this,timerActions,getString(R.string.scale_timer_reset)) {timer.reset();renderScale()}.apply {
            layoutParams=LinearLayout.LayoutParams(0,-2,1f).apply {topMargin=HoyiUi.dp(this@ScaleActivity,6)}
        }
        if(intent.getBooleanExtra(EXTRA_CAPTURE_BEAN_DOSE,false)) {
            // Full dose provenance remains available in the local scroll region.
            HoyiUi.label(this,secondary,getString(R.string.scale_dose_help),14,muted=true)
        }
        val device=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(0,HoyiUi.dp(this@ScaleActivity,24),0,0)
        }
        secondary.addView(device,LinearLayout.LayoutParams(-1,-2))
        HoyiUi.label(this,device,getString(R.string.scale_device_section),20,true).apply {
            setPadding(0,0,0,HoyiUi.dp(this@ScaleActivity,16))
        }
        battery=HoyiUi.label(this,device,"",14,muted=true).apply {
            setPadding(0,0,0,HoyiUi.dp(this@ScaleActivity,16))
        }
        deviceStatus=HoyiUi.label(this,device,"",14,muted=true).apply {
            setPadding(0,0,0,HoyiUi.dp(this@ScaleActivity,8))
        }
        val tools=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL}
        device.addView(tools)
        connectionButton=HoyiUi.button(this,tools,getString(R.string.scale_connect)) {startActivity(Intent(this,ScaleConnectionActivity::class.java))}.apply {
            layoutParams=LinearLayout.LayoutParams(-1,-2).apply {topMargin=HoyiUi.dp(this@ScaleActivity,8)}
        }
        HoyiUi.button(this,tools,getString(R.string.scale_settings)) {startActivity(Intent(this,ScaleSettingsActivity::class.java))}.apply {
            layoutParams=LinearLayout.LayoutParams(-1,-2).apply {topMargin=HoyiUi.dp(this@ScaleActivity,6)}
        }
        if(intent.getBooleanExtra(EXTRA_CAPTURE_BEAN_DOSE,false)) {
            capture=HoyiUi.button(this,body,getString(R.string.scale_dose_capture),primary=true) {
                val stable=stableDose(SystemClock.elapsedRealtime())
                if(stable==null) {message(getString(R.string.scale_dose_not_ready));return@button}
                setResult(RESULT_OK,Intent().putExtra(EXTRA_BEAN_DOSE_HUNDREDTHS_GRAM,stable));finish()
            }
        }
        renderScale()
    }
    override fun onSaveInstanceState(outState:Bundle) {
        val state=timer.snapshot();outState.putLong("scale.timer.accumulated",state.accumulatedMs)
        state.startedAtMs?.let {outState.putLong("scale.timer.origin",it)}
        super.onSaveInstanceState(outState)
    }
    override fun renderScale() {
        if(!::weight.isInitialized)return
        val service=owner;val snapshot=service?.snapshot;val now=SystemClock.elapsedRealtime()
        val live=snapshot?.let {LiveTelemetry.scale(it.scaleObservation,it.scaleState,now)}
        val digits=if(getSharedPreferences("scale_tool",MODE_PRIVATE).getBoolean("one_decimal",false))1 else 2
        weight.setIfChanged(live?.let {getString(R.string.scale_weight_format,String.format(Locale.getDefault(),"%.${digits}f",it.hundredthsGram/100.0))}
            ?: getString(R.string.scale_unavailable))
        freshness.setIfChanged(getString(when {
            BuildConfig.MOCK_MODE->R.string.scale_mock_notice
            snapshot?.scaleState!=DeviceState.READY->R.string.scale_disconnected
            live==null->R.string.scale_stale
            live.evidence==ScaleEvidence.OFFLINE_CANDIDATE->R.string.scale_offline_evidence
            live.evidence==ScaleEvidence.LIVE_READ_ONLY->R.string.scale_read_only_live
            else->R.string.scale_live
        }))
        deviceStatus.setIfChanged(freshness.text.toString())
        if (::trend.isInitialized) trend.present(service?.scaleTrendAt(now).orEmpty(), now, freshness.text.toString())
        battery.setIfChanged(live?.batteryPercent?.takeIf {live.capabilities.battery && it in 0..100}
            ?.let {getString(R.string.scale_battery_format,it)} ?: getString(R.string.scale_battery_unavailable))
        val allowed=changesAllowed()
        guard.setIfChanged(if(service!=null && !allowed)getString(R.string.scale_active_guard)else "")
        guard.visibility=if(guard.text.isEmpty())android.view.View.GONE else android.view.View.VISIBLE
        tare.isEnabled=!BuildConfig.MOCK_MODE && allowed && snapshot?.scaleState==DeviceState.READY && snapshot.scaleCapabilities?.tare==true &&
            service?.tareState !in setOf(StandaloneTare.State.WRITING,StandaloneTare.State.WAITING_ZERO)
        tareStatus.setIfChanged(getString(when(service?.tareState) {
            StandaloneTare.State.WRITING,StandaloneTare.State.WAITING_ZERO->R.string.scale_tare_waiting
            StandaloneTare.State.CONFIRMED->R.string.scale_tare_confirmed
            StandaloneTare.State.FAILED->R.string.scale_tare_failed
            StandaloneTare.State.UNKNOWN->R.string.scale_tare_unknown
            else->R.string.scale_tare_idle
        }))
        val tenths=timer.elapsedMs(now)/100
        timerValue.setIfChanged(getString(R.string.scale_timer_format,tenths/600,(tenths/10)%60,tenths%10))
        timerToggle.setIfChanged(getString(if(timer.running)R.string.scale_timer_pause else R.string.scale_timer_start))
        capture?.isEnabled=stableDose(now)!=null
        connectionButton.isEnabled=service==null || allowed
    }
    private fun TextView.setIfChanged(value:String) { if(text.toString()!=value) text=value }
}
