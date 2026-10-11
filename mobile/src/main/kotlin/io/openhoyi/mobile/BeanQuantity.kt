package io.openhoyi.mobile

import java.math.BigDecimal

internal object BeanQuantity {
    fun parseGrams(value: String): Long {
        val normalized = value.trim()
        require(Regex("(?:[0-9]+(?:\\.[0-9]{1,3})?|\\.[0-9]{1,3})").matches(normalized))
        return try { BigDecimal(normalized).movePointRight(3).longValueExact() }
        catch (e: ArithmeticException) { throw IllegalArgumentException("Quantity exceeds mg range", e) }
    }
    fun formatGrams(mg: Long): String = BigDecimal.valueOf(mg, 3).stripTrailingZeros().toPlainString()
}
