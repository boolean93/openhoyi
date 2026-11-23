package io.openhoyi.mobile

import android.app.Activity
import android.os.Bundle
import android.widget.FrameLayout

/** Fixed, offline device screenshot fixture. No key, device, service, or provider is opened. */
class BrewCompletionPreviewActivity : Activity() {
    private var card: BrewCompletionCardDialog? = null

    override fun attachBaseContext(base: android.content.Context) {
        val configuration = android.content.res.Configuration(base.resources.configuration)
        configuration.setLocale(java.util.Locale.forLanguageTag("zh-CN"))
        super.attachBaseContext(base.createConfigurationContext(configuration))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as MobileApplication
        if (!app.offlinePreviewProcessReady) { finish(); return }
        val background = FrameLayout(this).apply { setBackgroundColor(TaskProductVisualStyle(this@BrewCompletionPreviewActivity).page) }
        setContentView(background)
        val longCurve = intent.getBooleanExtra("longCurve", false)
        val model = ShotShareCardRenderer.Model(
            title = getString(R.string.completion_card_title),
            curveName = if (longCurve) "埃塞俄比亚古吉日晒批次低压长预浸轻柔收尾" else "SOE 浅烘",
            durationSeconds = 28.4,
            outputGrams = if (longCurve) null else 36.2,
            doseGrams = if (longCurve) null else 18.1,
            footer = if (longCurve) "Mock 样例" else "",
            samples = listOf(0.0, 1.0, 2.0, 4.0, 7.2, 8.9, 9.2, 9.0, 8.8, 8.4, 8.0, 7.6, 7.1, 6.7, 6.0)
                .mapIndexed { index, value -> ShotShareCardRenderer.Sample(index * 2.0, value) },
        )
        card = BrewCompletionCardDialog(this, model, {}, {}).also {
            val review = intent.getBooleanExtra("showReview", true)
            it.refresh(review, review)
            if (intent.getBooleanExtra("exportFixture", false)) {
                java.io.File(cacheDir, "completion-card-fixture.png").outputStream().use(it::writePreviewPng)
            }
            it.dialog.setOnDismissListener { finish() }
            it.dialog.show()
        }
    }

    override fun onDestroy() {
        card?.dialog?.setOnDismissListener(null)
        card?.dismiss()
        super.onDestroy()
    }
}
