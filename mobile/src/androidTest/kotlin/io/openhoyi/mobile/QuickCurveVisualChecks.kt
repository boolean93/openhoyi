package io.openhoyi.mobile

import android.app.Instrumentation
import android.content.ContentValues
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.SystemClock
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.roundToInt
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataInputStream
import java.io.File
import java.util.UUID

/** Real Home window, synthetic data only. Caller must provide a fresh disposable CI emulator. */
internal class QuickCurveVisualChecks(private val test: Instrumentation) {
    private fun <T> main(block: () -> T): T = BoundedInstrumentationMain.run("Home shortcut capture", 5000, block)

    fun run(dark: Boolean, wallpaperEnabled: Boolean) {
        val context = test.targetContext
        check(BuildConfig.MOCK_MODE && context.packageName == "io.openhoyi.mobile.mock")
        val app = context.applicationContext as MobileApplication
        val ids = listOf("factory-v3-050", "factory-v3-001", "factory-v3-002", "factory-v3-004", "factory-v3-005", "factory-v3-004")
        val names = listOf("浅烘Slayer长预浸", "Espresso深烘", "SOE浅烘", "HOYI Limit Extraction", "Hyper Ex 超萃手冲", "HOYI Limit Extraction")
        val tags = (0..4).map { "product/home/direct-preset/$it" } + "product/home/last-curve"
        val titleTags = tags.take(5).map { "$it/name" } + "product/home/quick-recipe"
        val labels = listOf("拨杆配方", "预设 A", "预设 B", "预设 C", "预设 D", "复用上一杯")
        val presets = context.getSharedPreferences("presets", Context.MODE_PRIVATE)
        val storedFifth = "factory-v3-005"
        main {
            check(app.history.entries.isEmpty()) { "Refusing to seed application with existing shot history" }
            check(app.languagePreferences.select(AppLanguage.CHINESE) != AppLanguagePreference.Selection.SAVE_FAILED)
            check(app.fontSizePreferences.select(AppFontSize.MEDIUM) != AppFontSizePreference.Selection.SAVE_FAILED)
            check(app.appearancePreferences.selectDark(dark) != AppAppearancePreference.Selection.SAVE_FAILED)
            check(presets.all.isEmpty()) { "Expected fresh disposable Mock data" }
            var editor = presets.edit()
            for (index in 1..4) editor = editor.putString(PresetSlots.key(index), ids[index])
            check(editor.putString(PresetSlots.key(5), storedFifth).commit())
            val usage = app.curveUsageResult.getOrThrow()
            check(usage.latest() == null)
            check(usage.record("shortcut-visual-fixture", ids.last(), 1L))
            val lever = app.leverRecipes
            val address = "00:00:00:00:00:01"
            check(lever.attemptFor(address) == null && !lever.blocked)
            val token = requireNotNull(lever.begin(MachineLeverRecipeStore.Snapshot(address, ids.first(), names.first(),
                "08825c0046031e5a0000009608019000000000d8", 1L, "1.1.5")))
            check(lever.complete(token, MachineLeverRecipeStore.State.SENT))
        }
        if (wallpaperEnabled) {
            // An asymmetric high-/low-luminance fixture makes foreground materials and gaps observable.
            val side = 600
            val colors = intArrayOf(0xffc0cbc5.toInt(), 0xff969c98.toInt(), 0xffe9e5db.toInt(), 0xfff5f7f1.toInt())
            val pixels = IntArray(side * side) { index ->
                val x = index % side
                val y = index / side
                colors[(if (x < side / 2) 0 else 2) + (x / 70 + y / 70) % 2]
            }
            val source = Bitmap.createBitmap(pixels, side, side, Bitmap.Config.ARGB_8888)
            val resolver = context.contentResolver
            val wallpaper = try {
                checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "shortcut-visual-${UUID.randomUUID()}.png")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }))
            } catch (failure: Throwable) {
                source.recycle()
                throw failure
            }
            try {
                checkNotNull(resolver.openOutputStream(wallpaper)).use {
                    check(source.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
                check(resolver.update(wallpaper, ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }, null, null) == 1)
                val magic = ByteArray(8)
                DataInputStream(checkNotNull(resolver.openInputStream(wallpaper))).use { it.readFully(magic) }
                check(magic.contentEquals(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10))) {
                    "Generated fixture input is not PNG"
                }
                val backgrounds = main { app.pageBackgrounds }
                runBlocking {
                    withTimeout(15000) {
                        val previous = backgrounds.current.config
                        check(previous.choice == AppBackground.Solid)
                        val draft = backgrounds.import(wallpaper, UUID.randomUUID().toString())
                        val next = AppBackgroundConfig(draft.choice, AppBackgroundConfig.MAX_STRENGTH)
                        check(backgrounds.apply(previous, next, draft) == AppBackgroundStore.Result.APPLIED)
                    }
                }
            } finally {
                source.recycle()
                check(resolver.delete(wallpaper, null, null) == 1) { "Could not delete owned fixture MediaStore row" }
            }
        } else {
            check(main { app.pageBackgrounds.current.config.choice } == AppBackground.Solid)
        }

        val page = MainPageTestHost.launch(test, timeoutMs = 15000)
        val host = page.activity
        val out = File(context.filesDir, "quick-curve-visual").apply { check(mkdirs() || isDirectory) }
        try {
            val deadline = SystemClock.elapsedRealtime() + 12000
            while (!main {
                val root = page.active("root:BREW").requireView()
                host.currentService?.running == true && titleTags.indices.all { index ->
                    root.findViewWithTag<TextView>(titleTags[index])?.let { it.text.toString() == names[index] && it.layout != null } == true
                }
            }) {
                check(SystemClock.elapsedRealtime() < deadline) { "Populated quick-curve Home did not become ready" }
                SystemClock.sleep(50)
            }
            SystemClock.sleep(300)
            val proof = main {
                val root = page.active("root:BREW").requireView()
                check(root.findViewWithTag<View>("product/home/quick-title") == null &&
                    root.findViewWithTag<View>("product/home/preview-legend") == null)
                val density = host.resources.displayMetrics.density
                val portrait = host.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
                if (!portrait) {
                    HomePresetRowChecks.verifyWideReparent(host, host.findViewById<ViewGroup>(android.R.id.content))
                    check(root.findViewWithTag<ScrollView>("product/home/page-scroll")?.scrollY == 0) {
                        "Tablet shortcut proof must not scroll to reveal its cards"
                    }
                }
                val bounds = tags.map { tag -> Rect().apply {
                    val view = checkNotNull(root.findViewWithTag<View>(tag))
                    check(view.getGlobalVisibleRect(this) && width() == view.width && height() == view.height) {
                        "$tag clipped or needs scrolling: $this / ${view.width}×${view.height}"
                    }
                } }
                val items = JSONArray()
                tags.indices.forEach { index ->
                    val tag = tags[index]
                    val title = requireNotNull(root.findViewWithTag<TextView>(titleTags[index]))
                    val layout = requireNotNull(title.layout)
                    val label = requireNotNull(root.findViewWithTag<TextView>("$tag/label"))
                    check(label.text.toString() == labels[index]) { "Unexpected slot label: ${label.text}" }
                    check(layout.lineCount == 1 && layout.getEllipsisCount(0) == 0 &&
                        layout.getLineWidth(0) <= (title.width - title.compoundPaddingLeft - title.compoundPaddingRight) + 1f) {
                        "Shortcut name wraps or clips: ${title.text}"
                    }
                    val preview = requireNotNull(root.findViewWithTag<View>("$tag/preview"))
                    check(preview.isShown && preview.width > 0 && preview.height > 0)
                    val card = bounds[index]
                    val heightDp = card.height() / density
                    val expectedHeight = if (portrait) 59f else 66f
                    check(kotlin.math.abs(heightDp - expectedHeight) <= 2f) {
                        "$tag height ${heightDp}dp, expected compact ${expectedHeight}dp on the actual viewport"
                    }
                    val titleBounds = Rect().also { check(title.getGlobalVisibleRect(it)) }
                    val labelBounds = Rect().also { check(label.getGlobalVisibleRect(it)) }
                    val tapTag = if (index == 5) "product/home/quick-launch" else tag
                    val control = requireNotNull(root.findViewWithTag<View>(tapTag))
                    check(control.isClickable && control.isEnabled && control.width >= 48 * density && control.height >= 48 * density) {
                        "$tapTag must be a direct action ≥48dp"
                    }
                    items.put(JSONObject().put("name", names[index]).put("label", label.text.toString())
                        .put("rect", JSONArray(listOf(card.left, card.top, card.right, card.bottom)))
                        .put("heightDp", heightDp.toDouble())
                        .put("nameSp", title.textSize / density).put("labelSp", label.textSize / density)
                        .put("nameOffsetPx", JSONArray(listOf(titleBounds.left + title.paddingStart - card.left, titleBounds.top - card.top)))
                        .put("labelOffsetPx", JSONArray(listOf(labelBounds.left - card.left, labelBounds.top - card.top)))
                        .put("previewPx", JSONArray(listOf(preview.width, preview.height))))
                }
                if (portrait) {
                    check(bounds.indices.all { index ->
                        val even = index - index % 2
                        bounds[index].top == bounds[even].top &&
                            (index % 2 == 0 || bounds[index].left >= bounds[even].right) &&
                            (index < 2 || bounds[index].top > bounds[index - 2].top)
                    }) { "Expected three rows in reading order: $bounds" }
                } else {
                    check(bounds.all { it.top == bounds.first().top } &&
                        bounds.zipWithNext().all { (first, next) -> first.right <= next.left }) {
                        "Expected six shortcut controls in one tablet row: $bounds"
                    }
                    check(root.findViewWithTag<View>("product/home/preset-group")?.isShown == true)
                }
                check(root.findViewWithTag<View>("product/home/direct-preset/5") == null)
                check(presets.getString(PresetSlots.key(5), null) == storedFifth)
                check(mainWallpaperVisible(root) == wallpaperEnabled)
                check(host.currentService?.shotState == io.openhoyi.session.ExtractionState.IDLE && app.history.entries.isEmpty()) {
                    "Visual fixture must not initiate or record an extraction"
                }
                val viewport = Rect().also {
                    check(root.findViewWithTag<ScrollView>("product/home/page-scroll").getGlobalVisibleRect(it))
                }
                val bottomGap = (viewport.bottom - bounds.maxOf { it.bottom }) / density
                check(bottomGap >= 10f) { "Expected breathing room inside the Home viewport, got ${bottomGap}dp" }
                JSONObject().put("portrait", portrait).put("dark", dark).put("wallpaper", wallpaperEnabled)
                    .put("viewport", JSONArray(listOf(viewport.left, viewport.top, viewport.right, viewport.bottom)))
                    .put("bottomGapDp", bottomGap.toDouble()).put("density", density.toDouble()).put("items", items)
            }
            val bitmap = checkNotNull(test.uiAutomation.takeScreenshot())
            try {
                if (!wallpaperEnabled && (!proof.getBoolean("dark") || !proof.getBoolean("portrait"))) {
                    val items = proof.getJSONArray("items")
                    val density = proof.getDouble("density")
                    val viewport = proof.getJSONArray("viewport")
                    val dark = proof.getBoolean("dark")
                    val first = items.getJSONObject(0).getJSONArray("rect")
                    val lastBottom = (0 until items.length()).maxOf {
                        items.getJSONObject(it).getJSONArray("rect").getInt(3)
                    }
                    val outer = first.getInt(0) - (8 * density).roundToInt()
                    fun luminance(x: Int, y: Int): Double {
                        val color = bitmap.getPixel(x, y)
                        return .2126 * Color.red(color) + .7152 * Color.green(color) + .0722 * Color.blue(color)
                    }
                    fun profile(card: JSONArray, offsetsDp: List<Double>): List<Double> {
                        val center = (card.getInt(0) + card.getInt(2)) / 2
                        return offsetsDp.map { offset ->
                            val y = card.getInt(3) + (offset * density).roundToInt()
                            check(outer >= viewport.getInt(0) && center < viewport.getInt(2) &&
                                y in viewport.getInt(1) until viewport.getInt(3)) {
                                "Shortcut shadow probe left the app viewport"
                            }
                            // Solid Home is uniform outside the card at this scan line.
                            luminance(outer, y) - luminance(center, y)
                        }
                    }
                    if (dark) {
                        val deltas = profile(first, listOf(1.0, 2.5))
                        check(deltas.first() >= 2.0 && deltas.last() > .5) {
                            "Dark tablet contact shadow missing just outside the card: $deltas"
                        }
                        proof.put("tabletSolidShadowSamples", JSONArray(deltas))
                    } else {
                        val profiles = JSONArray()
                        for (index in 0 until items.length()) {
                            val card = items.getJSONObject(index).getJSONArray("rect")
                            if (card.getInt(3) != lastBottom) continue
                            val deltas = profile(card, listOf(.75, 1.75, 3.0, 5.5))
                            check(deltas.first() > .5 && deltas.last() < deltas.first() - .25) {
                                "Light shortcut native shadow missing or not fading in the viewport: $deltas"
                            }
                            profiles.put(JSONArray(deltas))
                        }
                        proof.put("lightNativeShadowProfiles", profiles)
                    }
                }
                File(out, "home.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            } finally { bitmap.recycle() }
            File(out, "geometry.json").writeText(proof.toString(2))
        } finally {
            main { host.finish() }
        }
    }

    private fun mainWallpaperVisible(root: View): Boolean =
        (root.background as? PageBackdrop)?.snapshot?.image != null
}
