package io.openhoyi.mobile

import java.io.File
import java.security.MessageDigest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BrewFeedbackAssetsTest {
    @Test fun everyReferencedSoundMatchesTheCapturedOriginalBytes() {
        val manifest = JSONObject(File("../docs/evidence/local-brew-audio-assets.json").readText()).getJSONArray("assets")
        val expected = (0 until manifest.length()).associate { index ->
            val entry = manifest.getJSONObject(index)
            entry.getString("asset") to entry
        }
        val paths = BrewFeedbackClips.Level.entries.flatMap { level ->
            (0..3).flatMap { BrewFeedbackClips.sequence(level, it) }
        }.toSet()
        assertEquals(14, paths.size)
        assertEquals(expected.keys, paths)
        for(path in paths) {
            val bytes = File("src/main/assets/$path").readBytes()
            val entry = expected.getValue(path)
            assertEquals(path, entry.getLong("bytes"), bytes.size.toLong())
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
            assertEquals(path, entry.getString("sha256"), digest)
        }
    }
}
