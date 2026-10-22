package io.openhoyi.protocol

/** Known published 18-byte notification shape only; not a firmware/transport guarantee.
 * Source: Beanconqueror b975d393, felicita/felicita-readme.md. No device writes. */
data class FelicitaNotification(
    val signedHundredths:Int,
    val unit:Unit,
    val raw:ByteFrame,
) {
    enum class Unit { GRAM, OUNCE }
    // Battery encoding stays in raw. No verified percentage mapping or device timer is claimed.
}

object FelicitaNotificationCodec {
    fun decode(bytes:ByteArray):DecodeResult<FelicitaNotification> {
        val raw=ByteFrame(bytes)
        fun u(index:Int)=bytes[index].toInt() and 255
        if(bytes.size<2 || u(0)!=1 || u(1)!=2)return DecodeResult.Unknown(raw)
        if(bytes.size!=18)return DecodeResult.Invalid("Felicita notification must contain 18 bytes",raw)
        if(u(16)!=13 || u(17)!=10)return DecodeResult.Invalid("Invalid Felicita notification terminator",raw)
        val sign=when(u(2)) {
            '+'.code->1
            '-'.code->-1
            else->return DecodeResult.Invalid("Invalid Felicita weight sign",raw)
        }
        var magnitude=0
        for(index in 3..8) {
            val digit=u(index)-'0'.code
            if(digit !in 0..9)return DecodeResult.Invalid("Invalid Felicita ASCII weight digit",raw)
            magnitude=magnitude*10+digit
        }
        val unit=when {
            u(9)==' '.code && u(10)=='g'.code->FelicitaNotification.Unit.GRAM
            u(9)=='o'.code && u(10)=='z'.code->FelicitaNotification.Unit.OUNCE
            else->return DecodeResult.Unknown(raw)
        }
        return DecodeResult.Valid(FelicitaNotification(sign*magnitude,unit,raw))
    }
}
