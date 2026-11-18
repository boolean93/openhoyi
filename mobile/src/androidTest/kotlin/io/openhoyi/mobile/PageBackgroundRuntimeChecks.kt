package io.openhoyi.mobile

import android.app.Activity
import android.app.Instrumentation
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ListView
import android.widget.ScrollView
import android.widget.RadioButton
import android.widget.TextView
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** No real device, user pictures, BLE or model. The runner supplies a fresh disposable emulator. */
internal class PageBackgroundRuntimeChecks(private val test: Instrumentation) {
    private val context get() = test.targetContext
    private val app get() = context.applicationContext as MobileApplication
    private val owner get() = app.pageBackgrounds
    private val output get() = File(context.filesDir, "page-background-evidence")
    private val realPrefs get() = context.getSharedPreferences("page_background", Context.MODE_PRIVATE)
    private fun <T> main(action: () -> T) = BoundedInstrumentationMain.run("page background", action = action)

    fun run() {
        check(Build.VERSION.SDK_INT >= 29 && BuildConfig.MOCK_MODE &&
            context.packageName == "io.openhoyi.mobile.mock" && Build.HARDWARE in setOf("ranchu", "goldfish"))
        check(output.mkdir() && realPrefs.all.isEmpty())
        val ownedMedia = mutableListOf<Uri>()
        val previousAppearance = main { app.appearancePreferences.current }
        try {
            val sample = localImage(true).also(ownedMedia::add)
            val invalid = localImage(false).also(ownedMedia::add)
            verifyRollback()
            verifyImageTransactionAndRestore(sample)
            verifyMissingDraft()
            verifyLightFlow(sample, invalid)
            verifyDarkFlow(sample)
        } finally {
            ownedMedia.forEach { context.contentResolver.delete(it, null, null) }
            main {
                if (app.appearancePreferences.current.dark != previousAppearance.dark)
                    check(app.appearancePreferences.selectDark(previousAppearance.dark) != AppAppearancePreference.Selection.SAVE_FAILED)
            }
        }
    }

    private fun verifyRollback() = main {
        val original = owner.current.config
        var commits = 0
        val pointer = BackgroundPointer(realPrefs) { edit ->
            check(edit.commit())
            if (++commits == 1) {
                check(realPrefs.getString(BackgroundPointer.KEY, null)?.startsWith("mist|") == true)
                false
            } else true
        }
        check(!pointer.write(AppBackgroundConfig(AppBackground.Mist)) && commits == 2)
        check(realPrefs.all.isEmpty() && pointer.read() == original && owner.current.config == original)
    }

    private fun verifyImageTransactionAndRestore(sample: Uri) {
        val root = File(context.noBackupFilesDir, "isolated-background-transaction")
        val prefs = context.getSharedPreferences("fixture_background_transaction", Context.MODE_PRIVATE)
        check(!root.exists() && prefs.all.isEmpty())
        var rejectCommitTo: String? = null
        val store = main {
            AppBackgroundStore(context, root, prefs) { edit ->
                check(edit.commit())
                val reject = rejectCommitTo
                if (reject != null && prefs.getString(BackgroundPointer.KEY, null)?.startsWith("photo:$reject|") == true) {
                    check(File(root, "image-$reject.jpg").isFile && !File(root, "draft-$reject.jpg").exists())
                    rejectCommitTo = null
                    false
                } else true
            }
        }
        var expected = AppBackgroundConfig()
        try {
            val a = runBlocking { store.import(sample, UUID.randomUUID().toString()) }
            val b = runBlocking { store.import(sample, UUID.randomUUID().toString()) }
            val aConfig = AppBackgroundConfig(a.choice, 21)
            val bConfig = AppBackgroundConfig(b.choice, 27)
            check(runBlocking { store.apply(expected, aConfig, a) } == AppBackgroundStore.Result.APPLIED)
            expected = aConfig
            val first = main { store.current }
            check(first.image === a.image)
            val oldFile = File(root, "image-${a.id}.jpg")
            val draftB = File(root, "draft-${b.id}.jpg")
            val appliedB = File(root, "image-${b.id}.jpg")
            rejectCommitTo = b.id
            check(runBlocking { store.apply(expected, bConfig, b) } == AppBackgroundStore.Result.FAILED)
            check(BackgroundPointer(prefs).read() == aConfig && oldFile.isFile && draftB.isFile && !appliedB.exists())
            check(main { store.current === first && store.current.image === a.image })
            check(runBlocking { store.apply(expected, bConfig, b) } == AppBackgroundStore.Result.APPLIED)
            expected = bConfig
            check(BackgroundPointer(prefs).read() == bConfig && !oldFile.exists() && appliedB.isFile)

            // Rebuild a new ViewModel from only the SavedStateHandle values and the private draft.
            val c = runBlocking { store.import(sample, UUID.randomUUID().toString()) }
            val desired = AppBackgroundConfig(c.choice, 31)
            val restored = SavedStateHandle(mapOf(
                BackgroundEditorModel.BASELINE to bConfig.key,
                BackgroundEditorModel.SELECTION to desired.key,
                BackgroundEditorModel.DRAFT to c.id,
            ))
            var state: BackgroundEditorModel.State? = null
            val model = main { BackgroundEditorModel(restored).apply { render = { state = it }; bind(store) } }
            try {
                await("new model restores valid private draft and mask without applying it") {
                    state?.let { it.preview.config == desired && it.preview.image != null && !it.busy &&
                        it.error == null && store.current.config == bConfig } == true
                }
            } finally { main { model.cancel(); model.render = null } }
            await("restored draft removed on cancel") { !File(root, "draft-${c.id}.jpg").exists() }
        } finally {
            val current = main { store.current.config }
            if (current.choice != AppBackground.Solid) runBlocking {
                check(store.apply(current, AppBackgroundConfig(), null) == AppBackgroundStore.Result.APPLIED)
            }
        }
    }

