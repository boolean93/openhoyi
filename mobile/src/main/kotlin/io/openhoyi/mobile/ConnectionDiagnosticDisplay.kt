package io.openhoyi.mobile

/** Diagnostic traffic is not automatically a user-visible connection failure. */
internal object ConnectionDiagnosticDisplay {
    fun isInformational(detail: String): Boolean = detail in setOf(
        "scale decode: Unknown",
        "coffee decode: Unknown",
        "scale frame boundary resynchronized",
        "coffee.initial_sync.settings=received",
        "coffee.initial_sync.refresh=dispatched",
    )
}
