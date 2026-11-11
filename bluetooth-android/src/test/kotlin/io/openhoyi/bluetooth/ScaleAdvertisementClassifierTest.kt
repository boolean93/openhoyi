package io.openhoyi.bluetooth

import io.openhoyi.session.*
import org.junit.Assert.*
import org.junit.Test

class ScaleAdvertisementClassifierTest {
    @org.junit.Test fun discoveryNameFallbackAndPrecedence() {
        for(name in listOf("Microbalance","difluid","Mb Ti")) {
            assertEquals(name,ScaleAdvertisementClassifier.discoveryName(name,null))
            assertEquals(name,ScaleAdvertisementClassifier.discoveryName(null,name))
            assertEquals(ScaleAdvertisementClassifier.protocolId(name),ScaleAdvertisementClassifier.protocolId(ScaleAdvertisementClassifier.discoveryName(name,null)))
        }
        assertEquals("Mb Ti",ScaleAdvertisementClassifier.discoveryName("difluid","Mb Ti"))
        assertEquals("difluid",ScaleAdvertisementClassifier.discoveryName("unknown","difluid"))
        assertEquals("difluid",ScaleAdvertisementClassifier.discoveryName("  ","difluid"))
        assertEquals("difluid",ScaleAdvertisementClassifier.discoveryName("HOYI","difluid"))
        assertEquals("HOYI",ScaleAdvertisementClassifier.discoveryName("difluid","HOYI"))
        assertEquals("difluid",ScaleAdvertisementClassifier.discoveryName("difluid","unknown"))
        for(name in listOf("BOOKOO","ULTRA","FELICITA","LUNAR","PROCHBT","Mb Ti","Microbalance","difluid","TIMEMORE DOT","TIMEMORE BASIC 3 LINK","timemore scale","HOYI")) {
            assertEquals(name,ScaleAdvertisementClassifier.discoveryName("unknown",name))
            assertEquals(name,ScaleAdvertisementClassifier.discoveryName(name,"unknown"))
        }
        assertNull(ScaleAdvertisementClassifier.protocolId(ScaleAdvertisementClassifier.discoveryName(null,null)))
        assertNull(ScaleAdvertisementClassifier.protocolId(ScaleAdvertisementClassifier.discoveryName("unknown",null)))
    }
    @Test fun freshFelicitaSelectionUsesNewManualTareProfileButOldPersistedProfileStaysReadOnly() {
        assertEquals(FelicitaScaleProtocolAdapter.id,ScaleAdvertisementClassifier.protocolId("FELICITA"))
        assertEquals(FelicitaScaleProtocolAdapter.id,ScaleAdvertisementClassifier.protocolId("felicita arc"))
        assertFalse(FelicitaScaleProtocolAdapter.transportVerified)
        assertTrue(FelicitaReadOnlyScaleProtocolAdapter.readOnlyTransport)
    }
    @Test fun legacyExecutableScanAliasesSelectFixedProfilesWithoutGrantingBookooControlToUltra() {
        for(name in listOf("PROCHBT","ACAIA"))assertEquals(AcaiaLegacyScaleProtocolAdapter.id,ScaleAdvertisementClassifier.protocolId(name))
        for(name in listOf("LUNAR","PEARL","PYXIS","Acaia Lunar"))assertEquals(AcaiaPearlScaleProtocolAdapter.id,ScaleAdvertisementClassifier.protocolId(name))
        for(name in listOf("Microbalance","difluid"))assertEquals(DiFluidScaleProtocolAdapter.id,ScaleAdvertisementClassifier.protocolId(name))
        assertEquals(DiFluidTiScaleProtocolAdapter.id,ScaleAdvertisementClassifier.protocolId("Mb Ti"))
        assertEquals(BookooUltraLegacyScaleProtocolAdapter.id,ScaleAdvertisementClassifier.protocolId("ULTRA"))
        assertFalse(BookooUltraLegacyScaleProtocolAdapter.capabilities.validatedWeightControl)
    }
    @Test fun timemoreNamesChooseOnlyEvidencedModelsWithoutGuessingFamilyOrNonBleProducts() {
        for(name in listOf("TIMEMORE DOT","DOT","TES017","Timemore-DOT-1234"))
            assertEquals(TimemoreDotScaleProtocolAdapter.id,ScaleAdvertisementClassifier.protocolId(name))
        for(name in listOf("TIMEMORE Basic3 Link","Basic 3 Link","TES016"))
            assertEquals(TimemoreBasicLinkScaleProtocolAdapter.id,ScaleAdvertisementClassifier.protocolId(name))
        assertEquals(TimemoreLegacyReadOnlyScaleProtocolAdapter.id,ScaleAdvertisementClassifier.protocolId("timemore scale"))
        for(name in listOf("TIMEMORE","TIMEMORE Basic2","TIMEMORE Nano","TIMEMORE Mini","TIMEMORE Basic4","Black Mirror 2","FFF0"))
            assertNull(ScaleAdvertisementClassifier.protocolId(name))
    }
    @Test fun existingBookooNamesKeepTheirProtocolAndOtherFamiliesAreNotGuessed() {
        assertEquals(BookooScaleProtocolAdapter.id,ScaleAdvertisementClassifier.protocolId("BOOKOO MINI"))
        assertEquals(BookooScaleProtocolAdapter.id,ScaleAdvertisementClassifier.protocolId("bookoo"))
        for(name in listOf("HOYI","","unknown"))assertNull(ScaleAdvertisementClassifier.protocolId(name))
    }
}
