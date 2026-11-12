package dev.cookingclock.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dev.cookingclock.data.AlarmProfile

/** Plays the sound and vibration of one alarm profile until stopped. */
class Ringer(private val context: Context) {
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var playing: AlarmProfile? = null

    fun play(profile: AlarmProfile) {
        if (playing == profile) return
        stop()
        playing = profile
        profile.soundUri?.let { uri ->
            val mp = MediaPlayer()
            try {
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                mp.setDataSource(context, Uri.parse(uri))
                mp.isLooping = true
                mp.prepare()
                mp.start()
                player = mp
            } catch (e: Exception) {
                mp.release()
            }
        }
        if (profile.vibrate) {
            val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Vibrator::class.java)
            }
            vibrator = v
            val effect = VibrationEffect.createWaveform(longArrayOf(0, 600, 400), 0)
            if (Build.VERSION.SDK_INT >= 33) {
                v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
            }
        }
    }

    fun stop() {
        player?.let {
            runCatching { it.stop() }
            it.release()
        }
        player = null
        vibrator?.cancel()
        vibrator = null
        playing = null
    }
}
