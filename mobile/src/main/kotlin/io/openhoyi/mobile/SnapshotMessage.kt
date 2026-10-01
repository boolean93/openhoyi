package io.openhoyi.mobile

import java.math.BigDecimal
import java.math.BigInteger

/** Resource identity and frozen display arguments, with no callbacks, state or device access.
 * Scalars retain Formatter types; other %s arguments are captured as text at construction. */
sealed interface ResourceText {
    fun render(resolve: (Int, Array<out Any?>) -> String): String
}

class ResourceMessage(val resourceId: Int, vararg arguments: Any?) : ResourceText {
    private val values: List<Any?> = arguments.map { value ->
        when(value) {
            null, is String, is Char, is Boolean, is Byte, is Short, is Int, is Long,
            is Float, is Double, is ResourceText -> value
            else -> if (value.javaClass == BigInteger::class.java || value.javaClass == BigDecimal::class.java)
                value else value.toString()
        }
    }
    override fun render(resolve: (Int, Array<out Any?>) -> String): String =
        resolve(resourceId, values.map { if (it is ResourceText) it.render(resolve) else it }.toTypedArray())
    override fun toString() = "ResourceMessage(resourceId=$resourceId, argumentCount=${values.size})"
}

/** Immutable concatenation of resource text; captures array membership without holding a resolver. */
class ResourceSequence(vararg parts: ResourceText) : ResourceText {
    private val parts = parts.toList()
    override fun render(resolve: (Int, Array<out Any?>) -> String): String =
        parts.joinToString("") { it.render(resolve) }
    override fun toString() = "ResourceSequence(partCount=${parts.size})"
}

/** Captured text stays stable for diagnostics; rerendering only resolves display resources.
 * A raw message is never interpreted as a printf template, even when it contains '%' or is empty. */
class SnapshotMessage private constructor(val initialText: String, private val resource: ResourceText?) {
    fun render(resolve: (Int, Array<out Any?>) -> String): String = resource?.render(resolve) ?: initialText
    override fun toString() = "SnapshotMessage(translatable=${resource != null})"
    companion object {
        fun raw(text: String) = SnapshotMessage(text, null)
        fun resource(template: ResourceText, resolve: (Int, Array<out Any?>) -> String) =
            SnapshotMessage(template.render(resolve), template)
    }
}
