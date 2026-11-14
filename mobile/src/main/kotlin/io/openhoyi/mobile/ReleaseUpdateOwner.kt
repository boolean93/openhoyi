package io.openhoyi.mobile

import android.app.Activity
import android.app.Application
import android.app.KeyguardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.fragment.app.FragmentActivity
import io.openhoyi.bluetooth.SharedPreferenceTareStorage
import io.openhoyi.session.MachineWriteRecoveryState
import io.openhoyi.session.ShotRecoveryState
import java.util.concurrent.Executors
import kotlin.random.Random

/** Metadata scheduling never starts a download. Explicit download intent is delegated to its own owner. */
internal class ReleaseUpdateOwner(private val app: MobileApplication) : Application.ActivityLifecycleCallbacks {
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val prefs = app.getSharedPreferences("release_updates", Context.MODE_PRIVATE)
    private val installedIdentity = installedUpdateIdentity(app)
    private val automaticEligible = releaseAutomaticCheckEligible(installedIdentity)
    val coordinator = ReleaseUpdateCoordinator(object : ReleaseUpdateCoordinator.Storage {
        override fun read() = ReleaseUpdateCoordinator.Saved(prefs.getBoolean("enabled", true),
            prefs.getLong("nextAutoAt", 0), prefs.getLong("limitedUntil", 0),
            prefs.getInt("rateFailures", 0), prefs.getString("index", null), prefs.getInt("autoFailures", 0))
        override fun write(value: ReleaseUpdateCoordinator.Saved) = prefs.edit()
            .putBoolean("enabled", value.enabled).putLong("nextAutoAt", value.nextAutoAt)
            .putLong("limitedUntil", value.rateLimitedUntil).putInt("rateFailures", value.rateFailures)
            .putString("index", value.cachedIndex).putInt("autoFailures", value.autoFailures).commit()
    }, System::currentTimeMillis, { Random.nextLong(ReleaseUpdateCoordinator.MAX_JITTER + 1) }, ::request,
        automaticEligible = automaticEligible)
    private var resumed: Activity? = null
    private var service: MobileService? = null
    private var prompt: ReleaseUpdatePrompt? = null
    private var shown: ReleaseUpdate? = null
    private var progressPrompt: ReleaseDownloadProgressPrompt? = null
    private var progressOwner: java.lang.ref.WeakReference<Activity>? = null
    private var progressPage: java.lang.ref.WeakReference<Any>? = null
    private var progressSuppressed = true
    private val tick = Runnable { refresh() }
    internal val downloads = ReleaseDownloadOwner(app, this)
    init {
        app.registerActivityLifecycleCallbacks(this)
        app.visibility.observe("release-updates") { handler.post(tick) }
        coordinator.observe { refresh() }
        downloads.controller.observe { handler.post(tick) }
    }
    fun serviceCreated(value: MobileService) { service = value }
    fun serviceDestroyed(value: MobileService) { if (service === value) service = null }
    private fun request(result: (Result<ReleaseUpdate>) -> Unit): () -> Unit {
        val call = ReleaseUpdateHttpCall(installedVersionCode = installedIdentity?.versionCode,
            installedSignerSha256 = installedIdentity?.signerSha256, diagnostic = { event ->
            runCatching { app.logs.record("update.transfer", event.fields()) }
        })
        var finished = false // Main-thread callbacks own this lease; the worker only posts a value.
        var future: java.util.concurrent.Future<*>? = null
        val timeout = Runnable {
            if (!finished) {
                finished = true; call.cancel(); future?.cancel(true)
                result(Result.failure(java.net.SocketTimeoutException("Update check timed out")))
            }
        }
        future = executor.submit {
            val value = runCatching { call.execute() }
            handler.post {
                if (!finished) {
                    finished = true; handler.removeCallbacks(timeout); result(value)
                }
            }
        }
        handler.postDelayed(timeout, 10_000)
        return { finished = true; handler.removeCallbacks(timeout); call.cancel(); future?.cancel(true) }
    }
    private fun interactive() = app.getSystemService(PowerManager::class.java).isInteractive &&
        !app.getSystemService(KeyguardManager::class.java).isKeyguardLocked
    private fun foreground(activity: Activity) = resumed === activity && !activity.isFinishing &&
        !activity.isDestroyed && interactive() && app.visibility.visible &&
        (activity !is FragmentActivity || !activity.supportFragmentManager.isStateSaved)
    internal fun updateBlocked(): Boolean = runCatching {
        service?.updateInterruptionBlocked ?: (
            ShotRecoveryState(RecoveryPreferenceStorage.shot(app)).pending ||
                MachineWriteRecoveryState(RecoveryPreferenceStorage.machine(app)).pending ||
                SharedPreferenceTareStorage(app).read() || app.leverRecipes.blocked)
    }.getOrDefault(true)
    internal fun currentActivity() = resumed
    internal fun safeForUpdateAction(activity: Activity) = foreground(activity) && !updateBlocked()
    internal fun downloadIntentChanged() { handler.post(tick) }
    internal fun interruptionStateChanged() {
        if ((downloads.needsSafetyWatch || progressPrompt != null) && updateBlocked()) interruptInstallLease()
    }
    internal fun interruptInstallLease() { downloads.interrupt(); hideProgress(suppress = true) }
    fun download(activity: Activity, update: ReleaseUpdate): Boolean {
        val started = downloads.start(activity, update)
        if (started || (downloads.controller.state.phase == ReleaseDownloadController.Phase.FAILED &&
                downloads.controller.state.target == update && safeForUpdateAction(activity))) {
            coordinator.dismiss(update); hide()
            progressOwner = java.lang.ref.WeakReference(activity)
            progressPage = java.lang.ref.WeakReference((activity as? FragmentActivity)?.supportFragmentManager?.primaryNavigationFragment ?: activity)
            // Settings already observes this same controller through its inline update card.
            progressSuppressed = progressPage?.get() is AppSettingsFragment
        }
        return started
    }
    private fun refresh() {
        handler.removeCallbacks(tick)
        downloads.tick()
        val activity = resumed
        val active = activity != null && foreground(activity)
        coordinator.setForegroundIfChanged(active)
        if (!active) { hide(); hideProgress(suppress = true); return }
        renderProgress(activity!!)
        coordinator.automaticCheck()
        val potential = coordinator.promptCandidate(installedIdentity, safe = true)
        val candidate = potential?.takeIf { !updateBlocked() }
        if (shown != candidate) hide()
        if (candidate != null && prompt == null &&
            releaseUpdateStatus(installedUpdateIdentity(activity!!), candidate) == ReleaseUpdateStatus.AVAILABLE) {
            shown = candidate
            val value = ReleaseUpdatePrompt(activity, candidate, later = {
                coordinator.dismiss(candidate); prompt = null; shown = null
                handler.post(tick)
            }, open = {
                prompt = null; shown = null
                download(activity, candidate)
                handler.post(tick)
            })
            prompt = value
            try { value.show() } catch (_: android.view.WindowManager.BadTokenException) { hide() }

        }
        // Poll machine state only while an undismissed, eligible update awaits safe presentation.
        // Off/Mock/non-local Debug owners wake only for lifecycle or explicit user events.
        when {
            downloads.needsSafetyWatch || potential != null -> handler.postDelayed(tick, 250L)
            automaticEligible && coordinator.enabled -> handler.postDelayed(tick,
                (coordinator.nextCheckAt - System.currentTimeMillis()).coerceIn(1_000L, 60_000L))
        }
    }
    private fun hideProgress(suppress: Boolean) {
        if (suppress) progressSuppressed = true
        progressPrompt?.hide(); progressPrompt = null
    }
    private fun renderProgress(activity: Activity) {
        if (progressSuppressed) return
        val page = (activity as? FragmentActivity)?.supportFragmentManager?.primaryNavigationFragment ?: activity
        val state = downloads.controller.state
        if (!showReleaseDownloadProgress(
                settingsPage = page is AppSettingsFragment,
                suppressed = progressSuppressed,
                sameActivity = progressOwner?.get() === activity,
                samePage = progressPage?.get() === page,
                blocked = updateBlocked(), phase = state.phase)) {
            hideProgress(suppress = true); return
        }
        if (progressPrompt == null) {
            progressPrompt = ReleaseDownloadProgressPrompt(activity, action = {
                val current = downloads.controller.state
                when {
                    downloads.controller.working -> { downloads.cancel(); hideProgress(suppress = true) }
                    current.phase in setOf(ReleaseDownloadController.Phase.FAILED, ReleaseDownloadController.Phase.INTERRUPTED) ->
                        current.target?.let { download(activity, it) }
                    else -> downloads.install(activity)
                }
            }, dismissed = { downloads.interrupt(); hideProgress(suppress = true) }).also {
                it.render(state, downloads.rateLimited)
                try { it.show() } catch (_: android.view.WindowManager.BadTokenException) { hideProgress(suppress = true) }
            }
        }
        progressPrompt?.render(state, downloads.rateLimited)
    }
    // Avoid recursive refresh on initial foreground transitions or synchronous request completion.
    private var foregroundState = false
    private fun ReleaseUpdateCoordinator.setForegroundIfChanged(active: Boolean) {
        if (foregroundState == active) return
        foregroundState = active
        setForeground(active)
    }
    private fun hide() { prompt?.dismiss(); prompt = null; shown = null }
    override fun onActivityResumed(activity: Activity) { resumed = activity; downloads.resumed(activity); handler.post(tick) }
    override fun onActivityPaused(activity: Activity) {
        if (resumed !== activity) return
        downloads.paused()
        resumed = null; hide(); hideProgress(suppress = true); handler.removeCallbacks(tick)
        // A page transition is not a new network owner. Let the next resumed page inherit the flight.
        handler.postDelayed(tick, 250)
    }
    override fun onActivityDestroyed(activity: Activity) { if (resumed === activity) onActivityPaused(activity); downloads.destroyed(activity) }
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) { downloads.created(activity) }
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}
