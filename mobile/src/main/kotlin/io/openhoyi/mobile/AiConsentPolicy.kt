package io.openhoyi.mobile

/** Disclosure versions are independent of credentials and encrypted conversation storage. */
internal object AiConsentPolicy {
    const val VERSION_KEY = "deepseek_consent_version"
    // v2 discloses four searches per reply; the legacy boolean disclosed only two.
    const val CURRENT_VERSION = 2
    fun accepts(version: Int) = version == CURRENT_VERSION
}
