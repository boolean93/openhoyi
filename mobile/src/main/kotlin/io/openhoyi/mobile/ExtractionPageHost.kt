package io.openhoyi.mobile

/** The same ExtractionFragment can be owned by the shell or a passive task-stack host. */
internal interface ExtractionPageHost {
    val observeOnly: Boolean
    val allowAutomaticPermissionPrompt: Boolean get() = !observeOnly
    fun visibleService(candidate: MobileService?): MobileService? = candidate
    fun observeService(observer: (MobileService?) -> Unit)
    fun removeServiceObserver(observer: (MobileService?) -> Unit)
    fun select(destination: TaskDestination) {}
    fun revokeShortcutStart() {}
    fun liveShortcutChoices(): List<LiveShortcutChoice> = emptyList()
    fun liveShortcutSession(fragment: ExtractionFragment): LiveShortcutSession<MobileService>? = null
    fun liveShortcutBlock(fragment: ExtractionFragment, session: LiveShortcutSession<MobileService>? = null): String? = null
    fun openLiveShortcut(fragment: ExtractionFragment, session: LiveShortcutSession<MobileService>, choice: LiveShortcutChoice) {}
}
