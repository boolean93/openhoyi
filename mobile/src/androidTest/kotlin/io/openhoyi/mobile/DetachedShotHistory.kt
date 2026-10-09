package io.openhoyi.mobile

/** Private history owned by one synthetic service fixture, never the running Mock app's journal. */
internal fun detachedShotHistory(): ShotHistory = ShotHistory(object : ShotHistory.Storage {
    private var value = ""
    override fun read() = value
    override fun write(value: String) { this.value = value }
})