    private fun verifyMissingDraft() {
        val id = UUID.randomUUID().toString()
        val state = SavedStateHandle(mapOf(
            BackgroundEditorModel.BASELINE to AppBackgroundConfig().key,
            BackgroundEditorModel.SELECTION to AppBackgroundConfig(AppBackground.Photo(id), 24).key,
            BackgroundEditorModel.DRAFT to id,
        ))
        lateinit var model: BackgroundEditorModel
        var current: BackgroundEditorModel.State? = null
        main {
            model = BackgroundEditorModel(state)
            model.render = { current = it }
            model.bind(owner)
        }
        try {
            await("restored stale file falls back to already applied color") {
                current?.let { !it.busy && it.error == R.string.background_restore_interrupted &&
                    it.preview.config.choice == AppBackground.Solid } == true
            }
        } finally { main { model.cancel(); model.render = null } }
    }

    private fun verifyLightFlow(sample: Uri, invalid: Uri) {
        main { app.appearancePreferences.selectDark(false) }
        val host = MainPageTestHost.launch(test, TaskDestination.SETTINGS, timeoutMs = 10_000).activity
        val settings = MainPageTestHost(test, host).active("root:SETTINGS") as AppSettingsFragment
        val document = settings.requireView()
        val originalBackground = main { document.background }
        check(originalBackground is ColorDrawable)
        val originalColor = originalBackground.color
        await("same Mock owner ready") { host.currentService != null }
        val service = host.currentService
        var editor: BackgroundSettingsActivity? = null
        fun open(): BackgroundSettingsActivity {
            main { settings.openSection("appearance") }
            val entry = document.findViewWithTag<Button>("product/settings/background/open")
            await("visible background settings entry") { entry.getGlobalVisibleRect(Rect()) }
            return launched(BackgroundSettingsActivity::class.java) { check(entry.performClick()) }
        }
        try {
            var page = open().also { editor = it }
            main {
                choose(page, "mist")
                check(owner.current.config.choice == AppBackground.Solid)
                check(document.background === originalBackground)
                modelImport(page, sample)
            }
            await("photo preview staged while original unchanged") { preview(page)?.snapshot?.image != null }
            val originalDraft = main { checkNotNull(preview(page)?.snapshot?.config?.choice as? AppBackground.Photo) }
            main { page.editor.strength(AppBackgroundConfig.MAX_STRENGTH) }
            page = launched(BackgroundSettingsActivity::class.java) { page.recreate() }.also { editor = it }
            await("photo draft and mask survive recreation") {
                preview(page)?.snapshot?.let { it.config == AppBackgroundConfig(originalDraft, AppBackgroundConfig.MAX_STRENGTH) && it.image != null } == true
            }
            main { modelImport(page, invalid) }
            await("bad import preserves staged photo and applied value") {
                page.window.decorView.findViewWithTag<TextView>("product/background/status").text ==
                    page.getString(R.string.background_import_failed) && owner.current.config.choice == AppBackground.Solid &&
                    preview(page)?.snapshot?.config?.choice == originalDraft
            }
            main {
                modelImport(page, sample)
                // Same main turn: the previous request cannot finish before the newer choice fences it.
                choose(page, "slate")
            }
            await("newer preview remains selected") { preview(page)?.snapshot?.config?.choice == AppBackground.Slate }
            main {
                check(owner.current.config.choice == AppBackground.Solid)
                check(page.editor.beginPicker())
                choose(page, "mist") // Return from the already-open picker must be ignored.
                page.editor.pickerResult(sample)
                check(preview(page)?.snapshot?.config?.choice == AppBackground.Mist)
                page.window.decorView.findViewWithTag<Button>("product/background/cancel").performClick()
            }
            await("cancel restores exact default drawable") { page.isDestroyed && host.hasWindowFocus() && document.background === originalBackground }
            check(originalBackground.color == originalColor)

            page = open().also { editor = it }
            main { modelImport(page, sample) }
            await("selected high contrast photo") { preview(page)?.snapshot?.image != null }
            main { page.editor.strength(AppBackgroundConfig.MAX_STRENGTH) }
            verifyScrolledRootGeometry(page)
            verifyPixelsAndCapture(page, "light-photo.png")
            main { page.window.decorView.findViewWithTag<Button>("product/background/apply").performClick() }
            await("applied page updates in place on return") {
                host.hasWindowFocus() && !host.isDestroyed && host.currentService === service &&
                    document.background is PageBackdrop && owner.current.config.choice is AppBackground.Photo
            }
            val photo = main { owner.current.config.choice as AppBackground.Photo }
            val file = File(context.noBackupFilesDir, "page-backgrounds/image-${photo.id}.jpg")
            check(file.isFile && file.length() > 0)
            check(main { owner.current.image?.let { BackgroundImageLimits.fits(it.width, it.height) } } == true)
            verifyAppliedPages(host, "light")
            await("retained host uses its original owner") { host.currentService === service }
            page = open().also { editor = it }
            main {
                page.window.decorView.findViewWithTag<Button>("product/background/restore").performClick()
                check(owner.current.config.choice == photo)
                page.window.decorView.findViewWithTag<Button>("product/background/apply").performClick()
            }
            await("reset restores original surface and removes only replaced private copy") {
                host.hasWindowFocus() && document.background === originalBackground &&
                    owner.current.config.choice == AppBackground.Solid && !file.exists()
            }
            checkNotNull(context.contentResolver.openInputStream(sample)).use { check(it.read() >= 0) }
        } finally { main { editor?.takeUnless { it.isDestroyed }?.finish(); host.finish() } }
    }

