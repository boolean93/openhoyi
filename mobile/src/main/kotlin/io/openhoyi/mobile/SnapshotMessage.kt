package io.openhoyi.mobile

import java.math.BigDecimal
import java.math.BigInteger

/** Resource identity and frozen display arguments, with no callbacks, state or device access.
 * Scalars retain Formatter types; other %s arguments are captured as text at construction. */
class ResourceMessage(val resourceId: Int, vararg arguments: Any?) {
    private val values: List<Any?> = arguments.map { value ->
        when(value) {
            null, is String, is Char, is Boolean, is Byte, is Short, is Int, is Long,
            is Float, is Double -> value
            else -> if (value.javaClass == BigInteger::class.java || value.javaClass == BigDecimal::class.java)
                value else value.toString()
        }
    }
    fun render(resolve: (Int, Array<out Any?>) -> String): String =
        resolve(resourceId, values.toTypedArray())
    override fun toString() = "ResourceMessage(resourceId=$resourceId, argumentCount=${values.size})"
}

/** Captured text stays stable for diagnostics; rerendering only resolves display resources.
 * A raw message is never interpreted as a printf template, even when it contains '%' or is empty. */
class SnapshotMessage private constructor(val initialText: String, private val resource: ResourceMessage?) {
    fun render(resolve: (Int, Array<out Any?>) -> String): String = resource?.render(resolve) ?: initialText
    override fun toString() = "SnapshotMessage(translatable=${resource != null})"
    companion object {
        fun raw(text: String) = SnapshotMessage(text, null)
        fun resource(template: ResourceMessage, resolve: (Int, Array<out Any?>) -> String) =
            SnapshotMessage(template.render(resolve), template)
    }
}
