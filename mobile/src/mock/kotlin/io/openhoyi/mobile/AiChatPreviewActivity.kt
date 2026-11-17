package io.openhoyi.mobile

import android.app.Activity
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.WindowManager
import android.widget.PopupWindow

/** Mock-only view preview. It never constructs the chat engine, key store, service or user session. */
open class AiChatPreviewActivity : Activity() {
    protected open val isolatedOfflinePreview = false
    private var offlineRejected = false
    private var metadataProbe = false
    private var offlineHost: AiOfflinePreviewHost? = null
    private var storageProbe: AiStorageProbeHost? = null
    private var navigationFixture: AiNavigationPreviewFixtureHost? = null
    private var imageFixture: AiImagePreviewFixtureHost? = null
    private var streamFixture: AiStreamPreviewFixtureHost? = null
    private lateinit var presentation: AiChatPresentation
    private var usageDialog: android.app.AlertDialog? = null
    private val lines = mutableListOf<AiChatLine>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(BuildConfig.MOCK_MODE)
        val language = when (intent.getStringExtra("previewLanguage")) {
            "en" -> AppLanguage.ENGLISH
            else -> AppLanguage.CHINESE
        }
        val font = when (intent.getStringExtra("previewFont")) {
            "small" -> AppFontSize.SMALL
            "large" -> AppFontSize.LARGE
            else -> AppFontSize.MEDIUM
        }
        val dark = intent.getBooleanExtra("previewDark", false)
        // Keep the Activity as the base so dialogs inherit its window token. A detached
        // createConfigurationContext is suitable for resources, not for showing dialogs.
        val previewContext = ContextThemeWrapper(this, theme).apply {
            applyOverrideConfiguration(AppLanguageContext.configuration(this@AiChatPreviewActivity, language, dark, font))
        }
        if (language == AppLanguage.CHINESE) theme.applyStyle(R.style.MobileChineseTypography, true)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        window.statusBarColor = if (dark) 0xFF11191A.toInt() else 0xFFF9FAF9.toInt()
        window.navigationBarColor = if (dark) 0xFF1B2526.toInt() else 0xFFFFFFFF.toInt()
        if (isolatedOfflinePreview) {
            if (!(application as MobileApplication).offlinePreviewProcessReady) {
                offlineRejected = true
                setContentView(SettingsVisualStyle(previewContext).palette.text("无法确认隔离进程，离线预览已停止。未初始化设备或更新Owner。\nIsolated process could not be verified; offline preview stopped. No device/update owner initialized.", TaskProductVisualStyle.body))
                return
            }
            if (intent.getBooleanExtra("metadataProbe", false)) {
                metadataProbe = true
                setContentView(AiMetadataProbe.view(previewContext))
                return
            }
            if (intent.getBooleanExtra("streamFixture", false)) {
                streamFixture = AiStreamPreviewFixtureHost(this, previewContext)
                setContentView(streamFixture!!.root)
                return
            }
            if (intent.getBooleanExtra("navigationFixture", false) || intent.getBooleanExtra("waitingFixture", false)) {
                navigationFixture = AiNavigationPreviewFixtureHost(this, previewContext, savedInstanceState,
                    intent.getBooleanExtra("waitingFixture", false))
                setContentView(navigationFixture!!.root)
                return
            }
            if (intent.getBooleanExtra("imagePreviewFixture", false)) {
                imageFixture = AiImagePreviewFixtureHost(this, previewContext)
                setContentView(imageFixture!!.root)
                return
            }
            if (intent.getBooleanExtra("storageProbe", false)) {
                storageProbe = AiStorageProbeHost(this, previewContext)
                setContentView(storageProbe!!.root)
                return
            }
            offlineHost = AiOfflinePreviewHost(this, previewContext, savedInstanceState)
            setContentView(offlineHost!!.root)
            return
        }
        val question = if (language == AppLanguage.CHINESE) "这杯咖啡为什么偏酸？" else "Why does this coffee taste sour?"
        val answer = if (language == AppLanguage.CHINESE)
            "## 萃取建议\n- **先看时间**与研磨粗细\n| 参数 | 建议 |\n| --- | --- |\n| 时间 | `25 s` |\n| 研磨 | 略细 |\n这是离线界面样例，不读取设备或萃取记录。"
        else "## Extraction tip\n- Check **time** and grind size\n| Parameter | Suggestion |\n| --- | --- |\n| Time | `25 s` |\n| Grind | Slightly finer |\nThis offline UI sample reads no device or brew history."
        lines += AiChatLine(true, question)
        lines += AiChatLine(false, answer)
        presentation = AiChatPresentation(previewContext, SettingsVisualStyle(previewContext), "", 0, 0,
            onBack = { finish() },
            onNewChat = { lines.clear(); presentation.input.text.clear(); render() },
            onKey = {},
            onSendOrStop = {
                // Typed text never leaves this view or enters the sample transcript.
                presentation.input.text.clear()
                lines += AiChatLine(true, question)
                lines += AiChatLine(false, answer)
                render()
            },
            keyEnabled = false,
            onUsage = {
                usageDialog?.dismiss()
                val sample = AiUsageSnapshot(since = 1794700800000L,
                    models = listOf(AiModelUsage("deepseek-flash", 3, 2, 1000, 120, 600, 200, 1)),
                    searches = 2, searchNonempty = 1, searchEmpty = 1)
                val missing = AiUsageSnapshot(since = sample.since,
                    models = listOf(AiModelUsage("deepseek-flash", requests = 1)))
                usageDialog = AiUsageDialog.create(previewContext, missing, sample).also {
                    it.setTitle(R.string.ai_preview_title)
                    it.show(); SettingsVisualStyle(previewContext).dialog(it)
                }
            })
        setContentView(presentation.root)
        render()
    }
    override fun onResume() {
        super.onResume()
        // Mock-only window sizing: no global display/density or user preferences are changed.
        if (isolatedOfflinePreview) {
            val width = intent.getIntExtra("previewWidthDp", 0)
            if (width in 320..720) window.setLayout(
                (width * resources.displayMetrics.density).toInt(), WindowManager.LayoutParams.MATCH_PARENT)
        }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        navigationFixture?.result(requestCode, resultCode, data)
    }
    private fun render() = presentation.render(lines, "", false, "")
    internal fun optionsForCheck(): PopupWindow? = presentation.options
    internal fun busyForCheck(value: Boolean) = presentation.render(lines, "pending", value, "")
    override fun onSaveInstanceState(outState: Bundle) {
        navigationFixture?.save(outState)
        offlineHost?.save(outState)
        super.onSaveInstanceState(outState)
    }
    override fun onPause() {
        if (offlineRejected || metadataProbe) { super.onPause(); return }
        if (streamFixture != null) { streamFixture?.pause(); super.onPause(); return }
        if (navigationFixture != null) { navigationFixture?.pause(); super.onPause(); return }
        if (imageFixture != null) { imageFixture?.pause(); super.onPause(); return }
        if (storageProbe != null) { super.onPause(); return }
        if (offlineHost != null) { offlineHost?.pause(); super.onPause(); return }
        presentation.dismissOptions()
        usageDialog?.dismiss(); usageDialog = null
        presentation.input.text.clear()
        super.onPause()
    }
    override fun onDestroy() {
        if (offlineRejected || metadataProbe) { super.onDestroy(); return }
        if (streamFixture != null) { streamFixture?.destroy(); streamFixture = null; super.onDestroy(); return }
        if (navigationFixture != null) { navigationFixture?.destroy(); navigationFixture = null; super.onDestroy(); return }
        if (imageFixture != null) { imageFixture?.destroy(); imageFixture = null; super.onDestroy(); return }
        if (storageProbe != null) { storageProbe?.destroy(); storageProbe = null; super.onDestroy(); return }
        if (offlineHost != null) { offlineHost?.destroy(); offlineHost = null; super.onDestroy(); return }
        presentation.dismissOptions()
        usageDialog?.dismiss(); usageDialog = null
        lines.clear()
        super.onDestroy()
    }
}

