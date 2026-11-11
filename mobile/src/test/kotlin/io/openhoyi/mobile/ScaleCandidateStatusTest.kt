package io.openhoyi.mobile

import io.openhoyi.session.BookooScaleProtocolAdapter
import io.openhoyi.session.DiFluidScaleProtocolAdapter
import io.openhoyi.session.FelicitaScaleProtocolAdapter
import io.openhoyi.session.FelicitaReadOnlyScaleProtocolAdapter
import org.junit.Assert.*
import org.junit.Test

class ScaleCandidateStatusTest {
    @Test fun labelsDocumentedTargetAndReadOnlyWithoutGrantingCapabilities() {
        assertEquals(R.string.scale_documented_target_live, ScaleCandidateStatus.resource(FelicitaScaleProtocolAdapter.id))
        assertEquals(R.string.scale_documented_target_live, ScaleCandidateStatus.resource(DiFluidScaleProtocolAdapter.id))
        assertEquals(R.string.scale_read_only_live, ScaleCandidateStatus.resource(FelicitaReadOnlyScaleProtocolAdapter.id))
        assertNull(ScaleCandidateStatus.resource(BookooScaleProtocolAdapter.id))
        assertNull(ScaleCandidateStatus.resource(null))
    }
}
