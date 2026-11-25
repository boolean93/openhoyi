package io.openhoyi.mobile

/** Display projection only: no permission, recovery storage or device operation is performed. */
@ConsistentCopyVisibility
data class SafetyNotificationPresentation private constructor(
    val warningResource: Int?,
    val destination: Destination,
) {
    enum class Destination { HOME, EXTRACTION }
    fun warning(resolve: (Int, Array<out Any?>) -> String): String? =
        warningResource?.let { resolve(it, emptyArray()) }

    companion object {
        internal fun machineAttention(resource: Int?, writeProgress: Boolean, splitRecovery: Boolean): Int? =
            when (resource) {
                R.string.machine_recovery_setting,
                R.string.machine_recovery_cup_reset,
                R.string.machine_recovery_sleep_schedule,
                R.string.machine_recovery_sleep_now -> resource.takeUnless { writeProgress }
                R.string.service_v2_split_pending -> resource.takeIf { splitRecovery }
                else -> resource
            }

        internal fun leverAttention(store: MachineLeverRecipeStore, inFlight: String?): Int? {
            if (!store.blocked) return null
            val isCurrentPending = inFlight != null && store.blockingAttempt?.token == inFlight && store.pending(inFlight)
            return if (isCurrentPending) null else R.string.lever_unresolved
        }

        fun from(shotWarning: Int?, manualWarning: Int?, machineWarning: Int?) =
            SafetyNotificationPresentation(shotWarning ?: manualWarning ?: machineWarning,
                if (machineWarning != null && manualWarning == null && shotWarning == null)
                    Destination.HOME else Destination.EXTRACTION)
    }
}
