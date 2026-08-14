package com.example.kinetixfsl.game.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.kinetixfsl.R

/**
 * Plays the short answer sound effects (bundled in `res/raw`) and a matching
 * haptic buzz. Uses [SoundPool] for low-latency playback of the two clips.
 */
class QuizFeedbackPlayer(context: Context) {

    private val appContext = context.applicationContext

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val correctSound = soundPool.load(appContext, R.raw.sfx_correct, 1)
    private val wrongSound = soundPool.load(appContext, R.raw.sfx_wrong, 1)

    private val vibrator: Vibrator? = resolveVibrator(appContext)

    /** Play the sound + haptic for a submitted answer. */
    fun play(correct: Boolean) {
        soundPool.play(if (correct) correctSound else wrongSound, 1f, 1f, 1, 0, 1f)
        vibrate(correct)
    }

    private fun vibrate(correct: Boolean) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val effect = if (correct) {
            // A single crisp buzz for success.
            VibrationEffect.createOneShot(70, VibrationEffect.DEFAULT_AMPLITUDE)
        } else {
            // A short double-tap for "not quite".
            VibrationEffect.createWaveform(longArrayOf(0, 40, 70, 40), -1)
        }
        v.vibrate(effect)
    }

    fun release() {
        soundPool.release()
    }

    private fun resolveVibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
}

/** Remembers a [QuizFeedbackPlayer] tied to the composition, releasing on dispose. */
@Composable
fun rememberQuizFeedback(): QuizFeedbackPlayer {
    val context = LocalContext.current
    val player = remember { QuizFeedbackPlayer(context) }
    DisposableEffect(Unit) {
        onDispose { player.release() }
    }
    return player
}
