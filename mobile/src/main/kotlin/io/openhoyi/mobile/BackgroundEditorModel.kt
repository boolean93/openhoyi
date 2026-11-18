package io.openhoyi.mobile

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.UUID

/** Only stable configuration, a request ID and a private draft ID cross process death. */
internal class BackgroundEditorModel(private val saved: SavedStateHandle) : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val fence = BackgroundRequestFence()
    private lateinit var store: AppBackgroundStore
    private var stop: (() -> Unit)? = null
    private var importJob: Job? = null
    private var draft: BackgroundDraft? = null
    private var baseline = AppBackgroundConfig()
    private var selection = AppBackgroundConfig()
    private var error: Int? = null
    private var done = false
    private var applying = false
    private var restoring = false
    private var pendingDraftId: String? = null
    private var closed = false
    var render: ((State) -> Unit)? = null
        set(value) { field = value; if (::store.isInitialized) emit() }

    data class State(
        val preview: BackgroundSnapshot,
        val photo: AppBackground.Photo?,
        val error: Int?,
        val busy: Boolean,
        val applying: Boolean,
        val canApply: Boolean,
        val done: Boolean,
    )

    fun bind(owner: AppBackgroundStore) {
        if (::store.isInitialized) { emit(); return }
        store = owner
        val applied = owner.current.config
        baseline = saved.get<String>(BASELINE)?.let(AppBackgroundConfig::restore) ?: applied
        selection = saved.get<String>(SELECTION)?.let(AppBackgroundConfig::restore) ?: applied
        if (baseline != applied) {
            // Process death after the pointer commit or a newer editor won the transaction.
            baseline = applied
            selection = applied
        }
        val restoreId = saved.get<String>(DRAFT)?.takeIf { AppBackground.validId(it) }
        val interrupted = saved.get<Boolean>(IMPORTING) == true || saved.get<Boolean>(APPLYING) == true
        // This exact staging UUID belonged to the interrupted worker, never the applied pointer.
        saved.get<String>(IMPORT_FILE)?.takeIf { AppBackground.validId(it) }?.let(owner::discardDraft)
        saved[IMPORT_FILE] = null
        saved[IMPORTING] = false; saved[APPLYING] = false
        if (interrupted) {
            selection = applied
            error = R.string.background_restore_interrupted
            if (restoreId != null) owner.discardDraft(restoreId)
            remember()
        } else if (restoreId != null) {
            val token = fence.next()
            restoring = true
            pendingDraftId = restoreId
            remember()
            importJob = scope.launch {
                val recovered = owner.restoreDraft(restoreId)
                if (!fence.accepts(token)) {
                    if (recovered != null) owner.discardDraft(recovered.id)
                    return@launch
                }
                restoring = false
                pendingDraftId = null
                draft = recovered
                if (recovered == null && selection.choice != applied.choice) {
                    selection = applied
                    error = R.string.background_restore_interrupted
                }
                remember()
                emit()
            }
        } else if (selection.choice is AppBackground.Photo && selection.choice != applied.choice) {
            selection = applied
            error = R.string.background_restore_interrupted
            remember()
        } else remember()
        stop = owner.observe { emit() }
    }

    private fun remember() {
        saved[BASELINE] = baseline.key
        saved[SELECTION] = selection.key
        saved[DRAFT] = draft?.id ?: pendingDraftId
    }

    fun currentPreview(): BackgroundSnapshot? {
        if (!::store.isInitialized) return null
        val applied = store.current
        val selectedPhoto = selection.choice as? AppBackground.Photo
        val image = if (selectedPhoto != null) draft?.takeIf { it.choice == selectedPhoto }?.image
            ?: applied.image.takeIf { applied.config.choice == selectedPhoto } else null
        return BackgroundSnapshot(selection, image, unavailable = selectedPhoto != null && image == null && applied.unavailable)
    }

    private fun emit() {
        val preview = currentPreview() ?: return
        val applied = store.current
        render?.invoke(State(
            preview = preview,
            photo = draft?.choice ?: (applied.config.choice as? AppBackground.Photo),
            error = error,
            busy = saved.get<String>(REQUEST) != null || saved.get<Boolean>(IMPORTING) == true || restoring,
            applying = applying,
            canApply = !restoring && (selection.choice !is AppBackground.Photo || preview.image != null),
            done = done,
        ))
    }

    fun choose(choice: AppBackground) {
        if (closed || applying) return
        if (selection.choice == choice) return
        stopImport()
        if (saved.get<String>(REQUEST) != null) saved[REQUEST_STALE] = true
        selection = selection.copy(choice = choice)
        error = null
        remember(); emit()
    }

    fun strength(value: Int) {
        if (closed || applying) return
        selection = selection.copy(strength = value.coerceIn(AppBackgroundConfig.MIN_STRENGTH, AppBackgroundConfig.MAX_STRENGTH))
        remember(); emit()
    }

    fun beginPicker(): Boolean {
        if (closed || applying || saved.get<String>(REQUEST) != null) return false
        stopImport()
        saved[REQUEST] = UUID.randomUUID().toString()
        saved[REQUEST_STALE] = false
        error = null
        remember()
        emit()
        return true
    }

    fun pickerFailedToLaunch() {
        saved[REQUEST] = null
        saved[REQUEST_STALE] = false
        error = R.string.background_import_failed
        emit()
    }

    fun pickerResult(uri: Uri?) {
        // One in-flight picker only; a callback without its saved request is stale.
        if (closed || saved.get<String>(REQUEST) == null) return
        saved[REQUEST] = null
        val stale = saved.get<Boolean>(REQUEST_STALE) == true
        saved[REQUEST_STALE] = false
        if (uri == null || stale) { emit(); return }
        val token = fence.next()
        val importId = UUID.randomUUID().toString()
        saved[IMPORT_FILE] = importId
        saved[IMPORTING] = true
        error = null
        emit()
        importJob = scope.launch {
            try {
                val imported = store.import(uri, importId)
                if (!fence.accepts(token)) {
                    store.discardDraft(imported.id)
                    return@launch
                }
                draft?.let { store.discardDraft(it.id) }
                draft = imported
                selection = selection.copy(choice = imported.choice)
                remember()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (fence.accepts(token)) error = R.string.background_import_failed }
            catch (_: OutOfMemoryError) { if (fence.accepts(token)) error = R.string.background_import_failed }
            finally {
                if (fence.accepts(token)) {
                    saved[IMPORTING] = false
                    saved[IMPORT_FILE] = null
                    emit()
                }
            }
        }
    }

    fun apply() {
        if (closed || applying || restoring || saved.get<String>(REQUEST) != null ||
            saved.get<Boolean>(IMPORTING) == true) return
        val next = selection
        val candidate = draft
        applying = true
        saved[APPLYING] = true
        error = null
        emit()
        scope.launch {
            val result = store.apply(baseline, next, candidate)
            if (closed) return@launch
            applying = false
            saved[APPLYING] = false
            when (result) {
                AppBackgroundStore.Result.APPLIED, AppBackgroundStore.Result.UNCHANGED -> {
                    done = true
                    if (next.choice != candidate?.choice) candidate?.let { store.discardDraft(it.id) }
                    draft = null
                }
                AppBackgroundStore.Result.STALE -> {
                    baseline = store.current.config
                    error = R.string.background_changed_elsewhere
                }
                AppBackgroundStore.Result.FAILED -> error = R.string.background_save_failed
            }
            remember()
            emit()
        }
    }

    fun cancel(): Boolean {
        if (applying) return false
        closed = true
        stop?.invoke(); stop = null
        stopImport()
        saved[REQUEST] = null
        saved[REQUEST_STALE] = false
        draft?.let { store.discardDraft(it.id) }
        draft = null
        remember()
        return true
    }

    private fun stopImport() {
        fence.next()
        importJob?.cancel()
        importJob = null
        pendingDraftId?.let { store.discardDraft(it) }
        pendingDraftId = null
        restoring = false
        saved[IMPORTING] = false
        saved[IMPORT_FILE] = null
    }

    override fun onCleared() {
        stop?.invoke()
        if (!closed && ::store.isInitialized) {
            if (!applying) cancel() else closed = true
        }
        scope.cancel()
        super.onCleared()
    }

    companion object {
        internal const val BASELINE = "bg.baseline"
        internal const val SELECTION = "bg.selection"
        internal const val DRAFT = "bg.draft"
        internal const val REQUEST = "bg.picker"
        private const val REQUEST_STALE = "bg.picker.stale"
        internal const val IMPORTING = "bg.importing"
        internal const val IMPORT_FILE = "bg.importFile"
        internal const val APPLYING = "bg.applying"
    }
}
