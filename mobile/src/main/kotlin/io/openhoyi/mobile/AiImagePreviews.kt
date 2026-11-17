package io.openhoyi.mobile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.view.View
import android.view.ViewTreeObserver
import android.widget.ImageView
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** Local-only, viewport-lazy previews. Vault reads and sampled decoding never run on the UI thread. */
internal class AiImagePreviews(context: Context, private val root: View) : AutoCloseable {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    // A cancelled blocking vault read/decode must finish before another can allocate its buffer.
    private val decoder = Semaphore(1)
    private val cache = AiImagePreviewCache<Bitmap>()
    private data class Target(val key: AiImagePreviewKey, val item: AiAttachment,
        val image: ImageView, val placeholder: TextView, var job: Job? = null, var shown: Boolean = false)
    private val targets = mutableListOf<Target>()
    private var generation = 0L
    private var epoch: String? = null
    private var closed = false
    private val rectangle = Rect()
    private val listener = ViewTreeObserver.OnPreDrawListener { refresh(); true }
    init { root.viewTreeObserver.addOnPreDrawListener(listener) }
    fun begin(owner: String?, session: String?, items: List<AiAttachment>) {
        generation++
        releaseTargets()
        epoch = owner
        cache.begin(session, if (owner == null) emptySet() else items.filter { it.kind == AiAttachmentKind.IMAGE }
            .map { AiImagePreviewKey.of(owner, it) }.toSet())
    }
    fun bind(item: AiAttachment, image: ImageView, placeholder: TextView) {
        val owner = epoch ?: return
        if (!closed) targets.add(Target(AiImagePreviewKey.of(owner, item), item, image, placeholder))
    }
    private fun refresh() {
        if (closed) return
        targets.forEach { target ->
            val visible = root.isShown && target.image.getGlobalVisibleRect(rectangle)
            if (!visible) {
                target.job?.cancel()
                target.image.setImageDrawable(null); target.shown = false
            } else if (!target.shown && target.job == null) {
                val existing = cache[target.key]
                if (existing != null) show(target, existing.value)
                else load(target)
            }
        }
    }
    private fun show(target: Target, bitmap: Bitmap?) {
        target.image.setImageBitmap(bitmap)
        target.placeholder.visibility = if (bitmap == null) View.VISIBLE else View.GONE
        target.placeholder.setText(if (bitmap == null) R.string.ai_image_preview_unavailable else R.string.ai_image_preview_loading)
        target.image.contentDescription = target.image.context.getString(if (bitmap == null)
            R.string.ai_image_preview_unavailable else R.string.ai_image_preview_description)
        target.shown = true
    }
    private fun load(target: Target) {
        val version = generation
        target.job = scope.launch {
            var decoded: Bitmap? = null
            var published = false
            try {
                decoder.withPermit {
                    withContext(Dispatchers.IO) {
                        ensureActive()
                        // Both private v2 images and legacy encrypted images go through the same vault.
                        decoded = AiImagePreviewPolicy.read(target.item,
                            { AiAttachmentVault(app, target.key.epoch).load(it) }) { bytes ->
                                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                                check(bounds.outMimeType == "image/jpeg")
                                val sample = AiImagePreviewPolicy.sample(bounds.outWidth, bounds.outHeight)
                                ensureActive()
                                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
                                    inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888
                                }) ?: error("Unavailable")
                                val fit = AiImagePreviewPolicy.fit(bitmap.width, bitmap.height,
                                    AiImagePreviewPolicy.MAX_EDGE, AiImagePreviewPolicy.MAX_EDGE)
                                if (fit != (bitmap.width to bitmap.height)) {
                                    bitmap.recycle(); error("Unavailable")
                                }
                                bitmap
                        }
                    }
                }
                ensureActive()
                if (closed || generation != version || target !in targets) return@launch
                cache.put(target.key, decoded, decoded?.allocationByteCount ?: 0)
                published = true
                show(target, decoded)
            } finally {
                // Never recycle a bitmap already shared by another visible target/cache entry.
                if (!published) decoded?.recycle()
                if (generation == version) { target.job = null; target.image.invalidate() }
            }
        }
    }
    private fun releaseTargets() {
        targets.forEach { it.job?.cancel(); it.image.setImageDrawable(null) }
        targets.clear()
    }
    override fun close() {
        closed = true; generation++
        if (root.viewTreeObserver.isAlive) root.viewTreeObserver.removeOnPreDrawListener(listener)
        releaseTargets(); cache.clear(); scope.cancel()
    }
}
