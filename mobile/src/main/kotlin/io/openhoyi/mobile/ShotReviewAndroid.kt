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
    private val liveController by lazy {
        AutomaticShotReview(scope, ::current, ledger, ::send, publishIfCurrent = { cfg, publish ->
            withAuthorization(cfg, publish)
        })
    }
    private data class MockFixture(val enabled: () -> Boolean, val ledger: BoundedShotReviewLedger, val controller: AutomaticShotReview)
    @Volatile private var mockFixture: MockFixture? = null
    private val controller get() = mockFixture?.controller ?: liveController
    internal fun installMockFixture(enabled: () -> Boolean, send: suspend (String) -> String): () -> Unit {
        check(BuildConfig.MOCK_MODE && context.packageName == "io.openhoyi.mobile.mock" && mockFixture == null)
        var bytes: ByteArray? = null
        val store = BoundedShotReviewLedger({ bytes }, { bytes = it })
        val owner = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
        val fake = MockFixture(enabled, store, AutomaticShotReview(owner, { enabled() }, store, { _, payload -> send(payload) }))
        mockFixture = fake
        return {
            check(mockFixture === fake)
            mockFixture = null
            owner.coroutineContext[Job]?.cancel()
        }
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
        if (BuildConfig.MOCK_MODE) return mockFixture?.takeIf { it.enabled() }?.let {
            ShotReviewConfig(Long.MIN_VALUE, Long.MIN_VALUE, language)
        }
        if (!ready) return null
        val keyRevision = key.revision()
        if (!key.hasSavedValue()) return null
        val allowed = preferences.enabledRevision() ?: return null
        return ShotReviewConfig(allowed, keyRevision, language)
    }

    private fun current(cfg: ShotReviewConfig): Boolean =
        key.revision() == cfg.keyRevision && key.hasSavedValue() &&
            preferences.revision(key.credentialEpoch()) == cfg.consentRevision

    fun updateRetained(ids: Set<String>) {
        val target = if (BuildConfig.MOCK_MODE) mockFixture?.ledger ?: return
            else if (ready) ledger else return
        target.publishRetained(ids)
        scope.launch { target.prune() }
    }

    fun afterPersisted(entry: ShotHistory.Entry, points: List<ShotPoint>, cfg: ShotReviewConfig) {
        controller.afterPersisted(entry.id, cfg, points, entry.observedMachineElapsedSeconds)
    }

    private suspend fun send(cfg: ShotReviewConfig, request: String): String {
        if (!runCatching(grant::exists).getOrDefault(false)) throw AiFailure(AiProblem.KEY)
        val transport = AiSafeTransport(key = { key.readForRequest(cfg.keyRevision) },
            keyStillCurrent = { current(cfg) }, budget = AiRequestBudget(), maxResponseBytes = 32_768,
            sendPermit = { withAuthorization(cfg) {} })
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

    fun bind(label: TextView, shotId: String, openDetails: ((String) -> Unit)? = null): () -> Unit {
        var observer: Job? = null
        fun action(report: String?) {
            val click = if (openDetails != null && report != null) View.OnClickListener {
                if (label.isAttachedToWindow) openDetails(report)
            } else null
            label.setOnClickListener(click)
            label.isClickable = click != null
            label.isFocusable = click != null
            if (openDetails != null) {
                val style = TaskProductVisualStyle(label.context)
                label.minimumHeight = if (click != null) style.dp(48) else 0
                label.setTextColor(if (click != null) style.accent else style.ink)
                label.paint.isUnderlineText = click != null
            }
        }
        fun start() {
            observer?.cancel()
            observer = scope.launch(Dispatchers.Main.immediate) {
                controller.changes.collect {
                    val result = kotlinx.coroutines.withContext(Dispatchers.IO) { controller.result(shotId) }
                    if (!label.isAttachedToWindow) return@collect
                    val readyText = result?.takeIf { it.status == ShotReviewStatus.READY }?.text
                    action(readyText)
                    label.text = when (result?.status) {
                        ShotReviewStatus.PENDING -> label.context.getString(R.string.shot_review_pending)
                        ShotReviewStatus.READY -> if (openDetails != null) label.context.getString(R.string.shot_review_open)
                            else label.context.getString(R.string.shot_review_ready, readyText.orEmpty())
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
            override fun onViewDetachedFromWindow(v: View) { observer?.cancel(); observer = null; action(null) }
        }
        label.addOnAttachStateChangeListener(listener)
        if (label.isAttachedToWindow) start()
        return {
            label.removeOnAttachStateChangeListener(listener)
            observer?.cancel(); observer = null
            action(null)
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

internal fun showShotReviewDetails(context: Context, review: String): android.app.AlertDialog = AppDialog.show(
    context, context.getString(R.string.shot_review_title),
    TextView(context).apply {
        tag = "product/extraction/ai-feedback-details"
        text = review
        maxLines = Int.MAX_VALUE
        ellipsize = null
    },
    AppDialog.Action(context.getString(android.R.string.ok), onClick = {}),
)
