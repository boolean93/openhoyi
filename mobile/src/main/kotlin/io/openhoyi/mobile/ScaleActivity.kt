package io.openhoyi.mobile

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
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
    override fun onStart() {super.onStart();if(redirectedRoot)return;visible=true;bindOwner();handler.post(refresh)}
    override fun onStop() {if(!redirectedRoot){visible=false;handler.removeCallbacks(refresh);releaseOwner()};super.onStop()}
    private fun releaseOwner() {if(bound)unbindService(connection);bound=false;owner=null}
    protected abstract fun renderScale()
    protected fun changesAllowed():Boolean = owner?.let { !it.manualShotActive && DeviceConnectionGate.mayChangeScale(it.shotState) } == true
    protected fun message(value:String) {Toast.makeText(this,value,Toast.LENGTH_SHORT).show()}
    protected fun scaleBody(title:Int,subtitle:Int,scrollable:Boolean=false,visual:ScaleVisualStyle?=null):LinearLayout {
        if (visual != null) return visual.page(this, title, subtitle)
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
    private lateinit var weightUnit:TextView
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
    val productLayoutEvidence = org.json.JSONObject()
    private fun stableDose(now:Long):Int? {
        return owner?.beanDoseCaptureAt(now)
    }
    override fun onStop() { if (::trend.isInitialized) trend.dismiss(); owner?.resetBeanDoseCapture();super.onStop()}
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        if (!intent.getBooleanExtra(EXTRA_CAPTURE_BEAN_DOSE, false) && redirectRoot(TaskDestination.SCALE)) return
        timer=LocalScaleTimer(LocalScaleTimer.State(savedInstanceState?.getLong("scale.timer.accumulated") ?: 0,
            savedInstanceState?.takeIf {it.containsKey("scale.timer.origin")}?.getLong("scale.timer.origin")))
        val visual = ScaleVisualStyle(this)
        val style = visual.palette
        val body=scaleBody(R.string.scale_title,R.string.scale_subtitle,visual=visual)
        guard=style.text("",TaskProductVisualStyle.auxiliary,"product/scale/guard",true).also { style.add(body,it) }
        val workspace=ScaleVisualWorkspace(this,visual)
        productLayoutEvidence.put("workspace",workspace.productLayoutEvidence)
        body.addView(workspace,LinearLayout.LayoutParams(-1,0,1f))
        val primary=workspace.primary
        val secondary=workspace.secondary
        val readingLabel=style.text(getString(R.string.scale_current_weight),TaskProductVisualStyle.auxiliary,secondary=true).apply {
            gravity=android.view.Gravity.CENTER
        }
        val quantity=style.row("product/scale/quantity").apply {isBaselineAligned=true;gravity=android.view.Gravity.CENTER_HORIZONTAL or android.view.Gravity.TOP}
        weight=style.text(getString(R.string.scale_unavailable),if(resources.configuration.screenWidthDp>=840)visual.largeReading else visual.narrowReading,
            "product/scale/weight/number")
        quantity.addView(weight,LinearLayout.LayoutParams(-2,-2))
        weightUnit=style.text("",visual.unit,"product/scale/weight/unit",true)
        quantity.addView(weightUnit,LinearLayout.LayoutParams(-2,-2).apply {marginStart=style.dp(8)})
        freshness=style.text("",TaskProductVisualStyle.auxiliary,"product/scale/freshness",true).apply {gravity=android.view.Gravity.CENTER}
        val reading=ScaleReadingLayout(this,style,readingLabel,quantity,freshness)
        primary.addView(reading,LinearLayout.LayoutParams(-1,0,1f))
        tare=HoyiUi.button(this,primary,getString(R.string.scale_tare),
            primary=!intent.getBooleanExtra(EXTRA_CAPTURE_BEAN_DOSE,false)) {
            val service=owner ?: return@button
            if(BuildConfig.MOCK_MODE || !changesAllowed()) {message(getString(R.string.scale_active_guard));return@button}
            service.resetBeanDoseCapture()
            message(service.tareScale() ?: getString(R.string.scale_tare_requested));renderScale()
        }.apply {
            style.button(this,primary=!intent.getBooleanExtra(EXTRA_CAPTURE_BEAN_DOSE,false))
            tag="product/scale/tare"
            layoutParams=LinearLayout.LayoutParams(-2,-2).apply {gravity=android.view.Gravity.CENTER_HORIZONTAL;topMargin=style.dp(8)}
        }
        tareStatus=style.text("",TaskProductVisualStyle.auxiliary,"product/scale/tare-status",true).apply {gravity=android.view.Gravity.CENTER}
        style.add(primary,tareStatus,8)
        trend=ScaleTrendPanel(this,primary,secondary,visual)
        workspace.onPrimarySpaceAvailable={width,height->
            fun natural(view:android.view.View):Int {
                view.measure(android.view.View.MeasureSpec.makeMeasureSpec(width,android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(0,android.view.View.MeasureSpec.UNSPECIFIED))
                val params=view.layoutParams as LinearLayout.LayoutParams
                return view.measuredHeight+params.topMargin+params.bottomMargin
            }
            val buttonWidth=minOf(width,style.dp(240)).coerceAtLeast(0)
            (tare.layoutParams as LinearLayout.LayoutParams).width=buttonWidth
            tare.measure(android.view.View.MeasureSpec.makeMeasureSpec(buttonWidth,android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(0,android.view.View.MeasureSpec.UNSPECIFIED))
            val params=tare.layoutParams as LinearLayout.LayoutParams
            reading.requestInline(false)
            val verticalReadingHeight=natural(reading)
            val tareHeight=tare.measuredHeight+params.topMargin+params.bottomMargin
            val explanationHeight=natural(tareStatus)
            reading.requestInline(verticalReadingHeight+tareHeight+explanationHeight>height)
            val readingHeight=natural(reading)
            val required=readingHeight+tareHeight+explanationHeight
            val trendHeight=trend.fit(width,height-required)
            productLayoutEvidence.put("readingNaturalPx",readingHeight).put("tareNaturalPx",tareHeight)
                .put("readingVerticalNaturalPx",verticalReadingHeight).put("readingLayout",reading.productLayoutEvidence)
                .put("readingAndControlsBudgetSatisfiable",required<=height)
                .put("tareExplanationNaturalPx",explanationHeight).put("trendNaturalPx",trendHeight)
                .put("trendInline",trendHeight>0).put("trendBudget",trend.productLayoutEvidence)
            required+trendHeight
        }
        val timerCard=style.column("product/scale/timer")
        style.add(secondary,timerCard)
        style.add(timerCard,style.text(getString(R.string.scale_timer_title),TaskProductVisualStyle.section))
        timerValue=style.text("",TaskProductVisualStyle.largeValue,"product/scale/timer/value")
        style.add(timerCard,timerValue,16)
        style.add(timerCard,style.text(getString(R.string.m6_app_timer),TaskProductVisualStyle.auxiliary,secondary=true),8)
        style.add(timerCard,style.text(getString(R.string.scale_timer_help),TaskProductVisualStyle.auxiliary,secondary=true),8)
        val timerActions=style.row("product/scale/timer/actions")
        style.add(timerCard,timerActions,16)
        timerToggle=HoyiUi.button(this,timerActions,getString(R.string.scale_timer_start)) {
            if(timer.running)timer.pause(MobileMonotonicClock.elapsedRealtime())else timer.start(MobileMonotonicClock.elapsedRealtime());renderScale()
        }.apply {style.button(this,outline=true);tag="product/scale/timer/toggle";layoutParams=LinearLayout.LayoutParams(0,-2,1f).apply {marginEnd=style.dp(16)}}
        toolLink(timerActions,getString(R.string.scale_timer_reset),centered=true) {timer.reset();renderScale()}.apply {
            style.button(this);tag="product/scale/timer/reset";layoutParams=LinearLayout.LayoutParams(0,-2,1f)
        }
        if(intent.getBooleanExtra(EXTRA_CAPTURE_BEAN_DOSE,false))
            style.add(secondary,style.text(getString(R.string.scale_dose_help),TaskProductVisualStyle.auxiliary,secondary=true),16)
        val device=style.column("product/scale/device")
        style.add(secondary,device,24)
        style.add(device,style.text(getString(R.string.scale_device_section),TaskProductVisualStyle.section))
        var firstDeviceRow=true
        fun deviceRow(label:Int,id:String):TextView {
            device.addView(android.view.View(this).apply {setBackgroundColor(style.border)},
                LinearLayout.LayoutParams(-1,style.dp(1)).apply {topMargin=if(firstDeviceRow)style.dp(16) else 0})
            firstDeviceRow=false
            val row=style.row().apply {gravity=android.view.Gravity.CENTER_VERTICAL;setPadding(0,style.dp(8),0,style.dp(8));minimumHeight=style.dp(48)}
            style.add(device,row)
            row.addView(style.text(getString(label),TaskProductVisualStyle.auxiliary,secondary=true),LinearLayout.LayoutParams(0,-2,.35f).apply {marginEnd=style.dp(8)})
            return style.text("",TaskProductVisualStyle.body,id).apply {
                gravity=android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL
                row.addView(this,LinearLayout.LayoutParams(0,-2,.65f))
            }
        }
        battery=deviceRow(R.string.scale_device_battery_label,"product/scale/battery")
        deviceStatus=deviceRow(R.string.scale_device_status_label,"product/scale/device-status")
        connectionButton=toolLink(device,getString(R.string.scale_connect)) {startActivity(Intent(this,ScaleConnectionActivity::class.java))}.apply {
            style.button(this);HomeVisualIcon.buttonIcon(this,"chevron",atEnd=true);tag="product/scale/connect"
            layoutParams=LinearLayout.LayoutParams(-1,-2).apply {topMargin=style.dp(16)}
        }
        toolLink(device,getString(R.string.scale_settings)) {startActivity(Intent(this,ScaleSettingsActivity::class.java))}.apply {
            style.button(this);HomeVisualIcon.buttonIcon(this,"chevron",atEnd=true);tag="product/scale/settings"
            layoutParams=LinearLayout.LayoutParams(-1,-2).apply {topMargin=style.dp(8)}
        }
        // Construction only: keep the timer first; short-height trend details follow the routine tools.
        secondary.findViewWithTag<android.view.View>("scale-trend-fallback")?.let { fallback ->
            secondary.removeView(fallback)
            secondary.addView(fallback,LinearLayout.LayoutParams(-1,-2).apply {topMargin=style.dp(16)})
        }
        if(intent.getBooleanExtra(EXTRA_CAPTURE_BEAN_DOSE,false)) {
            capture=HoyiUi.button(this,body,getString(R.string.scale_dose_capture),primary=true) {
                val stable=stableDose(MobileMonotonicClock.elapsedRealtime())
                if(stable==null) {message(getString(R.string.scale_dose_not_ready));return@button}
                setResult(RESULT_OK,Intent().putExtra(EXTRA_BEAN_DOSE_HUNDREDTHS_GRAM,stable));finish()
            }.apply {style.button(this,primary=true);tag="product/scale/capture";layoutParams=LinearLayout.LayoutParams(-1,-2).apply {topMargin=style.dp(8)}}
        }
        renderScale()
    }
    override fun onSaveInstanceState(outState:Bundle) {
        if (redirectedRoot) { super.onSaveInstanceState(outState); return }
        val state=timer.snapshot();outState.putLong("scale.timer.accumulated",state.accumulatedMs)
        state.startedAtMs?.let {outState.putLong("scale.timer.origin",it)}
        super.onSaveInstanceState(outState)
    }
    override fun renderScale() {
        if(!::weight.isInitialized)return
        val service=owner;val snapshot=service?.snapshot;val now=MobileMonotonicClock.elapsedRealtime()
        val live=snapshot?.let {LiveTelemetry.scale(it.scaleObservation,it.scaleState,now)}
        val digits=if(getSharedPreferences("scale_tool",MODE_PRIVATE).getBoolean("one_decimal",false))1 else 2
        setWeightReading(live?.let {getString(R.string.scale_weight_format,String.format(Locale.getDefault(),"%.${digits}f",it.hundredthsGram/100.0))}
            ?: getString(R.string.scale_unavailable))
        freshness.setIfChanged(getString(when {
            snapshot?.scaleState!=DeviceState.READY->R.string.scale_disconnected
            live==null->R.string.scale_stale
            live.evidence==ScaleEvidence.OFFLINE_CANDIDATE->R.string.scale_offline_evidence
            live.evidence==ScaleEvidence.LIVE_READ_ONLY->R.string.scale_read_only_live
            else->R.string.scale_live
        }))
        deviceStatus.setIfChanged(freshness.text.toString())
        if (::trend.isInitialized) trend.present(service?.scaleTrendAt(now).orEmpty(), now, freshness.text.toString())
        battery.setIfChanged(live?.batteryPercent?.takeIf {live.capabilities.battery && it in 0..100}
            ?.let {getString(R.string.scale_battery_value,it)} ?: getString(R.string.scale_battery_unavailable))
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
    private fun toolLink(parent:LinearLayout, value:String, centered:Boolean=false, action:()->Unit):Button =
        HoyiUi.button(this,parent,value,action=action).apply {
            gravity=if(centered)android.view.Gravity.CENTER else android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
            setTextColor(android.content.res.ColorStateList(
                arrayOf(intArrayOf(-android.R.attr.state_enabled),intArrayOf()),
                intArrayOf(getColor(R.color.mobile_muted),getColor(R.color.mobile_accent))))
            val states=android.graphics.drawable.StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused),
                    HoyiUi.shape(this@ScaleActivity,android.R.color.transparent,8,R.color.mobile_accent))
                addState(intArrayOf(),HoyiUi.shape(this@ScaleActivity,android.R.color.transparent,8))
            }
            background=android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled),intArrayOf()),
                    intArrayOf(android.graphics.Color.TRANSPARENT,getColor(R.color.mobile_accent_soft))),
                states,HoyiUi.shape(this@ScaleActivity,android.R.color.white,8))
        }
    private fun setWeightReading(value: String) {
        val unit=getString(R.string.scale_weight_format,"").trim()
        val hasUnit=unit.isNotEmpty() && value.endsWith(unit)
        weight.setIfChanged(if(hasUnit)value.removeSuffix(unit).trimEnd() else value)
        weightUnit.setIfChanged(if(hasUnit)unit else "")
        weightUnit.visibility=if(hasUnit)android.view.View.VISIBLE else android.view.View.GONE
    }
    private fun TextView.setIfChanged(value:String) { if(text.toString()!=value) text=value }
}
