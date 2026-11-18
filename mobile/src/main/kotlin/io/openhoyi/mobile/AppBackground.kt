package io.openhoyi.mobile

internal sealed interface AppBackground {
    val key: String
    data object Solid : AppBackground { override val key = "solid" }
    data object Mist : AppBackground { override val key = "mist" }
    data object Slate : AppBackground { override val key = "slate" }
    data class Photo(val id: String) : AppBackground {
        init { require(validId(id)) }
        override val key get() = "photo:$id"
    }

    companion object {
        private val idPattern = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        fun validId(id: String?) = id != null && idPattern.matches(id)
        fun parse(key: String?): AppBackground = when (key) {
            Mist.key -> Mist
            Slate.key -> Slate
            else -> key?.takeIf { it.startsWith("photo:") }?.substringAfter(':')
                ?.takeIf(::validId)?.let(::Photo) ?: Solid
        }
    }
}

internal data class AppBackgroundConfig(
    val choice: AppBackground = AppBackground.Solid,
    val strength: Int = DEFAULT_STRENGTH,
) {
    init { require(strength in MIN_STRENGTH..MAX_STRENGTH) }
    val key get() = "${choice.key}|$strength"

    companion object {
        const val MIN_STRENGTH = 10
        const val MAX_STRENGTH = 32
        const val DEFAULT_STRENGTH = 20

        fun restore(key: String?) = AppBackgroundConfig(
            AppBackground.parse(key?.substringBefore('|')),
            key?.substringAfter('|', "")?.toIntOrNull()?.coerceIn(MIN_STRENGTH, MAX_STRENGTH) ?: DEFAULT_STRENGTH,
        )
    }
}

internal object BackgroundImageLimits {
    const val MAX_SOURCE_BYTES = 20 * 1024 * 1024L
    const val MAX_EDGE = 2048
    const val MAX_PIXELS = 4_000_000L
    private const val MAX_SOURCE_EDGE = 32768
    private const val MAX_SOURCE_PIXELS = 80_000_000L

    fun sample(width: Int, height: Int): Int {
        require(width in 1..MAX_SOURCE_EDGE && height in 1..MAX_SOURCE_EDGE &&
            width.toLong() * height <= MAX_SOURCE_PIXELS)
        var result = 1
        fun sampled(size: Int) = (size.toLong() + result - 1) / result
        while (maxOf(sampled(width), sampled(height)) > MAX_EDGE || sampled(width) * sampled(height) > MAX_PIXELS) {
            result *= 2
        }
        return result
    }

    fun fits(width: Int, height: Int) =
        width in 1..MAX_EDGE && height in 1..MAX_EDGE && width.toLong() * height <= MAX_PIXELS
}

/** Reject a late picker/decoder result without changing what a newer preview or Apply owns. */
internal class BackgroundRequestFence {
    private var generation = 0L
    fun next() = ++generation
    fun accepts(request: Long) = request == generation
}
