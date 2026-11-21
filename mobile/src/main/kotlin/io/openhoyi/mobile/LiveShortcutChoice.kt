package io.openhoyi.mobile

/** Snapshot of the actual row tapped, not a mutable slot number to resolve later. */
internal data class LiveShortcutChoice(
    val slot: Int,
    val binding: String?,
    val curveId: String,
    val item: CurveLibraryItem?,
    val route: PresetSlots.Route.Ready?,
) {
    companion object {
        val slots = PresetSlots.userFacing

        /** Entry gate only; the shot's own start/tare transitions are permitted after claiming. */
        fun operationBlock(busyReason: String, automatic: Boolean, manual: Boolean, scalePreflight: Boolean,
                           preparing: Boolean, startAlreadyIssued: Boolean, tareBlock: String?,
                           recoveryBlock: String?): String? =
            if (automatic || manual || scalePreflight || preparing || startAlreadyIssued) busyReason
            else tareBlock ?: recoveryBlock
    }
}

/** A displayed chooser belongs to one service instance and one coffee session, never its successor. */
internal class LiveShortcutSession<T : Any>(private val owner: T, private val device: Any,
                                            private val serviceRevision: Long, private val presetRevision: Long,
                                            private val libraryRevision: Long?) {
    fun matches(currentOwner: T?, currentDevice: Any?, currentServiceRevision: Long, currentPresetRevision: Long,
                currentLibraryRevision: Long?): Boolean =
        currentOwner === owner && currentDevice == device && currentServiceRevision == serviceRevision &&
            currentPresetRevision == presetRevision &&
            currentLibraryRevision == libraryRevision
}
