package io.openhoyi.mobile

/** Pure native evidence for browsing only. No names, model links, selection writes or machine action. */
internal object AiCurveLibraryRoute {
    const val REQUEST_BROWSE = 8420
    const val EXTRA_EXIT_INTENT = "io.openhoyi.mobile.ai_curve_exit_intent"
    const val EXTRA_EXIT_DESTINATION = "io.openhoyi.mobile.ai_curve_exit_destination"
    const val EXTRA_RETURN_TO_CHAT = "io.openhoyi.mobile.ai_curve_return_to_chat"
    const val EXTRA_CARD = "io.openhoyi.mobile.ai_curve_library_reference"
    fun exitDestination(raw: String?, resultOk: Boolean): TaskDestination? =
        if (!resultOk) null else raw?.let { runCatching { TaskDestination.valueOf(it) }.getOrNull() }
            ?.takeUnless { it == TaskDestination.CURVES }
    /** A proposal points to its unchanged stored origin, never to the unsaved draft. */
    fun reference(card: AiCurveCard): AiCurveCard? = when (card.kind) {
        AiCurveCard.Kind.RECOMMENDATION -> card
        AiCurveCard.Kind.COPY_FACTORY -> card.factorySource
        AiCurveCard.Kind.REPLACE -> card.before?.let { before ->
            AiCurveCard.recommendation(CurveLibraryItem(before.id, before.name, "", "", null, customDocument = before))
        }
        AiCurveCard.Kind.CREATE -> null
    }
    fun resolve(card: AiCurveCard, find: (String) -> CurveLibraryItem?): AiCurveCard? = runCatching {
        reference(card)?.takeIf { it.matches(find(it.id)) }
    }.getOrNull()
    /** Receiver independently validates both the bounded evidence and the current library revision. */
    fun receive(encoded: String?, find: (String) -> CurveLibraryItem?): CurveLibraryItem? = runCatching {
        require(encoded != null && encoded.toByteArray(Charsets.UTF_8).size <= AiCurveCard.MAX_ENCODED_BYTES)
        val reference = AiCurveCard.decode(org.json.JSONObject(encoded))
        require(reference.kind == AiCurveCard.Kind.RECOMMENDATION)
        find(reference.id)?.takeIf { reference.matches(it) }
    }.getOrNull()
}