    private fun verifyDarkFlow(sample: Uri) {
        main { app.appearancePreferences.selectDark(true) }
        val host = MainPageTestHost.launch(test, timeoutMs = 10_000).activity
        val page = launched(BackgroundSettingsActivity::class.java) {
            host.startActivity(Intent(host, BackgroundSettingsActivity::class.java))
        }
        try {
            main { check(HoyiUi.dark(page)); modelImport(page, sample) }
            await("dark photo preview ready") { preview(page)?.snapshot?.image != null }
            main { page.editor.strength(AppBackgroundConfig.MAX_STRENGTH) }
            verifyPixelsAndCapture(page, "dark-photo.png")
            main { page.window.decorView.findViewWithTag<Button>("product/background/apply").performClick() }
            await("dark apply returns to the same host") {
                page.isDestroyed && host.hasWindowFocus() && owner.current.config.choice is AppBackground.Photo
            }
            verifyAppliedPages(host, "dark")
            val reset = launched(BackgroundSettingsActivity::class.java) {
                host.startActivity(Intent(host, BackgroundSettingsActivity::class.java))
            }
            try {
                main {
                    reset.window.decorView.findViewWithTag<Button>("product/background/restore").performClick()
                    reset.window.decorView.findViewWithTag<Button>("product/background/apply").performClick()
                }
                await("dark reset returns to solid") { reset.isDestroyed && owner.current.config.choice == AppBackground.Solid }
            } finally { main { reset.takeUnless { it.isDestroyed }?.finish() } }
        } finally { main { page.takeUnless { it.isDestroyed }?.finish(); host.finish() } }
    }

