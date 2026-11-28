package io.openhoyi.mobile

import android.app.AlertDialog
import android.content.Intent
import android.widget.Button
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Resolve the visible selection on tap and hand it to the existing chat disclosure flow. */
internal fun ThemedActivity.historyAiReviewButton(
    style: TaskProductVisualStyle,
    selectedId: () -> String?,
): Button = style.button(Button(this)).apply {
    tag = "product/history/ai-review"
    setText(R.string.completion_card_review)
    maxWidth = style.dp(112)
    val activity = this@historyAiReviewButton
    val app = application as MobileApplication
    val access = app.completionShotReviewAccess
    var reading = false
    setOnClickListener {
        val id = selectedId() ?: return@setOnClickListener
        if (reading) return@setOnClickListener
        if (!access.hasConfiguredKey()) {
            val message = if (AiKeyStore(activity).status() == AiKeyStatus.MISSING)
                R.string.ai_key_missing else R.string.ai_key_unavailable
            beanShowDialog(AlertDialog.Builder(activity)
                .setTitle(R.string.completion_card_review)
                .setMessage(message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.ai_key_title) { _, _ ->
                    startActivity(Intent(activity, AiKeyActivity::class.java))
                }.create())
            return@setOnClickListener
        }
        reading = true
        lifecycleScope.launch {
            try {
                val hasSamples = withContext(Dispatchers.IO) {
                    AiExtractionSamples.validId(id) && runCatching {
                        app.samples.loadPersisted(id).isNotEmpty()
                    }.getOrDefault(false)
                }
                if (isFinishing || isDestroyed || !isShown || !hasWindowFocus() || selectedId() != id) return@launch
                if (!hasSamples) {
                    beanShowDialog(AlertDialog.Builder(activity)
                        .setTitle(R.string.completion_card_review)
                        .setMessage(R.string.completion_card_no_samples)
                        .setPositiveButton(android.R.string.ok, null).create())
                    return@launch
                }
                val ticket = access.issueHistory(id) ?: return@launch
                try { startActivity(AiChatActivity.completionReviewIntent(activity, ticket)) }
                catch (_: RuntimeException) { access.abandon(ticket) }
            } finally {
                reading = false
            }
        }
    }
}
