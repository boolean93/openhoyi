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

    fun observe(anchor: View, shotId: String, present: (ShotReviewResult?) -> Unit): () -> Unit {
        var observer: Job? = null
        fun start() {
            observer?.cancel()
            observer = scope.launch(Dispatchers.Main.immediate) {
                controller.changes.collect {
                    val result = kotlinx.coroutines.withContext(Dispatchers.IO) { controller.result(shotId) }
                    if (anchor.isAttachedToWindow) present(result)
                }
            }
        }
        val listener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = start()
            override fun onViewDetachedFromWindow(v: View) { observer?.cancel(); observer = null }
        }
        anchor.addOnAttachStateChangeListener(listener)
        if (anchor.isAttachedToWindow) start()
        return {
            anchor.removeOnAttachStateChangeListener(listener)
            observer?.cancel(); observer = null
            present(null)
        }
    }

    fun bindDetails(label: TextView, shotId: String): () -> Unit = observe(label, shotId) { result ->
        label.text = if (result?.status == ShotReviewStatus.READY)
            label.context.getString(R.string.shot_review_ready, result.text.orEmpty())
        else result?.let { label.context.getString(it.status.message()) }.orEmpty()
        label.visibility = if (result == null) View.GONE else View.VISIBLE
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


internal fun ShotReviewStatus.message(): Int = when (this) {
    ShotReviewStatus.PENDING -> R.string.shot_review_pending
    ShotReviewStatus.READY -> R.string.shot_review_open
    ShotReviewStatus.FAILED -> R.string.shot_review_failed
    ShotReviewStatus.UNKNOWN -> R.string.shot_review_unknown
}

/** Draws into the existing tools button; result state never participates in layout. */
internal class ShotReviewToolEntry(private val activity: android.app.Activity, private val button: View, private val style: TaskProductVisualStyle) {
    private var result: ShotReviewResult? = null
    private var menu: TextView? = null
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private val badge = object : android.graphics.drawable.Drawable() {
        override fun draw(canvas: android.graphics.Canvas) {
            val status = result?.status ?: return
            val d = button.resources.displayMetrics.density
            val left = if (button.layoutDirection == View.LAYOUT_DIRECTION_RTL) bounds.width() - 19*d else 3*d
            paint.color = if (status == ShotReviewStatus.READY) style.accent else style.ink
            canvas.drawRoundRect(left, 2*d, left+16*d, 14*d, 4*d, 4*d, paint)
            paint.color = style.surface
            paint.textAlign = android.graphics.Paint.Align.CENTER
            paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
            paint.textSize = 8*d
            canvas.drawText("AI",left+8*d,11*d,paint)
        }
        override fun setAlpha(alpha: Int) = Unit
        override fun setColorFilter(filter: android.graphics.ColorFilter?) = Unit
        @Deprecated("Deprecated in Java") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }
    init {
        button.overlay.add(badge)
        button.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ -> badge.setBounds(0, 0, r-l, b-t) }
    }
    fun present(next: ShotReviewResult?) {
        if (result == next) return
        result = next
        androidx.core.view.ViewCompat.setStateDescription(button, next?.let { button.context.getString(it.status.message()) })
        badge.invalidateSelf()
        updateMenu()
    }
    fun addMenu(parent: android.widget.LinearLayout, open: (String) -> Unit) {
        menu = HoyiUi.button(activity, parent, "") {
            result?.takeIf { it.status == ShotReviewStatus.READY }?.text?.let(open)
        }.apply { tag = "product/extraction/automatic-shot-review"; accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        updateMenu()
    }
    fun clearMenu() { menu = null }
    private fun updateMenu() {
        val status = result?.status
        menu?.apply {
            text = status?.let { context.getString(it.message()) }.orEmpty()
            isEnabled = status == ShotReviewStatus.READY
            visibility = if (status == null) View.GONE else View.VISIBLE
        }
    }
}
