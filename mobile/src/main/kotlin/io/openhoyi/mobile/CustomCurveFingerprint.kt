package io.openhoyi.mobile

internal object CustomCurveFingerprint {
    /** Only recipe parameters, not the editable display name or live scale connection. */
    fun of(document: CustomCurveDocument): String {
        document.validate()
        val normalized = buildString {
            append("custom-curve-parameters-v1|")
            append(document.temperatureC).append('|')
            append(document.targetHundredthsGram).append('|')
            append(document.controlMode.name).append('|')
            append(document.stages.size).append('|')
            document.stages.forEach { append(it.target).append(',').append(it.waterTenthsMl).append(';') }
            // Keep historical schema-1 fingerprints stable until an explicit ceiling is saved.
            document.totalWaterLimitMl?.let { append("|totalWaterLimitMl=").append(it) }
        }
        return java.security.MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
