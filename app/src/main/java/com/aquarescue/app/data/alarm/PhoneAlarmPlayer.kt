package com.aquarescue.app.data.alarm

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Звуковое оповещение на телефоне (системный генератор тонов, поток будильника).
 * Не требует аудиофайлов и разрешений.
 */
class PhoneAlarmPlayer {

    private val handler = Handler(Looper.getMainLooper())
    private var current: ToneGenerator? = null

    fun playDanger() = play(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 4000)

    fun playConnectionLost() = play(ToneGenerator.TONE_CDMA_ABBR_ALERT, 1500)

    fun playTest() = play(ToneGenerator.TONE_PROP_BEEP2, 600)

    private fun play(tone: Int, durationMs: Int) {
        handler.post {
            try {
                current?.release()
                val generator = ToneGenerator(AudioManager.STREAM_ALARM, VOLUME)
                current = generator
                generator.startTone(tone, durationMs)
                handler.postDelayed({
                    // Если за это время запущен новый сигнал, старый уже освобождён в play().
                    if (current === generator) {
                        generator.release()
                        current = null
                    }
                }, durationMs + 200L)
            } catch (e: RuntimeException) {
                Log.w("PhoneAlarmPlayer", "Failed to play tone", e)
            }
        }
    }

    private companion object {
        const val VOLUME = 90
    }
}
