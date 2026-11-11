package io.openhoyi.mobile

import io.openhoyi.protocol.ByteFrame
import io.openhoyi.protocol.ScaleEvidence
import io.openhoyi.protocol.ScaleObservation
import io.openhoyi.protocol.ScaleObservationDecoder

/** Control-test fixture only. Real adapter provenance is checked in JVM protocol pipeline tests. */
internal fun scaleTestObservation(at: Long, weight: Int = 0): ScaleObservation =
    ScaleObservation(weight, at, ScaleObservationDecoder.bookooCapabilities,
        ScaleEvidence.VERIFIED_TRANSPORT, raw = ByteFrame(byteArrayOf()))
