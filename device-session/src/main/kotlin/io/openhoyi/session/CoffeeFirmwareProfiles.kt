package io.openhoyi.session

/**
 * Firmware profiles whose control frames have protocol evidence sufficient to enable control.
 * Membership never implies hardware acceptance: README/plans record what was verified on a real machine.
 */
object CoffeeFirmwareProfiles {
    /** 1.1.3: authenticated real-machine sessions, telemetry, extraction and settings writes verified. */
    const val FW_1_1_3 = 0x113
    /**
     * 1.1.4: protocol evidence supports enabling control (docs/evidence/2026-11-09-firmware-114-parity.md);
     * real-machine start/stop/settings acceptance is still pending, tracked in docs/firmware-status.md.
     * Never describe 1.1.4 as verified before that hardware acceptance is recorded.
     */
    const val FW_1_1_4 = 0x114

    /** 1.1.5: static parity evidence, not hardware acceptance; see docs/evidence/2026-11-11-firmware-115-parity.md. */
    const val FW_1_1_5 = 0x115

    private val controlPermitted = setOf(FW_1_1_3, FW_1_1_4, FW_1_1_5)

    fun permitsControl(firmwareInteger: Int): Boolean = firmwareInteger in controlPermitted
}