/** Exported only in Mock and isolated by a manifest process. Intent data/paths are never read. */
class AiOfflinePreviewActivity : AiChatPreviewActivity() {
    override val isolatedOfflinePreview = true
}

/** Only generated pixels in a newly owned directory. Never reads user sessions, keys or photos. */
private class AiImagePreviewFixtureHost(private val activity: Activity, themed: android.content.Context) {
    private val epoch = java.util.UUID.randomUUID().toString()
    private val directory = java.io.File(activity.noBackupFilesDir, "ai-image-preview-" + epoch)
    // The production loader resolves applicationContext; keep both v2 and legacy fallback paths
    // confined to this run. No supplied path, URI, epoch or attachment id is accepted.
    private val context = object : android.content.ContextWrapper(themed) {
        override fun getApplicationContext(): android.content.Context = this
        override fun getNoBackupFilesDir(): java.io.File = directory
        override fun getFilesDir(): java.io.File = java.io.File(directory, "unused-legacy")
    }
    private val gate = Any()
    private val destroyed = java.util.concurrent.atomic.AtomicBoolean(false)
    private val owned = mutableListOf<AiAttachment>() // Only fully saved items, under gate.
    private val draft = mutableListOf<AiAttachment>()
    private val lines = mutableListOf<AiChatLine>()
    private var revision = 0L
    private var ready = false
    private var failed = false
    private val visual = SettingsVisualStyle(context)
    private fun t(zh: String, en: String) = if (context.resources.configuration.locales[0].language == "zh") zh else en
    private val presentation = AiChatPresentation(context, visual, "", 0, 0,
        onBack = { activity.finish() },
        onNewChat = { lines.clear(); draft.clear(); revision++; render() },
        onKey = {}, keyEnabled = false,
        onSendOrStop = {
            if (ready && draft.isNotEmpty()) {
                lines += AiChatLine(true, "", draft.toList())
                draft.clear(); presentationInputClear(); render()
            }
        },
        onAttachment = {
            if (ready) { draft.clear(); synchronized(gate) { draft.addAll(owned) }; render() }
        },
        onRemoveAttachment = { item -> draft.removeAll { it.id == item.id }; render() })
    val root get() = presentation.root
    init {
        check(guard())
        render()
        Thread({
            try {
                val images = synchronized(gate) { seed() }
                activity.runOnUiThread {
                    if (!destroyed.get()) {
                        ready = true
                        draft.addAll(images)
                        lines += AiChatLine(true, "", images)
                        render()
                    }
                }
            } catch (_: Exception) {
                activity.runOnUiThread { if (!destroyed.get()) { failed = true; render() } }
            }
        }, "ai-image-fixture-seed").start()
    }
    private fun presentationInputClear() { presentation.input.text.clear() }
    private fun guard() = BuildConfig.MOCK_MODE &&
        (activity.application as MobileApplication).offlinePreviewProcessReady
    private fun vault() = AiPrivateImageVault(AndroidAiPrivateImageStorage(context), epoch)
    private fun seed(): List<AiAttachment> {
        check(guard() && !destroyed.get())
        check(directory.mkdir()) // Existing/unknown paths are not adopted or removed.
        val vault = vault()
        listOf(960 to 640, 480 to 960, 720 to 720).forEachIndexed { index, (width, height) ->
            check(!destroyed.get())
            val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
            val output = AiAttachmentBuffer()
            try {
                val canvas = android.graphics.Canvas(bitmap)
                canvas.drawColor(listOf(0xFFE5D3BB.toInt(), 0xFFBDD9D6.toInt(), 0xFFD7C8E8.toInt())[index])
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                val x = width / 2f; val y = height / 2f; val radius = minOf(width, height) * .28f
                paint.color = 0xFFF9F7EF.toInt(); canvas.drawCircle(x, y, radius, paint)
                paint.color = 0xFF805535.toInt(); canvas.drawCircle(x, y, radius * .72f, paint)
                paint.color = 0xFFF0CD95.toInt(); canvas.drawOval(x - radius * .35f, y - radius * .15f,
                    x + radius * .35f, y + radius * .15f, paint)
                check(bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, output))
                val item = AiAttachment(java.util.UUID.randomUUID().toString(), "synthetic-${index + 1}.jpg",
                    "image/jpeg", output.size(), AiAttachmentKind.IMAGE)
                AiPreparedAttachment(item, output.toByteArray()).use { vault.save(it) }
                owned.add(item)
            } finally { output.wipe(); bitmap.recycle() }
        }
        return owned.toList()
    }
    private fun render() {
        val status = when {
            failed -> t("合成图片准备失败，未读用户图片、Key或会话。", "Synthetic setup failed. No user photos, keys or sessions read.")
            !ready -> t("正在生成本次合成图片…", "Generating this run's synthetic images…")
            else -> t("离线合成图片：仅生成/读取本次自有JPG，不访问用户图片、Key或会话，不联网。＋恢复三张草稿，发送仅模拟。",
                "Offline synthetic images: only this run's generated JPGs are read. No user photos, keys, sessions or network. + restores three drafts; Send is simulated.")
        }
        presentation.render(lines, "", false, status, draft, attachmentBusy = !ready,
            ownerEpoch = if (ready) epoch else null, previewSession = "$epoch:$revision")
    }
    fun pause() { presentation.dismissOptions() }
    fun destroy() {
        destroyed.set(true)
        pause()
        presentation.closeImagePreviews() // Invalidate/cancel reads before authenticated fixture cleanup.
        Thread({
            synchronized(gate) {
                if (guard()) {
                    val vault = vault()
                    owned.forEach { vault.remove(it) }
                    owned.clear()
                    // No recursive/root deletion: interrupted or unknown reservations remain untouched.
                }
            }
        }, "ai-image-fixture-cleanup").start()
    }
}

