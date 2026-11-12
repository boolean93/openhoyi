package io.openhoyi.mobile

import java.io.File

/** One process-owned immutable download target; callbacks can never switch an in-flight target. */
internal class ReleaseDownloadController(
    restored: State = State(),
    private val save: (State) -> Boolean,
    private val download: (State, (Long, Boolean) -> Unit, (Result<File>) -> Unit) -> (() -> Unit),
    private val verify: (State, (Result<Unit>) -> Unit) -> (() -> Unit),
    private val newFile: () -> File,
) {
    enum class Phase { IDLE, DOWNLOADING, VERIFYING, READY, NEEDS_VERIFICATION, INTERRUPTED, FAILED, INSTALLER_OPENED }
    data class State(val phase: Phase = Phase.IDLE, val target: ReleaseUpdate? = null,
        val file: File? = null, val received: Long = 0, val generation: Long = 0)
    var state = restored
        private set
    private var generation = restored.generation
    private var cancel: (() -> Unit)? = null
    private val observers = mutableSetOf<(State) -> Unit>()
    val working get() = state.phase in setOf(Phase.DOWNLOADING, Phase.VERIFYING)
    fun observe(value: (State) -> Unit) { observers += value; value(state) }
    fun unobserve(value: (State) -> Unit) { observers -= value }
    private fun publish(value: State) { state = value; observers.toList().forEach { it(value) } }
    fun start(target: ReleaseUpdate): Boolean {
        if (working) return false
        val value = State(Phase.DOWNLOADING, target, newFile(), generation = ++generation)
        if (!save(value)) { publish(value.copy(phase = Phase.FAILED)); return false }
        publish(value)
        try {
            val stop = download(value, { bytes, verifying ->
                if (generation == value.generation && working) publish(state.copy(received = bytes,
                    phase = if (verifying) Phase.VERIFYING else Phase.DOWNLOADING))
            }, { result ->
                if (generation == value.generation && working) {
                    cancel = null
                    val next = result.fold({ state.copy(phase = Phase.READY, file = it) }, { state.copy(phase = Phase.FAILED) })
                    save(next); publish(next)
                }
            })
            if (generation == value.generation && working) cancel = stop else runCatching(stop)
        } catch (_: Exception) { if (generation == value.generation) publish(value.copy(phase = Phase.FAILED)) }
        return true
    }
    fun reverify(done: (Boolean) -> Unit): Boolean {
        if (working || state.file?.isFile != true || state.target == null) return false
        val value = state.copy(phase = Phase.VERIFYING, generation = ++generation)
        publish(value)
        try {
            val stop = verify(value) { result ->
                if (generation == value.generation && state.phase == Phase.VERIFYING) {
                    cancel = null
                    val next = value.copy(phase = if (result.isSuccess) Phase.READY else Phase.FAILED)
                    save(next); publish(next); done(result.isSuccess)
                }
            }
            if (generation == value.generation && working) cancel = stop else runCatching(stop)
        } catch (_: Exception) {
            if (generation == value.generation) { publish(value.copy(phase = Phase.FAILED)); done(false) }
        }
        return true
    }
    fun cancel() {
        generation++
        cancel?.let { runCatching(it) }; cancel = null
        val next = state.copy(phase = Phase.INTERRUPTED, generation = generation)
        save(next); publish(next)
    }
    fun reject(target: ReleaseUpdate) { publish(State(Phase.FAILED, target, generation = ++generation)) }
    fun clear() {
        generation++
        cancel?.let { runCatching(it) }; cancel = null
        val next = State(generation = generation)
        save(next); publish(next)
    }
    fun reconcileInstalled(identity: InstalledUpdateIdentity?): Boolean {
        val target = state.target ?: return false
        if (identity?.packageName != "io.openhoyi.mobile" || identity.debuggable ||
            identity.signerSha256 != target.signerSha256 || identity.versionCode < target.versionCode) return false
        clear(); return true
    }
    fun installerOpened() { publish(state.copy(phase = Phase.INSTALLER_OPENED)) }
}
