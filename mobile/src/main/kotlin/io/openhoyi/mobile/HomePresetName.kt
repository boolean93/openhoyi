package io.openhoyi.mobile

/** Presentation only: never changes the slot identity or recipe binding. */
internal object HomePresetName {
    fun resolve(customName: String?, curveName: String?, unconfigured: String): String =
        customName?.trim()?.takeIf { it.isNotEmpty() }
            ?: curveName?.trim()?.takeIf { it.isNotEmpty() }
            ?: unconfigured
}
