package io.openhoyi.mobile

import io.openhoyi.protocol.ByteFrame
import io.openhoyi.protocol.Settings
import io.openhoyi.session.OperationResult
import org.junit.Assert.*
import org.junit.Test

class StudioStartGateTest {
    private val profile = CurveCatalog.profiles[1]
    private val settings = Settings(1, 1, 3, 0x04, 92, 0, 125, 15, 70, 0, 0, ByteFrame(byteArrayOf()))

    @Test fun studioRequiresCurrentTargetTemperatureAndMatchingPreparation() {
        val preparation = BrewPreparation()
        assertEquals("尚未收到机器运行模式", StudioStartGate.block(null, 9200, profile, preparation))
        assertEquals("工作室模式温度未达到曲线目标，请先预热",
            StudioStartGate.block(settings, 9099, profile, preparation))
        assertNull(StudioStartGate.block(settings, 9100, profile, preparation))
        val token = requireNotNull(preparation.begin(profile.id, profile.temperatureC))
        preparation.written(token, OperationResult.Success(), 1)
        assertEquals("预热尚未就绪或曲线已变化，请先取消预热",
            StudioStartGate.block(settings, 9200, profile, preparation))
        preparation.observe(2, 9200)
        assertNull(StudioStartGate.block(settings, 9200, profile, preparation))
        assertEquals("运行模式已变化，请先取消预热",
            StudioStartGate.block(settings.copy(flags = 0), 9200, profile, preparation))
        assertEquals("预热尚未就绪或曲线已变化，请先取消预热",
            StudioStartGate.block(settings, 9200, CurveCatalog.profiles[0], preparation))
    }
}
