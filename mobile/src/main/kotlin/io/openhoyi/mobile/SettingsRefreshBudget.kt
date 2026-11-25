package io.openhoyi.mobile

/** One bounded burst per settings observation; rendering never floods the BLE queue. */
internal class SettingsRefreshBudget {
    private var observation: Long? = null
    private var connection: Long? = null
    private var initialized = false
    private var attempts = 0
    private var nextAt = 0L
    fun available(generation:Long,settingsAt:Long?,now:Long,explicit:Boolean=false):Boolean =
        !initialized || connection!=generation || observation!=settingsAt ||
            (now>=nextAt && (explicit || attempts<3))

    fun claim(generation: Long, settingsAt: Long?, now: Long, explicit: Boolean = false): Boolean {
        if (!initialized || connection != generation || observation != settingsAt) {
            initialized = true; connection = generation; observation = settingsAt; attempts = 0; nextAt = 0
        }
        if(!available(generation,settingsAt,now,explicit))return false
        attempts++; nextAt = now + 30_000
        return true
    }
}