    private fun verifyAppliedPages(host: MainActivity, mode: String) {
        fun go(destination: TaskDestination): View {
            main { host.select(destination) }
            var root: View? = null
            await("applied ${destination.name} visible") {
                root = host.supportFragmentManager.findFragmentByTag("root:${destination.name}")?.view
                root?.let { it.isShown && it.isLaidOut && (it.background as? PageBackdrop)?.protectedAreas?.isNotEmpty() == true } == true
            }
            return checkNotNull(root)
        }
        fun nodes(view: View): Sequence<View> = sequence {
            yield(view)
            if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(nodes(view.getChildAt(i)))
        }
        val settings = go(TaskDestination.SETTINGS)
        val scroll = settings.findViewWithTag<ScrollView>("settings/document/scroll")
        main {
            check(host.window.decorView.findViewWithTag<TaskPageFrame>(TaskPageFrame.TAG).navigation.background !is PageBackdrop)
            scroll.scrollTo(0, HoyiUi.dp(host, 120))
        }
        await("settings content actually scrolled") { scroll.scrollY > 0 }
        captureApplied(host, settings, "$mode-settings.png") {
            nodes(scroll).filterIsInstance<TextView>().first {
                it.background == null && it.getGlobalVisibleRect(Rect()) && it.height >= HoyiUi.dp(host, 18)
            }
        }
        val home = go(TaskDestination.BREW)
        captureApplied(host, home, "$mode-home.png") {
            nodes(home).filterIsInstance<BoilerTemperatureDial>().firstOrNull { it.getGlobalVisibleRect(Rect()) }
                ?: home.findViewWithTag("product/home/brew-boiler")
        }
        val curves = go(TaskDestination.CURVES)
        captureApplied(host, curves, "$mode-curve.png") {
            nodes(curves).filterIsInstance<ListView>().first { it.getGlobalVisibleRect(Rect()) }
        }
        go(TaskDestination.SETTINGS)
        check(AiKeyStore(context).status() == AiKeyStatus.MISSING)
        val chat = launched(AiChatActivity::class.java) { host.startActivity(Intent(host, AiChatActivity::class.java)) }
        try {
            val root = chat.window.decorView.findViewWithTag<View>("ai/chat/root")
            captureApplied(chat, root, "$mode-ai.png") { root.findViewWithTag("ai/chat/input-frame") }
        } finally { main { chat.finish() } }
        await("same settings host after chat") { chat.isDestroyed && host.hasWindowFocus() && !host.isDestroyed }
    }

    private fun captureApplied(page: ThemedActivity, root: View, name: String, protectedView: () -> View) {
        await("$name has current backdrop and laid-out protected rows") {
            (root.background as? PageBackdrop)?.let { it.snapshot.image != null && it.protectedAreas?.isNotEmpty() == true } == true
        }
        data class Pixel(val x: Int, val y: Int, val color: Int)
        val (exposed, protectedPixels, solidColors) = main {
            val paper = root.background as PageBackdrop
            val style = TaskProductVisualStyle(page)
            val base = if (name.endsWith("-home.png")) style.homePage else style.page
            val cover = Rect()
            check(protectedView().getGlobalVisibleRect(cover))
            // Visible rects use decor coordinates; translate the decor origin to display pixels.
            val decorOffset = IntArray(2)
            page.window.decorView.getLocationOnScreen(decorOffset)
            cover.offset(decorOffset[0], decorOffset[1])
            val start = IntArray(2)
            root.getLocationOnScreen(start)
            val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            try {
                paper.draw(Canvas(bitmap))
                val photo = mutableListOf<Pixel>()
                val protected = mutableListOf<Pixel>()
                val step = HoyiUi.dp(page, 6).coerceAtLeast(1)
                for (y in step until root.height step step) for (x in step until root.width step step) {
                    val expected = bitmap.getPixel(x, y)
                    val displayX = start[0] + x
                    val displayY = start[1] + y
                    if (expected != base && !cover.contains(displayX, displayY)) photo += Pixel(displayX, displayY, expected)
                    if (expected == base && cover.contains(displayX, displayY)) protected += Pixel(displayX, displayY, expected)
                }
                check(photo.isNotEmpty() && protected.isNotEmpty()) { "$name needs exposed photo and protection probes" }
                Triple(photo, protected, setOf(base, style.surface, style.selected))
            } finally { bitmap.recycle() }
        }
        // Compare real compositor pixels, not the backdrop object or only the offscreen drawing.
        fun matches(actual: Int, expected: Int) = abs(Color.red(actual) - Color.red(expected)) <= 2 &&
            abs(Color.green(actual) - Color.green(expected)) <= 2 && abs(Color.blue(actual) - Color.blue(expected)) <= 2
        for (attempt in 1..4) {
            val frame = CountDownLatch(1)
            main {
                check(root.isAttachedToWindow && root.isHardwareAccelerated)
                root.viewTreeObserver.registerFrameCommitCallback { frame.countDown() }
                root.invalidate()
            }
            check(frame.await(5, TimeUnit.SECONDS))
            val image = checkNotNull(test.uiAutomation.takeScreenshot())
            try {
                fun sample(point: Pixel) = if (point.x in 0 until image.width && point.y in 0 until image.height)
                    image.getPixel(point.x, point.y) else null
                val artVisible = exposed.count { probe -> sample(probe)?.let { matches(it, probe.color) } == true }
                val contentSolid = protectedPixels.count { probe ->
                    sample(probe)?.let { ink -> solidColors.any { matches(ink, it) } } == true
                }
                if (artVisible < 5 || contentSolid < 5) continue
                File(output, name).outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                return
            } finally { image.recycle() }
        }
        error("$name never presented both visible photo and protected content")
    }

