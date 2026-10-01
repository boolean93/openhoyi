package io.openhoyi.mobile

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Looper

/** Application-owned assets and Android media only. Call and receive callbacks on main. */
internal class AndroidBrewFeedbackAudio(context: Context) : BrewFeedbackAudio.Driver {
    private val app = context.applicationContext
    private val manager = app.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()

    override fun start(path: String, done: (BrewFeedbackAudio.Result) -> Unit): BrewFeedbackAudio.Cancel {
        check(Looper.myLooper() == Looper.getMainLooper())
        val player = MediaPlayer()
        var finished = false
        var focus: AudioFocusRequest? = null
        fun finish(result: BrewFeedbackAudio.Result?, notify: Boolean) {
            if (finished) return
            finished = true
            player.setOnPreparedListener(null)
            player.setOnCompletionListener(null)
            player.setOnErrorListener(null)
            runCatching { player.release() }
            focus?.let { runCatching { manager.abandonAudioFocusRequest(it) } }
            if (notify) done(requireNotNull(result))
        }
        val cancel = BrewFeedbackAudio.Cancel { finish(null, false) }
        try {
            focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attributes).setOnAudioFocusChangeListener { change ->
                    if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
                        finish(BrewFeedbackAudio.Result.INTERRUPTED, true)
                }.build()
            if (manager.requestAudioFocus(requireNotNull(focus)) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                finish(BrewFeedbackAudio.Result.INTERRUPTED, true)
                return cancel
            }
            player.setAudioAttributes(attributes)
            app.assets.openFd(path).use { player.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            player.setOnCompletionListener { finish(BrewFeedbackAudio.Result.COMPLETED, true) }
            player.setOnErrorListener { _, _, _ -> finish(BrewFeedbackAudio.Result.FAILED, true); true }
            player.setOnPreparedListener {
                if (!finished) try { player.start() } catch (_: RuntimeException) { finish(BrewFeedbackAudio.Result.FAILED, true) }
            }
            player.prepareAsync()
        } catch (_: Exception) { finish(BrewFeedbackAudio.Result.FAILED, true) }
        return cancel
    }
}
