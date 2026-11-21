package io.openhoyi.mobile

import android.content.Intent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Settings is only an entry point; credential, session and consent owners stay unchanged. */
internal object AiSettingsCard {
    fun create(activity: MainActivity, style: TaskProductVisualStyle): View {
        val refresh = mutableListOf<() -> Unit>()
        var observedModel: String? = null
        var requestGeneration = 0
        var cachedRevision: Long? = null
        fun invalidateModel() {
            requestGeneration++
            cachedRevision = null
            observedModel = null
        }
        fun refreshModel() {
            val revision = AiKeyStore(activity).revision()
            if (cachedRevision == revision) return
            cachedRevision = revision
            observedModel = null
            val generation = ++requestGeneration
            activity.lifecycleScope.launch {
                val model = withContext(Dispatchers.IO) {
                    val epoch = AiKeyStore(activity).credentialEpoch() ?: return@withContext null
                    val result = settingsObservedModel(AiSessionStore(activity).load(epoch))
                    if (AiKeyStore(activity).credentialEpoch() != epoch) return@withContext null
                    result
                }
                if (generation == requestGeneration) {
                    observedModel = model
                    refresh.forEach { it() }
                }
            }
        }
        val card = object : LinearLayout(activity) {
            override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
                super.onWindowFocusChanged(hasWindowFocus)
                if (hasWindowFocus) {
                    refreshModel()
                    refresh.forEach { it() }
                }
            }
        }.apply {
            orientation = LinearLayout.VERTICAL
            tag = "settings/ai/config"
            setPadding(style.dp(16), style.dp(12), style.dp(16), style.dp(12))
            background = style.shape(style.surface, true, 16)
            markSettingsGroupMaterial(style)
        }
        fun openChat(action: String? = null) {
            invalidateModel()
            activity.startActivity(Intent(activity, AiChatActivity::class.java)
                .apply { action?.let { putExtra(AiChatActivity.SETTINGS_ENTRY, it) } })
        }
        fun openKey(review: Boolean = false) {
            invalidateModel()
            activity.startActivity(Intent(activity, AiKeyActivity::class.java)
                .apply { if (review) putExtra(AiKeyActivity.SETTINGS_REVIEW, true) })
        }
        fun row(label: Int, id: String, value: (() -> String)? = null, click: () -> Unit) {
            if (card.childCount > 0) style.divider(card, top = 1, bottom = 1)
            val entry = style.row(id).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(style.dp(4), style.dp(8), style.dp(4), style.dp(8))
                style.clickable(this)
                setOnClickListener { click() }
            }
            val labelText = activity.getString(label)
            val title = style.text(labelText, TaskProductVisualStyle.body)
            entry.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
            val detail: TextView? = value?.let {
                style.text(it(), TaskProductVisualStyle.auxiliary, secondary = true).apply {
                    gravity = Gravity.END or Gravity.CENTER_VERTICAL
                    entry.addView(this, LinearLayout.LayoutParams(0, -2, 0.85f).apply { marginStart = style.dp(12) })
                }
            }
            entry.addView(style.text("›", TaskProductVisualStyle.body, secondary = true),
                LinearLayout.LayoutParams(-2, -2).apply { marginStart = style.dp(10) })
            fun update() {
                val current = value?.invoke()
                detail?.text = current
                entry.contentDescription = if (current.isNullOrEmpty()) labelText else "$labelText, $current"
            }
            for (index in 0 until entry.childCount) {
                entry.getChildAt(index).importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            refresh += ::update
            update()
            style.add(card, entry)
        }
        style.action(card, activity.getString(R.string.settings_ai_open), "settings/ai-assistant", top = 0) { openChat() }
            .apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL }
        val keyStore = AiKeyStore(activity)
        row(R.string.ai_key_title, "settings/ai/key", { activity.getString(when (keyStore.status()) {
            AiKeyStatus.MISSING -> R.string.settings_ai_not_set
            AiKeyStatus.SAVED -> R.string.ai_key_saved
            AiKeyStatus.UNAVAILABLE -> R.string.settings_ai_key_unavailable
        }) }) { openKey() }
        row(R.string.settings_ai_model, "settings/ai/model",
            { observedModel ?: activity.getString(if (keyStore.status() == AiKeyStatus.MISSING)
                R.string.settings_ai_not_set else R.string.settings_ai_key_unavailable) }) {
            openChat(AiChatActivity.SETTINGS_MODEL)
        }
        row(R.string.ai_reasoning_title, "settings/ai/reasoning",
            { activity.getString(AiReasoningPreference.read(activity).label) }) { openChat(AiChatActivity.SETTINGS_REASONING) }
        row(R.string.ai_usage_title, "settings/ai/usage") { openChat(AiChatActivity.SETTINGS_USAGE) }
        if (!BuildConfig.MOCK_MODE) {
            val reviews = (activity.application as MobileApplication).shotReviews
            row(R.string.settings_ai_review, "settings/ai/review", { activity.getString(when {
                reviews.revocationUnconfirmed -> R.string.shot_review_revoke_failed
                keyStore.status() == AiKeyStatus.MISSING -> R.string.settings_hig_ai_configure_key
                keyStore.status() == AiKeyStatus.UNAVAILABLE -> R.string.settings_ai_key_unavailable
                reviews.enabled() -> R.string.settings_ai_on
                else -> R.string.settings_ai_off
            }) }) { openKey(review = keyStore.status() == AiKeyStatus.SAVED) }
        }
        style.divider(card, top = 1, bottom = 4)
        if (BuildConfig.MOCK_MODE) {
            style.action(card, activity.getString(R.string.ai_preview_title), "settings/ai-chat-preview", top = 0) {
                activity.startActivity(Intent().setClassName(activity, "io.openhoyi.mobile.AiChatPreviewActivity"))
            }.apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL }
        }
        return card
    }
}

/** Matches the fresh conversation's actual model; unreadable history is not empty. */
internal fun settingsObservedModel(session: AiSessionLoad): String? {
    val id = when (session) {
        AiSessionLoad.Empty -> AiProtocolHistory.empty().modelId
        is AiSessionLoad.Ready -> session.session.modelId
        AiSessionLoad.Unavailable -> return null
    }
    return AiModelPolicy.models.firstOrNull { it.id == id }?.label
}
