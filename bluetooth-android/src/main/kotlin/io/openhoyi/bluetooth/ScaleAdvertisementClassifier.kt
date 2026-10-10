package io.openhoyi.bluetooth

import io.openhoyi.session.BookooScaleProtocolAdapter
import io.openhoyi.session.FelicitaReadOnlyScaleProtocolAdapter

/** Advertisement names are discovery hints, never proof of protocol or firmware compatibility. */
object ScaleAdvertisementClassifier {
    fun protocolId(name:String):String? =
        when {
            name.contains("BOOKOO",ignoreCase=true)->BookooScaleProtocolAdapter.id
            name.contains("FELICITA",ignoreCase=true)->FelicitaReadOnlyScaleProtocolAdapter.id
            else->null
        }
}
