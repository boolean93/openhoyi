package io.openhoyi.mobile

/** Display projection only: no permission, recovery storage or device operation is performed. */
data class SafetyNotificationPresentation private constructor(
    val warningResource: Int?,
    val destination: Destination,
) {
    enum class Destination { HOME, EXTRACTION }
    fun warning(resolve: (Int, Array<out Any?>) -> String): String? =
        warningResource?.let { resolve(it, emptyArray()) }

    companion object {
        fun from(shotWarning: Int?, manualWarning: Int?, machineWarning: Int?) =
            SafetyNotificationPresentation(shotWarning ?: manualWarning ?: machineWarning,
                if (machineWarning != null && manualWarning == null && shotWarning == null)
                    Destination.HOME else Destination.EXTRACTION)
    }
}
