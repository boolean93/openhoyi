package io.openhoyi.mobile

import java.util.UUID

/** A fresh native editor transaction. Opening it never writes; only an explicit editor Save does. */
internal class AiCurveEditorDraft private constructor(
    val card: AiCurveCard,
    private val store: CustomCurveStore,
    private val revision: Long,
    private val resolveFactory: (String) -> CurveLibraryItem?,
    private val originCurrent: () -> Boolean,
) {
    val seed: CustomCurveDocument get() = requireNotNull(card.document)
    private var resolved = false
    @Synchronized fun available(): Boolean = !resolved && runCatching {
        check(originCurrent())
        val current = store.aiWriteSnapshot(if (card.kind == AiCurveCard.Kind.REPLACE) card.id else null)
        check(current.first == revision)
        check(if (card.kind == AiCurveCard.Kind.REPLACE) current.second == card.before else store.find(card.id) == null)
        card.factorySource?.let { check(it.matches(resolveFactory(it.id))) }
        true
    }.getOrDefault(false)
    /** One attempt. An uncertain disk result is never automatically retried. */
    @Synchronized fun saveFromEditor(document: CustomCurveDocument, actualStore: CustomCurveStore,
        permitted: (String) -> Boolean = { true }): CustomCurveDocument {
        check(actualStore === store && available()) { "Editor draft expired; reopen the card" }
        resolved = true
        require(document.id == seed.id && document.isEditable && AiCurveDetail.validId(document.id))
        store.saveAiConfirmed(document, if (card.kind == AiCurveCard.Kind.REPLACE) card.before else null, revision) {
            originCurrent() && (card.kind != AiCurveCard.Kind.REPLACE || permitted(document.id)) &&
                (card.factorySource?.let { it.matches(resolveFactory(it.id)) } ?: true)
        }
        return document
    }
    @Synchronized fun cancel() { resolved = true }
    companion object {
        fun open(card: AiCurveCard, store: CustomCurveStore,
            resolveFactory: (String) -> CurveLibraryItem? = { null }, originCurrent: () -> Boolean = { true }): AiCurveEditorDraft {
            require(card.kind != AiCurveCard.Kind.RECOMMENDATION && card.document?.isEditable == true)
            val baseline = store.aiWriteSnapshot(if (card.kind == AiCurveCard.Kind.REPLACE) card.id else null)
            require(if (card.kind == AiCurveCard.Kind.REPLACE) baseline.second == card.before else store.find(card.id) == null)
            card.factorySource?.let { require(it.matches(resolveFactory(it.id))) }
            return AiCurveEditorDraft(card, store, baseline.first, resolveFactory, originCurrent).also { check(it.available()) }
        }
    }
}

/** Process-local typed data. No JSON/commands in Intent; configuration recreation can reuse the same entry. */
internal object AiCurveEditorDrafts {
    private val entries = linkedMapOf<String, AiCurveEditorDraft>()
    @Synchronized fun put(draft: AiCurveEditorDraft): String {
        check(entries.size < 8) { "Close an existing draft first" }
        return UUID.randomUUID().toString().also { entries[it] = draft }
    }
    @Synchronized fun get(token: String?): AiCurveEditorDraft? = token?.takeIf { it.length == 36 }?.let(entries::get)
    @Synchronized fun remove(token: String?) { entries.remove(token)?.cancel() }
}
