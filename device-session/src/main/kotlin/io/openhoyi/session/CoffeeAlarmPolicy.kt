package io.openhoyi.session

/** HOYI wire alarm bits: only bit 14 (legacy C16 tail-water warning) is advisory.
 * All other 16-bit faults, including unknown bit 15, block new control.
 * Recovery cancellation and emergency stop keep their separate dispatch rules.
 */
object CoffeeAlarmPolicy {
    fun firstBlockingBit(bits: Int): Int? {
        val blocking = bits and 0xBFFF
        return if (blocking == 0) null else blocking.countTrailingZeroBits()
    }

    fun permitsNewControl(bits: Int): Boolean = firstBlockingBit(bits) == null
}
