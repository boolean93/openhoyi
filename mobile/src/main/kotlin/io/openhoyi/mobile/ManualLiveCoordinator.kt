package io.openhoyi.mobile

import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.lifecycle.Lifecycle

/** One host's presentation lease. No Activity routes or device commands. */
internal class ManualLiveCoordinator(private val host: ThemedActivity) {
    private val app get() = host.application as MobileApplication
    private val gate get() = app.manualShotPresentation
    private val manager get() = host.supportFragmentManager
    private val handler = Handler(Looper.getMainLooper())
    private var resumed = false
    private var topResumed = android.os.Build.VERSION.SDK_INT < 29
    private var bindingActive = false
    private var samples: ((String) -> ManualLiveSamples?)? = null
    private val changed: () -> Unit = ::schedule
    private val tick = Runnable { present() }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            if (!binding.bound) return
            val service = (binder as? MobileService.LocalBinder)?.service
            samples = service?.let { it::manualLiveSamples }
            schedule()
        }
        override fun onServiceDisconnected(name: ComponentName) { samples = null; schedule() }
        override fun onBindingDied(name: ComponentName) { binding.release() }
        override fun onNullBinding(name: ComponentName) { binding.release() }
    }
    private val binding: VisibleServiceBinding by lazy {
        VisibleServiceBinding(app.serviceAvailability,
            bind = { create ->
                check(!create) { "Manual live chart may only bind an existing service" }
                val accepted = host.bindService(Intent(host, MobileService::class.java), connection, 0)
                if (!accepted) host.unbindService(connection)
                accepted
            },
            unbind = { host.unbindService(connection) },
            onReleased = { samples = null; schedule() },
            onAvailable = ::schedule)
    }

    fun resume() {
        if (resumed) return
        resumed = true
        gate.observe(changed)
        schedule()
    }

    fun pause() {
        if (!resumed) return
        resumed = false
        gate.unobserve(changed)
        releaseBinding()
        handler.removeCallbacks(tick)
    }

    fun topChanged(isTop: Boolean) {
        topResumed = isTop
        if (!isTop) releaseBinding()
        schedule()
    }

    fun dialogClosed() = schedule()

    private fun schedule() {
        handler.removeCallbacks(tick)
        if (resumed && topResumed) handler.post(tick)
    }

    private fun releaseBinding() {
        if (!bindingActive) return
        bindingActive = false
        binding.stop()
    }

    private fun present() {
        val foreground = resumed && topResumed && !host.isFinishing && !host.redirectedRoot &&
            host.lifecycle.currentState == Lifecycle.State.RESUMED &&
            host.getSystemService(PowerManager::class.java)?.isInteractive == true &&
            host.getSystemService(KeyguardManager::class.java)?.isDeviceLocked != true
        if (!foreground || manager.isStateSaved) {
            releaseBinding()
            return
        }

        val dialog = manager.findFragmentByTag(ManualLiveDialog.TAG) as? ManualLiveDialog
        val id = gate.activeId
        if (dialog == null && id != null && host.showsManualLiveChart()) {
            gate.claim(id, foreground)
        }
        val eligible = id != null && gate.eligible(id, foreground)
        val needsSamples = eligible || dialog?.observationId == id && id != null
        if (!needsSamples) releaseBinding()
        else if (!bindingActive) {
            bindingActive = true
            binding.start()
        }

        if (id != null && needsSamples) {
            val frame = samples?.invoke(id)
            if (frame != null && frame.points.isNotEmpty()) {
                if (dialog == null && eligible) {
                    val next = ManualLiveDialog.open(id)
                    next.showNow(manager, ManualLiveDialog.TAG)
                    gate.claim(id, foreground)
                    next.render(id, frame)
                } else if (dialog != null) {
                    if (eligible) gate.claim(id, foreground)
                    dialog.render(id, frame)
                }
            }
        }
        if (dialog != null && dialog.observationId != id) {
            dialog.finishObservation(gate.terminalFor(dialog.observationId))
        }
        if (needsSamples) handler.postDelayed(tick, 250)
    }
}