    private fun preview(page: Activity) = page.window.decorView
        .findViewWithTag<View>("product/background/preview").background as? PageBackdrop

    private fun choose(page: Activity, key: String) {
        page.window.decorView.findViewWithTag<RadioButton>("product/background/choice/$key").performClick()
    }

    private fun modelImport(page: BackgroundSettingsActivity, selected: Uri) {
        check(page.editor.beginPicker())
        page.editor.pickerResult(selected)
    }

    private fun verifyScrolledRootGeometry(page: BackgroundSettingsActivity) {
        await("preview backing ready") { preview(page)?.protectedAreas?.isNotEmpty() == true }
        val sample = main { page.window.decorView.findViewWithTag<View>("product/background/preview") }
        val inset = HoyiUi.dp(page, 4)
        val initial = main { Rect(preview(page)!!.protectedAreas!!.first()) }
        main { sample.scrollTo(inset, 0) }
        await("background coordinates compensate the owning root's scroll") {
            preview(page)?.protectedAreas?.firstOrNull()?.left == initial.left - inset
        }
        main { sample.scrollTo(0, 0) }
        await("restored root backdrop coordinates") { preview(page)?.protectedAreas?.firstOrNull() == initial }
    }

    private fun verifyPixelsAndCapture(page: BackgroundSettingsActivity, name: String) {
        await("preview shows laid-out protected content and image") {
            preview(page)?.let { it.protectedAreas?.isNotEmpty() == true && it.snapshot.image != null } == true
        }
        val committed = CountDownLatch(1)
        main {
            val root = page.window.decorView
            val sample = root.findViewWithTag<View>("product/background/preview")
            val card = root.findViewWithTag<View>("product/background/preview/card")
            val paper = checkNotNull(preview(page))
            val solid = TaskProductVisualStyle(page).page
            val bitmap = Bitmap.createBitmap(sample.width, sample.height, Bitmap.Config.ARGB_8888)
            try {
                paper.draw(Canvas(bitmap))
                val region = checkNotNull(paper.protectedAreas).first { it.contains(card.left + card.width / 2, card.top + card.height / 2) }
                check(bitmap.getPixel(region.centerX(), region.centerY()) == solid)
                val x = HoyiUi.dp(page, 5)
                val y = sample.height / 2
                val opposite = sample.width - x - 1
                check(bitmap.getPixel(x, y) != bitmap.getPixel(opposite, y))
            } finally { bitmap.recycle() }
            sample.requestRectangleOnScreen(Rect(0, 0, sample.width, sample.height), true)
            root.viewTreeObserver.registerFrameCommitCallback { committed.countDown() }
            sample.invalidate(); root.invalidate()
        }
        check(committed.await(5, TimeUnit.SECONDS))
        val window = checkNotNull(test.uiAutomation.takeScreenshot())
        try {
            File(output, name).outputStream().use { check(window.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { window.recycle() }
    }

    private fun localImage(valid: Boolean): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "openhoyi-bg-${UUID.randomUUID()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        try {
            checkNotNull(resolver.openOutputStream(uri)).use { stream ->
                if (valid) {
                    val bitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888)
                    try {
                        Canvas(bitmap).apply { drawColor(Color.WHITE); drawRect(0f, 0f, 160f, 240f, Paint().apply { color = Color.BLACK }) }
                        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
                    } finally { bitmap.recycle() }
                } else stream.write(byteArrayOf(1, 2, 3, 4))
            }
            if (valid) check(resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null) == 1)
            return uri
        } catch (failure: Throwable) {
            resolver.delete(uri, null, null)
            throw failure
        }
    }

    private fun await(label: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        do {
            if (main(condition)) return
            SystemClock.sleep(30)
        } while (SystemClock.elapsedRealtime() < deadline)
        error(label)
    }

    private fun <T : Activity> launched(type: Class<T>, action: () -> Unit): T {
        val monitor = test.addMonitor(type.name, null, false)
        try {
            main(action)
            val value = checkNotNull(type.cast(monitor.waitForActivityWithTimeout(10_000))) { "Missing ${type.simpleName}" }
            await("Background activity visible") { value.hasWindowFocus() }
            return value
        } finally { test.removeMonitor(monitor) }
    }
}
