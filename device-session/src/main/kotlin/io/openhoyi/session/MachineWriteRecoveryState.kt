package io.openhoyi.session

/** Durable intent written before a machine control whose outcome may outlive the Service. */
class MachineWriteRecoveryState(private val storage: Storage) {
    enum class Kind { CUP_RESET, SETTING, SLEEP_SCHEDULE, SLEEP_NOW, BREW_WAIT, UNKNOWN }
    data class Record(val kind: Kind?, val address: String?) {
        val pending: Boolean get() = kind != null
    }
    interface Storage {
        fun read(): Record
        /** Synchronous failure must prevent the machine write. */
        fun write(record: Record): Boolean
    }

    private val addressPattern = Regex("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}")
    private fun normalize(address: String?): String? = address?.uppercase()?.takeIf(addressPattern::matches)
    private var record = runCatching(storage::read).getOrDefault(Record(Kind.UNKNOWN,null)).let {
        when {
            !it.pending -> Record(null,null)
            normalize(it.address)==null -> Record(Kind.UNKNOWN,null)
            else -> Record(it.kind,normalize(it.address))
        }
    }
    val pending: Boolean get() = record.pending
    val kind: Kind? get() = record.kind
    val address: String? get() = record.address
    /** Corrupt legacy identity still allows a read-only connection; control remains blocked. */
    fun matchesDevice(candidate: String?): Boolean = !pending || address==null ||
        normalize(candidate)==address

    data class CupResetEvidence(val address: String?, val settingsCount: Int?, val settingsSerial: Long,
        val idleCount: Int?, val idleSerial: Long, val idleAtMs: Long?, val nowMs: Long,
        val writeActive: Boolean)
    fun canClearCupReset(evidence: CupResetEvidence, afterSettingsSerial: Long, afterIdleSerial: Long): Boolean =
        kind==Kind.CUP_RESET && matchesDevice(evidence.address) && !evidence.writeActive &&
        evidence.settingsSerial>afterSettingsSerial && evidence.idleSerial>afterIdleSerial &&
        evidence.settingsCount!=null && evidence.settingsCount==evidence.idleCount &&
        evidence.idleAtMs?.let { it<=evidence.nowMs && evidence.nowMs-it<=1500 }==true

    data class SettingEvidence(val address: String?, val settingsPresent: Boolean, val settingsSerial: Long,
        val idleAwake: Boolean, val idleAtMs: Long?, val nowMs: Long, val writeActive: Boolean)
    fun canClearSetting(evidence: SettingEvidence, afterSettingsSerial: Long): Boolean =
        kind==Kind.SETTING && matchesDevice(evidence.address) && !evidence.writeActive &&
        evidence.settingsPresent && evidence.settingsSerial>afterSettingsSerial && evidence.idleAwake &&
        evidence.idleAtMs?.let { it<=evidence.nowMs && evidence.nowMs-it<=1500 }==true

    data class ScheduleEvidence(val address: String?, val completePlan: Boolean,
        val firstSerial: Long, val secondSerial: Long, val idleAwake: Boolean,
        val idleAtMs: Long?, val nowMs: Long, val writeActive: Boolean)
    fun canClearSchedule(evidence: ScheduleEvidence, afterFirstSerial: Long, afterSecondSerial: Long): Boolean =
        kind==Kind.SLEEP_SCHEDULE && matchesDevice(evidence.address) && !evidence.writeActive &&
        evidence.completePlan && evidence.firstSerial>afterFirstSerial &&
        evidence.secondSerial>afterSecondSerial && evidence.idleAwake &&
        evidence.idleAtMs?.let { it<=evidence.nowMs && evidence.nowMs-it<=1500 }==true

    data class SleepEvidence(val address: String?, val sleepStateRaw: Int?, val sleepSerial: Long,
        val idleAtMs: Long?, val nowMs: Long, val writeActive: Boolean)
    fun canClearSleep(evidence: SleepEvidence, afterSleepSerial: Long): Boolean =
        kind==Kind.SLEEP_NOW && matchesDevice(evidence.address) && !evidence.writeActive &&
        evidence.sleepSerial>afterSleepSerial &&
        (evidence.sleepStateRaw==0 || evidence.sleepStateRaw==1) &&
        evidence.idleAtMs?.let { it<=evidence.nowMs && evidence.nowMs-it<=1500 }==true

    /** No independent preheat-cancel readback exists: this only enables explicit human acknowledgement. */
    data class BrewWaitEvidence(val address: String?, val idleAwake: Boolean, val idleSerial: Long,
        val idleAtMs: Long?, val nowMs: Long, val writeActive: Boolean)
    fun canClearBrewWait(evidence: BrewWaitEvidence, afterIdleSerial: Long): Boolean =
        kind == Kind.BREW_WAIT && matchesDevice(evidence.address) && !evidence.writeActive &&
        evidence.idleAwake && evidence.idleSerial > afterIdleSerial &&
        evidence.idleAtMs?.let { it <= evidence.nowMs && evidence.nowMs - it <= 1500 } == true

    fun arm(kind: Kind,address: String?): Boolean {
        if (kind==Kind.UNKNOWN || pending) return false
        val normalized=normalize(address) ?: return false
        val next=Record(kind,normalized)
        if (!runCatching { storage.write(next) }.getOrDefault(false)) return false
        record=next
        return true
    }
    fun clear(): Boolean {
        if (!pending) return true
        val next=Record(null,null)
        if (!runCatching { storage.write(next) }.getOrDefault(false)) return false
        record=next
        return true
    }
}