/** Synthetic card/draft only. The target is the real read-only curve route, not a fake receiver. */
private class AiNavigationPreviewFixtureHost(
    private val activity: Activity, private val themed: android.content.Context,
    state: Bundle?, waiting: Boolean,
) {
    private val item = CurveLibrary(FactoryCurveCatalog.load(themed.assets.open("factory_curves_v3.tsv")))
        .find("factory-v3-001")!!
    private val card = AiCurveCard.recommendation(item)
    private var busy = waiting && state == null
    private lateinit var presentation: AiChatPresentation
    val root: android.view.View get() = presentation.root
    init {
        presentation = AiChatPresentation(themed, SettingsVisualStyle(themed),
            state?.getString("nav-fixture-draft") ?: "导航返回测试草稿", 0, 0,
            onBack = { activity.finish() }, onNewChat = {}, onKey = {}, keyEnabled = false,
            onSendOrStop = { busy = false; render() },
            onViewCurve = { reference ->
                activity.startActivityForResult(android.content.Intent(activity, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_INITIAL_DESTINATION, TaskDestination.CURVES.name)
                    .putExtra(AiCurveLibraryRoute.EXTRA_RETURN_TO_CHAT, true)
                    .putExtra(AiCurveLibraryRoute.EXTRA_CARD, reference.encode().toString()), AiCurveLibraryRoute.REQUEST_BROWSE)
            }, curveLibraryAvailable = { it.matches(item) })
        render()
        presentation.scroll.post { presentation.scroll.scrollTo(0, state?.getInt("nav-fixture-scroll") ?: 0) }
    }
    private fun render() {
        presentation.render(listOf(AiChatLine(false, "", curveCards = listOf(card))),
            presentation.input.text.toString(), busy, if (busy) themed.getString(R.string.ai_waiting) else "")
    }
    fun save(out: Bundle) {
        out.putString("nav-fixture-draft", presentation.input.text.toString())
        out.putInt("nav-fixture-scroll", presentation.scroll.scrollY)
    }
    fun result(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        if (requestCode != AiCurveLibraryRoute.REQUEST_BROWSE || resultCode != Activity.RESULT_OK) return
        @Suppress("DEPRECATION")
        val forwarded = runCatching { data?.getParcelableExtra<android.content.Intent>(AiCurveLibraryRoute.EXTRA_EXIT_INTENT) }.getOrNull()
        val destination = AiCurveLibraryRoute.exitDestination(
            data?.getStringExtra(AiCurveLibraryRoute.EXTRA_EXIT_DESTINATION), true)
        val next = forwarded ?: destination?.let {
            android.content.Intent().putExtra(MainActivity.EXTRA_INITIAL_DESTINATION, it.name)
        } ?: return
        activity.startActivity(android.content.Intent(next).setClass(activity, MainActivity::class.java)
            .setFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP))
        activity.finish()
    }
    fun pause() { busy = false; render(); presentation.dismissOptions() }
    fun destroy() { presentation.dismissOptions(); presentation.closeImagePreviews() }
}

