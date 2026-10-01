package io.openhoyi.mobile

/** Original local sounds only. These levels never encode a machine LED command. */
internal object BrewFeedbackClips {
    enum class Level { BRAVO, HIGH_FLOW, LOW_FLOW }
    fun sequence(level: Level, variant: Int): List<String> {
        require(variant in 0..3)
        val stem = when (level) {
            Level.BRAVO -> "bravo"
            Level.HIGH_FLOW -> "perfect"
            Level.LOW_FLOW -> "wow"
        }
        val intro = if (level == Level.BRAVO) "1.mp3" else "2.mp3"
        return listOf("brew-feedback/$intro", "brew-feedback/en_${stem}_${variant + 1}.wav")
    }
}
