package com.alijaya.smartschool.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.exp
import kotlin.math.sin

/**
 * Text-to-Speech & Sound Engine yang mengadopsi arsitektur TtsVoiceManager dari D:\kolam-pancing.
 * Fitur:
 * 1. Dual Engine: Online Natural Human TTS (Google Neural Streamer dengan MD5 disk cache)
 *    + Offline Fallback TTS Engine (Google Speech Services / Default System TTS id-ID).
 * 2. Harmonic Airport Chime sintetis PCM AudioTrack 44.1kHz 16-bit (D5 587.33 Hz & A4 440.0 Hz).
 * 3. Pemilihan karakter suara santri laki-laki & perempuan dengan pitch/speed natural.
 * 4. Antrian suara sekuensial (Queue Sequential Speech) agar panggilan tidak bertumpuk.
 */
class BeepTtsHelper(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
) {

    companion object {
        private const val TAG = "BeepTtsHelper"
        const val MODE_ONLINE_NATURAL = "ONLINE_NATURAL"
        const val MODE_OFFLINE_STB = "OFFLINE_STB"
    }

    private data class SpeechTask(
        val text: String,
        val isMale: Boolean,
        val playChime: Boolean = false
    )

    private var toneGen: ToneGenerator? = null
    private var tts: TextToSpeech? = null
    private var isOfflineTtsInitialized = false

    private val queue = mutableListOf<SpeechTask>()
    private var isProcessingQueue = false

    var voiceMode: String = MODE_ONLINE_NATURAL
    var speechRate: Float = 0.96f
    var pitch: Float = 1.0f

    private var maleVoice: Voice? = null
    private var femaleVoice: Voice? = null

    init {
        try {
            toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        } catch (_: Throwable) {}

        initOfflineTtsEngine()
    }

    private fun initOfflineTtsEngine() {
        try {
            val googleTtsPkg = "com.google.android.tts"
            val hasGoogleTts = isPackageInstalled(googleTtsPkg)
            val listener = TextToSpeech.OnInitListener { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isOfflineTtsInitialized = true
                    applyOfflineVoiceSettings()
                    Log.i(TAG, "Offline TTS Engine ready.")
                } else {
                    isOfflineTtsInitialized = false
                    Log.w(TAG, "Offline TTS onInit failed with status $status")
                }
            }

            tts = if (hasGoogleTts) {
                Log.i(TAG, "Using Google Speech Services Engine ($googleTtsPkg)...")
                TextToSpeech(context.applicationContext, listener, googleTtsPkg)
            } else {
                Log.i(TAG, "Using System Default TTS Engine...")
                TextToSpeech(context.applicationContext, listener)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed creating TextToSpeech: ${t.message}", t)
            tts = null
            isOfflineTtsInitialized = false
        }
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun applyOfflineVoiceSettings() {
        val t = tts ?: return
        if (!isOfflineTtsInitialized) return
        try {
            val idLocale = Locale("id", "ID")
            val res = t.setLanguage(idLocale)
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                t.language = Locale.getDefault()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val voices = t.voices
                if (!voices.isNullOrEmpty()) {
                    for (v in voices) {
                        val lang = v.locale.language.lowercase(Locale.ROOT)
                        if (lang == "id" || lang == "in") {
                            val name = v.name.lowercase(Locale.ROOT)
                            if (name.contains("male") || name.contains("-m-") || name.contains("pria") || name.contains("laki")) {
                                maleVoice = v
                            } else if (name.contains("female") || name.contains("-f-") || name.contains("wanita") || name.contains("perempuan")) {
                                femaleVoice = v
                            }
                        }
                    }
                }
            }
            t.setSpeechRate(speechRate)
            t.setPitch(pitch)
        } catch (t: Throwable) {
            Log.w(TAG, "Error applying offline voice settings: ${t.message}")
        }
    }

    // =========================================================================
    // NADA BIP DAN AIRPORT CHIME (SINTESIS HARMONIK PCM D5 -> A4)
    // =========================================================================
    fun playBeepSuccess() {
        try {
            toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        } catch (_: Throwable) {}
    }

    fun playBeepWarning() {
        try {
            toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP2, 250)
        } catch (_: Throwable) {}
    }

    fun playChime() {
        scope.launch(Dispatchers.IO) {
            playAirportChime()
        }
    }

    /**
     * Airport Chime 2-Nada (D5 587.33Hz -> A4 440.0Hz) murni AudioTrack 16-bit PCM
     * Diadopsi persis dari TtsVoiceManager kolam-pancing
     */
    private fun playAirportChime() {
        try {
            val sampleRate = 44100
            val duration1 = 0.22 // Tone D5 (587.33 Hz)
            val duration2 = 0.35 // Tone A4 (440.00 Hz)
            val n1 = (sampleRate * duration1).toInt()
            val n2 = (sampleRate * duration2).toInt()
            val total = n1 + n2
            val buffer = ShortArray(total)

            // Ding: 587.33 Hz with smooth exponential decay and harmonic warmth
            for (i in 0 until n1) {
                val t = i.toDouble() / sampleRate
                val decay = exp(-3.2 * (i.toDouble() / n1))
                val sample = (sin(2.0 * Math.PI * 587.33 * t) + 0.25 * sin(4.0 * Math.PI * 587.33 * t)) * decay
                buffer[i] = (sample * 24000.0).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }

            // Dong: 440.0 Hz with gentle lingering decay
            for (i in 0 until n2) {
                val t = i.toDouble() / sampleRate
                val decay = exp(-2.6 * (i.toDouble() / n2))
                val sample = (sin(2.0 * Math.PI * 440.0 * t) + 0.25 * sin(4.0 * Math.PI * 440.0 * t)) * decay
                buffer[n1 + i] = (sample * 24000.0).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }

            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val track = AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                (buffer.size * 2).coerceAtLeast(minBuf),
                AudioTrack.MODE_STATIC
            )
            track.write(buffer, 0, buffer.size)
            track.play()
            Thread.sleep(600)
            track.stop()
            track.release()
        } catch (_: Throwable) {
            playFallbackTone()
        }
    }

    private fun playFallbackTone() {
        try {
            toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP2, 300)
            Thread.sleep(350)
        } catch (_: Throwable) {}
    }

    // =========================================================================
    // METODE PEMANGGILAN TEXT-TO-SPEECH
    // =========================================================================

    /**
     * Masukkan teks ke antrian bicara (Queue)
     */
    @Synchronized
    fun speak(text: String, isMale: Boolean = true) {
        if (text.isBlank()) return
        val clean = formatIndonesianSpokenText(text)
        queue.add(SpeechTask(text = clean, isMale = isMale, playChime = false))
        triggerQueueProcessing()
    }

    /**
     * Mainkan Chime Bel Bandara lalu ucapkan kalimat (Queue)
     */
    @Synchronized
    fun playChimeAndSpeak(text: String, isMale: Boolean = true, delayMs: Long = 400L) {
        if (text.isBlank()) return
        val clean = formatIndonesianSpokenText(text)
        queue.add(SpeechTask(text = clean, isMale = isMale, playChime = true))
        triggerQueueProcessing()
    }

    /**
     * Mainkan Beep Singkat lalu ucapkan kalimat
     */
    @Synchronized
    fun playBeepAndSpeak(text: String, isMale: Boolean = true, delayMs: Long = 250L) {
        playBeepSuccess()
        speak(text, isMale)
    }

    private fun triggerQueueProcessing() {
        if (isProcessingQueue) return
        scope.launch(Dispatchers.IO) {
            processQueueLoop()
        }
    }

    private suspend fun processQueueLoop() {
        isProcessingQueue = true
        try {
            while (true) {
                val task = synchronized(queue) {
                    if (queue.isEmpty()) null else queue.removeAt(0)
                } ?: break

                // Bunyikan bel airport chime jika diinstruksikan
                if (task.playChime) {
                    playAirportChime()
                    delay(200)
                }

                // Coba Online Natural Human TTS (Google Translate Neural Audio)
                var spoken = false
                if (voiceMode == MODE_ONLINE_NATURAL) {
                    spoken = speakOnlineNatural(task.text, task.isMale)
                }

                // Fallback ke Offline System TTS jika online gagal atau mode offline
                if (!spoken) {
                    speakOfflineTts(task.text, task.isMale)
                }

                delay(300)
            }
        } finally {
            isProcessingQueue = false
        }
    }

    // =========================================================================
    // ONLINE NATURAL HUMAN TTS ENGINE (GOOGLE STREAM + MD5 DISK CACHING)
    // =========================================================================
    private suspend fun speakOnlineNatural(fullText: String, isMale: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val sentences = splitIntoSentences(fullText)
            for (sentence in sentences) {
                val ok = playSentenceOnline(sentence)
                if (!ok) return@withContext false
            }
            true
        } catch (e: Throwable) {
            Log.w(TAG, "speakOnlineNatural failed: ${e.message}")
            false
        }
    }

    private fun splitIntoSentences(text: String): List<String> {
        val raw = text.split(Regex("(?<=[.!?])\\s+"))
        val result = mutableListOf<String>()
        for (item in raw) {
            val trimmed = item.trim()
            if (trimmed.length > 150) {
                val sub = trimmed.split(Regex("(?<=,)\\s+"))
                result.addAll(sub.filter { it.isNotBlank() })
            } else if (trimmed.isNotBlank()) {
                result.add(trimmed)
            }
        }
        return if (result.isEmpty()) listOf(text) else result
    }

    private suspend fun playSentenceOnline(sentence: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val cacheDir = File(context.cacheDir, "tts_cache").apply { mkdirs() }
            val md5Name = md5(sentence) + ".mp3"
            val audioFile = File(cacheDir, md5Name)

            if (!audioFile.exists() || audioFile.length() < 100L) {
                val encoded = URLEncoder.encode(sentence, "UTF-8")
                val url = URL("https://translate.google.com/translate_tts?ie=UTF-8&client=tw-ob&tl=id&q=$encoded")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    connectTimeout = 6000
                    readTimeout = 8000
                }
                if (conn.responseCode == 200) {
                    conn.inputStream.use { input ->
                        FileOutputStream(audioFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                } else {
                    return@withContext false
                }
            }

            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .build()
                )
                setDataSource(audioFile.absolutePath)
                prepare()
            }

            val finished = CompletableDeferred<Unit>()
            mp.setOnCompletionListener {
                finished.complete(Unit)
            }
            mp.setOnErrorListener { _, _, _ ->
                finished.complete(Unit)
                true
            }

            mp.start()
            withTimeoutOrNull(30000L) {
                finished.await()
            }

            try {
                mp.stop()
                mp.release()
            } catch (_: Throwable) {}

            true
        } catch (e: Throwable) {
            Log.w(TAG, "playSentenceOnline error: ${e.message}")
            false
        }
    }

    // =========================================================================
    // OFFLINE TTS ENGINE FALLBACK (GOOGLE SPEECH SERVICES / ANDROID TTS)
    // =========================================================================
    private suspend fun speakOfflineTts(text: String, isMale: Boolean): Boolean = withContext(Dispatchers.Main) {
        val t = tts
        if (!isOfflineTtsInitialized || t == null) return@withContext false

        // Atur Voice & Pitch Pria / Wanita
        if (isMale) {
            if (maleVoice != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                t.voice = maleVoice
            }
            t.setPitch(0.88f)
            t.setSpeechRate(0.96f)
        } else {
            if (femaleVoice != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                t.voice = femaleVoice
            }
            t.setPitch(1.15f)
            t.setSpeechRate(0.98f)
        }

        val utteranceId = "utt_${System.currentTimeMillis()}"
        val deferred = CompletableDeferred<Unit>()

        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) {
                if (id == utteranceId) deferred.complete(Unit)
            }
            override fun onError(id: String?) {
                if (id == utteranceId) deferred.complete(Unit)
            }
        })

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        val res = t.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        if (res == TextToSpeech.SUCCESS) {
            withTimeoutOrNull(30000L) {
                deferred.await()
            }
            true
        } else {
            false
        }
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Memperhalus pengucapan kata dan angka agar terdengar fasih di TTS
     */
    private fun formatIndonesianSpokenText(raw: String): String {
        var s = raw.trim()
        s = s.replace("'", "").replace("\"", "")
        s = s.replace("#", "")
        return s
    }

    fun release() {
        try {
            toneGen?.release()
            toneGen = null
        } catch (_: Throwable) {}

        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isOfflineTtsInitialized = false
        } catch (_: Throwable) {}

        scope.cancel()
    }
}
