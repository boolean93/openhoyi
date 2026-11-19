package io.openhoyi.mobile

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.graphics.drawable.StateListDrawable
import android.text.Editable
import android.text.TextWatcher
import kotlinx.coroutines.delay
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.WindowInsets
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.FrameLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Visible state is retained for configuration changes; disk persistence is separately encrypted. */
internal class AiChatViewState : ViewModel() {
    val lines = mutableListOf<AiChatLine>()
    var usage = AiUsageLedger()
    var protocol = AiProtocolHistory.empty()
    var contextGeneration = 0L // Native pending approvals must bind this generation.
    var keyRevision: Long? = null
    var restored = false
    var credentialEpoch: String? = null
    val ocr = mutableListOf<AiOcrRecord>()
    val inactive = mutableListOf<AiSession>()
    val attachments = mutableListOf<AiAttachment>()
    var attachmentGeneration = 0L
    val submission = AiChatPendingDraft()
    var draft: String
        get() = submission.draft
        set(value) { submission.draft = value }
    var selectionStart = 0
    var selectionEnd = 0
    var focused = false
    var scrollY = 0
    var diagnostic: AiDiagnostic? = null
    var failureAttemptId: String? = null
    var failureNotice = 0
    var notice = 0
    fun clearCurrent() { ocr.clear(); attachmentGeneration++; attachments.clear(); contextGeneration++; usage = AiUsageLedger(); protocol = AiProtocolHistory.empty(protocol.modelId); lines.clear(); submission.clear(); selectionStart = 0; selectionEnd = 0; notice = 0; diagnostic = null; failureAttemptId = null; failureNotice = 0 }
    fun clear() { clearCurrent(); inactive.clear(); protocol = AiProtocolHistory.empty() }
    override fun onCleared() = clear()
}

