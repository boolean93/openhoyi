package io.openhoyi.mobile

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** A single-cup review transcript, deliberately isolated from the regular conversation and draft. */
class CompletionReviewChatActivity : ThemedActivity() {
    internal class State : ViewModel() {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        var started = false
        val result = MutableStateFlow<ShotReviewResult?>(null)
        fun launchReview(access: CompletionShotReviewAccess, token: String) {
            if (started) return
            started = true
            val request = access.consume(token)
            if (request == null) { result.value = ShotReviewResult(ShotReviewStatus.FAILED); return }
            scope.launch { result.value = access.review(request) }
        }
        override fun onCleared() { scope.cancel() }
    }

    private var disclosure: android.app.AlertDialog? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val access = (application as MobileApplication).completionShotReviewAccess
        val token = intent.getStringExtra(TICKET)
        if (token == null || !access.hasConfiguredKey()) {
            token?.let(access::abandon)
            finish()
            return
        }
        val state = ViewModelProvider(this)[State::class.java]
        val style = TaskProductVisualStyle(this)
        val body = style.page(this, getString(R.string.ai_title))
        style.add(body, style.text(getString(R.string.completion_card_request), TaskProductVisualStyle.body).apply {
            tag = "completion-review-question"
            gravity = Gravity.END
            setPadding(style.dp(12), style.dp(10), style.dp(12), style.dp(10))
            background = style.shape(style.selected)
        }, top = 12)
        val answer = style.text("", TaskProductVisualStyle.body).apply {
            tag = "completion-review-answer"
            setPadding(style.dp(12), style.dp(10), style.dp(12), style.dp(10))
            background = style.shape(style.surface, outline = true)
            accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        style.add(body, answer, top = 12)
        lifecycleScope.launch {
            state.result.collect { result ->
                answer.text = when (result?.status) {
                    ShotReviewStatus.READY -> result.text
                    ShotReviewStatus.FAILED -> getString(R.string.completion_card_unavailable)
                    ShotReviewStatus.UNKNOWN -> getString(R.string.completion_card_unknown)
                    else -> getString(R.string.completion_card_waiting)
                }
            }
        }
        if (state.started) return
        if (savedInstanceState != null) {
            // A recreated process has no valid one-shot grant and must not retry.
            access.abandon(token)
            state.started = true
            state.result.value = ShotReviewResult(ShotReviewStatus.UNKNOWN)
            return
        }
        if (access.hasChatConsent()) state.launchReview(access, token)
        else {
            disclosure = AppDialog.confirm(this, getString(R.string.ai_consent_title),
                getString(R.string.ai_consent_body, AiChatPolicy.SEARCH_CALLS),
                AppDialog.Action(getString(R.string.ai_agree)) {
                    val saved = getSharedPreferences("ai_consent", MODE_PRIVATE).edit()
                        .putInt(AiConsentPolicy.VERSION_KEY, AiConsentPolicy.CURRENT_VERSION).commit()
                    if (saved) state.launchReview(access, token)
                    else { access.abandon(token); state.result.value = ShotReviewResult(ShotReviewStatus.FAILED) }
                }, getString(R.string.ai_cancel)).apply {
                setOnDismissListener {
                    if (!state.started && state.result.value == null && !isChangingConfigurations) {
                        access.abandon(token)
                        finish()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        disclosure?.setOnDismissListener(null)
        disclosure?.dismiss()
        if (isFinishing) intent.getStringExtra(TICKET)?.let {
            (application as MobileApplication).completionShotReviewAccess.abandon(it)
        }
        super.onDestroy()
    }

    companion object {
        private const val TICKET = "openhoyi.completion-review-ticket"
        internal fun intent(context: Context, ticket: String): Intent =
            Intent(context, CompletionReviewChatActivity::class.java).putExtra(TICKET, ticket)
    }
}
