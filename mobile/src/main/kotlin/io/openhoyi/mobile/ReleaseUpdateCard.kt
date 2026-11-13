package io.openhoyi.mobile

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast

/** Compact settings disclosure: version/check row, optional result and explicit user actions. */
internal class ReleaseUpdateCard(private val activity: Activity, parent: LinearLayout, visual: SettingsVisualStyle,
    private val actions: ReleaseUpdateUiActions = LiveReleaseUpdateUiActions(activity)) {
    private val controller = actions.metadata
    private val downloads = actions.downloads
    private val style = visual.palette
    private val card = style.column("product/settings/app/update/card")
    private val version = style.text(activity.getString(R.string.update_current_version, BuildConfig.VERSION_NAME),
        TaskProductVisualStyle.body, "product/settings/app/update/version")
    private val check = style.button(Button(activity)).apply {
        text = activity.getString(R.string.update_check); tag = "product/settings/app/update/check"
        setOnClickListener { controller.manualCheck() }
    }
    private val status = style.text("", TaskProductVisualStyle.auxiliary, "product/settings/app/update/status", true).apply {
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val progress = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
        max = 1000; tag = "product/settings/app/update/progress"
    }
    private val action = style.button(Button(activity), outline = true).apply {
        tag = "product/settings/app/update/open"
        setOnClickListener {
            when {
                downloads.working -> actions.cancel()
                downloads.state.phase == ReleaseDownloadController.Phase.FAILED ->
                    downloads.state.target?.let { actions.download(it) }
                downloads.state.file?.isFile == true -> actions.install()
                downloads.state.phase in setOf(ReleaseDownloadController.Phase.FAILED, ReleaseDownloadController.Phase.INTERRUPTED) ->
                    downloads.state.target?.let { actions.download(it) }
                else -> controller.state.update?.let { actions.download(it) }
            }
        }
    }
    private val discard = style.button(Button(activity)).apply {
        text = activity.getString(R.string.update_discard_download)
        tag = "product/settings/app/update/discard"
        setOnClickListener { actions.discard() }
    }
    private val toggle = HoyiSwitch(activity).apply {
        text = activity.getString(R.string.update_auto_enabled)
        tag = "product/settings/app/update/automatic"; isChecked = controller.enabled
        setOnCheckedChangeListener { _, enabled ->
            if (!controller.setEnabled(enabled)) {
                isChecked = controller.enabled
                Toast.makeText(activity, R.string.update_preference_failed, Toast.LENGTH_LONG).show()
            }
        }
    }
    private val metadataObserver: (ReleaseUpdateCoordinator.State) -> Unit = { render() }
    private val downloadObserver: (ReleaseDownloadController.State) -> Unit = { render() }
    init {
        visual.switch(toggle)
        // Wrap naturally for long development versions and large text instead of squeezing actions.
        style.add(card, visual.pair(version, check, "product/settings/app/update/version-row"))
        style.add(card, status, 8); style.add(card, progress, 8); style.add(card, action, 8); style.add(card, discard, 8)
        visual.divider(card)
        style.add(card, toggle); style.add(parent, card)
        render()
    }
    private fun render() {
        val metadata = controller.state
        val transfer = downloads.state
        check.isEnabled = !metadata.checking
        check.setText(if (metadata.checking) R.string.update_checking else R.string.update_check)
        progress.visibility = View.GONE; action.visibility = View.GONE
        discard.visibility = if (!downloads.working && transfer.target != null) View.VISIBLE else View.GONE
        val text = when (transfer.phase) {
            ReleaseDownloadController.Phase.DOWNLOADING -> {
                val total = transfer.target?.apkSize ?: 1
                progress.visibility = View.VISIBLE
                progress.progress = (transfer.received * 1000 / total.coerceAtLeast(1)).toInt()
                action.visibility = View.VISIBLE; action.setText(R.string.update_cancel_download)
                activity.getString(R.string.update_download_progress, transfer.received, total)
            }
            ReleaseDownloadController.Phase.VERIFYING -> {
                action.visibility = View.VISIBLE; action.setText(R.string.update_cancel_download)
                activity.getString(R.string.update_verifying)
            }
            ReleaseDownloadController.Phase.READY, ReleaseDownloadController.Phase.NEEDS_VERIFICATION,
            ReleaseDownloadController.Phase.INSTALLER_OPENED -> {
                action.visibility = View.VISIBLE; action.setText(R.string.update_install)
                activity.getString(R.string.update_ready)
            }
            ReleaseDownloadController.Phase.FAILED, ReleaseDownloadController.Phase.INTERRUPTED -> {
                action.visibility = View.VISIBLE; action.setText(if (transfer.phase != ReleaseDownloadController.Phase.FAILED && transfer.file?.isFile == true) R.string.update_install else R.string.update_retry_download)
                activity.getString(if (actions.rateLimited) R.string.update_rate_limited else if (transfer.phase == ReleaseDownloadController.Phase.FAILED) R.string.update_download_failed else R.string.update_download_interrupted)
            }
            ReleaseDownloadController.Phase.IDLE -> when {
                metadata.checking -> activity.getString(R.string.update_checking)
                metadata.limited -> activity.getString(R.string.update_rate_limited)
                metadata.failed -> activity.getString(R.string.update_failed)
                metadata.update != null -> {
                    val update = metadata.update
                    val verdict = releaseUpdateStatus(actions.identity(), update)
                    if (verdict == ReleaseUpdateStatus.AVAILABLE) { action.visibility = View.VISIBLE; action.setText(R.string.update_download) }
                    activity.getString(when (verdict) {
                        ReleaseUpdateStatus.AVAILABLE -> R.string.update_found_compact
                        ReleaseUpdateStatus.SAME_VERSION -> R.string.update_same_version
                        ReleaseUpdateStatus.INSTALLED_NEWER -> R.string.update_installed_newer
                        ReleaseUpdateStatus.DEBUG_PREVIEW -> R.string.update_debug_preview
                        ReleaseUpdateStatus.MOCK_PREVIEW -> R.string.update_mock_preview
                        ReleaseUpdateStatus.SIGNATURE_MISMATCH -> R.string.update_signer_mismatch
                        ReleaseUpdateStatus.IDENTITY_UNAVAILABLE -> R.string.update_identity_unavailable
                    }, update.versionName, update.versionCode)
                }
                else -> ""
            }
        }
        status.text = text; status.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
        status.setTextColor(style.muted)
    }
    fun resume() { controller.observe(metadataObserver); downloads.observe(downloadObserver) }
    fun pause() { controller.unobserve(metadataObserver); downloads.unobserve(downloadObserver) }
    fun close() { pause() }
}
