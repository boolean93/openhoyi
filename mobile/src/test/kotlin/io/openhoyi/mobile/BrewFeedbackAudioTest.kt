package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class BrewFeedbackAudioTest {
    private class Driver : BrewFeedbackAudio.Driver {
        data class Call(val path: String, val done: (BrewFeedbackAudio.Result) -> Unit, var cancels: Int = 0)
        val calls = mutableListOf<Call>()
        override fun start(path: String, done: (BrewFeedbackAudio.Result) -> Unit): BrewFeedbackAudio.Cancel {
            val call = Call(path, done); calls.add(call)
            return BrewFeedbackAudio.Cancel { call.cancels++; call.done(BrewFeedbackAudio.Result.INTERRUPTED) }
        }
    }
    @Test fun introThenVoiceIsExactlyTwoClips() {
        val driver = Driver(); val audio = BrewFeedbackAudio(driver)
        audio.play(BrewFeedbackClips.Level.BRAVO, 2)
        assertEquals(listOf("brew-feedback/1.mp3"), driver.calls.map { it.path })
        driver.calls[0].done(BrewFeedbackAudio.Result.COMPLETED)
        assertEquals("brew-feedback/en_bravo_3.wav", driver.calls[1].path)
        driver.calls[0].done(BrewFeedbackAudio.Result.COMPLETED)
        assertEquals(2, driver.calls.size)
        driver.calls[1].done(BrewFeedbackAudio.Result.COMPLETED)
        assertFalse(audio.playing)
    }
    @Test fun stopAndSupersessionInvalidateCallbacksBeforeCancel() {
        val driver = Driver(); val audio = BrewFeedbackAudio(driver)
        audio.play(BrewFeedbackClips.Level.HIGH_FLOW, 0)
        val old = driver.calls.single()
        audio.play(BrewFeedbackClips.Level.LOW_FLOW, 3)
        assertEquals(1, old.cancels)
        old.done(BrewFeedbackAudio.Result.COMPLETED)
        assertEquals(2, driver.calls.size)
        driver.calls[1].done(BrewFeedbackAudio.Result.COMPLETED)
        assertEquals("brew-feedback/en_wow_4.wav", driver.calls.last().path)
        audio.stop(); audio.stop()
        driver.calls.last().done(BrewFeedbackAudio.Result.COMPLETED)
        assertFalse(audio.playing)
        assertEquals(3, driver.calls.size)
        assertEquals(1, driver.calls.last().cancels)
    }
    @Test fun focusInterruptionDoesNotStartTheVoice() {
        val driver = Driver(); val audio = BrewFeedbackAudio(driver)
        audio.play(BrewFeedbackClips.Level.BRAVO, 0)
        driver.calls.single().done(BrewFeedbackAudio.Result.INTERRUPTED)
        assertFalse(audio.playing)
        assertEquals(1, driver.calls.size)
    }
    @Test fun failedIntroTriesVoiceOnceWithoutRetryingClips() {
        val driver = Driver(); val audio = BrewFeedbackAudio(driver)
        audio.play(BrewFeedbackClips.Level.HIGH_FLOW, 1)
        driver.calls[0].done(BrewFeedbackAudio.Result.FAILED)
        assertEquals("brew-feedback/en_perfect_2.wav", driver.calls[1].path)
        driver.calls[1].done(BrewFeedbackAudio.Result.FAILED)
        assertFalse(audio.playing)
        assertEquals(2, driver.calls.size)
    }
    @Test fun synchronousCallbacksAndStartExceptionsCannotLeaveStaleHandles() {
        val paths = mutableListOf<String>(); var released = 0
        val audio = BrewFeedbackAudio(object : BrewFeedbackAudio.Driver {
            override fun start(path: String, done: (BrewFeedbackAudio.Result) -> Unit): BrewFeedbackAudio.Cancel {
                paths.add(path); done(BrewFeedbackAudio.Result.COMPLETED)
                return BrewFeedbackAudio.Cancel { released++ }
            }
        })
        audio.play(BrewFeedbackClips.Level.BRAVO, 0)
        assertFalse(audio.playing); assertEquals(2, paths.size); assertEquals(2, released)
        var attempts = 0
        val failed = BrewFeedbackAudio(object : BrewFeedbackAudio.Driver {
            override fun start(path: String, done: (BrewFeedbackAudio.Result) -> Unit): BrewFeedbackAudio.Cancel {
                attempts++; throw IllegalStateException("decoder unavailable")
            }
        })
        failed.play(BrewFeedbackClips.Level.BRAVO, 0)
        assertFalse(failed.playing); assertEquals(2, attempts)
    }
}
