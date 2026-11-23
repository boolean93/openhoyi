package io.openhoyi.mobile

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.FrameLayout

/** Pushes the actual extraction Fragment above a standalone Activity without replacing its task. */
class PassiveExtractionHostActivity : ThemedActivity() {
    private val gate get() = (application as MobileApplication).manualShotPresentation
    private var observation: String? = null
    private var shown = false
    private var touched = false
    private var bound = false
    private var service: MobileService? = null
    private val observers = mutableSetOf<(MobileService?) -> Unit>()
    private val examine = Runnable { presentIfEligible() }
    private val changed: () -> Unit = ::schedule
    private var containerId = View.NO_ID
    internal override val extractionPageHost = object : ExtractionPageHost {
        override val observeOnly = true
        override fun visibleService(candidate:MobileService?):MobileService? {
            val matching=observation?.let {id->candidate?.takeIf {it.manualChartIdentity==id}}
            if(shown && candidate?.running==true && matching==null) schedule()
            return matching
        }
        override fun observeService(observer:(MobileService?)->Unit) { observers += observer; observer(service) }
        override fun removeServiceObserver(observer:(MobileService?)->Unit) { observers -= observer }
    }
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name:ComponentName,binder:IBinder) {
            if (!bound) return
            service=(binder as? MobileService.LocalBinder)?.service
            observers.toList().forEach {it(service)}
            schedule()
        }
        override fun onServiceDisconnected(name:ComponentName) {
            service=null; observers.toList().forEach {it(null)}
        }
        override fun onNullBinding(name:ComponentName) {finish()}
        override fun onBindingDied(name:ComponentName) {finish()}
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        observation=intent.getStringExtra(EXTRA_OBSERVATION)
        shown=savedInstanceState?.getBoolean("shown")==true
        touched=savedInstanceState?.getBoolean("touched")==true
        val id=observation
        if (id==null || if (shown) gate.activeId!=id && gate.terminalFor(id)==null else !gate.hasWindowOffer(id)) {
            finish()
            return
        }
        containerId=savedInstanceState?.getInt("container",View.NO_ID)?.takeIf {it!=View.NO_ID} ?: View.generateViewId()
        val style=TaskProductVisualStyle(this)
        val root=android.widget.LinearLayout(this).apply { orientation=android.widget.LinearLayout.VERTICAL }
        HoyiUi.button(this,root,getString(android.R.string.cancel)) { finish() }
        root.addView(FrameLayout(this).apply {this.id=containerId;setBackgroundColor(style.page)},
            android.widget.LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root)
        // Observe an already running owner. Never create/start one from this route.
        bound=bindService(Intent(this,MobileService::class.java),connection,0)
        if(!bound) {
            runCatching {unbindService(connection)}
            finish()
        }
    }
    override fun onPostResume() {
        super.onPostResume()
        gate.observe(changed);schedule()
    }
    override fun onPause() {
        gate.unobserve(changed)
        window.decorView.removeCallbacks(examine)
        super.onPause()
    }
    override fun onWindowFocusChanged(hasFocus:Boolean) {super.onWindowFocusChanged(hasFocus);if(hasFocus)schedule()}
    override fun onTopResumedActivityChanged(isTopResumedActivity:Boolean) {
        super.onTopResumedActivityChanged(isTopResumedActivity)
        if(isTopResumedActivity)schedule()
    }
    override fun onUserInteraction() {super.onUserInteraction();touched=true}
    private fun schedule() {
        window.decorView.removeCallbacks(examine)
        window.decorView.post(examine)
    }
    private fun presentIfEligible() {
        if (!manualWindowForeground()) return
        val id=observation ?: return
        if (shown) {
            // Never reuse this visible lease for a different cup.
            if(gate.activeId?.let {it!=id}==true ||
                service?.let {it.running && it.manualChartIdentity!=id}==true) {finish();return}
            val outcome=gate.terminalFor(id)?.outcome
            if(!touched && (outcome==ManualShotPresentationGate.Outcome.DISCARDED ||
                outcome==null && gate.activeId!=id)) finish()
            return
        }
        if (!gate.hasWindowOffer(id)) {finish();return}
        if (service?.running != true || service?.manualChartIdentity != id) return
        val fragment=ExtractionFragment()
        supportFragmentManager.beginTransaction().add(containerId,fragment,"manual:extraction")
            .setPrimaryNavigationFragment(fragment).commitNow()
        if (gate.acceptWindow(id,true)) shown=true else finish()
    }
    override fun onSaveInstanceState(outState:Bundle) {
        outState.putInt("container",containerId);outState.putBoolean("shown",shown);outState.putBoolean("touched",touched)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() {
        window.decorView.removeCallbacks(examine)
        if (!shown && !isChangingConfigurations) observation?.let(gate::dropWindow)
        if(bound){bound=false;unbindService(connection)}
        super.onDestroy()
    }
    companion object {internal const val EXTRA_OBSERVATION="internal_passive_observation_id"}
}