/** Timed synthetic deltas only, using the same ephemeral state and display as real streaming. */
private class AiStreamPreviewFixtureHost(private val activity: Activity, private val themed: android.content.Context) {
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var preview = AiStreamPreview()
    private var generation = 0L
    private var busy = false
    private var notice = ""
    private val lines = mutableListOf<AiChatLine>()
    private lateinit var presentation: AiChatPresentation
    private fun t(zh: String, en: String) = if (themed.resources.configuration.locales[0].language == "zh") zh else en
    private val question get() = t("离线合成样例：这杯咖啡为什么偏酸？", "Offline synthetic sample: why does this coffee taste sour?")
    private val answer get() = t("先把研磨调细一小格，保持粉量不变，再比较酸感与甜感。", "Try a slightly finer grind with the same dose, then compare acidity and sweetness.")
    val root get() = presentation.root
    init {
        check(BuildConfig.MOCK_MODE && (activity.application as MobileApplication).offlinePreviewProcessReady)
        presentation = AiChatPresentation(themed, SettingsVisualStyle(themed), "", 0, 0,
            onBack = { activity.finish() }, onNewChat = { start() }, onKey = {}, keyEnabled = false,
            onSendOrStop = { if (busy) stop() else start() })
        start()
    }
    private fun start() {
        preview.clear()
        val current = AiStreamPreview().also { preview = it }
        val token = ++generation
        busy = true; notice = ""; lines.clear()
        presentation.input.text.clear(); presentation.resetStreamPreview(); render()
        fun after(delay: Long, event: () -> Unit) {
            handler.postDelayed({
                if (token == generation && busy && preview === current) { event(); presentation.updateStreamPreview(current.snapshot()) }
            }, delay)
        }
        after(2500) { current.append(AiStreamDelta.Reasoning(t("先区分豆子的明亮酸质与萃取不足。", "First distinguish bright bean acidity from under-extraction."))) }
        after(4500) { current.append(AiStreamDelta.Reasoning(t("\n比较研磨、时间与甜感，先选一次只改一个变量的调整。", "\nCompare grind, time and sweetness; change just one variable first."))) }
        after(7500) { current.toolStarted() }
        after(10500) { current.append(AiStreamDelta.Content(t("先把研磨调细一小格，", "Try a slightly finer grind, "))) }
        after(12500) { current.append(AiStreamDelta.Content(t("保持粉量不变，再比较酸感与甜感。", "keep the dose unchanged, then compare acidity and sweetness."))) }
        after(15500) {
            current.finish(); busy = false
            lines += AiChatLine(true, question); lines += AiChatLine(false, answer)
            render()
        }
    }
    private fun render() {
        presentation.render(lines, if (busy) question else "", busy,
            if (busy) themed.getString(R.string.ai_waiting) else notice)
        presentation.updateStreamPreview(preview.snapshot())
    }
    private fun stop() {
        val stopped = preview
        generation++; busy = false; stopped.interrupt()
        notice = themed.getString(R.string.ai_interrupted)
        render()
        // Deliberate late local arrival verifies terminal state cannot resurrect the stopped preview.
        handler.postDelayed({ stopped.append(AiStreamDelta.Reasoning("late synthetic delta")) }, 800L)
    }
    fun pause() {
        generation++; busy = false; preview.clear()
        presentation.resetStreamPreview(); presentation.dismissOptions()
    }
    fun destroy() { generation++; preview.clear(); handler.removeCallbacksAndMessages(null); presentation.closeImagePreviews() }
}
