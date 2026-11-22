package io.openhoyi.mobile

import io.openhoyi.session.SleepScheduleWriteTracker

/** Home heating normally shares the exclusive durable machine-write slot. This projection neither grants
 * transport permission nor stores a user intent to replay. */
internal enum class BoilerHeatingAdmission(val reason: Int? = null) {
    AVAILABLE,
    UNAVAILABLE,
    SCHEDULE_WRITING(R.string.home_boiler_schedule_writing),
    SCHEDULE_READBACK(R.string.home_boiler_schedule_readback);

    val canSubmit: Boolean get() = this == AVAILABLE

    companion object {
        fun evaluate(owner: MobileService?, snapshot: MobileSnapshot = owner?.snapshot ?: MobileSnapshot(),
            now: Long = android.os.SystemClock.elapsedRealtime()): BoilerHeatingAdmission =
            resolve(MachineSettingsEditGate.allowed(owner, snapshot, now) || owner?.allowsSplitBoiler() == true,
                owner?.scheduleWriteState)

        // These phases may be more specific than the shared durable recovery warning, but
        // no display classification may override the original admission predicate.
        internal fun resolve(editable: Boolean, schedule: SleepScheduleWriteTracker.State?): BoilerHeatingAdmission =
            if (editable) AVAILABLE else when (schedule) {
                SleepScheduleWriteTracker.State.WRITING -> SCHEDULE_WRITING
                SleepScheduleWriteTracker.State.WAITING_READBACK -> SCHEDULE_READBACK
                else -> UNAVAILABLE
            }
    }
}
