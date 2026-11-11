package io.openhoyi.bluetooth

import io.openhoyi.session.*
import java.util.Locale

/** Advertisement names are discovery hints, never proof of protocol or firmware compatibility. */
object ScaleAdvertisementClassifier {
    /** Preserve supported live advertisements; cached Android names are discovery fallbacks. */
    fun discoveryName(deviceName:String?,localName:String?):String {
        fun supported(name:String?)=name!=null && (name.contains("HOYI",true) || protocolId(name)!=null)
        return when {
            supported(localName)->localName!!
            supported(deviceName)->deviceName!!
            !localName.isNullOrBlank()->localName
            else->deviceName.orEmpty()
        }
    }

    fun protocolId(name:String):String? {
        val normalized=name.lowercase(Locale.ROOT).replace(Regex("[-_\\s]+")," ").trim()
        return when {
            name.contains("BOOKOO",ignoreCase=true)->BookooScaleProtocolAdapter.id
            name.contains("ULTRA",ignoreCase=true)->BookooUltraLegacyScaleProtocolAdapter.id
            name.contains("FELICITA",ignoreCase=true)->FelicitaScaleProtocolAdapter.id
            listOf("PEARL","LUNAR","PYXIS").any {name.contains(it,ignoreCase=true)}->AcaiaPearlScaleProtocolAdapter.id
            listOf("PROCHBT","ACAIA").any {name.contains(it,ignoreCase=true)}->AcaiaLegacyScaleProtocolAdapter.id
            name.contains("Mb Ti",ignoreCase=true)->DiFluidTiScaleProtocolAdapter.id
            name.contains("Microbalance",ignoreCase=true) || name.contains("difluid",ignoreCase=true)->DiFluidScaleProtocolAdapter.id
            // Names are hints only; exact endpoints and gram readback remain mandatory.
            Regex("(?:timemore )?(?:dot|tes017)(?: [a-z0-9]+)?").matches(normalized)->TimemoreDotScaleProtocolAdapter.id
            Regex("(?:timemore )?(?:basic ?3 link|tes016)(?: [a-z0-9]+)?").matches(normalized)->TimemoreBasicLinkScaleProtocolAdapter.id
            normalized=="timemore scale"->TimemoreLegacyReadOnlyScaleProtocolAdapter.id
            else->null
        }
    }
}
