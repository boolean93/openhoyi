package io.openhoyi.mobile

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import java.util.concurrent.Executors

/** About-page, user-initiated update check. External browser owns download and any later install. */
internal class ReleaseUpdateCard(
    private val activity: Activity,
    parent: LinearLayout,
    visual: SettingsVisualStyle,
) {
    private val identity = installedUpdateIdentity(activity)
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val style = visual.palette
    private val card = style.column("product/settings/app/update/card").apply {
        setPadding(style.dp(16), style.dp(16), style.dp(16), style.dp(16))
        background = style.shape(style.surface, outline = true)
    }
    private val status = style.text(activity.getString(R.string.update_idle), TaskProductVisualStyle.auxiliary,
        "product/settings/app/update/status", secondary = true).apply {
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private var target: String? = null
    private val check: android.widget.Button = HoyiUi.button(activity, card, activity.getString(R.string.update_check)) { controller.check() }.apply {
        tag = "product/settings/app/update/check"
        visual.action(this)
    }
    private val open: android.widget.Button = HoyiUi.button(activity, card, activity.getString(R.string.update_view_release)) {
        target?.let(::openBrowser)
    }.apply { tag = "product/settings/app/update/open"; visual.action(this, outline = true); visibility = View.GONE }
    private val controller: ReleaseUpdateCheckController = ReleaseUpdateCheckController(::request, ::render)

    init {
        // Stable vertical layout also permits full English labels and large text to wrap naturally.
        card.addView(style.text(activity.getString(R.string.update_title), TaskProductVisualStyle.action), 0)
        card.addView(status, 1, LinearLayout.LayoutParams(-1, -2).apply { topMargin = style.dp(8); bottomMargin = style.dp(8) })
        style.add(card, style.text(activity.getString(R.string.update_manual_hint), TaskProductVisualStyle.auxiliary,
            secondary = true), 8)
        style.add(parent, card, 16)
        render(controller.state)
    }
    private fun request(result: (Result<ReleaseUpdate>) -> Unit): () -> Unit {
        val call = ReleaseUpdateHttpCall()
        var finished = false // Accessed only by this UI thread and its Handler callbacks.
        var future: java.util.concurrent.Future<*>? = null
        val timeout = Runnable {
            if (!finished) {
                finished = true
                call.cancel(); future?.cancel(true)
                result(Result.failure(java.net.SocketTimeoutException("Update check timed out")))
            }
        }
        future = executor.submit {
            val value = runCatching { call.execute() }
            handler.post {
                if (!finished) {
                    finished = true
                    handler.removeCallbacks(timeout)
                    result(value)
                }
            }
        }
        // Also bounds UI waiting if platform DNS resolution ignores socket timeouts.
        handler.postDelayed(timeout, 10_000)
        return {
            finished = true
            handler.removeCallbacks(timeout)
            call.cancel(); future?.cancel(true)
        }
    }
    private fun render(state: ReleaseUpdateCheckController.State) {
        target = null
        open.visibility = View.GONE
        check.isEnabled = state != ReleaseUpdateCheckController.State.Checking
        check.setText(if (state == ReleaseUpdateCheckController.State.Checking) R.string.update_checking else R.string.update_check)
        status.setTextColor(activity.getColor(if (state == ReleaseUpdateCheckController.State.Failed) R.color.mobile_danger else R.color.mobile_muted))
        when (state) {
            ReleaseUpdateCheckController.State.Idle -> status.text = activity.getString(when {
                identity?.packageName == "io.openhoyi.mobile.mock" -> R.string.update_idle_mock
                identity?.debuggable == true -> R.string.update_idle_debug
                else -> R.string.update_idle
            })
            ReleaseUpdateCheckController.State.Checking -> status.setText(R.string.update_checking)
            ReleaseUpdateCheckController.State.Failed -> status.setText(R.string.update_failed)
            is ReleaseUpdateCheckController.State.Checked -> {
                val update = state.update
                val verdict = releaseUpdateStatus(identity, update)
                val message = when (verdict) {
                    ReleaseUpdateStatus.AVAILABLE -> R.string.update_available
                    ReleaseUpdateStatus.SAME_VERSION -> R.string.update_same_version
                    ReleaseUpdateStatus.INSTALLED_NEWER -> R.string.update_installed_newer
                    ReleaseUpdateStatus.DEBUG_PREVIEW -> R.string.update_debug_preview
                    ReleaseUpdateStatus.MOCK_PREVIEW -> R.string.update_mock_preview
                    ReleaseUpdateStatus.SIGNATURE_MISMATCH -> R.string.update_signer_mismatch
                    ReleaseUpdateStatus.IDENTITY_UNAVAILABLE -> R.string.update_identity_unavailable
                }
                status.text = activity.getString(message, update.versionName, update.versionCode)
                target = if (verdict == ReleaseUpdateStatus.AVAILABLE) update.downloadUrl else update.releaseUrl
                open.setText(if (verdict == ReleaseUpdateStatus.AVAILABLE) R.string.update_download else R.string.update_view_release)
                open.visibility = View.VISIBLE
            }
        }
    }
    private fun openBrowser(url: String) {
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(activity, R.string.update_no_browser, Toast.LENGTH_LONG).show()
        } catch (_: SecurityException) {
            Toast.makeText(activity, R.string.update_no_browser, Toast.LENGTH_LONG).show()
        }
    }
    fun resume() { controller.resume() }
    fun pause() { controller.pause() }
    fun close() { controller.pause(); executor.shutdownNow(); handler.removeCallbacksAndMessages(null) }
}
