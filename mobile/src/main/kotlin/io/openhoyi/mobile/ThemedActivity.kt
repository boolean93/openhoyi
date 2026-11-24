package io.openhoyi.mobile

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.FragmentActivity

/** App-owned appearance. System density/window geometry stay intact; font size is independent. */
abstract class ThemedActivity : FragmentActivity() {
    internal var redirectedRoot = false
    private lateinit var appliedAppearance: AppAppearance
    internal open val extractionPageHost: ExtractionPageHost? get() = null
    private val standaloneManualGate get() = (application as MobileApplication).manualShotPresentation
    private var topManualWindow = android.os.Build.VERSION.SDK_INT < 29
    private var rejectedObservation: String? = null
    private val offerManualWindow = Runnable {
        if (this is MainActivity || this is PassiveExtractionHostActivity || !manualWindowForeground()) return@Runnable
        val observation = standaloneManualGate.activeId ?: return@Runnable
        if (observation == rejectedObservation || !standaloneManualGate.offerWindow(observation,true)) return@Runnable
        try {
            startActivity(android.content.Intent(this,PassiveExtractionHostActivity::class.java)
                .putExtra(PassiveExtractionHostActivity.EXTRA_OBSERVATION,observation))
        } catch (_: Exception) {
            rejectedObservation = observation
            standaloneManualGate.dropWindow(observation)
        }
    }
    private val standaloneObservationChanged:()->Unit=::scheduleManualWindow

    internal fun manualWindowForeground():Boolean =
        topManualWindow && hasWindowFocus() && !isFinishing && !redirectedRoot &&
            lifecycle.currentState==androidx.lifecycle.Lifecycle.State.RESUMED &&
            !supportFragmentManager.isStateSaved &&
            getSystemService(android.app.KeyguardManager::class.java)?.isDeviceLocked!=true &&
            getSystemService(android.os.PowerManager::class.java)?.isInteractive==true

    private fun scheduleManualWindow() {
        window.decorView.removeCallbacks(offerManualWindow)
        window.decorView.post(offerManualWindow)
    }

    override fun onPostResume() {
        super.onPostResume()
        if (this !is MainActivity && this !is PassiveExtractionHostActivity) {
            standaloneManualGate.observe(standaloneObservationChanged)
            scheduleManualWindow()
        }
    }
    override fun onPause() {
        standaloneManualGate.unobserve(standaloneObservationChanged)
        window.decorView.removeCallbacks(offerManualWindow)
        super.onPause()
    }
    override fun onWindowFocusChanged(hasFocus:Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && this !is MainActivity && this !is PassiveExtractionHostActivity) scheduleManualWindow()
    }
    override fun onTopResumedActivityChanged(isTopResumedActivity:Boolean) {
        super.onTopResumedActivityChanged(isTopResumedActivity)
        topManualWindow = isTopResumedActivity
        if (isTopResumedActivity && this !is MainActivity && this !is PassiveExtractionHostActivity) scheduleManualWindow()
    }

    override fun attachBaseContext(newBase: Context) {
        val app = newBase.applicationContext as MobileApplication
        appliedAppearance = app.appearancePreferences.current
        val configured = AppLanguageContext.wrap(newBase, app.languagePreferences.current,
            appliedAppearance.dark, app.fontSizePreferences.current)
        super.attachBaseContext(AppAppearanceContext(configured, appliedAppearance))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        theme.applyStyle(appliedAppearance.palette.nativeOverlay, true)
        if (resources.configuration.locales[0].language == "zh") theme.applyStyle(R.style.MobileChineseTypography, true)
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        val app = application as MobileApplication
        if (appliedAppearance != app.appearancePreferences.current ||
            resources.configuration.locales[0] != app.languagePreferences.current.locale ||
            resources.configuration.fontScale != app.fontSizePreferences.current.scale) recreate()
    }
}
