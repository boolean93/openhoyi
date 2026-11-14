package io.openhoyi.mobile

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.IdentityHashMap
import java.util.UUID
import java.util.concurrent.Executors

/** Download continues without a window; continuation into system UI requires live, revocable user intent. */
internal class ReleaseDownloadOwner(private val app: MobileApplication, private val updates: ReleaseUpdateOwner) {
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val verifier = ReleaseApkVerifier(app)
    private val bridge = ReleaseInstallerBridge(app)
    private val lease = ReleaseInstallLease(SystemClock::elapsedRealtime)
    private var safetyEpoch = 0L
    private var permissionSerial = 0L
    private val permissionLaunchers = IdentityHashMap<FragmentActivity, ActivityResultLauncher<android.content.Intent>>()
    private val permissionRequests = IdentityHashMap<Activity, Long>()
    private val journal = AtomicDocumentStorage(File(app.filesDir, "updates/attempt.json").toPath())
    private val verifiedDirectory = File(app.filesDir, "updates/verified")
    private val stagingDirectory = File(app.filesDir, "update-staging")
    private val activeWorkers = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val apkFiles = ReleaseApkFiles(verifiedDirectory, stagingDirectory)
    private var recoveredPart: File? = null
    private val ratePrefs = app.getSharedPreferences("release_download_limit", Context.MODE_PRIVATE)
    private var limitedUntil = runCatching { ratePrefs.getLong("until", 0) }.getOrDefault(Long.MAX_VALUE)
    private var rateFailures = runCatching { ratePrefs.getInt("failures", 0) }.getOrDefault(8).coerceIn(0,8)
    val rateLimited get() = System.currentTimeMillis() < limitedUntil
    private fun readState(): ReleaseDownloadController.State = runCatching {
        val json = JSONObject(journal.read()?.toString(Charsets.UTF_8) ?: return@runCatching ReleaseDownloadController.State())
        if (json.optBoolean("cleared")) return@runCatching ReleaseDownloadController.State()
        val target = ReleaseUpdateIndex.parse(json.getString("index"))
        val name = json.getString("file")
        require(name.matches(Regex("[a-f0-9-]{36}\\.apk")))
        val file = File(verifiedDirectory, name)
        recoveredPart = File(stagingDirectory, name.removeSuffix(".apk") + ".part")
        ReleaseDownloadController.State(if (file.isFile) ReleaseDownloadController.Phase.NEEDS_VERIFICATION
            else ReleaseDownloadController.Phase.INTERRUPTED, target, file)
    }.getOrDefault(ReleaseDownloadController.State())
    val controller = ReleaseDownloadController(readState(), ::save, ::download, ::verify, {
        var file: File
        do { file = File(verifiedDirectory, UUID.randomUUID().toString() + ".apk") }
        while (file.exists() || File(stagingDirectory, file.name.removeSuffix(".apk") + ".part").exists())
        file
    })
    private var starting = false
    init {
        val old = controller.state
        val actual = installedUpdateIdentity(app)
        apkFiles.cleanupInstalled(actual)
        if (controller.reconcileInstalled(actual)) cleanup(old, allowHandoff = true)
        controller.observe { state ->
            if (state.phase in setOf(ReleaseDownloadController.Phase.FAILED, ReleaseDownloadController.Phase.INTERRUPTED)) lease.revoke()
            if (lease.active) {
                reviewLease()
                if (!starting && state.phase == ReleaseDownloadController.Phase.READY) continueInstallation()
            }
        }
    }
    private fun save(state: ReleaseDownloadController.State): Boolean = runCatching {
        val target = state.target ?: run {
            journal.write(JSONObject().put("cleared", true).toString().toByteArray(Charsets.UTF_8))
            return@runCatching true
        }
        val raw = requireNotNull(target.indexJson)
        journal.write(JSONObject().put("index", raw).put("file", requireNotNull(state.file).name)
            .toString().toByteArray(Charsets.UTF_8)); true
    }.getOrDefault(false)
    private fun download(state: ReleaseDownloadController.State, progress: (Long, Boolean) -> Unit,
        done: (Result<File>) -> Unit): () -> Unit {
        val call = ReleaseApkDownload(verify = verifier::verify, diagnostic = { event ->
            runCatching { app.logs.record("update.transfer", event.fields()) }
        })
        val target = requireNotNull(state.target)
        val destination = requireNotNull(state.file)
        val part = File(stagingDirectory, destination.name.removeSuffix(".apk") + ".part")
        val cancelled = java.util.concurrent.atomic.AtomicBoolean(false)
        val workerStarted = java.util.concurrent.atomic.AtomicBoolean(false)
        activeWorkers += destination.name
        var completed = false // Main-thread terminal lease, independent of worker socket behaviour.
        var future: java.util.concurrent.Future<*>? = null
        val timeout = Runnable {
            if (!completed) {
                completed = true; cancelled.set(true); call.cancel(); future?.cancel(true)
                if (!workerStarted.get()) activeWorkers -= destination.name
                done(Result.failure(java.net.SocketTimeoutException("Download timed out")))
            }
        }
        future = executor.submit {
            workerStarted.set(true)
            var downloaded = false
            val result = runCatching {
                call.download(target, part) { bytes, verifying -> handler.post { progress(bytes, verifying) } }
                downloaded = true
                if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException()
                check(verifiedDirectory.isDirectory || verifiedDirectory.mkdirs())
                check(!destination.exists() && part.setReadOnly())
                Files.move(part.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
                destination
            }
            if (downloaded) part.delete()
            if (cancelled.get()) destination.delete() // Never published or granted by a retired worker.
            activeWorkers -= destination.name
            handler.post {
                if (completed) return@post
                completed = true; handler.removeCallbacks(timeout)
                val error = result.exceptionOrNull()
                if (error is ReleaseUpdateHttpException && (error.code == 429 || (error.code == 503 && error.retryAfter != null))) {
                    rateFailures = (rateFailures + 1).coerceAtMost(8)
                    val fallback = (15 * 60_000L * (1L shl (rateFailures - 1))).coerceAtMost(ReleaseUpdateCoordinator.DAY)
                    val delay = updateRetryDelay(error.retryAfter, System.currentTimeMillis()) ?: fallback
                    val now = System.currentTimeMillis()
                    limitedUntil = maxOf(limitedUntil, if (delay > Long.MAX_VALUE - now) Long.MAX_VALUE else now + delay)
                    ratePrefs.edit().putLong("until", limitedUntil).putInt("failures", rateFailures).commit()
                } else if (result.isSuccess) {
                    limitedUntil = 0; rateFailures = 0
                    ratePrefs.edit().putLong("until", 0).putInt("failures", 0).commit()
                }
                done(result)
            }
        }
        handler.postDelayed(timeout, 30 * 60_000L)
        return {
            completed = true; handler.removeCallbacks(timeout)
            cancelled.set(true); call.cancel(); future?.cancel(true)
            if (!workerStarted.get()) activeWorkers -= destination.name
        }
    }
    private fun verify(state: ReleaseDownloadController.State, done: (Result<Unit>) -> Unit): () -> Unit {
        val future = executor.submit {
            val result = runCatching { verifier.verify(requireNotNull(state.file), requireNotNull(state.target)) }
            handler.post { done(result) }
        }
        return { future.cancel(true) }
    }
    private fun page(activity: Activity): Any? = (activity as? FragmentActivity)?.supportFragmentManager?.primaryNavigationFragment
    private fun safe(activity: Activity) = updates.safeForUpdateAction(activity)
    private fun issue(activity: Activity, target: ReleaseUpdate, generation: Long) {
        lease.begin(ReleaseInstallLease.Token(generation, releaseUpdateKey(target),
            activity, page(activity), safetyEpoch))
    }
    fun start(activity: Activity, target: ReleaseUpdate): Boolean {
        if (!safe(activity) || controller.working || releaseUpdateStatus(installedUpdateIdentity(activity), target) != ReleaseUpdateStatus.AVAILABLE)
            return false
        lease.revoke()
        cleanup(controller.state)
        if (rateLimited || app.filesDir.usableSpace < target.apkSize + 8 * 1024 * 1024) {
            controller.reject(target); return false
        }
        starting = true
        issue(activity, target, controller.state.generation + 1)
        val started = controller.start(target)
        if (!started) lease.revoke()
        starting = false
        if (started && controller.state.phase == ReleaseDownloadController.Phase.READY) continueInstallation()
        updates.downloadIntentChanged()
        return started
    }
    fun install(activity: Activity): Boolean {
        if (!safe(activity) || controller.working || controller.state.target == null || controller.state.file?.isFile != true) return false
        lease.revoke()
        starting = true
        issue(activity, requireNotNull(controller.state.target), controller.state.generation + 1)
        val accepted = controller.reverify { valid -> if (valid) continueInstallation() else lease.revoke() }
        if (!accepted) lease.revoke()
        starting = false
        if (accepted && controller.state.phase == ReleaseDownloadController.Phase.READY) continueInstallation()
        updates.downloadIntentChanged()
        return accepted
    }
    fun cancel() { lease.revoke(); controller.cancel(); updates.downloadIntentChanged() }
    fun discard() {
        lease.revoke(); val old = controller.state; controller.clear()
        old.file?.let { file -> runCatching {
            app.revokeUriPermission(androidx.core.content.FileProvider.getUriForFile(app, app.packageName + ".updates", file),
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } }
        cleanup(old, allowHandoff = true); updates.downloadIntentChanged()
    }
    private fun cleanup(state: ReleaseDownloadController.State, allowHandoff: Boolean = false) {
        val old = state.file
        if (old != null && old.name !in activeWorkers && old.canonicalFile.parentFile == verifiedDirectory.canonicalFile) {
            state.target?.let { apkFiles.cleanup(old, it, allowHandoff) }
        }
        recoveredPart?.let { if (it.name.removeSuffix(".part") + ".apk" !in activeWorkers) it.delete() }
        recoveredPart = null
    }
    fun interrupt() { safetyEpoch++; lease.revoke() }
    fun stateMayHaveBecomeBusy() { if (lease.active && updates.updateBlocked()) interrupt() }
    private fun reviewLease() {
        if (!lease.active) return
        if (updates.updateBlocked()) { interrupt(); return }
        val activity = updates.currentActivity() ?: run { if (!lease.awaitingPermission) lease.revoke(); return }
        if (!lease.matches(controller.state.generation, controller.state.target?.let(::releaseUpdateKey).orEmpty(),
                activity, page(activity), safetyEpoch)) lease.revoke()
    }
    fun tick() { reviewLease() }
    val needsSafetyWatch get() = lease.active
    private fun continueInstallation() {
        reviewLease()
        val activity = updates.currentActivity() ?: return
        val state = controller.state
        val target = state.target ?: return
        if (!lease.matches(state.generation, releaseUpdateKey(target), activity, page(activity), safetyEpoch) ||
            !safe(activity) || state.phase != ReleaseDownloadController.Phase.READY) return
        if (releaseUpdateStatus(installedUpdateIdentity(activity), target) != ReleaseUpdateStatus.AVAILABLE) {
            lease.revoke(); return
        }
        if (!bridge.permitted()) {
            val launcher = (activity as? FragmentActivity)?.let(permissionLaunchers::get) ?: run { lease.revoke(); return }
            if (permissionRequests.containsKey(activity)) { lease.revoke(); return }
            val id = ++permissionSerial
            if (!lease.requestPermission(id)) return
            permissionRequests[activity] = id
            try { launcher.launch(bridge.permissionIntent()) } catch (_: Exception) {
                permissionRequests.remove(activity); lease.revoke()
            }
            return
        }
        // Reverification already ran on the worker. All final state/permission/safety checks and
        // consumption happen in one main-thread turn, with no automatic retry if launch fails.
        if (!lease.consume()) return
        try {
            val file = requireNotNull(state.file)
            if (!apkFiles.rememberHandoff(file, target)) return // Stop rather than hand out an unprotected URI.
            bridge.install(activity, file); controller.installerOpened()
        }
        catch (_: Exception) { /* Retain READY; a new explicit install action may retry. */ }
    }
    fun created(activity: Activity) {
        val host = activity as? FragmentActivity ?: return
        val launcher = host.activityResultRegistry.register("openhoyi-update-permission", host,
            ActivityResultContracts.StartActivityForResult()) {
            val id = permissionRequests.remove(host)
            if (id == null || !lease.permissionMatches(id)) return@register
            val expected = lease.snapshot() ?: return@register
            if (!lease.permissionResult(id) || !bridge.permitted()) { lease.revoke(); return@register }
            // ActivityResult may precede onResume; post only handles this matched result, never a bare resume.
            handler.post {
                if (!lease.owns(expected)) return@post // Never disturb an intent created after this result.
                if (!safe(host) || !lease.matches(expected.generation, expected.target, host, page(host), safetyEpoch) ||
                    controller.state.generation != expected.generation || controller.state.target?.let(::releaseUpdateKey) != expected.target) {
                    lease.revoke(); return@post
                }
                // reverify advances the generation; issue a new lease bound to the same matched flow.
                starting = true
                issue(host, requireNotNull(controller.state.target), controller.state.generation + 1)
                val accepted = controller.reverify { valid -> if (valid) continueInstallation() else lease.revoke() }
                if (!accepted) lease.revoke()
                starting = false
                if (accepted && controller.state.phase == ReleaseDownloadController.Phase.READY) continueInstallation()
                updates.downloadIntentChanged()
            }
        }
        permissionLaunchers[host] = launcher
        host.supportFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentPaused(fm: FragmentManager, fragment: Fragment) { lease.pageLeft(fragment) }
            override fun onFragmentDestroyed(fm: FragmentManager, fragment: Fragment) { lease.pageLeft(fragment, destroyed = true) }
        }, false)
    }
    fun resumed(activity: Activity) {
        val old = controller.state
        val actual = installedUpdateIdentity(app)
        apkFiles.cleanupInstalled(actual)
        if (controller.reconcileInstalled(actual)) { lease.revoke(); cleanup(old, allowHandoff = true) }
        lease.ownerResumed(activity, page(activity))
    }
    fun paused() { lease.paused() }
    fun destroyed(activity: Activity) {
        lease.ownerResumed(Any(), null)
        permissionRequests.remove(activity)
        (activity as? FragmentActivity)?.let { permissionLaunchers.remove(it)?.unregister() }
    }
}
