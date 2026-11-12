package io.openhoyi.mobile

import android.app.Activity
import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.FragmentActivity

/** Visible feedback in the exact window where a user accepted the upper-right download prompt. */
internal class ReleaseDownloadProgressPrompt(private val activity: Activity,
    private val action: () -> Unit, private val dismissed: () -> Unit) {
    private val style = TaskProductVisualStyle(activity)
    private val visible = Rect().apply { activity.window.decorView.getWindowVisibleDisplayFrame(this) }
    private val bounds = releaseUpdatePromptBounds(visible.width(), visible.height(), activity.resources.displayMetrics.density)
    private val shell = object : LinearLayout(activity) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(bounds.maxHeight, View.MeasureSpec.AT_MOST))
        }
    }.apply {
        orientation = LinearLayout.VERTICAL; tag = "product/update/download-prompt"
        background = style.shape(style.surface, outline = true)
        setPadding(style.dp(16), style.dp(16), style.dp(16), style.dp(12))
    }
    private val text = style.text("", TaskProductVisualStyle.auxiliary, "product/update/download-prompt/status", true)
    private val progress = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply { max = 1000 }
    private val button = style.button(Button(activity), outline = true).apply { setOnClickListener { action() } }
    private val popup = PopupWindow(shell, bounds.width, -2, false).apply {
        isOutsideTouchable = true; inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
        setBackgroundDrawable(style.shape(style.surface, outline = true)); elevation = style.dp(8).toFloat(); animationStyle = 0
    }
    private var programmatic = false
    private val back = object : OnBackPressedCallback(true) { override fun handleOnBackPressed() { popup.dismiss() } }
    init {
        style.add(shell, style.text(activity.getString(R.string.update_title), TaskProductVisualStyle.action))
        shell.addView(android.widget.ScrollView(activity).apply { addView(text) },
            LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = style.dp(8) })
        style.add(shell, progress, 8); style.add(shell, button, 8)
        popup.setOnDismissListener { back.remove(); if (!programmatic) dismissed() }
    }
    fun render(state: ReleaseDownloadController.State, limited: Boolean) {
        val total = state.target?.apkSize ?: 1
        progress.visibility = if (state.phase == ReleaseDownloadController.Phase.DOWNLOADING) View.VISIBLE else View.GONE
        progress.progress = (state.received * 1000 / total.coerceAtLeast(1)).toInt()
        text.text = when (state.phase) {
            ReleaseDownloadController.Phase.DOWNLOADING -> activity.getString(R.string.update_download_progress, state.received, total)
            ReleaseDownloadController.Phase.VERIFYING -> activity.getString(R.string.update_verifying)
            ReleaseDownloadController.Phase.FAILED -> activity.getString(if (limited) R.string.update_rate_limited else R.string.update_download_failed)
            ReleaseDownloadController.Phase.INTERRUPTED -> activity.getString(R.string.update_download_interrupted)
            else -> activity.getString(R.string.update_ready)
        }
        button.setText(when (state.phase) {
            ReleaseDownloadController.Phase.DOWNLOADING, ReleaseDownloadController.Phase.VERIFYING -> R.string.update_cancel_download
            ReleaseDownloadController.Phase.FAILED, ReleaseDownloadController.Phase.INTERRUPTED -> R.string.update_retry_download
            else -> R.string.update_install
        })
    }
    fun show() {
        (activity as? FragmentActivity)?.onBackPressedDispatcher?.addCallback(back)
        try { popup.showAtLocation(activity.window.decorView, Gravity.TOP or Gravity.RIGHT,
            bounds.right + (activity.resources.displayMetrics.widthPixels - visible.right).coerceAtLeast(0), visible.top + bounds.top) }
        catch (error: RuntimeException) { back.remove(); throw error }
    }
    fun hide() { programmatic = true; popup.dismiss(); back.remove() }
}
