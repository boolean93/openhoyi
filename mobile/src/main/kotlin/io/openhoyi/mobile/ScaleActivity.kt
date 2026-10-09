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
    protected fun scaleBody(title:Int,subtitle:Int):LinearLayout {
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
            val padding=HoyiUi.dp(this@ScaleOwnerActivity,if(HoyiUi.wide(this@ScaleOwnerActivity))40 else 20)
            setPadding(padding,HoyiUi.dp(this@ScaleOwnerActivity,16),padding,HoyiUi.dp(this@ScaleOwnerActivity,24))
        }
        root.addView(ScrollView(this).apply {addView(body)},LinearLayout.LayoutParams(-1,0,1f))
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
    private lateinit var guard:TextView
    private lateinit var tareStatus:TextView
    private lateinit var tare:Button
    private lateinit var timerValue:TextView
    private lateinit var timerToggle:Button
    private lateinit var connectionButton:Button
    private var capture:Button?=null
    private var timer=LocalScaleTimer()
    private fun stableDose(now:Long):Int? {
        return owner?.beanDoseCaptureAt(now)
    }
    override fun onStop() {owner?.resetBeanDoseCapture();super.onStop()}
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        timer=LocalScaleTimer(LocalScaleTimer.State(savedInstanceState?.getLong("scale.timer.accumulated") ?: 0,
            savedInstanceState?.takeIf {it.containsKey("scale.timer.origin")}?.getLong("scale.timer.origin")))
        val body=scaleBody(R.string.scale_title,R.string.scale_subtitle)
        val reading=HoyiUi.card(this,body)
        weight=HoyiUi.label(this,reading,getString(R.string.scale_unavailable),if(HoyiUi.wide(this))96 else 72,true).apply {
            setAutoSizeTextTypeUniformWithConfiguration(28,if(HoyiUi.wide(this@ScaleActivity))96 else 72,2,android.util.TypedValue.COMPLEX_UNIT_SP)
            layoutParams=LinearLayout.LayoutParams(-1,-2)
            maxLines=1;typeface=android.graphics.Typeface.create("sans-serif-light",android.graphics.Typeface.NORMAL)
        }
        freshness=HoyiUi.label(this,reading,"",14,muted=true)
        battery=HoyiUi.label(this,reading,"",13,muted=true)
        guard=HoyiUi.label(this,body,"",14,muted=true)
        tare=HoyiUi.button(this,body,getString(R.string.scale_tare),primary=true) {
            val service=owner ?: return@button
            if(BuildConfig.MOCK_MODE || !changesAllowed()) {message(getString(R.string.scale_active_guard));return@button}
            service.resetBeanDoseCapture()
            message(service.tareScale() ?: getString(R.string.scale_tare_requested));renderScale()
        }
        tareStatus=HoyiUi.label(this,body,"",13,muted=true)
        val timerCard=HoyiUi.card(this,body,getString(R.string.scale_timer_title))
        timerValue=HoyiUi.label(this,timerCard,"",40,true)
        HoyiUi.label(this,timerCard,getString(R.string.scale_timer_help),13,muted=true)
        timerToggle=HoyiUi.button(this,timerCard,getString(R.string.scale_timer_start)) {
            if(timer.running)timer.pause(SystemClock.elapsedRealtime())else timer.start(SystemClock.elapsedRealtime());renderScale()
        }
        HoyiUi.button(this,timerCard,getString(R.string.scale_timer_reset)) {timer.reset();renderScale()}
        if(intent.getBooleanExtra(EXTRA_CAPTURE_BEAN_DOSE,false)) {
            val dose=HoyiUi.card(this,body,getString(R.string.scale_dose_title))
            HoyiUi.label(this,dose,getString(R.string.scale_dose_help),14,muted=true)
            capture=HoyiUi.button(this,dose,getString(R.string.scale_dose_capture),primary=true) {
                val stable=stableDose(SystemClock.elapsedRealtime())
                if(stable==null) {
                    message(getString(R.string.scale_dose_not_ready));return@button
                }
                setResult(RESULT_OK,Intent().putExtra(EXTRA_BEAN_DOSE_HUNDREDTHS_GRAM,stable));finish()
            }
        }
        connectionButton=HoyiUi.button(this,body,getString(R.string.scale_connect)) {startActivity(Intent(this,ScaleConnectionActivity::class.java))}
        HoyiUi.button(this,body,getString(R.string.scale_settings)) {startActivity(Intent(this,ScaleSettingsActivity::class.java))}
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
        weight.text=live?.let {getString(R.string.scale_weight_format,String.format(Locale.getDefault(),"%.${digits}f",it.hundredthsGram/100.0))}
            ?: getString(R.string.scale_unavailable)
        freshness.setText(when {
            BuildConfig.MOCK_MODE->R.string.scale_mock_notice
            snapshot?.scaleState!=DeviceState.READY->R.string.scale_disconnected
            live==null->R.string.scale_stale
            live.evidence==ScaleEvidence.OFFLINE_CANDIDATE->R.string.scale_offline_evidence
            live.evidence==ScaleEvidence.LIVE_READ_ONLY->R.string.scale_read_only_live
            else->R.string.scale_live
        })
        battery.text=live?.batteryPercent?.takeIf {live.capabilities.battery && it in 0..100}
            ?.let {getString(R.string.scale_battery_format,it)} ?: getString(R.string.scale_battery_unavailable)
        val allowed=changesAllowed()
        guard.text=if(service!=null && !allowed)getString(R.string.scale_active_guard)else ""
        tare.isEnabled=!BuildConfig.MOCK_MODE && allowed && snapshot?.scaleState==DeviceState.READY && snapshot.scaleCapabilities?.tare==true &&
            service?.tareState !in setOf(StandaloneTare.State.WRITING,StandaloneTare.State.WAITING_ZERO)
        tareStatus.setText(when(service?.tareState) {
            StandaloneTare.State.WRITING,StandaloneTare.State.WAITING_ZERO->R.string.scale_tare_waiting
            StandaloneTare.State.CONFIRMED->R.string.scale_tare_confirmed
            StandaloneTare.State.FAILED->R.string.scale_tare_failed
            StandaloneTare.State.UNKNOWN->R.string.scale_tare_unknown
            else->R.string.scale_tare_idle
        })
        val tenths=timer.elapsedMs(now)/100
        timerValue.text=getString(R.string.scale_timer_format,tenths/600,(tenths/10)%60,tenths%10)
        timerToggle.setText(if(timer.running)R.string.scale_timer_pause else R.string.scale_timer_start)
        capture?.isEnabled=stableDose(now)!=null
        connectionButton.isEnabled=service==null || allowed
    }
}