class AiChatActivity : ThemedActivity() {
    private val memory by lazy { ViewModelProvider(this)[AiChatViewState::class.java] }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val requestDiagnostics by lazy { AiDiagnosticRecorder(record = { kind, fields -> (application as MobileApplication).logs.record(kind, fields) }) }
    private var diagnosticAttempt: AiDiagnosticRecorder.Attempt? = null
    private enum class CompactionStatus { IDLE, RUNNING, SAVED, FAILED }
    private var compactionStatus = CompactionStatus.IDLE
    private var retryProgress: AiRetryProgress? = null
    private var streamPreview = AiStreamPreview()
    private var request: Job? = null
    private var draftSave: Job? = null
    private val sessionStore by lazy { AiSessionStore(this) }
    private var sessionLoaded = false
    private val sessionLock = Any()
    private var sessionBase: AiSession? = null
    private var sessionUsage = AiUsageSnapshot()
    private var usageGeneration = 0L
    private val ocrFence = AiOperationFence()
    private var ocrRequest: Job? = null
    private var attachmentBusy = false
    private var attachmentDialog: AlertDialog? = null
    private var pickerEpoch: String? = null
    private var pickerGeneration = -1L
    private var busy = false
    private var generation = 0L
    private var consentDialog: AlertDialog? = null
    private var linkDialog: AlertDialog? = null
    private var usageDialog: AlertDialog? = null
    private var recoveryDialog: AlertDialog? = null
    private val curveSession = java.util.UUID.randomUUID().toString()
    private var curveCoordinator: AiCurveWriteCoordinator? = null
    private fun curveEpoch(revision: Long = AiKeyStore(this).revision()) =
        AiCurveWriteCoordinator.Epoch("$curveSession:${memory.contextGeneration}", revision)
    private fun cancelCurveProposal() { curveCoordinator?.cancel() }
    private var stopKeyObserver: (() -> Unit)? = null
    private lateinit var visual: SettingsVisualStyle
    private lateinit var transcript: LinearLayout
    private lateinit var input: EditText
    private lateinit var send: Button
    private lateinit var status: TextView
    private lateinit var presentation: AiChatPresentation
    private var scroll: ScrollView? = null
    private var service: MobileService? = null
    private val binding: VisibleServiceBinding by lazy {
        VisibleServiceBinding((application as MobileApplication).serviceAvailability,
            bind = {
                val accepted = bindService(Intent(this, MobileService::class.java), connection, 0)
                if (!accepted) unbindService(connection)
                accepted
            }, unbind = { unbindService(connection) }, onReleased = { service = null }, onAvailable = {})
    }
    private val connection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binding.bound) service = (binder as? MobileService.LocalBinder)?.service
        }
        override fun onServiceDisconnected(name: ComponentName?) { service = null }
        override fun onBindingDied(name: ComponentName?) { binding.release() }
        override fun onNullBinding(name: ComponentName?) { binding.release() }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restoreSession()
        pickerEpoch = savedInstanceState?.getString("ai-picker-epoch")
        pickerGeneration = savedInstanceState?.getLong("ai-picker-generation", -1L) ?: -1L
        visual = SettingsVisualStyle(this)
        presentation = AiChatPresentation(this, visual, memory.draft, memory.selectionStart, memory.selectionEnd,
            onBack = { finish() },
            onNewChat = ::newConversation,
            onKey = { stop(); startActivity(Intent(this, AiKeyActivity::class.java)) },
            onSendOrStop = { if (busy) stop(preservePartial = true) else sendOrConfirm() },
            onOpenLink = ::confirmExternalLink,
            onUsage = ::showUsage, onChooseAttachment = { pickAttachment(it) },
            onRemoveAttachment = ::removeAttachment, onModel = ::showModels, onReasoning = ::showReasoning,
            onOpenCurve = ::openCurveCard, curveAvailable = ::curveCardAvailable,
            onReviewCurve = ::reviewCurveCard, curveReviewable = ::curveCardReviewable,
            onViewCurve = ::viewCurveInLibrary, curveLibraryAvailable = ::curveLibraryAvailable)
        transcript = presentation.transcript
        input = presentation.input
        send = presentation.send
        status = presentation.status
        scroll = presentation.scroll
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (memory.submission.pending != null && !canEditDuringReply()) return
                rememberInput()
                draftSave?.cancel()
                draftSave = scope.launch { delay(300); persistSession() }
            }
        })
        installPageBackdrop(presentation.root, visual.palette.page)
        setContentView(presentation.root)
        render()
        if (memory.focused) input.requestFocus()
        stopKeyObserver = AiKeyStore(this).observeMutation {
            scope.launch {
                cancelOcr(false); stop(); memory.clear(); input.text.clear()
                memory.credentialEpoch = AiKeyStore(this@AiChatActivity).credentialEpoch()
                sessionLoaded = false
                loadSessionFence()
                configureUsage()
                memory.keyRevision = AiKeyStore(this@AiChatActivity).revision(); render()
            }
        }
        scroll?.post { scroll?.scrollTo(0, memory.scrollY) }
    }
    override fun onStart() {
        super.onStart()
        val revision = AiKeyStore(this).revision()
        if (memory.keyRevision != null && memory.keyRevision != revision) {
            cancelOcr(false); stop(); memory.clear(); input.text.clear()
            memory.credentialEpoch = AiKeyStore(this).credentialEpoch()
            sessionLoaded = false; loadSessionFence(); configureUsage(); render()
        }
        memory.keyRevision = revision
        if (consented()) binding.start()
    }
    private fun restoreSession() {
        if (!memory.restored) {
            memory.restored = true
            memory.credentialEpoch = AiKeyStore(this).credentialEpoch()
            val epoch = memory.credentialEpoch
            if (epoch == null) memory.notice = R.string.ai_session_unavailable
            else when (val loaded = sessionStore.load(epoch)) {
                is AiSessionLoad.Ready -> {
                    val state = loaded.session
                    memory.ocr.addAll(state.ocr); memory.inactive.addAll(state.inactive); memory.lines.addAll(state.lines); memory.protocol = state.protocol
                    memory.draft = state.draft; memory.attachments.addAll(state.draftAttachments); memory.usage = AiUsageLedger(state.usage)
                }
                AiSessionLoad.Unavailable -> memory.notice = R.string.ai_session_unavailable
                AiSessionLoad.Empty -> Unit
            }
            sessionLoaded = epoch != null
        } else loadSessionFence()
        configureUsage()
    }
    private fun loadSessionFence() {
        if (sessionLoaded) return
        memory.credentialEpoch?.let {
            if (sessionStore.load(it) == AiSessionLoad.Unavailable) memory.notice = R.string.ai_session_unavailable
            sessionLoaded = true
        }
    }
    private fun configureUsage() {
        val initial = memory.usage.snapshot()
        val usageToken = synchronized(sessionLock) {
            sessionUsage = initial
            sessionBase = memory.credentialEpoch?.let { AiSession(it, memory.lines.toList(), memory.protocol,
                memory.submission.pending ?: memory.draft, initial, memory.attachments.toList(), memory.inactive.toList(), memory.ocr.toList()) }
            ++usageGeneration
        }
        memory.usage = AiUsageLedger(initial) { next ->
            // Transport callbacks run on IO. Use only immutable committed UI snapshots here.
            val saved = synchronized(sessionLock) {
                if (usageToken != usageGeneration) true
                else {
                    sessionUsage = next
                    val base = sessionBase
                    val success = base != null && AiKeyStore(this).credentialEpoch() == base.epoch &&
                        sessionStore.save(base.withUsage(next))
                    if (!success) sessionUsage = next.copy(incomplete = true)
                    success
                }
            }
            if (!saved) scope.launch { memory.notice = R.string.ai_session_unavailable; render() }
            saved
        }
    }
    private fun persistSession(): Boolean {
        loadSessionFence()
        val epoch = memory.credentialEpoch
        val saved = synchronized(sessionLock) {
            val state = epoch?.let { AiSession(it, memory.lines.toList(), memory.protocol,
                memory.submission.persistedDraft(), sessionUsage, memory.attachments.toList(), memory.inactive.toList(), memory.ocr.toList()) }
            state != null && AiSessionTransactions.commit(sessionLock, state,
                save = { AiKeyStore(this).credentialEpoch() == epoch && sessionStore.save(it) },
                committed = { sessionBase = it })
        }
        if (!saved) {
            memory.notice = R.string.ai_session_unavailable
            if (::presentation.isInitialized) render()
        }
        return saved
    }
    private fun newConversation() {
        cancelOcr()
        stop()
        val epoch = memory.credentialEpoch
        if (epoch == null) { memory.notice = R.string.ai_session_unavailable; render(); return }
        // Recheck the current file/alias before a destructive new-chat choice, including late key loss.
        synchronized(sessionLock) { sessionStore.load(epoch) }
        if (!sessionStore.needsRecovery()) {
            val oldAttachments = allAttachments()
            memory.clearCurrent(); configureUsage(); input.text.clear()
            if (persistSession()) removeUnreferenced(epoch, oldAttachments)
            render(); return
        }
        if (recoveryDialog != null) return
        recoveryDialog = AlertDialog.Builder(this).setTitle(R.string.ai_session_recovery_title)
            .setMessage(R.string.ai_session_recovery_body)
            .setNegativeButton(R.string.ai_cancel, null)
            .setPositiveButton(R.string.ai_session_recovery_action) { _, _ ->
                val saved = synchronized(sessionLock) {
                    val epoch = memory.credentialEpoch
                    val initial = AiUsageSnapshot()
                    val recovered = epoch != null && AiKeyStore(this).credentialEpoch() == epoch && sessionStore.recover(
                        AiSession(epoch, emptyList(), AiProtocolHistory.empty(), "", initial))
                    if (recovered) {
                        clearStreamPreview()
                        memory.clear(); memory.usage = AiUsageLedger(initial); configureUsage(); input.text.clear()
                    }
                    recovered
                }
                if (!saved) memory.notice = R.string.ai_session_unavailable
                render()
            }.create().also { dialog ->
                dialog.setOnDismissListener { recoveryDialog = null }
                dialog.show(); visual.dialog(dialog)
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply { setSingleLine(false); maxLines = Int.MAX_VALUE }
            }
    }
    private fun removeUnreferenced(epoch: String, items: List<AiAttachment>) {
        val referenced = allAttachments().map { it.id }.toSet()
        val removed = items.distinctBy { it.id }.filter { it.id !in referenced }
        if (removed.isNotEmpty()) scope.launch(NonCancellable + Dispatchers.IO) {
            val vault = AiAttachmentVault(this@AiChatActivity, epoch)
            removed.forEach { vault.remove(it) }
        }
    }
    private fun consented() = getSharedPreferences("ai_consent", MODE_PRIVATE).getInt(AiConsentPolicy.VERSION_KEY, 0).let(AiConsentPolicy::accepts)
    private fun showUsage() {
        if (usageDialog != null) return
        AiUsageDialog.create(this, memory.usage.snapshot(), AiUsageStore.get(this).snapshot()).also { dialog ->
            usageDialog = dialog
            dialog.setOnDismissListener { if (usageDialog === dialog) usageDialog = null }
            dialog.show(); visual.dialog(dialog)
        }
    }
    private fun confirmExternalLink(url: String) {
        if (linkDialog != null || !AiMarkdown.safeHttpUrl(url)) return
        AlertDialog.Builder(this).setTitle(R.string.ai_open_link_title).setMessage(url)
            .setNegativeButton(R.string.ai_cancel, null)
            .setPositiveButton(R.string.ai_open_link) { _, _ ->
                if (!AiMarkdown.safeHttpUrl(url)) return@setPositiveButton
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
                } catch (_: ActivityNotFoundException) {
                    android.widget.Toast.makeText(this, R.string.ai_open_link_failed, android.widget.Toast.LENGTH_SHORT).show()
                } catch (_: SecurityException) {
                    android.widget.Toast.makeText(this, R.string.ai_open_link_failed, android.widget.Toast.LENGTH_SHORT).show()
                }
            }.create().also { dialog ->
                linkDialog = dialog
                dialog.setOnDismissListener { if (linkDialog === dialog) linkDialog = null }
                dialog.show(); visual.dialog(dialog)
            }
    }
    private fun allAttachments(): List<AiAttachment> {
        fun ocrItems(records: List<AiOcrRecord>) = records.flatMap { listOfNotNull(it.original, it.extracted) }
        return memory.lines.flatMap { it.attachments } + memory.attachments + ocrItems(memory.ocr) +
            memory.inactive.flatMap { it.lines.flatMap { l -> l.attachments } + it.draftAttachments + ocrItems(it.ocr) }
    }
    private fun sendAttachments() = memory.attachments.map { item ->
        if (item.kind == AiAttachmentKind.DOCUMENT) memory.ocr.firstOrNull { it.original.id == item.id && it.status == AiOcrStatus.READY }?.extracted
            ?: throw AiAttachmentFailure(AiAttachmentProblem.DOCUMENT)
        else item
    }
    private fun ocrNotice(status: AiOcrStatus) = when(status) {
        AiOcrStatus.READY -> R.string.ai_ocr_ready
        AiOcrStatus.INCOMPLETE -> R.string.ai_ocr_incomplete
        AiOcrStatus.LIMIT -> R.string.ai_ocr_context_limit
        AiOcrStatus.IN_FLIGHT, AiOcrStatus.UNKNOWN, AiOcrStatus.FAILED -> R.string.ai_ocr_unknown
    }
    private fun confirmOcr(document: AiAttachment) {
        if (attachmentBusy || busy || attachmentDialog != null || memory.ocr.any { it.original.id == document.id }) return
        val epoch = memory.credentialEpoch ?: return
        val modelId = memory.protocol.modelId
        val version = memory.attachmentGeneration
        attachmentDialog = AlertDialog.Builder(this).setTitle(R.string.ai_ocr_title).setMessage(R.string.ai_ocr_consent)
            .setNegativeButton(R.string.ai_cancel, null).setPositiveButton(R.string.ai_ocr_agree) { _, _ ->
                if (!busy && !attachmentBusy && memory.credentialEpoch == epoch && memory.protocol.modelId == modelId && memory.attachmentGeneration == version) startOcr(document, epoch, modelId)
            }.create().also { dialog ->
                dialog.setOnDismissListener { if (attachmentDialog === dialog) attachmentDialog = null }
                dialog.show(); visual.dialog(dialog)
            }
    }
    private fun cancelOcr(saveState: Boolean = true) {
        val job = ocrRequest ?: return
        memory.ocr.replaceAll { if (it.status == AiOcrStatus.IN_FLIGHT) AiOcrRecord(it.original, AiOcrStatus.UNKNOWN) else it }
        ocrFence.invalidate(); ocrRequest = null; attachmentBusy = false
        job.cancel()
        if (saveState) { memory.notice = R.string.ai_ocr_unknown; persistSession() }
    }
    private fun startOcr(document: AiAttachment, epoch: String, modelId: String) {
        if (memory.ocr.any { it.original.id == document.id }) return
        if (AiKeyStore(this).status() != AiKeyStatus.SAVED) { memory.notice = R.string.ai_key_unavailable; render(); return }
        val revision = AiKeyStore(this).revision()
        cancelCurveProposal(); memory.contextGeneration++
        val contextToken = memory.contextGeneration
        val operation = ocrFence.begin()
        memory.ocr.add(AiOcrRecord(document, AiOcrStatus.IN_FLIGHT))
        // Durable attempt before constructing any network connection. Recovery never resubmits it.
        if (!persistSession()) { memory.ocr.removeAll { it.original.id == document.id }; return }
        attachmentBusy = true; memory.notice = R.string.ai_ocr_waiting; render()
        ocrRequest = scope.launch {
            val vault = AiAttachmentVault(this@AiChatActivity, epoch)
            val client = AiDocumentOcr({ AiKeyStore(this@AiChatActivity).readForRequest(revision) }, { AiKeyStore(this@AiChatActivity).revision() == revision })
            val stopObserver = AiKeyStore(this@AiChatActivity).observeMutation(client::close)
            val created = java.util.concurrent.atomic.AtomicReference<AiAttachment?>()
            var referenced = false
            fun current() = ocrFence.current(operation) && memory.credentialEpoch == epoch && memory.protocol.modelId == modelId && memory.contextGeneration == contextToken && AiKeyStore(this@AiChatActivity).revision() == revision
            try {
                val result = withContext(Dispatchers.IO) { vault.load(document).use { client.recognize(document, it.bytes) } }
                val extracted = withContext(Dispatchers.IO) {
                    val bytes = result.text.toByteArray(Charsets.UTF_8)
                    val item = AiAttachment(java.util.UUID.randomUUID().toString(), AiAttachmentPolicy.typedName(document.name + "-OCR", "text/plain"), "text/plain", bytes.size, AiAttachmentKind.OCR_TEXT)
                    AiPreparedAttachment(item, bytes).use { vault.save(it); created.set(item); item }
                }
                if (!current()) return@launch
                val status = if (result.incomplete) AiOcrStatus.INCOMPLETE else AiOcrStatus.READY
                memory.ocr.removeAll { it.original.id == document.id }
                memory.ocr.add(AiOcrRecord(document, status, extracted, result.pages, result.referenceInput, result.referenceOutput))
                referenced = true; memory.attachmentGeneration++; memory.contextGeneration++
                memory.notice = ocrNotice(status); persistSession()
            } catch (_: CancellationException) {
                if (current()) {
                    memory.ocr.removeAll { it.original.id == document.id }; memory.ocr.add(AiOcrRecord(document, AiOcrStatus.UNKNOWN))
                    memory.notice = R.string.ai_ocr_unknown
                }
            } catch (failure: Exception) {
                if (current()) {
                    val status = if (failure is AiOcrFailure && failure.problem == AiOcrProblem.LIMIT) AiOcrStatus.LIMIT else AiOcrStatus.UNKNOWN
                    memory.ocr.removeAll { it.original.id == document.id }; memory.ocr.add(AiOcrRecord(document, status))
                    memory.notice = ocrNotice(status)
                }
            } finally {
                stopObserver(); client.close()
                if (!referenced) created.get()?.let { item -> withContext(NonCancellable + Dispatchers.IO) { vault.remove(item) } }
                if (ocrFence.current(operation)) {
                    attachmentBusy = false; ocrRequest = null
                    if (current()) persistSession()
                    if (!isDestroyed) render()
                }
            }
        }
    }
    private fun showReasoning() {
        if (busy || attachmentBusy || attachmentDialog != null) return
        val current = AiReasoningPreference.read(this)
        attachmentDialog = AlertDialog.Builder(this).setTitle(R.string.ai_reasoning_title)
            .setSingleChoiceItems(AiReasoningEffort.entries.map { getString(it.label) }.toTypedArray(), current.ordinal) { dialog, at ->
                if (!AiReasoningPreference.save(this, AiReasoningEffort.entries[at])) {
                    memory.notice = R.string.ai_reasoning_save_failed; render()
                }
                dialog.dismiss()
            }.create().also { dialog ->
                dialog.setOnDismissListener { if (attachmentDialog === dialog) attachmentDialog = null }
                dialog.show(); visual.dialog(dialog)
            }
    }

    private fun showModels() {
        if (busy || attachmentBusy || attachmentDialog != null) return
        attachmentDialog = AlertDialog.Builder(this).setTitle(R.string.ai_model_choose_title)
            .setItems(AiModelPolicy.models.map { it.label + if (it.id == memory.protocol.modelId) " ✓" else "" }.toTypedArray()) { _, at ->
                val target = AiModelPolicy.models[at]
                if (target.id != memory.protocol.modelId) scope.launch { confirmModel(target) }
            }.setNegativeButton(R.string.ai_cancel, null).create().also { dialog ->
                dialog.setOnDismissListener { if (attachmentDialog === dialog) attachmentDialog = null }
                dialog.show(); visual.dialog(dialog)
            }
    }
    private fun confirmModel(target: AiModelSpec) {
        if (busy || attachmentBusy || isFinishing) return
        val source = memory.protocol.modelId
        val epoch = memory.credentialEpoch ?: return
        AlertDialog.Builder(this).setTitle(getString(R.string.ai_model_switch_title, target.label))
            .setMessage(R.string.ai_model_switch_body).setNegativeButton(R.string.ai_cancel, null)
            .setPositiveButton(R.string.ai_model_switch) { _, _ ->
                if (busy || attachmentBusy || memory.protocol.modelId != source || memory.credentialEpoch != epoch) return@setPositiveButton
                rememberInput(); cancelCurveProposal()
                val current = AiSession(epoch, memory.lines.toList(), memory.protocol, memory.draft, sessionUsage, memory.attachments.toList(), memory.inactive.toList(), memory.ocr.toList())
                val candidate = try { AiModelSessions.switch(current, target.id) } catch (_: Exception) { memory.notice = R.string.ai_session_unavailable; render(); return@setPositiveButton }
                val saved = AiSessionTransactions.commit(sessionLock, candidate,
                    save = { AiKeyStore(this).credentialEpoch() == epoch && sessionStore.save(it) },
                    committed = { usageGeneration++; sessionBase = it; sessionUsage = it.usage })
                if (!saved) { memory.notice = R.string.ai_session_unavailable; render(); return@setPositiveButton }
                clearStreamPreview()
                memory.clear(); memory.contextGeneration++
                memory.protocol = candidate.protocol; memory.lines.addAll(candidate.lines)
                memory.draft = candidate.draft; memory.attachments.addAll(candidate.draftAttachments)
                memory.ocr.addAll(candidate.ocr); memory.inactive.addAll(candidate.inactive); memory.usage = AiUsageLedger(candidate.usage)
                configureUsage(); input.setText(memory.draft); input.setSelection(input.length()); render()
            }.create().also { dialog ->
                attachmentDialog = dialog
                dialog.setOnDismissListener { if (attachmentDialog === dialog) attachmentDialog = null }
                dialog.show(); visual.dialog(dialog)
            }
    }
    private fun confirmAttachments(text: String) {
        val active = memory.protocol.messages.any { AiAttachmentConversation.items(it).isNotEmpty() }
        if (memory.attachments.isEmpty() && !active) { begin(text); return }
        if (attachmentDialog != null || busy || attachmentBusy) return
        val epoch = memory.credentialEpoch
        val version = memory.attachmentGeneration
        attachmentDialog = AlertDialog.Builder(this).setTitle(R.string.ai_attachment_send_title)
            .setMessage(R.string.ai_attachment_send_body)
            .setNegativeButton(R.string.ai_cancel, null)
            .setPositiveButton(R.string.ai_attachment_send) { _, _ ->
                if (epoch == memory.credentialEpoch && version == memory.attachmentGeneration && input.text.toString().ifBlank { getString(R.string.ai_attachment_question) } == text) begin(text)
            }.create().also { dialog ->
                dialog.setOnDismissListener { if (attachmentDialog === dialog) attachmentDialog = null }
                dialog.show(); visual.dialog(dialog)
            }
    }
    private fun pickAttachment(image: Boolean) {
        if (busy || attachmentBusy || attachmentDialog != null) return
        if (image && !AiModelPolicy.known(memory.protocol.modelId).images) {
            memory.notice = R.string.ai_model_image_unavailable; render(); return
        }
        rememberInput()
        pickerEpoch = memory.credentialEpoch; pickerGeneration = memory.attachmentGeneration
        if (pickerEpoch == null) { memory.notice = R.string.ai_session_unavailable; render() }
        else try { startActivityForResult(if (image) AiAttachmentPicker.imageIntent() else AiAttachmentPicker.documentIntent(), 8417) }
        catch (_: Exception) { memory.notice = R.string.ai_attachment_unavailable; render() }
    }
    private fun removeAttachment(item: AiAttachment) {
        if (busy || attachmentBusy) return
        val index = memory.attachments.indexOfFirst { it.id == item.id }
        if (index < 0) return
        // Retain copies referenced by history/checkpoints; rollback draft removal if persistence fails.
        memory.attachments.removeAt(index)
        memory.attachmentGeneration++; memory.contextGeneration++; cancelCurveProposal()
        if (persistSession()) {
            val epoch = memory.credentialEpoch
            if (epoch != null && allAttachments().none { it.id == item.id }) scope.launch(NonCancellable + Dispatchers.IO) {
                AiAttachmentVault(this@AiChatActivity, epoch).remove(item)
            }
        } else { memory.attachments.add(index, item) }
        render()
    }
    @Deprecated("System picker compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == AiCurveLibraryRoute.REQUEST_BROWSE) {
            // Explicit navigation away ends this return relationship, not the persisted chat.
            @Suppress("DEPRECATION")
            val forwarded = if (resultCode == RESULT_OK) runCatching {
                data?.getParcelableExtra<Intent>(AiCurveLibraryRoute.EXTRA_EXIT_INTENT)
            }.getOrNull() else null
            if (forwarded != null) {
                // Keep the original shell navigation extras, never an external component/launch flag.
                startActivity(Intent(forwarded).setClass(this, MainActivity::class.java)
                    .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                finish()
                return
            }
            val destination = AiCurveLibraryRoute.exitDestination(
                data?.getStringExtra(AiCurveLibraryRoute.EXTRA_EXIT_DESTINATION), resultCode == RESULT_OK)
            if (destination != null) {
                startActivity(Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(MainActivity.EXTRA_INITIAL_DESTINATION, destination.name))
                finish()
            }
            return
        }
        if (requestCode != 8417 || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val epoch = pickerEpoch ?: return
        val version = pickerGeneration
        pickerEpoch = null
        if (epoch != memory.credentialEpoch || version != memory.attachmentGeneration || busy || attachmentBusy) return
        val existing = memory.attachments.toList()
        attachmentBusy = true; render()
        scope.launch {
            val attachmentTrace = AiAttachmentTrace { kind, fields -> (application as MobileApplication).logs.record(kind, fields) }
            val created = java.util.concurrent.atomic.AtomicReference<AiAttachment?>()
            var referenced = false
            var attachmentFailed = false
            try {
                // Own and wipe bytes on IO. Cancellation closes provider streams and signals query/open.
                val item = withContext(Dispatchers.IO) {
                    suspendCancellableCoroutine<AiAttachment> { pending ->
                        val signal = android.os.CancellationSignal()
                        val stream = java.util.concurrent.atomic.AtomicReference<java.io.InputStream?>()
                        pending.invokeOnCancellation { signal.cancel(); runCatching { stream.getAndSet(null)?.close() } }
                        try {
                            AndroidAiAttachmentSource(this@AiChatActivity, trace = attachmentTrace).prepare(uri, existing, signal) { opened ->
                                stream.set(opened)
                                if (!pending.isActive) { opened.close(); throw CancellationException() }
                            }.use { prepared ->
                                if (!pending.isActive) throw CancellationException()
                                attachmentTrace.event(AiAttachmentStage.STORE, bytes = prepared.bytes.size)
                                AiAttachmentVault(this@AiChatActivity, epoch, attachmentTrace).save(prepared)
                                created.set(prepared.attachment)
                                if (pending.isActive) pending.resume(prepared.attachment)
                            }
                        } catch (e: Exception) { if (pending.isActive) pending.resumeWithException(e) }
                        finally { runCatching { stream.getAndSet(null)?.close() } }
                    }
                }
                if (epoch != memory.credentialEpoch || version != memory.attachmentGeneration || AiKeyStore(this@AiChatActivity).credentialEpoch() != epoch) {
                    return@launch
                }
                attachmentTrace.event(AiAttachmentStage.READY, bytes = item.size)
                memory.attachments.add(item); referenced = true; memory.attachmentGeneration++
                memory.contextGeneration++; cancelCurveProposal()
                memory.notice = if (item.kind == AiAttachmentKind.DOCUMENT) R.string.ai_ocr_selected else R.string.ai_attachment_local
                persistSession()
            } catch (_: CancellationException) { throw CancellationException() }
            catch (failure: AiAttachmentFailure) {
                attachmentFailed = true
                attachmentTrace.failed(failure.problem, failure)
                memory.notice = when (failure.problem) {
                    AiAttachmentProblem.COUNT -> R.string.ai_attachment_error_count
                    AiAttachmentProblem.STORAGE_QUOTA -> R.string.ai_attachment_error_storage_quota
                    AiAttachmentProblem.TYPE -> R.string.ai_attachment_error_type
                    AiAttachmentProblem.SOURCE_SIZE -> R.string.ai_attachment_error_source_size
                    AiAttachmentProblem.PIXELS -> R.string.ai_attachment_error_pixels
                    AiAttachmentProblem.IMAGE -> R.string.ai_attachment_error_image
                    AiAttachmentProblem.TEXT -> R.string.ai_attachment_error_text
                    AiAttachmentProblem.DOCUMENT -> R.string.ai_attachment_error_document
                    AiAttachmentProblem.SIZE -> R.string.ai_attachment_error_size
                    AiAttachmentProblem.UNAVAILABLE -> R.string.ai_attachment_unavailable
                }
            }
            catch (_: Exception) { attachmentFailed = true; attachmentTrace.failed(AiAttachmentProblem.UNAVAILABLE); memory.notice = R.string.ai_attachment_unavailable }
            finally {
                if (!referenced) created.get()?.let { item -> withContext(NonCancellable + Dispatchers.IO) {
                    AiAttachmentVault(this@AiChatActivity, epoch).remove(item)
                } }
                attachmentBusy = false
                if (!isDestroyed) {
                    render()
                    if (attachmentFailed) scroll?.post { scroll?.fullScroll(View.FOCUS_DOWN) }
                }
            }
        }
    }
    private fun sendOrConfirm() {
        if (busy || attachmentBusy) return
        val text = input.text.toString().ifBlank { if (memory.attachments.isNotEmpty()) getString(R.string.ai_attachment_question) else "" }
        if (!AiChatPolicy.accepts(memory.lines, text)) { memory.notice = if (text.length > AiChatPolicy.INPUT_CHARS || text.isBlank()) R.string.ai_input_limit else R.string.ai_storage_limit; render(); return }
        if (AiKeyStore(this).status() != AiKeyStatus.SAVED) {
            memory.notice = R.string.ai_key_unavailable; render(); return
        }
        if (consented()) { sendAfterConsent(text); return }
        if (consentDialog != null) return
        consentDialog = AlertDialog.Builder(this).setTitle(R.string.ai_consent_title).setMessage(getString(R.string.ai_consent_body, AiChatPolicy.SEARCH_CALLS))
            .setNegativeButton(R.string.ai_cancel, null)
            .setPositiveButton(R.string.ai_agree) { _, _ ->
                // Memory-only assent is sufficient for this send if persistence fails; retry asks again.
                getSharedPreferences("ai_consent", MODE_PRIVATE).edit().putInt(AiConsentPolicy.VERSION_KEY, AiConsentPolicy.CURRENT_VERSION).commit()
                binding.start()
                sendAfterConsent(text)
            }.create().also { dialog ->
                dialog.setOnDismissListener { consentDialog = null }
                dialog.show(); visual.dialog(dialog)
                listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE).forEach { which ->
                    dialog.getButton(which)?.apply { setSingleLine(false); maxLines = Int.MAX_VALUE; ellipsize = null }
                }
            }
    }
    // Both the chat request and separately confirmed OCR require the current disclosure.
    private fun sendAfterConsent(text: String) {
        val document = memory.attachments.firstOrNull { it.kind == AiAttachmentKind.DOCUMENT && memory.ocr.none { r -> r.original.id == it.id && r.status == AiOcrStatus.READY } }
        if (document != null) {
            val attempt = memory.ocr.firstOrNull { it.original.id == document.id }
            if (attempt == null) confirmOcr(document) else { memory.notice = ocrNotice(attempt.status); render() }
            return
        }
        if (memory.attachments.any { it.kind == AiAttachmentKind.DOCUMENT } && AiContextPolicy.projected(AiProtocolHistory.empty(memory.protocol.modelId), text, AiAttachmentConversation.tokens(listOf(AiAttachmentConversation.message(text, sendAttachments())))) > AiContextPolicy.INPUT_TOKENS) {
            memory.notice = R.string.ai_ocr_context_limit; render(); return
        }
        confirmAttachments(text)
    }
    private fun begin(text: String) {
        val attachments = sendAttachments()
        if (busy || attachmentBusy || !AiChatPolicy.accepts(memory.lines, text) || !memory.submission.begin(text)) return
        cancelCurveProposal()
        busy = true
        compactionStatus = CompactionStatus.IDLE
        streamPreview = AiStreamPreview()
        presentation.resetStreamPreview()
        val preview = streamPreview
        val token = ++generation
        val retryKeyRevision = AiKeyStore(this).revision()
        val reasoningEffort = AiReasoningPreference.read(this)
        memory.notice = 0
        memory.diagnostic = null
        memory.failureAttemptId = null; memory.failureNotice = 0; retryProgress = null
        memory.selectionStart = input.selectionStart.coerceAtLeast(0)
        memory.selectionEnd = input.selectionEnd.coerceAtLeast(0)
        input.text.clear()
        render()
        scroll?.post { scroll?.fullScroll(View.FOCUS_DOWN) }
        // Save the pending question as a draft before any request or tool can begin.
        if (!persistSession()) {
            restorePendingDraft(); busy = false; render(); return
        }
        if (!synchronized(sessionLock) { sessionBase?.let(AiSessionCodec::hasTurnHeadroom) == true }) {
            restorePendingDraft(); busy = false; memory.notice = R.string.ai_storage_limit; render(); return
        }
        val initialization = AiDiagnostics()
        val attempt = requestDiagnostics.begin(memory.protocol.modelId)
        diagnosticAttempt = attempt
        request = scope.launch {
            try {
                val data = LocalAiReadOnlyData(this@AiChatActivity) { service?.snapshot }
                val app = application as MobileApplication
                val coordinator = app.customCurvesResult.getOrNull()?.let { store ->
                    curveCoordinator ?: AiCurveWriteCoordinator(store, resolveFactory = { id -> app.curves.find(id) }) { id -> app.curveDeletionGuards.values.none { it(id) } }.also { curveCoordinator = it }
                }
                val sink = object : AiCurveProposalSink {
                    override fun propose(argumentsJson: String, credentialRevision: Long): AiCurveProposalStatus {
                        if (token != generation || AiKeyStore(this@AiChatActivity).revision() != credentialRevision) return AiCurveProposalStatus.UNAVAILABLE
                        val c = coordinator ?: return AiCurveProposalStatus.UNAVAILABLE
                        if (c.pending(curveEpoch(credentialRevision)) != null) return AiCurveProposalStatus.REQUIRES_CANCEL
                        return try { c.propose(argumentsJson, curveEpoch(credentialRevision)); AiCurveProposalStatus.AWAITING_EDITOR }
                        catch (_: Exception) { AiCurveProposalStatus.INVALID_ARGUMENTS }
                    }
                    override fun readFactory(id: String, credentialRevision: Long): CurveLibraryItem? =
                        if (token == generation && AiKeyStore(this@AiChatActivity).revision() == credentialRevision) app.curves.find(id)?.takeIf { it.factoryCurve != null } else null
                    override fun readDocument(id: String, credentialRevision: Long): CustomCurveDocument? =
                        if (token == generation && AiKeyStore(this@AiChatActivity).revision() == credentialRevision) coordinator?.readDocument(id) else null
                }
                val result = AiChatEngine(this@AiChatActivity, data, memory.usage, sink) { id -> app.curves.find(id) }.reply(memory.lines.toList(), text, memory.protocol, attachments, reasoningEffort = reasoningEffort, onProgress = { delta ->
                    preview.append(delta)
                    scheduleStreamPreview(token, retryKeyRevision, preview)
                }, onToolStarted = {
                    preview.toolStarted()
                    scheduleStreamPreview(token, retryKeyRevision, preview)
                }, onRetry = { progress ->
                    scope.launch {
                        if (token == generation && busy && AiKeyStore(this@AiChatActivity).revision() == retryKeyRevision) {
                            retryProgress = progress
                            progress?.takeIf { it.waitMillis > 0 }?.let { requestDiagnostics.retry(attempt, it) }
                            render()
                        }
                    }
                }, onCompactionStarted = {
                    if (token == generation) { compactionStatus = CompactionStatus.RUNNING; render() }
                }) { compacted ->
                    if (token != generation || AiKeyStore(this@AiChatActivity).credentialEpoch() != memory.credentialEpoch) false
                    else {
                        val previous = memory.protocol
                        memory.protocol = compacted
                        if (persistSession()) {
                            cancelCurveProposal()
                            memory.contextGeneration++
                            compactionStatus = CompactionStatus.SAVED
                            render()
                            true
                        } else {
                            memory.protocol = previous
                            false
                        }
                    }
                }
                if (token != generation) return@launch
                memory.lines += AiChatLine(true, text, attachments)
                memory.protocol = result.history
                val proposalCard = coordinator?.pending(curveEpoch())?.let { runCatching { AiCurveCard.proposal(it) }.getOrNull() }
                memory.lines += AiChatLine(false, result.text, curveCards = result.curveCards + listOfNotNull(proposalCard))
                memory.submission.committed()
                rememberInput()
                preview.finish()
                memory.attachments.clear(); memory.attachmentGeneration++
                requestDiagnostics.succeeded(attempt)
            } catch (_: CancellationException) {
                if (token == generation) preview.interrupt()
                requestDiagnostics.cancelled(attempt)
                // Explicit stop already updates the generation and visible state.
            } catch (failure: AiFailure) {
                requestDiagnostics.failed(attempt, failure.problem, failure.diagnostic)
                if (token == generation) {
                    if (failure.problem == AiProblem.REFUSED) preview.clear() else preview.interrupt()
                    memory.diagnostic = failure.diagnostic
                    memory.notice = when (failure.problem) {
                    AiProblem.KEY -> R.string.ai_key_unavailable
                    AiProblem.AUTH -> R.string.ai_error_auth
                    AiProblem.RATE_LIMIT -> R.string.ai_error_rate
                    AiProblem.REFUSED -> R.string.ai_error_refused
                    AiProblem.LIMIT -> R.string.ai_limit
                    AiProblem.INPUT_LIMIT -> R.string.ai_input_limit
                    AiProblem.CONTEXT_LIMIT -> R.string.ai_context_limit
                    AiProblem.RESPONSE_LIMIT -> when (failure.diagnostic?.kind) {
                        AiFailureKind.RESPONSE_SIZE_LIMIT -> R.string.ai_response_memory_limit
                        AiFailureKind.STREAM_WIRE_LIMIT -> R.string.ai_response_wire_limit
                        else -> R.string.ai_response_limit
                    }
                    AiProblem.TOOL_LIMIT -> when (AiErrorPresentation.semantic(failure.problem, failure.diagnostic)) {
                        AiErrorPresentation.Semantic.MODEL_BUDGET -> R.string.ai_model_request_budget
                        AiErrorPresentation.Semantic.SEARCH_BUDGET -> R.string.ai_search_budget
                        AiErrorPresentation.Semantic.TOOL_BUDGET -> R.string.ai_tool_call_budget
                        AiErrorPresentation.Semantic.TOOL_RESULT_BUDGET -> R.string.ai_tool_result_budget
                        AiErrorPresentation.Semantic.AGENT_BUDGET -> R.string.ai_agent_iteration_budget
                        else -> R.string.ai_tool_limit
                    }
                    AiProblem.REQUEST_LIMIT -> R.string.ai_request_limit
                    AiProblem.STORAGE_LIMIT -> R.string.ai_storage_limit
                    AiProblem.MODEL -> R.string.ai_model_incompatible
                    AiProblem.ATTACHMENT -> R.string.ai_attachment_read_failed
                    AiProblem.COMPACT_FAILED -> R.string.ai_compact_failed
                    AiProblem.EMPTY -> R.string.ai_error_empty
                    AiProblem.NETWORK -> networkErrorNotice(failure.diagnostic)
                    AiProblem.PROTOCOL -> R.string.ai_error_protocol
                    }
                    memory.failureAttemptId = attempt.id; memory.failureNotice = memory.notice
                }
            } catch (_: Exception) {
                requestDiagnostics.failed(attempt, AiProblem.NETWORK, initialization.failure(AiFailureKind.SDK))
                if (token == generation) {
                    preview.interrupt()
                    memory.notice = R.string.ai_error_local_processing
                    memory.diagnostic = initialization.failure(AiFailureKind.SDK)
                    memory.failureAttemptId = attempt.id; memory.failureNotice = memory.notice
                }
            } finally {
                if (diagnosticAttempt === attempt) diagnosticAttempt = null
                if (token == generation) {
                    if (compactionStatus == CompactionStatus.RUNNING) compactionStatus = CompactionStatus.FAILED
                    restorePendingDraft()
                    busy = false; request = null; retryProgress = null
                    // Partial/safety/error results may keep display evidence, never a live save action.
                    cancelCurveProposal()
                    persistSession(); render()
                    scroll?.post { scroll?.fullScroll(View.FOCUS_DOWN) }
                }
            }
        }
    }
    private fun scheduleStreamPreview(token: Long, revision: Long, preview: AiStreamPreview) {
        if (!preview.scheduleUpdate()) return
        presentation.root.postDelayed({
            preview.updateDelivered()
            if (token == generation && busy && streamPreview === preview &&
                AiKeyStore(this).revision() == revision) presentation.updateStreamPreview(preview.snapshot())
        }, 120L)
    }

    private fun clearStreamPreview() {
        streamPreview.clear()
        if (::presentation.isInitialized) presentation.resetStreamPreview()
    }
    private fun stop(preservePartial: Boolean = false) {
        // Only the explicit Stop button keeps this attempt's visible text. Lifecycle, key and
        // conversation changes clear it so another session never inherits partial content.
        if (preservePartial && busy) streamPreview.interrupt() else clearStreamPreview()
        retryProgress = null
        compactionStatus = CompactionStatus.IDLE
        cancelCurveProposal()
        if (!busy) return
        diagnosticAttempt?.let(requestDiagnostics::cancelled); diagnosticAttempt = null
        generation++; request?.cancel(); request = null; busy = false
        restorePendingDraft()
        memory.diagnostic = null
        memory.notice = R.string.ai_interrupted
        persistSession()
        if (::input.isInitialized) render()
    }
    private fun curveCardAvailable(card: AiCurveCard): Boolean = runCatching {
        card.matches((application as MobileApplication).curves.find(card.id))
    }.getOrDefault(false)
    // Wired only when the root library receiver is integrated; never falls back to extraction.
    private fun curveLibraryAvailable(card: AiCurveCard): Boolean =
        AiCurveLibraryRoute.resolve(card) { (application as MobileApplication).curves.find(it) } != null
    private fun viewCurveInLibrary(card: AiCurveCard) {
        if (busy || attachmentBusy) return
        val reference = AiCurveLibraryRoute.resolve(card) { (application as MobileApplication).curves.find(it) }
        if (reference == null) {
            AlertDialog.Builder(this).setMessage(if (resources.configuration.locales[0].language == "zh")
                "来源曲线不存在或已变化，请重新获取曲线。" else "The source curve is missing or changed. Request the curve again.")
                .setPositiveButton(android.R.string.ok, null).create().also { it.show(); visual.dialog(it) }
            return
        }
        rememberInput(); if (!persistSession()) return
        cancelCurveProposal()
        startActivityForResult(Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_INITIAL_DESTINATION, TaskDestination.CURVES.name)
            .putExtra(AiCurveLibraryRoute.EXTRA_RETURN_TO_CHAT, true)
            .putExtra(AiCurveLibraryRoute.EXTRA_CARD, reference.encode().toString()), AiCurveLibraryRoute.REQUEST_BROWSE)
    }
    private fun openCurveCard(card: AiCurveCard) {
        if (busy || attachmentBusy) return
        if (!curveCardAvailable(card)) { render(); return }
        // This route only selects and opens slot 7. Never use shortcut/preheat/start helpers here.
        rememberInput(); if (!persistSession()) return
        if (!getSharedPreferences("curves", MODE_PRIVATE).edit().putString("selected", card.id).commit()) return
        cancelCurveProposal()
        startActivity(Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_EXTRACTION, true).putExtra(PresetSlots.EXTRA_SLOT, 7))
    }
    private fun curveCardReviewable(card: AiCurveCard): Boolean = !busy && card.kind != AiCurveCard.Kind.RECOMMENDATION &&
        memory.lines.any { line -> !line.user && !line.text.endsWith(AiContextPolicy.INCOMPLETE) && line.curveCards.any { it === card } }
    private fun reviewCurveCard(card: AiCurveCard) {
        if (!curveCardReviewable(card)) return
        rememberInput(); if (!persistSession()) return
        val app = application as MobileApplication
        val store = app.customCurvesResult.getOrNull() ?: return
        val origin = memory
        val contextVersion = memory.contextGeneration
        val epoch = memory.credentialEpoch
        val credentialRevision = AiKeyStore(this).revision()
        var handoff: String? = null
        try {
            val draft = AiCurveEditorDraft.open(card, store, { id -> app.curves.find(id) }) {
                origin.contextGeneration == contextVersion && origin.credentialEpoch == epoch &&
                    AiKeyStore(app).revision() == credentialRevision
            }
            handoff = AiCurveEditorDrafts.put(draft)
            cancelCurveProposal()
            startActivity(Intent(this, CurveEditorActivity::class.java).putExtra(CurveEditorActivity.AI_DRAFT, handoff))
        } catch (_: Exception) {
            AiCurveEditorDrafts.remove(handoff)
            AlertDialog.Builder(this).setMessage(R.string.ai_curve_editor_expired).setPositiveButton(android.R.string.ok, null)
                .create().also { it.show(); visual.dialog(it) }
        }
    }
    private fun networkErrorNotice(diagnostic: AiDiagnostic?): Int = when (AiErrorPresentation.semantic(AiProblem.NETWORK, diagnostic)) {
        AiErrorPresentation.Semantic.PROTOCOL -> R.string.ai_error_protocol
        AiErrorPresentation.Semantic.ACCESS -> R.string.ai_error_auth
        AiErrorPresentation.Semantic.RATE -> R.string.ai_error_rate
        AiErrorPresentation.Semantic.CONNECTION -> R.string.ai_error_connection
        AiErrorPresentation.Semantic.CONNECT_TIMEOUT -> R.string.ai_error_connect_timeout
        AiErrorPresentation.Semantic.READ_TIMEOUT -> R.string.ai_error_read_timeout
        AiErrorPresentation.Semantic.TIMEOUT -> R.string.ai_error_timeout
        AiErrorPresentation.Semantic.REPLY_TIMEOUT -> R.string.ai_reply_timeout
        AiErrorPresentation.Semantic.SERVICE -> R.string.ai_error_service
        AiErrorPresentation.Semantic.LOCAL_PROCESSING -> R.string.ai_error_local_processing
        else -> R.string.ai_error_unknown
    }
    private fun canEditDuringReply() = compactionStatus == CompactionStatus.RUNNING || compactionStatus == CompactionStatus.SAVED

    private fun AiChatPendingDraft.persistedDraft(): String =
        pending?.let { recoverableDraft(if (::input.isInitialized) input.text.toString() else "") } ?: draft

    private fun render() {
        val statusText = when {
            busy -> retryProgress?.let { progress ->
                if (progress.waitMillis > 0) getString(R.string.ai_retry_wait, progress.index, AiRetryPolicy.MAX_RETRIES, progress.waitMillis / 1000)
                else getString(R.string.ai_retry_connecting, progress.index, AiRetryPolicy.MAX_RETRIES)
            } ?: getString(R.string.ai_waiting)
            memory.notice != 0 -> getString(memory.notice) + if (BuildConfig.MOCK_MODE && memory.failureAttemptId != null && memory.failureNotice == memory.notice) {
                "\n" + AiErrorPresentation.mockDetails(BuildConfig.VERSION_CODE, memory.protocol.modelId, memory.diagnostic, memory.failureAttemptId)
            } else ""
            else -> ""
        }
        val compactionMessage = when (compactionStatus) {
            CompactionStatus.RUNNING -> getString(R.string.ai_compaction_running)
            CompactionStatus.SAVED -> getString(R.string.ai_compaction_saved)
            CompactionStatus.FAILED -> if (memory.notice == R.string.ai_compact_failed) "" else getString(R.string.ai_compact_failed)
            CompactionStatus.IDLE -> ""
        }
        val contextStatus = if (memory.protocol.coveredLines > 0) getString(R.string.ai_compacted, memory.protocol.coveredLines / 2) else ""
        val ocrStatus = memory.ocr.filter { record -> memory.attachments.any { it.id == record.original.id } }.joinToString("\n") {
            it.original.name + ": " + getString(ocrNotice(it.status)) + (it.pages?.let { count -> "\n" + getString(R.string.ai_ocr_reference, count, it.referenceInput ?: 0, it.referenceOutput ?: 0) } ?: "")
        }
        presentation.render(memory.lines, memory.submission.pending ?: memory.draft, busy, listOf(compactionMessage, statusText.takeUnless { compactionStatus == CompactionStatus.RUNNING && retryProgress == null }.orEmpty(), contextStatus, ocrStatus).filter { it.isNotBlank() }.joinToString("\n"), memory.attachments, attachmentBusy, memory.protocol.modelId, memory.credentialEpoch, "${memory.credentialEpoch}:${memory.protocol.modelId}:${memory.contextGeneration}",
            allowDraftWhileBusy = canEditDuringReply(), draftCapacity = memory.submission.remainingDraftCapacity(AiChatPolicy.INPUT_CHARS))
        presentation.updateStreamPreview(streamPreview.snapshot())
    }
    private fun restorePendingDraft() {
        val draft = memory.submission.restore(if (::input.isInitialized) input.text.toString() else "") ?: return
        if (::input.isInitialized) {
            presentation.setDraftCapacity(AiChatPolicy.INPUT_CHARS)
            input.setText(draft)
            input.setSelection(memory.selectionStart.coerceIn(0, draft.length), memory.selectionEnd.coerceIn(0, draft.length))
        }
    }
    private fun rememberInput() {
        if (!::input.isInitialized) return
        if (memory.submission.pending == null) {
            memory.draft = input.text.toString()
            memory.selectionStart = input.selectionStart.coerceAtLeast(0)
            memory.selectionEnd = input.selectionEnd.coerceAtLeast(0)
        }
        memory.focused = input.hasFocus()
        memory.scrollY = scroll?.scrollY ?: 0
    }
    override fun onSaveInstanceState(outState: Bundle) {
        rememberInput()
        outState.putString("ai-picker-epoch", pickerEpoch)
        outState.putLong("ai-picker-generation", pickerGeneration)
        super.onSaveInstanceState(outState)
    }
    override fun onPause() {
        // Cancel before another screen can replace/delete the key. Do not clear here:
        // a configuration recreation also pauses, and its draft belongs to the retained ViewModel.
        rememberInput(); presentation.dismissOptions(); stop(); cancelOcr()
        draftSave?.cancel(); persistSession()
        super.onPause()
    }
    override fun onStop() {
        rememberInput(); presentation.dismissOptions(); stop(); attachmentDialog?.dismiss(); consentDialog?.dismiss(); linkDialog?.dismiss(); usageDialog?.dismiss(); recoveryDialog?.dismiss(); binding.stop()
        super.onStop()
    }
    override fun onDestroy() {
        // A cancelled transport can finish on IO after this Activity has been replaced.
        synchronized(sessionLock) { usageGeneration++ }
        presentation.dismissOptions()
        presentation.closeImagePreviews()
        attachmentDialog?.dismiss()
        linkDialog?.dismiss()
        usageDialog?.dismiss()
        recoveryDialog?.dismiss()
        stopKeyObserver?.invoke(); stopKeyObserver = null
        scope.cancel()
        super.onDestroy()
    }
}
