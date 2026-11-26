package com.example.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class ArabicTtsManager(private val context: Context) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "ArabicTtsManager"
    }

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TTS", e)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val arLocale = Locale("ar")
            val result = tts?.setLanguage(arLocale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "Arabic TTS language missing or not supported, trying default")
                tts?.language = Locale.getDefault()
            }
            tts?.setSpeechRate(0.95f)
            tts?.setPitch(1.0f)
            isInitialized = true
            Log.d(TAG, "Arabic TTS initialized successfully")
        } else {
            Log.e(TAG, "TTS Initialization failed with status $status")
        }
    }

    fun speak(text: String) {
        // Spoken TTS completely disabled across the application per user requirement.
        // Simple subtle chime sounds are used instead.
    }

    fun speakRepeated(text: String, times: Int = 2, delayMs: Long = 3000L) {
        // Spoken TTS completely disabled.
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {}
        tts = null
        isInitialized = false
    }
}
