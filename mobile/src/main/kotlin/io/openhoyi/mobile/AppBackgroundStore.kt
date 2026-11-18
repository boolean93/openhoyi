package io.openhoyi.mobile

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal data class BackgroundSnapshot(
    val config: AppBackgroundConfig,
    val image: Bitmap? = null,
    val unavailable: Boolean = false,
    val version: Long = 0,
)

internal data class BackgroundDraft(val id: String, val image: Bitmap) {
    val choice get() = AppBackground.Photo(id)
}

/** Owns normalized files, one applied decode and all pointer transactions. Page views only see pixels. */
internal class AppBackgroundStore(
    context: Context,
    private val root: File = File(context.noBackupFilesDir, "page-backgrounds"),
    prefs: SharedPreferences = context.getSharedPreferences("page_background", Context.MODE_PRIVATE),
    commit: (SharedPreferences.Editor) -> Boolean = SharedPreferences.Editor::commit,
) {
    private val app = context.applicationContext
    private val writer = BackgroundPointer(prefs, commit)
    private val mutex = Mutex()
    private val importPermit = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val observers = mutableSetOf<(BackgroundSnapshot) -> Unit>()
    private var latest = BackgroundSnapshot(writer.read())
    val current get() = latest

    init {
        val snapshot = latest
        val photo = snapshot.config.choice as? AppBackground.Photo
        if (photo != null) scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                importPermit.withLock { mutex.withLock { readImage(owned(photo.id, draft = false)) } }
            }
            if (latest === snapshot) publish(snapshot.copy(image = bitmap, unavailable = bitmap == null))
        }
    }

    fun observe(observer: (BackgroundSnapshot) -> Unit): () -> Unit {
        observers += observer
        observer(latest)
        return { observers -= observer }
    }

    private fun publish(snapshot: BackgroundSnapshot) {
        latest = snapshot
        observers.toList().forEach { it(snapshot) }
    }

    private fun owned(id: String, draft: Boolean): File {
        require(AppBackground.validId(id))
        return File(root, if (draft) "draft-$id.jpg" else "image-$id.jpg")
    }

    private fun createRoot() {
        check(root.isDirectory || root.mkdirs())
        check(!Files.isSymbolicLink(root.toPath()))
    }

    private fun readImage(file: File): Bitmap? {
        if (Files.isSymbolicLink(root.toPath()) || Files.isSymbolicLink(file.toPath())) return null
        return try { BackgroundImageFile.inspectNormalized(file) }
        catch (_: Exception) { null }
        catch (_: OutOfMemoryError) { null }
    }

    suspend fun import(uri: Uri, id: String): BackgroundDraft {
        val file = owned(id, draft = true)
        var created = false
        try {
            return withContext(Dispatchers.IO) {
                mutex.withLock {
                    createRoot()
                    check(file.createNewFile())
                    created = true
                }
                // No persisted draft ID points here until normalization finishes. A slow
                // provider must not keep another editor from committing its own finished file.
                importPermit.withLock { BackgroundDraft(id, BackgroundImageFile.normalize(app.contentResolver, uri, file)) }
            }
        } catch (failure: Throwable) {
            if (created) withContext(NonCancellable + Dispatchers.IO) { mutex.withLock { file.delete() } }
            throw failure
        }
    }

    suspend fun restoreDraft(id: String): BackgroundDraft? = withContext(Dispatchers.IO) {
        importPermit.withLock { mutex.withLock { readImage(owned(id, draft = true))?.let { BackgroundDraft(id, it) } } }
    }

    fun discardDraft(id: String) {
        if (!AppBackground.validId(id)) return
        scope.launch(Dispatchers.IO) { mutex.withLock { owned(id, draft = true).delete() } }
    }

    enum class Result { APPLIED, UNCHANGED, FAILED, STALE }

    /** Noncancelable once the transaction starts; dispatch Apply only while the draft generation is current. */
    suspend fun apply(expected: AppBackgroundConfig, next: AppBackgroundConfig, draft: BackgroundDraft?): Result =
        withContext(NonCancellable + Dispatchers.Main.immediate) {
            mutex.withLock {
                val previous = latest
                if (previous.config != expected) return@withLock Result.STALE
                if (next == expected) return@withLock Result.UNCHANGED
                val photo = next.choice as? AppBackground.Photo
                val staged = draft?.takeIf { it.choice == photo }
                val decoded = if (photo == previous.config.choice) previous.image else staged?.image
                val committed = try {
                    withContext(Dispatchers.IO) {
                        val existing = photo?.let { owned(it.id, draft = false) }
                        if (photo != null && staged == null && photo != previous.config.choice)
                            return@withContext false
                        if (photo != null && (decoded == null || (staged == null &&
                            (existing?.isFile != true || Files.isSymbolicLink(existing.toPath()))))) return@withContext false
                        if (staged != null) {
                            val source = owned(staged.id, draft = true)
                            if (!source.isFile || Files.isSymbolicLink(source.toPath()) || existing == null || existing.exists())
                                return@withContext false
                            Files.move(source.toPath(), existing.toPath(), StandardCopyOption.ATOMIC_MOVE)
                        }
                        if (!writer.write(next)) {
                            if (staged != null && existing != null) {
                                // Preserve an editable draft where possible. Neither key references this new file.
                                runCatching { Files.move(existing.toPath(), owned(staged.id, draft = true).toPath(), StandardCopyOption.ATOMIC_MOVE) }
                            }
                            return@withContext false
                        }
                        true
                    }
                } catch (_: Exception) { false }
                if (!committed) return@withLock Result.FAILED
                publish(BackgroundSnapshot(config = next, image = decoded, version = previous.version + 1))
                val old = previous.config.choice as? AppBackground.Photo
                if (old != null && old != photo) withContext(Dispatchers.IO) {
                    // No live view needs the file; each uses the already decoded snapshot.
                    runCatching { owned(old.id, draft = false).delete() }
                }
                Result.APPLIED
            }
        }
}

/** A failed SharedPreferences commit may mutate its memory map; restore the previous value. */
internal class BackgroundPointer(
    private val prefs: SharedPreferences,
    private val commit: (SharedPreferences.Editor) -> Boolean = SharedPreferences.Editor::commit,
) {
    fun read(): AppBackgroundConfig = AppBackgroundConfig.restore(prefs.all[KEY] as? String)
    fun write(next: AppBackgroundConfig): Boolean {
        val before = prefs.all[KEY] as? String
        if (runCatching { commit(prefs.edit().putString(KEY, next.key)) }.getOrDefault(false)) return true
        runCatching { commit(prefs.edit().apply { if (before == null) remove(KEY) else putString(KEY, before) }) }
        return false
    }
    companion object { const val KEY = "applied" }
}
