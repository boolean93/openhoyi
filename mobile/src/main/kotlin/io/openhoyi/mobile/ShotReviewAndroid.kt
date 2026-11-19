package io.openhoyi.mobile

import android.content.Context
import android.content.SharedPreferences
import android.view.View
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

internal class AppShotReview(private val context: Context) {
    private val key = AiKeyStore(context)
    private val settings = context.getSharedPreferences("ai_automatic_shot_review_setting_v1", Context.MODE_PRIVATE)
    private val preferences = ShotReviewPreferences(
        loadEpoch = { settings.getString("authorized_deepseek_epoch", null) },
        persistEpoch = { epoch -> settings.edit().putString("authorized_deepseek_epoch", epoch).commit() },
    )
    private val transports = ConcurrentHashMap<AiSafeTransport, ShotReviewConfig>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ledger by lazy { PreferenceShotReviewLedger(
        context.getSharedPreferences("ai_automatic_shot_review_attempts_v1", Context.MODE_PRIVATE)) }
    private val controller by lazy {
        AutomaticShotReview(scope, ::current, ledger, ::send, publishIfCurrent = { cfg, publish ->
            key.publishForRevision(cfg.keyRevision) { epoch ->
                preferences.publishForRevision(epoch, cfg.consentRevision, publish)
            }
        })
    }
    init { key.observeMutation(::cancelTransport) }

    @Volatile private var ready = false
    fun warmup() {
        key.hasSavedValue() // Prefs are loaded without opening the keystore or revealing a secret.
        ready = true
    }

    fun enabled(): Boolean = preferences.revision(key.credentialEpoch()) != null && key.hasSavedValue()

    fun setEnabled(enabled: Boolean): Boolean {
        val epoch = if (enabled && key.status() == AiKeyStatus.SAVED) key.credentialEpoch() else null
        if (enabled && epoch == null) return false
        return preferences.set(epoch).also { if (!enabled) cancelTransport() }
    }

    fun config(language: AppLanguage): ShotReviewConfig? {
        // The Mock never contacts a live provider, even with a key explicitly saved.
        if (BuildConfig.MOCK_MODE || !ready) return null
        val keyRevision = key.revision()
        if (!key.hasSavedValue()) return null
        val allowed = preferences.enabledRevision() ?: return null
        return ShotReviewConfig(allowed, keyRevision, language)
    }

    private fun current(cfg: ShotReviewConfig): Boolean =
        key.revision() == cfg.keyRevision && key.hasSavedValue() &&
            preferences.revision(key.credentialEpoch()) == cfg.consentRevision

    fun afterPersisted(entry: ShotHistory.Entry, points: List<ShotPoint>, cfg: ShotReviewConfig) {
        if (entry.status == ShotHistory.Status.ENDED && entry.machineTimingInterrupted != true)
            controller.afterPersisted(entry.id, cfg, points, entry.observedMachineElapsedSeconds)
    }

    private suspend fun send(cfg: ShotReviewConfig, request: String): String {
        val transport = AiSafeTransport(key = { key.readForRequest(cfg.keyRevision) },
            keyStillCurrent = { current(cfg) }, budget = AiRequestBudget(), maxResponseBytes = 32_768)
        transports[transport] = cfg
        try {
            // No retryState, model tools, or web search. Same allow-listed endpoint as manual chat.
            return ShotReviewWire.send(transport, request)
        } finally {
            transports.remove(transport)
            transport.close()
        }
    }
    private fun cancelTransport() {
        // Close only transports whose authorization already changed. A new valid request
        // may have started concurrently with an unrelated credential/settings mutation.
        scope.launch { transports.forEach { (transport, cfg) -> if (!current(cfg)) transport.close() } }
    }

    fun bind(label: TextView, shotId: String): () -> Unit {
        var observer: Job? = null
        fun start() {
            observer?.cancel()
            observer = scope.launch(Dispatchers.Main.immediate) {
                controller.changes.collect {
                    val result = kotlinx.coroutines.withContext(Dispatchers.IO) { controller.result(shotId) }
                    if (!label.isAttachedToWindow) return@collect
                    label.text = when (result?.status) {
                        ShotReviewStatus.PENDING -> label.context.getString(R.string.shot_review_pending)
                        ShotReviewStatus.READY -> label.context.getString(R.string.shot_review_ready, result.text.orEmpty())
                        ShotReviewStatus.FAILED -> label.context.getString(R.string.shot_review_failed)
                        ShotReviewStatus.UNKNOWN -> label.context.getString(R.string.shot_review_unknown)
                        null -> ""
                    }
                    label.visibility = if (result == null) View.GONE else View.VISIBLE
                }
            }
        }
        val listener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = start()
            override fun onViewDetachedFromWindow(v: View) { observer?.cancel(); observer = null }
        }
        label.addOnAttachStateChangeListener(listener)
        if (label.isAttachedToWindow) start()
        return {
            label.removeOnAttachStateChangeListener(listener)
            observer?.cancel(); observer = null
            label.text = ""; label.visibility = View.GONE
        }
    }
}

private class PreferenceShotReviewLedger(private val prefs: SharedPreferences) : ShotReviewLedger {
    @Synchronized override fun reserve(id: String): Boolean =
        !prefs.contains(id) && prefs.edit().putString(id, ShotReviewStatus.PENDING.name).commit()

    override fun finish(id: String, result: ShotReviewResult) {
        prefs.edit().putString(id, result.status.name + (result.text?.let { "\n$it" } ?: "")).commit()
    }

    override fun read(id: String): ShotReviewResult? {
        val value = prefs.getString(id, null) ?: return null
        val status = ShotReviewStatus.entries.firstOrNull { it.name == value.substringBefore('\n') } ?: return null
        val text = value.substringAfter('\n', "").take(ShotReviewWire.RESPONSE_CHARS).ifBlank { null }
        return ShotReviewResult(status, text)
    }
}
