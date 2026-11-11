package io.openhoyi.mobile

import io.openhoyi.session.ScaleProtocolAdapter
import io.openhoyi.session.ScaleSelectionPolicy

/** Informational label only; it never grants a control permit. */
internal object ScaleCandidateStatus {
    fun resource(protocolId: String?): Int? {
        val adapter: ScaleProtocolAdapter = protocolId?.let(ScaleSelectionPolicy::adapter) ?: return null
        return when {
            adapter.readOnlyTransport -> R.string.scale_read_only_live
            adapter.documentedTransport && adapter.capabilities.supportsTargetWeight -> R.string.scale_documented_target_live
            adapter.documentedTransport -> R.string.scale_documented_live
            else -> null
        }
    }
}
