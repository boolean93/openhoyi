package io.openhoyi.mobile

import android.content.Context
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
    private val grant = AndroidShotReviewGrant()
    private val preferences = ShotReviewPreferences(
        loadEpoch = { settings.getString("authorized_deepseek_epoch", null) },
        persistEpoch = { epoch -> settings.edit().putString("authorized_deepseek_epoch", epoch).commit() },
        grantExists = grant::exists,
        revokeGrant = grant::revoke,
        createGrant = grant::create,
    )
    private val transports = ConcurrentHashMap<AiSafeTransport, ShotReviewConfig>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ledger by lazy {
        val file = java.io.File(context.filesDir, "ai_automatic_shot_review_attempts_v1.json")
        val document = AtomicDocumentStorage(file.toPath())
        BoundedShotReviewLedger(readBytes = {
            require(!file.exists() || file.length() <= BoundedShotReviewLedger.MAX_BYTES)
            document.read()
        }, writeBytes = document::write)
    }
    private val controller by lazy {
        AutomaticShotReview(scope, ::current, ledger, ::send, publishIfCurrent = { cfg, publish ->
            withAuthorization(cfg, publish)
        })
    }
    init { key.observeMutation(::cancelTransport) }

    @Volatile private var ready = false
    fun warmup() {
        key.hasSavedValue() // Prefs are loaded without opening the keystore or revealing a secret.
        ready = true
    }

    fun enabled(): Boolean = preferences.revision(key.credentialEpoch()) != null && key.hasSavedValue()

    val revocationUnconfirmed: Boolean get() = preferences.revocationUnconfirmed

    private fun withAuthorization(cfg: ShotReviewConfig, action: () -> Unit): Boolean =
        key.publishForRevision(cfg.keyRevision) { epoch ->
            preferences.publishForRevision(epoch, cfg.consentRevision, action)
        }

    fun setEnabled(enabled: Boolean): Boolean {
        if (!enabled) return preferences.set(null).also { cancelTransport() }
        val snapshot = key.revision()
        if (key.status() != AiKeyStatus.SAVED) return false
        return key.publishForRevision(snapshot, preferences::set)
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

    fun updateRetained(ids: Set<String>) {
        if (!ready) return
        ledger.publishRetained(ids)
        scope.launch { ledger.prune() }
    }

    fun afterPersisted(entry: ShotHistory.Entry, points: List<ShotPoint>, cfg: ShotReviewConfig) {
        controller.afterPersisted(entry.id, cfg, points, entry.observedMachineElapsedSeconds)
    }

    private suspend fun send(cfg: ShotReviewConfig, request: String): String {
        if (!runCatching(grant::exists).getOrDefault(false)) throw AiFailure(AiProblem.KEY)
        val transport = AiSafeTransport(key = { key.readForRequest(cfg.keyRevision) },
            keyStillCurrent = { current(cfg) }, budget = AiRequestBudget(), maxResponseBytes = 32_768,
            beforeWrite = { withAuthorization(cfg) {} })
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

/** A separate durable gate: an inaccessible/missing alias is denial, never implicit consent. */
private class AndroidShotReviewGrant {
    private val alias = "io.openhoyi.mobile.auto_shot_review_grant.v1"
    private fun store() = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    fun exists() = store().containsAlias(alias)
    fun revoke() = store().let { keyStore ->
        if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
        !keyStore.containsAlias(alias)
    }
    fun create(): Boolean {
        if (exists()) return true
        val spec = android.security.keystore.KeyGenParameterSpec.Builder(alias,
            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT)
            .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build()
        javax.crypto.KeyGenerator.getInstance(android.security.keystore.KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply { init(spec) }.generateKey()
        return exists()
    }
}
