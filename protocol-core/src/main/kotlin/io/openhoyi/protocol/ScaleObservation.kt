package io.openhoyi.protocol

/** Capabilities describe verified evidence, not a brand-name promise. Device timers and battery
 * stay unavailable until a captured format and transport path actually support them. */
data class ScaleCapabilities(
    val weight:Boolean = true,
    val tare:Boolean = false,
    val deviceFlow:Boolean = false,
    val battery:Boolean = false,
    val deviceTimer:Boolean = false,
    val validatedWeightControl:Boolean = false,
)
enum class ScaleEvidence { VERIFIED_TRANSPORT, OFFLINE_CANDIDATE }

data class ScaleObservation(
    val hundredthsGram:Int,
    val receivedAtMs:Long,
    val capabilities:ScaleCapabilities,
    val evidence:ScaleEvidence,
    val deviceFlowHundredths:Int? = null,
    val batteryPercent:Int? = null,
    val raw:ByteFrame,
) {
    val eligibleForControl:Boolean get() = evidence==ScaleEvidence.VERIFIED_TRANSPORT &&
        capabilities.weight && capabilities.validatedWeightControl
}

object ScaleObservationDecoder {
    val bookooCapabilities=ScaleCapabilities(tare=true,deviceFlow=true,validatedWeightControl=true)
    fun bookoo(bytes:ByteArray,receivedAtMs:Long):DecodeResult<ScaleObservation> =
        when(val decoded=BookooCodec.decode(bytes)) {
            is DecodeResult.Valid -> DecodeResult.Valid(ScaleObservation(decoded.value.weightHundredthsGram,
                receivedAtMs,bookooCapabilities,ScaleEvidence.VERIFIED_TRANSPORT,
                deviceFlowHundredths=decoded.value.deviceFlowHundredths,raw=decoded.value.raw))
            is DecodeResult.Invalid -> decoded
            is DecodeResult.Unknown -> decoded
        }
    /** Offline evidence only; deliberately cannot produce extraction-eligible observations. */
    fun offline(family:LegacyScaleFamily,bytes:ByteArray,receivedAtMs:Long):DecodeResult<ScaleObservation> =
        when(val decoded=LegacyScaleCandidateCodec.decode(family,bytes)) {
            is DecodeResult.Valid -> {
                val hundredths=decoded.value.weightGrams*100.0
                if(!hundredths.isFinite() || hundredths<Int.MIN_VALUE || hundredths>Int.MAX_VALUE)
                    DecodeResult.Invalid("Candidate weight out of range",decoded.value.raw)
                else DecodeResult.Valid(ScaleObservation(kotlin.math.round(hundredths).toInt(),receivedAtMs,
                    ScaleCapabilities(),ScaleEvidence.OFFLINE_CANDIDATE,raw=decoded.value.raw))
            }
            is DecodeResult.Invalid -> decoded
            is DecodeResult.Unknown -> decoded
        }
}
