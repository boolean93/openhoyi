package io.openhoyi.mobile

/** Narrow proposal-only capability. Never carries a confirmation token or write authority. */
internal fun interface AiCurveProposalSink {
    fun propose(argumentsJson: String, credentialRevision: Long): AiCurveProposalStatus
    fun readDocument(id: String, credentialRevision: Long): CustomCurveDocument? = null
    fun readFactory(id: String, credentialRevision: Long): CurveLibraryItem? = null
}
internal enum class AiCurveProposalStatus(val wire: String) {
    AWAITING_EDITOR("awaiting_editor"), INVALID_ARGUMENTS("invalid_arguments"),
    REQUIRES_CANCEL("requires_cancel"), UNAVAILABLE("unavailable");
    fun json() = "{\"status\":\"$wire\",\"saved\":false}"
}
