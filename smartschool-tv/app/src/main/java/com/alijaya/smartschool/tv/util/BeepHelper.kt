package com.alijaya.smartschool.tv.util

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.speech.tts.TextToSpeech
import java.util.Locale

class BeepHelper(context: Context) {
    private val soundPool: SoundPool = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
    } else {
        @Suppress("DEPRECATION")
        SoundPool(2, android.media.AudioManager.STREAM_NOTIFICATION, 0)
    }

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val res = tts?.setLanguage(Locale("id", "ID"))
                isTtsReady = res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }
    }

    fun speak(text: String) {
        if (isTtsReady && tts != null) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_kiosk")
        }
    }

    fun release() {
        soundPool.release()
        tts?.stop()
        tts?.shutdown()
    }
}
