package io.openhoyi.mobile

import android.app.Activity
import android.app.Instrumentation
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
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
    private val rejectedEvidence = mutableListOf<String>()
    private class PixelEvidenceFailure(message: String) : IllegalStateException(message)
    private inline fun requirePixelEvidence(ok: Boolean, reason: () -> String) {
        if (!ok) throw PixelEvidenceFailure(reason())
    }
    private fun <T> main(action: () -> T) = BoundedInstrumentationMain.run("page background", action = action)

    fun run() {
        check(Build.VERSION.SDK_INT >= 30 && BuildConfig.MOCK_MODE &&
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
            verifyShortLandscape(sample)
            check(rejectedEvidence.isEmpty()) {
                rejectedEvidence.joinToString(separator = "\n", prefix = "Window evidence failed:\n")
            }
            // Evidence branch only: seed a generated fixture photo for the post-run
            // 16:10 screenshot. Import owns a private copy and no user photos are used.
            val imported = runBlocking { owner.import(sample, UUID.randomUUID().toString()) }
            val wallpaper = AppBackgroundConfig(imported.choice, AppBackgroundConfig.MAX_STRENGTH)
            val original = main { owner.current.config }
            check(runBlocking { owner.apply(original, wallpaper, imported) } == AppBackgroundStore.Result.APPLIED)
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
        val home = solidHome(host)
        main { host.select(TaskDestination.SETTINGS) }
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
            verifyAppliedPages(host, "light", home)
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
            checkSolidHomeRestored(host, home)
            main {
                val result = host.supportFragmentManager.findFragmentByTag("root:CURVES")?.view
                    ?.findViewWithTag<View>("product/curve-library/results")
                check(result != null && !result.clipToOutline) { "Solid must restore the native list outline" }
                host.select(TaskDestination.BEANS)
            }
            await("default Beans header retains its original divider without round clipping") {
                val header = host.supportFragmentManager.findFragmentByTag("root:BEANS")?.view
                    ?.findViewWithTag<View>("product/inventory/header")
                header?.let { it.isShown && it.isLaidOut && it.foreground != null && !it.clipToOutline } == true
            }
            verifyMistHome(host, "light")
        } finally { main { editor?.takeUnless { it.isDestroyed }?.finish(); host.finish() } }
    }

    private fun verifyDarkFlow(sample: Uri) {
        main { app.appearancePreferences.selectDark(true) }
        val host = MainPageTestHost.launch(test, timeoutMs = 10_000).activity
        val home = solidHome(host)
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
            verifyAppliedPages(host, "dark", home)
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
            checkSolidHomeRestored(host, home)
            verifyMistHome(host, "dark")
        } finally { main { page.takeUnless { it.isDestroyed }?.finish(); host.finish() } }
    }

    private fun nodes(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(nodes(view.getChildAt(i)))
    }

    private fun PageBackdrop.hasProtection() = protectedAreas?.isNotEmpty() == true || roundedAreas.isNotEmpty()

    private fun visiblePage(host: MainActivity, fragmentTag: String): View {
        var root: View? = null
        await("applied $fragmentTag visible") {
            root = host.supportFragmentManager.findFragmentByTag(fragmentTag)?.view?.let { outer ->
                nodes(outer).firstOrNull { it.isShown && it.isLaidOut && !it.isLayoutRequested &&
                    (it.background as? PageBackdrop)?.hasProtection() == true }
            }
            root != null
        }
        return checkNotNull(root)
    }

    private fun verifyAppliedPages(host: MainActivity, mode: String, originalHome: SolidHome) {
        fun go(destination: TaskDestination): View {
            main { host.select(destination) }
            return visiblePage(host, "root:${destination.name}")
        }
        val style = TaskProductVisualStyle(host)
        val settings = go(TaskDestination.SETTINGS) as SettingsDocumentLayout
        val scroll = settings.scroll
        val section = main { settings.findViewWithTag<View>("settings/section/devices") }
        main {
            val nav = host.window.decorView.findViewWithTag<TaskPageFrame>(TaskPageFrame.TAG).navigation
            val paper = nav.background as PageBackdrop
            check(paper.snapshot.image != null && paper.photoScrim != null)
            check(paper.photoBounds?.invoke()?.contains(0, 0) == true)
            verifyShellBackdrop(host, photo = true)
            scroll.scrollTo(0, HoyiUi.dp(host, 120))
        }
        await("settings backing matches the section's scrolled visible bounds") {
            if (scroll.scrollY <= 0) return@await false
            val viewport = localRect(settings, section) ?: return@await false
            val column = localRect(settings, settings.body) ?: return@await false
            val origin = IntArray(2).also { settings.getLocationInWindow(it) }
            val sectionOrigin = IntArray(2).also { section.getLocationInWindow(it) }
            val sectionBottom = sectionOrigin[1] - origin[1] + section.height
            val content = Rect(column.left, viewport.top, column.right,
                minOf(viewport.bottom, sectionBottom - section.paddingBottom))
            val areas = (settings.background as PageBackdrop).roundedAreas.map { it.clip }
            // scrollY changes synchronously; the drawable's geometry follows on traversal.
            if (content.isEmpty || content !in areas) return@await false
            requireSamePanel(settings, settings.findViewWithTag("settings/document/title"),
                settings.findViewWithTag("settings/document/index-picker"))
            val gapY = sectionBottom - section.paddingBottom / 2
            check(gapY in viewport.top until viewport.bottom) { "Settings section gap must be visible in the fixture" }
            check(areas.none { it.contains(content.centerX(), gapY) }) {
                "Settings section padding must remain available for the background"
            }
            true
        }
        captureApplied(host, settings, "$mode-settings.png", backing = style.surface,
            requiredWallpaper = { shieldCorners(settings, section, preferBottom = true) },
        ) {
            nodes(scroll).filterIsInstance<TextView>().first {
                it.background == null && it.getGlobalVisibleRect(Rect()) && it.height >= HoyiUi.dp(host, 18)
            }
        }
        val home = go(TaskDestination.BREW)
        val boilerPressure = home.findViewWithTag<View>("product/home/${if (mode == "light") "brew" else "steam"}-boiler/pressure")
        val boilerDial = boilerPressure.parent as BoilerTemperatureDial
        captureApplied(host, home, "$mode-home.png", base = style.homePage, backing = style.surface,
            nativeCoverFill = boilerDial.faceFill,
            requiredWallpaper = { sampleGap ->
                check(home === originalHome.root && homeBounds(home) == originalHome.bounds) {
                    "Applying the backdrop must not move Home labels or hit targets"
                }
                homeWallpaperGaps(home, sampleGap)
            },
        ) { boilerPressure }
        if (mode == "dark") captureDisabledHomeModes(home)
        val curves = go(TaskDestination.CURVES)
        captureCurvePage(host, curves, "$mode-curve.png")
        if (mode == "dark") captureDarkCurveDetails(host, curves)
        captureScalePage(host, go(TaskDestination.SCALE), "$mode-scale.png")
        val history = go(TaskDestination.HISTORY)
        captureApplied(host, history, "$mode-history.png", backing = style.surface,
            requiredWallpaper = { shieldCorners(history, history.findViewWithTag("product/history/header")) },
            foregroundSurfaces = { listOf(visibleCollectionSurface(host, history,
                "product/history/list", "product/history/empty-card")) },
        ) { history.findViewWithTag("product/history/count") }
        val beans = go(TaskDestination.BEANS)
        captureApplied(host, beans, "$mode-beans.png", backing = style.surface,
            requiredWallpaper = { shieldCorners(beans, beans.findViewWithTag("product/inventory/header")) },
            foregroundSurfaces = { listOf(visibleCollectionSurface(host, beans,
                "product/inventory/list-scroll", "product/inventory/empty-card")) },
            inventoryForeground = {
                val add = beans.findViewWithTag<Button>("product/inventory/add")
                check(add.isShown && add.isEnabled && add.isClickable && add.text == host.getString(R.string.beans_add))
                InventoryForeground(beans.findViewWithTag("product/inventory/header"), add)
            },
        ) { beans.findViewWithTag("product/inventory/header-summary") }

        // Selecting this read-only view is not a start/prepare gesture. Assert both remain idle.
        val service = main { checkNotNull(host.currentService) }
        main {
            check(service.activeShotRecipe == null && service.brewPreparationState == io.openhoyi.session.BrewPreparation.State.IDLE)
            host.showExtraction()
        }
        val extraction = visiblePage(host, "extraction")
        captureApplied(host, extraction, "$mode-extraction.png", nativeCoverFill = style.surface,
            requiredWallpaper = {
                val plot = checkNotNull(localRect(extraction, extraction.findViewWithTag("product/extraction/chart")))
                listOf(Point(plot.left / 2, plot.centerY()),
                    Point((plot.right + extraction.width) / 2, plot.centerY()))
            },
        ) { extraction.findViewWithTag("product/extraction/chart") }
        main { check(service.activeShotRecipe == null && service.brewPreparationState == io.openhoyi.session.BrewPreparation.State.IDLE && service.autoPreheatInitialTemperature == null) }

        go(TaskDestination.SETTINGS)
        check(AiKeyStore(context).status() == AiKeyStatus.MISSING)
        val chat = launched(AiChatActivity::class.java) { host.startActivity(Intent(host, AiChatActivity::class.java)) }
        try {
            val presentation = main {
                val field = AiChatActivity::class.java.getDeclaredField("presentation").apply { isAccessible = true }
                field.get(chat) as AiChatPresentation
            }
            val root = presentation.root
            captureApplied(chat, root, "$mode-ai-welcome.png", backing = style.surface,
                requiredWallpaper = { shieldCorners(root, root.findViewWithTag("ai/chat/welcome")) },
            ) { root.findViewWithTag("ai/chat/welcome") }
            main {
                // Real Activity and production presentation, but no memory/session write or send action.
                val previewCard = AiCurveCardConstructionChecks.cards().first()
                presentation.render(listOf(
                    AiChatLine(true, "这支豆子怎么萃取？"),
                    AiChatLine(false, "先用较低压力预浸。\n\n再平稳升压，后段放缓。", curveCards = listOf(previewCard)),
                ), presentation.input.text.toString(), false, "")
            }
            fun assistantBody(): TextView? = root.findViewWithTag<View>("ai/chat/assistant")?.let {
                nodes(it).filterIsInstance<TextView>().firstOrNull()
            }
            fun card(): AiCurveCardView? = nodes(presentation.transcript).filterIsInstance<AiCurveCardView>().singleOrNull()
            await("AI user text, multi-line assistant body and recommendation are visible") {
                val assistant = assistantBody() ?: return@await false
                val recommendation = card() ?: return@await false
                val user = root.findViewWithTag<View>("ai/chat/user") ?: return@await false
                val cardBounds = localRect(root, recommendation) ?: return@await false
                if (assistant.lineCount < 2 || localRect(root, assistant) == null || localRect(root, user) == null) return@await false
                check(assistant.text.contains("后段放缓"))
                val hostWidth = (recommendation.parent as View).width
                check(recommendation.width == minOf(hostWidth, HoyiUi.dp(chat, 400)) &&
                    cardBounds.left > 0 && cardBounds.right < root.width) { "Backdrop must not stretch the AI recommendation" }
                true
            }
            captureApplied(chat, root, "$mode-ai.png",
                foregroundSurfaces = {
                    listOf(SolidSurface(checkNotNull(assistantBody()), style.surface), SolidSurface(checkNotNull(card()), style.surface))
                },
                decorativeGuttersOf = { checkNotNull(card()) },
            ) { root.findViewWithTag("ai/chat/input-frame") }
            PageBackgroundAiChecks(test, chat, presentation, output).run(mode)
        } finally { main { chat.finish() } }
        await("same settings host after chat") { chat.isDestroyed && host.hasWindowFocus() && !host.isDestroyed }
    }

    private fun localRect(root: View, view: View): Rect? {
        val raw = Rect()
        if (!view.getGlobalVisibleRect(raw)) return null
        val globalRoot = IntArray(2).also { root.rootView.getLocationInWindow(it) }
        val localRoot = IntArray(2).also { root.getLocationInWindow(it) }
        raw.offset(globalRoot[0] - localRoot[0], globalRoot[1] - localRoot[1])
        return raw
    }

    private fun requireSamePanel(root: View, vararg children: View) {
        val visible = children.map { checkNotNull(localRect(root, it)) }
        val paper = root.background as PageBackdrop
        val areas = paper.protectedAreas.orEmpty() + paper.roundedAreas.map { it.clip }
        check(areas.any { panel -> visible.all(panel::contains) }) {
            "Semantic content must share one backdrop panel: children=$visible actual=$areas"
        }
    }

    private fun shieldCorners(root: View, view: View, preferBottom: Boolean = false): List<Point> {
        val child = checkNotNull(localRect(root, view))
        val paper = root.background as PageBackdrop
        val shield = checkNotNull(paper.roundedAreas.firstOrNull { it.clip.contains(child.centerX(), child.centerY())
            || it.clip.contains(child.centerX(), child.top + 1) }) { "No rounded shield for ${view.tag}" }
        val box = shield.bounds
        val inset = root.resources.displayMetrics.density.toInt().coerceAtLeast(1)
        val ys = if (preferBottom) listOf(box.bottom - 1 - inset, box.top + inset)
            else listOf(box.top + inset, box.bottom - 1 - inset)
        val sides = listOf(box.left + inset, box.right - 1 - inset)
        return ys.flatMap { y -> sides.map { x -> Point(x, y) } }
            .filter { shield.clip.contains(it.x, it.y) }.also { check(it.size >= 2) { "No visible rounded corners for ${view.tag}" } }
    }

    private fun visibleCollectionSurface(
        host: MainActivity, root: View, listTag: String, emptyTag: String,
    ): SolidSurface {
        val list = root.findViewWithTag<View>(listTag)
        val listRect = list?.let { localRect(root, it) }
        val empty = root.findViewWithTag<View>(emptyTag)
        val emptyRect = empty?.let { localRect(root, it) }
        check((listRect != null) xor (emptyRect != null)) {
            "Expected the real visible list or empty card, not both (list=$listTag empty=$emptyTag)"
        }
        if (listRect != null) {
            checkNotNull(list)
            val paper = root.background as PageBackdrop
            val fill = TaskProductVisualStyle(host).surface
            check(list.clipToOutline && paper.roundedAreas.any { it.clip == listRect && it.fill == fill })
            if (list is ListView) check(list.count > 0)
            return SolidSurface(list, fill)
        }
        return SolidSurface(checkNotNull(empty), host.appColor(R.color.mobile_surface))
    }

    private fun captureScalePage(host: MainActivity, root: View, name: String) {
        val style = TaskProductVisualStyle(host)
        val quantity = root.findViewWithTag<View>("product/scale/quantity")
        val isPhone = host.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        val timerValue = root.findViewWithTag<View>("product/scale/timer/value")
        val timerActions = root.findViewWithTag<View>("product/scale/timer/actions")
        captureApplied(host, root, name, backing = style.surface,
            foregroundSurfaces = {
                if (isPhone) listOf(SolidSurface(timerValue, style.surface), SolidSurface(timerActions, style.surface))
                else emptyList()
            },
            requiredWallpaper = { sampleGap ->
                val corners = shieldCorners(root, quantity)
                if (isPhone) {
                    val paper = root.background as PageBackdrop
                    val valueRect = checkNotNull(localRect(root, timerValue))
                    val actionsRect = checkNotNull(localRect(root, timerActions))
                    val readout = paper.roundedAreas.single { it.clip.contains(valueRect) }
                    val actions = paper.roundedAreas.single { it.clip.contains(actionsRect) }
                    check(readout.bounds.bottom < actions.bounds.top)
                    val left = maxOf(readout.bounds.left, actions.bounds.left)
                    val right = minOf(readout.bounds.right, actions.bounds.right)
                    val inset = (right - left) / 4
                    corners + sampleGap(Rect(left + inset, readout.bounds.bottom, right - inset, actions.bounds.top))
                } else corners
            },
        ) { quantity }
    }

    private fun captureCurvePage(host: MainActivity, root: View, name: String) {
        val style = TaskProductVisualStyle(host)
        val list = root.findViewWithTag<View>("product/curve-library/results")
        val filters = root.findViewWithTag<View>("product/curve-library/filter")
        captureApplied(host, root, name, backing = style.surface,
            requiredWallpaper = {
                val paper = root.background as PageBackdrop
                val toolBounds = checkNotNull(localRect(root, filters))
                val listBounds = checkNotNull(localRect(root, list))
                val countBounds = checkNotNull(localRect(root, root.findViewWithTag("product/curve-library/count")))
                val tools = checkNotNull(paper.roundedAreas.singleOrNull { it.clip.contains(toolBounds) && it.clip.contains(countBounds) })
                val results = checkNotNull(paper.roundedAreas.singleOrNull { it.clip.contains(listBounds) })
                check(tools != results && tools.bounds.bottom < results.bounds.top && list.clipToOutline)
                shieldCorners(root, list) + shieldCorners(root, filters)
            },
        ) { list }
    }

    private fun captureDarkCurveDetails(host: MainActivity, root: View) {
        val outputName = "dark-curve-detail.png"
        main {
            val list = nodes(root).filterIsInstance<ListView>().first { it.isShown }
            val index = list.firstVisiblePosition
            val item = checkNotNull(list.getChildAt(0))
            // View-only navigation: do not assign a recipe or enter preparation.
            check(list.performItemClick(item, index, list.adapter.getItemId(index)))
        }
        val style = TaskProductVisualStyle(host)
        val detailPane = root.findViewWithTag<ViewGroup>("product/curve-library/detail")
        val toolbar = main { detailPane.getChildAt(0) }
        val use = root.findViewWithTag<Button>("product/curve-library/use")
        await("real Curve detail toolbar backed after selecting a list row") {
            val visible = localRect(root, toolbar) ?: return@await false
            use.isShown && use.isEnabled && localRect(root, use) != null &&
                (root.background as PageBackdrop).roundedAreas.any { it.clip.contains(visible) }
        }
        captureApplied(host, root, outputName, backing = style.surface, translucentCover = true, blendGapProbes = true,
            foregroundSurfaces = { listOf(SolidSurface(use, style.primary)) },
            requiredWallpaper = {
                val bounds = checkNotNull(localRect(root, use))
                val inset = HoyiUi.dp(host, 1).coerceAtLeast(1)
                val x = listOf(bounds.left + inset, bounds.right - 1 - inset)
                val y = listOf(bounds.top + inset, bounds.bottom - 1 - inset)
                y.flatMap { row -> x.map { Point(it, row) } }
            },
        ) { toolbar }
    }

    private fun captureDisabledHomeModes(home: View) {
        val controls = main {
            listOf("machine-mode/control", "machine-mode/logic").map {
                checkNotNull(home.findViewWithTag<CurveEditorSegments>(it)).also { view ->
                    check(localRect(home, view) != null)
                }
            }
        }
        val enabled = main { controls.map { it.isEnabled } }
        val committed = CountDownLatch(1)
        val listener = ViewTreeObserver.OnPreDrawListener {
            // Drawing-only fixture. The service and state gates remain untouched; production
            // deliberately uses 45% whole-control alpha for the disabled native segments.
            controls.forEach { it.isEnabled = false }
            check(controls.all { !it.isEnabled && abs(it.alpha - .45f) < .001f })
            home.viewTreeObserver.registerFrameCommitCallback { committed.countDown() }
            true
        }
        main {
            home.viewTreeObserver.addOnPreDrawListener(listener)
            home.invalidate()
        }
        try {
            check(committed.await(5, TimeUnit.SECONDS))
            val image = checkNotNull(test.uiAutomation.takeScreenshot())
            try {
                File(output, "dark-home-disabled-modes.png").outputStream().use {
                    check(image.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
            } finally { image.recycle() }
        } finally {
            main {
                home.viewTreeObserver.removeOnPreDrawListener(listener)
                controls.zip(enabled).forEach { (control, wasEnabled) -> control.isEnabled = wasEnabled }
            }
        }
    }

    private fun verifyShortLandscape(sample: Uri) {
        val imported = runBlocking { owner.import(sample, UUID.randomUUID().toString()) }
        val config = AppBackgroundConfig(imported.choice, AppBackgroundConfig.MAX_STRENGTH)
        val original = main { owner.current.config }
        check(runBlocking { owner.apply(original, config, imported) } == AppBackgroundStore.Result.APPLIED)
        val portrait = MainPageTestHost.launch(test, TaskDestination.SCALE, timeoutMs = 10_000).activity
        var landscape: MainActivity? = null
        try {
            landscape = launched(MainActivity::class.java) { portrait.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            val host = landscape
            main {
                check(host.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
                    host.resources.configuration.screenHeightDp <= 480) { "Landscape fixture must use a short real window" }
            }
            fun go(page: TaskDestination): View {
                main { host.select(page) }
                return visiblePage(host, "root:${page.name}")
            }
            val home = go(TaskDestination.BREW)
            await("short landscape home shows both native boiler switches above targets") {
                listOf("brew", "steam").all { boiler ->
                    val key = "product/home/$boiler-boiler"
                    val power = home.findViewWithTag<HoyiSwitch>("$key/toggle") ?: return@all false
                    val target = home.findViewWithTag<View>("$key/target") ?: return@all false
                    val touch = HoyiUi.dp(host, 48)
                    val hit = localRect(home, power) ?: return@all false
                    val goal = localRect(home, target) ?: return@all false
                    hit.width() >= touch && hit.height() >= touch && hit.bottom <= goal.top
                }
            }
            main { verifyShellBackdrop(host, photo = true) }
            val homeFrame = captureCommittedFrame(home)
            try { writeScreenshot("landscape-home.png", homeFrame) } finally { homeFrame.recycle() }
            captureScalePage(host, go(TaskDestination.SCALE), "landscape-scale.png")
            captureCurvePage(host, go(TaskDestination.CURVES), "landscape-curve.png")
            val settings = go(TaskDestination.SETTINGS)
            captureApplied(host, settings, "landscape-settings.png", backing = TaskProductVisualStyle(host).surface,
                requiredWallpaper = { shieldCorners(settings, settings.findViewWithTag("settings/document/title")) },
            ) { settings.findViewWithTag("settings/document/title") }
        } finally {
            main { (landscape ?: portrait).finish() }
            check(runBlocking { owner.apply(config, original, null) } == AppBackgroundStore.Result.APPLIED)
        }
    }

    private data class SolidHome(val root: View, val original: ColorDrawable, val bounds: List<Rect>)
    private val homeGeometryTags = listOf(
        "brew-boiler", "brew-boiler/pressure", "brew-boiler/toggle", "steam-boiler",
        "steam-boiler/pressure", "steam-boiler/toggle", "quick-title", "preview-legend",
    )

    private fun homeBounds(root: View) = homeGeometryTags.map { key ->
        val view = checkNotNull(root.findViewWithTag<View>("product/home/$key"))
        val relative = IntArray(2).also { root.getLocationInWindow(it) }
        val position = IntArray(2).also { view.getLocationInWindow(it) }
        val x = position[0] - relative[0]
        val y = position[1] - relative[1]
        Rect(x, y, x + view.width, y + view.height)
    }

    private fun solidHome(host: MainActivity): SolidHome {
        var root: View? = null
        main { host.select(TaskDestination.BREW) }
        await("default Home has actual measured controls and original solid backing") {
            root = host.supportFragmentManager.findFragmentByTag("root:BREW")?.view
            root?.let { it.isShown && it.isLaidOut && !it.isLayoutRequested && it.background is ColorDrawable &&
                homeBounds(it).all { bounds -> !bounds.isEmpty } } == true
        }
        return main {
            val view = checkNotNull(root)
            val original = view.background as ColorDrawable
            check(original.color == TaskProductVisualStyle(host).homePage)
            SolidHome(view, original, homeBounds(view))
        }
    }

    private fun checkSolidHomeRestored(host: MainActivity, before: SolidHome) {
        val after = solidHome(host)
        main { verifyShellBackdrop(host, photo = false) }
        check(before.root === after.root && before.original === after.original && before.bounds == after.bounds) {
            "Returning to Solid must restore the exact Home surface and control bounds"
        }
    }

    private fun verifyShellBackdrop(host: MainActivity, photo: Boolean) {
        val frame = checkNotNull(host.window.decorView.findViewWithTag<TaskPageFrame>(TaskPageFrame.TAG))
        val shell = checkNotNull(frame.findViewWithTag<TaskShellStatusView>("task-shell-status"))
        if (photo) {
            val paper = shell.background as PageBackdrop
            val nav = frame.navigation.background as PageBackdrop
            check(paper.snapshot.image === nav.snapshot.image && paper.photoScrim == nav.photoScrim)
            val position = IntArray(2).also(shell::getLocationInWindow)
            val navPosition = IntArray(2).also(frame.navigation::getLocationInWindow)
            val shellCrop = checkNotNull(paper.photoBounds?.invoke()).apply { offset(position[0], position[1]) }
            val navCrop = checkNotNull(nav.photoBounds?.invoke()).apply { offset(navPosition[0], navPosition[1]) }
            check(shellCrop == navCrop) { "Status and navigation must share one window-space crop" }
        } else {
            check((shell.background as ColorDrawable).color == host.getColor(R.color.task_shell_surface))
        }
        for (device in listOf("coffee", "scale")) {
            val panel = checkNotNull(shell.findViewWithTag<View>("task-shell-$device-group"))
            val fill = (panel.background as android.graphics.drawable.GradientDrawable).color?.defaultColor
            check(fill != null && (if (photo) Color.alpha(fill) < 64 else fill == host.getColor(R.color.task_shell_card)))
        }
    }

    private fun homeWallpaperGaps(root: View, sampleGap: (Rect) -> Point): List<Point> {
        val paper = root.background as PageBackdrop
        fun home(key: String): View = checkNotNull(root.findViewWithTag<View>("product/home/$key"))
        val title = checkNotNull(localRect(root, home("quick-title")))
        val legend = checkNotNull(localRect(root, home("preview-legend")))
        val heading = Rect(title).apply { union(legend) }
        check(paper.roundedAreas.none { it.bounds.contains(title) || it.bounds.contains(legend) } &&
            paper.protectedAreas.orEmpty().none { it.contains(title) || it.contains(legend) }) {
            "Quick curve title and legend must not bring back a row backing over a photo"
        }
        fun dial(key: String): Rect {
            val column = home(key) as ViewGroup
            val dial = (0 until column.childCount).map(column::getChildAt).filterIsInstance<BoilerTemperatureDial>().single()
            val power = home("$key/toggle") as HoyiSwitch
            check(power.parent === dial)
            val pressure = home("$key/pressure")
            check(pressure.parent === dial && pressure.bottom <= dial.height) {
                "Pressure must remain inside the boiler face"
            }
            val goal = dial.findViewWithTag<View>("product/home/$key/target")
            check(goal != null && power.bottom <= goal.top && goal.bottom < pressure.top) {
                "Switch, target and pressure must remain distinct within the dial"
            }
            val touch = (48 * root.resources.displayMetrics.density).toInt()
            check(power.width >= touch && power.height >= touch && !power.contentDescription.isNullOrEmpty())
            return checkNotNull(localRect(root, dial))
        }
        val left = dial("brew-boiler")
        val right = dial("steam-boiler")
        check(left.right < right.left && maxOf(left.bottom, right.bottom) <= heading.top)
        fun corner(dial: Rect, fromLeft: Boolean) = Point(
            if (fromLeft) dial.left + dial.width() / 11 else dial.right - dial.width() / 11,
            dial.top + dial.height() / 11,
        )
        fun intercolumn(first: Rect, second: Rect): Rect {
            val space = second.left - first.right
            check(space > 0)
            val sideInset = space / 4
            val radius = (root.resources.displayMetrics.density * 5).toInt().coerceAtLeast(1)
            val y = minOf(first.centerY(), second.centerY())
            return Rect(first.right + sideInset, maxOf(first.top, second.top, y - radius),
                second.left - sideInset, minOf(first.bottom, second.bottom, y + radius + 1))
        }
        return buildList {
            addAll(listOf(corner(left, true), corner(left, false), corner(right, true), corner(right, false),
                sampleGap(intercolumn(left, right))))
            if (legend.left - title.right > 16 && title.centerY() in legend.top until legend.bottom) {
                add(sampleGap(intercolumn(title, legend)))
            }
            val control = checkNotNull(localRect(root, root.findViewWithTag("machine-mode/control")))
            val logic = checkNotNull(localRect(root, root.findViewWithTag("machine-mode/logic")))
            if (control.right < logic.left && control.centerY() in logic.top until logic.bottom) {
                add(Point((control.right + logic.left) / 2, control.centerY()))
            } else if (control.bottom < logic.top && control.centerX() in logic.left until logic.right) {
                add(Point(control.centerX(), (control.bottom + logic.top) / 2))
            }
        }
    }

    private fun verifyMistHome(host: MainActivity, mode: String) {
        fun edit() = launched(BackgroundSettingsActivity::class.java) {
            host.startActivity(Intent(host, BackgroundSettingsActivity::class.java))
        }
        var editor = edit()
        val mist = AppBackgroundConfig(AppBackground.Mist, AppBackgroundConfig.MAX_STRENGTH)
        try {
            main {
                choose(editor, "mist")
                // Test the strongest backdrop the user can select, even after resetting a previous photo.
                editor.editor.strength(mist.strength)
                editor.window.decorView.findViewWithTag<Button>("product/background/apply").performClick()
            }
            await("built-in Mist applied at maximum strength") {
                editor.isDestroyed && host.hasWindowFocus() && owner.current.config == mist
            }
            main { host.select(TaskDestination.BREW) }
            val root = main { checkNotNull(host.supportFragmentManager.findFragmentByTag("root:BREW")?.view) }
            val style = TaskProductVisualStyle(host)
            captureApplied(host, root, "$mode-home-mist.png", base = style.homePage, nativeCoverFill = style.surface) {
                // The quiet gradient can match the quiet footer fill at some pixels. Use the
                // native dial's real face and keep the same strict contrast filter.
                nodes(root).filterIsInstance<BoilerTemperatureDial>().first { it.getGlobalVisibleRect(Rect()) }
            }

            editor = edit()
            main {
                editor.window.decorView.findViewWithTag<Button>("product/background/restore").performClick()
                editor.window.decorView.findViewWithTag<Button>("product/background/apply").performClick()
            }
            await("built-in sample reset to solid") { editor.isDestroyed && owner.current.config.choice == AppBackground.Solid }
        } finally { main { editor.takeUnless { it.isDestroyed }?.finish() } }
    }

    private data class SolidSurface(val view: View, val fill: Int)
    private data class InventoryForeground(val header: View, val add: Button)

    private fun captureApplied(
        page: ThemedActivity,
        root: View,
        name: String,
        base: Int = TaskProductVisualStyle(page).page,
        backing: Int = base,
        requiredWallpaper: ((Rect) -> Point) -> List<Point> = { emptyList() },
        foregroundSurfaces: () -> List<SolidSurface> = { emptyList() },
        decorativeGuttersOf: (() -> View)? = null,
        nativeCoverFill: Int? = null,
        translucentCover: Boolean = false,
        blendGapProbes: Boolean = false,
        inventoryForeground: (() -> InventoryForeground)? = null,
        protectedView: () -> View,
    ) {
        await("$name has current backdrop and laid-out protected rows") {
            (root.background as? PageBackdrop)?.let {
                (it.snapshot.image != null || it.snapshot.config.choice == AppBackground.Mist) &&
                    it.hasProtection()
            } == true
        }
        // A two-level per-channel match is allowed for device compositing; a probe must exceed that
        // tolerance against every solid outcome, not merely against the page base.
        val matchTolerance = 2
        val contrastThreshold = 5
        check(contrastThreshold > matchTolerance * 2) // Matching ranges must not overlap.
        fun distance(a: Int, b: Int) = maxOf(abs(Color.red(a) - Color.red(b)),
            abs(Color.green(a) - Color.green(b)), abs(Color.blue(a) - Color.blue(b)))
        data class Pixel(val x: Int, val y: Int, val color: Int)
        data class Protection(val points: List<Pixel>, val expectedFills: Set<Int>)
        data class ForegroundInk(val rule: List<Pixel>, val label: List<Pixel>)
        data class PixelProof(val decorative: List<Pixel>, val protection: List<Protection>,
                              val wallpaperGaps: List<Pixel>, val inventoryInk: ForegroundInk?)
        val failureName = name.removeSuffix(".png") + ".failed.png"
        val (exposed, protection, wallpaperGaps, inventoryInk) = try {
            main {
                val paper = root.background as PageBackdrop
                val style = TaskProductVisualStyle(page)
                val solidColors = setOf(base, backing, style.surface, style.selected)
                fun againstFills(color: Int, fills: Set<Int>) = fills.all { distance(color, it) >= contrastThreshold }
                // Visible rects use decor coordinates; translate the decor origin to display pixels.
                val decorOffset = IntArray(2)
                page.window.decorView.getLocationOnScreen(decorOffset)
                fun screenBounds(view: View) = Rect().apply {
                    check(view.getGlobalVisibleRect(this))
                    offset(decorOffset[0], decorOffset[1])
                }
                val ink = inventoryForeground?.invoke()?.let { (header, add) ->
                    val bounds = screenBounds(header)
                    // Verify the separator survives in the center, with the existing corner probes
                    // independently proving that neither end crosses the round silhouette.
                    val rule = listOf(1, 3).map { quarter ->
                        Pixel(bounds.left + bounds.width() * quarter / 4, bounds.bottom - 1, style.border)
                    }
                    val location = IntArray(2).also(add::getLocationOnScreen)
                    val visible = screenBounds(add)
                    val labelBitmap = Bitmap.createBitmap(add.width, add.height, Bitmap.Config.ARGB_8888)
                    val label = try {
                        add.draw(Canvas(labelBitmap))
                        val textColor = add.currentTextColor
                        buildList {
                            // Restrict verification to word pixels (not the focus ring or background edge).
                            for (y in add.totalPaddingTop until add.height - add.totalPaddingBottom) {
                                for (x in add.totalPaddingLeft until add.width - add.totalPaddingRight) {
                                    val screenX = location[0] + x
                                    val screenY = location[1] + y
                                    if (visible.contains(screenX, screenY) && labelBitmap.getPixel(x, y) == textColor) {
                                        this.add(Pixel(screenX, screenY, textColor))
                                    }
                                }
                            }
                        }
                    } finally { labelBitmap.recycle() }
                    requirePixelEvidence(label.size >= 5) { "$name needs add-button glyph probes: actual=${label.size}" }
                    ForegroundInk(rule, label)
                }
                val cover = screenBounds(protectedView())
                val surfaces = foregroundSurfaces().map { screenBounds(it.view) to it.fill } +
                    if (nativeCoverFill != null) listOf(cover to nativeCoverFill) else emptyList()
                val backdropFills = solidColors + paper.roundedAreas.map { it.fill } + surfaces.map { it.second }
                val gutterNeighbor = decorativeGuttersOf?.invoke()?.let(::screenBounds)
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
                        val inDecorativeArea = gutterNeighbor == null ||
                            displayY in gutterNeighbor.top until gutterNeighbor.bottom && !gutterNeighbor.contains(displayX, displayY)
                        if (inDecorativeArea && againstFills(expected, backdropFills) && !cover.contains(displayX, displayY) &&
                            surfaces.none { (bounds, _) -> bounds.contains(displayX, displayY) }) photo += Pixel(displayX, displayY, expected)
                        if (cover.contains(displayX, displayY) &&
                            (expected == backing || translucentCover && paper.roundedAreas.any { area ->
                                Color.alpha(area.fill) < 255 && area.bounds.contains(x, y) && area.clip.contains(x, y) &&
                                    x >= area.bounds.left + area.radius && x < area.bounds.right - area.radius
                            })) protected += Pixel(displayX, displayY, expected)
                    }
                    // Recreate the bare wallpaper. Any pixel already close to a permitted solid
                    // fill is ineligible, including for a separate native bubble, chart or card.
                    PageBackdrop(base, HoyiUi.dark(page)).apply {
                        snapshot = paper.snapshot
                        photoBounds = paper.photoBounds
                        protectedAreas = emptyList()
                        setBounds(0, 0, root.width, root.height)
                        draw(Canvas(bitmap))
                    }
                    fun contrast(x: Int, y: Int) = againstFills(bitmap.getPixel(x, y), solidColors)
                    val verifiedProtection = protected.filter { pixel ->
                        val x = pixel.x - start[0]
                        val y = pixel.y - start[1]
                        contrast(x, y) && (!translucentCover || distance(bitmap.getPixel(x, y), pixel.color) >= matchTolerance)
                    }
                    // Each native view has an explicit fill; a nearby swatch from another
                    // plane must not make this own surface look protected.
                    val nativeProtection = surfaces.map { (bounds, fill) ->
                        val expectedFills = setOf(fill)
                        val points = buildList {
                            for (y in step until root.height step step) for (x in step until root.width step step) {
                                if (bounds.contains(start[0] + x, start[1] + y) &&
                                    againstFills(bitmap.getPixel(x, y), expectedFills)) {
                                    add(Pixel(start[0] + x, start[1] + y, fill))
                                }
                            }
                        }
                        Protection(points, expectedFills)
                    }
                    val gapPositions = requiredWallpaper { band ->
                        stableGapSample(band, bitmap, contrastThreshold, backdropFills, ::distance)
                    }
                    if (blendGapProbes) {
                        val bare = gapPositions.map { bitmap.getPixel(it.x, it.y) }
                        paper.draw(Canvas(bitmap))
                        requirePixelEvidence(gapPositions.indices.any { index ->
                            val point = gapPositions[index]
                            val blend = bitmap.getPixel(point.x, point.y)
                            distance(blend, bare[index]) >= matchTolerance && againstFills(blend, solidColors)
                        }) { "$name action corners should still carry a non-solid photo blend" }
                    }
                    val gaps = gapPositions.map { point ->
                        Pixel(start[0] + point.x, start[1] + point.y, bitmap.getPixel(point.x, point.y))
                    }
                    requirePixelEvidence(gaps.isEmpty() || gaps.count { againstFills(it.color, backdropFills) } >= minOf(2, (gaps.size + 1) / 2)) {
                        "$name needs contrast-sensitive wallpaper samples outside the rounded surfaces: " +
                            "gaps=$gaps fills=$backdropFills"
                    }
                    val protection = (if (nativeCoverFill != null) emptyList() else listOf(Protection(verifiedProtection, if (translucentCover) emptySet() else solidColors))) +
                        nativeProtection
                    requirePixelEvidence(photo.size >= 5 && protection.all { it.points.size >= 5 }) {
                        "$name needs at least five decorative and five contrast-sensitive probes per protected surface " +
                            "(decorative=${photo.size}, protection=${protection.map { it.points.size }}, base=${Integer.toHexString(base)})"
                    }
                    PixelProof(photo, protection, gaps, ink)
                } finally { bitmap.recycle() }
            }
        } catch (failure: IllegalStateException) {
            runCatching {
                val image = captureCommittedFrame(root)
                try { writeScreenshot(failureName, image) } finally { image.recycle() }
            }.onFailure(failure::addSuppressed)
            if (failure is PixelEvidenceFailure) {
                rejectedEvidence += "$name: ${failure.message}"
                return
            }
            throw failure
        }
        // Compare real compositor pixels, not the backdrop object or only the offscreen drawing.
        fun matches(actual: Int, expected: Int) = distance(actual, expected) <= matchTolerance
        var details = ""
        for (attempt in 1..4) {
            val image = captureCommittedFrame(root)
            try {
                fun sample(point: Pixel) = if (point.x in 0 until image.width && point.y in 0 until image.height)
                    image.getPixel(point.x, point.y) else null
                val artVisible = exposed.count { probe -> sample(probe)?.let { matches(it, probe.color) } == true }
                val solidCounts = protection.map { region ->
                    region.points.count { probe ->
                        sample(probe)?.let { ink -> if (region.expectedFills.isEmpty()) matches(ink, probe.color) else region.expectedFills.any { matches(ink, it) } } == true
                    }
                }
                val unmatchedGaps = wallpaperGaps.mapNotNull { probe ->
                    val observed = sample(probe)
                    if (observed != null && matches(observed, probe.color)) null
                    else "(${probe.x},${probe.y}) expected=#${probe.color.toUInt().toString(16)} " +
                        "observed=${observed?.toUInt()?.toString(16)}"
                }
                val ruleCount = inventoryInk?.rule?.count { point -> sample(point)?.let { matches(it, point.color) } == true }
                val labelCount = inventoryInk?.label?.count { point -> sample(point)?.let { matches(it, point.color) } == true }
                val inkVerified = inventoryInk == null || ruleCount == inventoryInk.rule.size && checkNotNull(labelCount) >= 5
                if (artVisible >= 5 && solidCounts.all { it >= 5 } && unmatchedGaps.isEmpty() && inkVerified) {
                    writeScreenshot(name, image)
                    return
                }
                if (attempt == 4) {
                    writeScreenshot(failureName, image)
                    val solidExamples = protection.map { region ->
                        region.points.take(3).map { probe -> "(${probe.x},${probe.y})=#${sample(probe)?.toUInt()?.toString(16)}" }
                    }
                    details = "decorative=$artVisible/${exposed.size} solid=$solidCounts/${protection.map { it.points.size }} " +
                        "expectedFills=${protection.map { it.expectedFills.map { fill -> fill.toUInt().toString(16) } }} " +
                        "solidExamples=$solidExamples gapMismatches=$unmatchedGaps" +
                        (inventoryInk?.let { " separator=$ruleCount/${it.rule.size} buttonGlyphs=$labelCount/${it.label.size}" } ?: "")
                }
            } finally { image.recycle() }
        }
        rejectedEvidence += "$name never presented both visible photo and protected content: $details"
    }

    /**
     * Choose a point before looking at the window shot. At a hard bitmap edge, CPU and
     * GPU interpolation can differ by several values while both still show wallpaper.
     * A locally flat 7×7 patch eliminates edge samples without widening the match rule.
     */
    private fun stableGapSample(
        band: Rect, bareWallpaper: Bitmap, minimumContrast: Int, fills: Set<Int>,
        distance: (Int, Int) -> Int,
    ): Point {
        val radius = 3
        val candidates = mutableListOf<Point>()
        for (y in band.top + radius until band.bottom - radius) {
            for (x in band.left + radius until band.right - radius) {
                val color = bareWallpaper.getPixel(x, y)
                if (fills.any { distance(color, it) < minimumContrast }) continue
                val flat = (y - radius..y + radius).all { dy ->
                    (x - radius..x + radius).all { dx -> distance(color, bareWallpaper.getPixel(dx, dy)) <= 1 }
                }
                if (flat) candidates += Point(x, y)
            }
        }
        return candidates.minByOrNull { point ->
            abs(point.x - band.centerX()) + abs(point.y - band.centerY())
        } ?: throw PixelEvidenceFailure("No flat, contrast-sensitive bare wallpaper sample inside requested gap $band")
    }

    private fun captureCommittedFrame(root: View): Bitmap {
        val frame = CountDownLatch(1)
        main {
            check(root.isAttachedToWindow && root.isHardwareAccelerated)
            root.viewTreeObserver.registerFrameCommitCallback { frame.countDown() }
            root.invalidate()
        }
        check(frame.await(5, TimeUnit.SECONDS))
        return checkNotNull(test.uiAutomation.takeScreenshot())
    }

    private fun writeScreenshot(name: String, image: Bitmap) {
        File(output, name).outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
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
        val card = main { sample.findViewWithTag<View>("product/background/preview/card") }
        val inset = HoyiUi.dp(page, 4)
        fun cardInViewport(): Rect {
            val visible = Rect()
            check(card.getGlobalVisibleRect(visible))
            val rootInWindow = IntArray(2).also { sample.rootView.getLocationInWindow(it) }
            val sampleInWindow = IntArray(2).also { sample.getLocationInWindow(it) }
            // getLocationInWindow describes the view origin, excluding its own scroll.
            visible.offset(rootInWindow[0] - sampleInWindow[0], rootInWindow[1] - sampleInWindow[1])
            return visible
        }
        val before = main {
            cardInViewport().also { check(preview(page)!!.protectedAreas!!.single() == it) }
        }
        main { sample.scrollTo(inset, 0) }
        val after = main {
            cardInViewport().also {
                check(sample.scrollX == inset && it != before && it.right == before.right - inset) {
                    "Expected a clipped card to shift in its real viewport: scroll=${sample.scrollX} before=$before after=$it"
                }
            }
        }
        var actual: Rect? = null
        try {
            await("background coordinates match the scrolled visible card") {
                actual = preview(page)?.protectedAreas?.singleOrNull()
                actual == after
            }
        } catch (failure: IllegalStateException) {
            throw IllegalStateException("Scrolled root protection mismatch: scroll=$inset before=$before expected=$after actual=$actual", failure)
        }
        main {
            val paper = checkNotNull(preview(page))
            val style = TaskProductVisualStyle(page)
            val bitmap = Bitmap.createBitmap(sample.width, sample.height, Bitmap.Config.ARGB_8888)
            try {
                paper.draw(Canvas(bitmap))
                val releasedX = before.right - 1
                val y = after.centerY()
                check(before.contains(releasedX, y) && !after.contains(releasedX, y))
                check(bitmap.getPixel(after.centerX(), y) == style.page) { "Scrolled card lost its solid backing" }
                check(bitmap.getPixel(releasedX, y) != style.page) { "Previously covered edge did not reveal the photo" }
            } finally { bitmap.recycle() }
            sample.scrollTo(0, 0)
        }
        await("restored root backdrop coordinates") { preview(page)?.protectedAreas?.singleOrNull() == before }
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
                        // Distinct dark/light regions remain for cross-page contrast checks; uneven
                        // geometric texture across each half exposes any false Home backing slab.
                        val dark = intArrayOf(0xff111820.toInt(), 0xff41382d.toInt(), 0xff174136.toInt())
                        val light = intArrayOf(0xfffaf9ef.toInt(), 0xffc9d4db.toInt(), 0xffeedecb.toInt())
                        val paint = Paint()
                        Canvas(bitmap).apply {
                            for (y in 0 until 240 step 12) for (x in 0 until 320 step 16) {
                                val palette = if (x < 160) dark else light
                                paint.color = palette[(x / 16 + y / 12 * 2) % palette.size]
                                drawRect(x.toFloat(), y.toFloat(), (x + 16).toFloat(), (y + 12).toFloat(), paint)
                            }
                        }
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
