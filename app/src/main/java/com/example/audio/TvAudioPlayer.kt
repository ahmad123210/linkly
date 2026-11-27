package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.model.AudioPlaybackState
import com.example.model.AudioTrackItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import kotlin.math.sin

/**
 * Manages background audio playback, playlist management, and audio alerts on TV and Phone.
 */
class TvAudioPlayer(private val context: Context) {

    companion object {
        private const val TAG = "TvAudioPlayer"
    }

    private val audioDir: File by lazy {
        File(context.filesDir, "linkly_audio").apply {
            if (!exists()) mkdirs()
        }
    }

    private var mediaPlayer: MediaPlayer? = null
    private var ambientTrack: AudioTrack? = null
    private var ambientJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _playlist = MutableStateFlow<List<AudioTrackItem>>(emptyList())
    val playlist: StateFlow<List<AudioTrackItem>> = _playlist.asStateFlow()

    private val _playbackState = MutableStateFlow(AudioPlaybackState())
    val playbackState: StateFlow<AudioPlaybackState> = _playbackState.asStateFlow()

    init {
        refreshPlaylist()
    }

    fun refreshPlaylist() {
        val tracks = mutableListOf<AudioTrackItem>()

        // Scan saved files in linkly_audio directory
        audioDir.listFiles()?.filter { it.isFile && (it.extension.lowercase() in listOf("mp3", "wav", "m4a", "ogg", "aac")) }?.forEach { file ->
            tracks.add(
                AudioTrackItem(
                    id = file.name,
                    title = file.nameWithoutExtension.replace('_', ' '),
                    fileName = file.name,
                    isBuiltIn = false
                )
            )
        }

        _playlist.value = tracks
    }

    fun saveAudioFile(fileName: String, base64Data: String): AudioTrackItem? {
        return try {
            val bytes = Base64.decode(base64Data, Base64.NO_WRAP)
            val cleanName = fileName.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
            val targetFile = File(audioDir, cleanName)
            FileOutputStream(targetFile).use { it.write(bytes) }
            refreshPlaylist()
            _playlist.value.find { it.fileName == cleanName }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving audio file", e)
            null
        }
    }

    fun playTrack(trackId: String) {
        val track = _playlist.value.find { it.id == trackId } ?: return
        stopCurrentPlayback()

        val file = File(audioDir, track.fileName)
        if (file.exists() && file.length() > 0) {
            try {
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    val fis = java.io.FileInputStream(file)
                    setDataSource(fis.fd)
                    fis.close()
                    isLooping = true
                    setVolume(_playbackState.value.volume, _playbackState.value.volume)
                    prepare()
                    start()
                }
                _playbackState.value = _playbackState.value.copy(
                    isPlaying = true,
                    currentTrackTitle = track.title
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error playing audio file", e)
            }
        }
    }

    fun togglePlayPause() {
        if (_playbackState.value.isPlaying) {
            stopCurrentPlayback()
            _playbackState.value = _playbackState.value.copy(isPlaying = false)
        } else {
            val trackToPlay = _playlist.value.firstOrNull()?.id ?: return
            playTrack(trackToPlay)
        }
    }

    fun stopCurrentPlayback() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (_: Exception) {}

        ambientJob?.cancel()
        ambientJob = null
        try {
            ambientTrack?.stop()
            ambientTrack?.release()
            ambientTrack = null
        } catch (_: Exception) {}

        _playbackState.value = _playbackState.value.copy(isPlaying = false)
    }

    /**
     * Plays soothing harmonic ambient tones in a continuous loop without external files.
     */
    private fun startBuiltInAmbient() {
        ambientJob = scope.launch {
            try {
                val sampleRate = 22050
                val bufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                ambientTrack = track
                track.play()

                // Generate harmonic ambient pad notes: C4 (261.63Hz), E4 (329.63Hz), G4 (392Hz), B4 (493.88Hz)
                val baseFreqs = listOf(261.63, 329.63, 392.00, 493.88)
                val samples = ShortArray(bufferSize / 2)
                var phase = 0.0

                while (isActive) {
                    for (i in samples.indices) {
                        var sum = 0.0
                        for (f in baseFreqs) {
                            sum += sin(2.0 * Math.PI * f * phase) * 0.25
                        }
                        samples[i] = (sum * 0.15 * Short.MAX_VALUE * _playbackState.value.volume).toInt().toShort()
                        phase += 1.0 / sampleRate
                    }
                    track.write(samples, 0, samples.size)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ambient loop ended", e)
            }
        }
    }

    /**
     * Pleasant chime notification tone when an order arrives.
     */
    fun playOrderChime() {
        scope.launch {
            try {
                val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val ringtone = RingtoneManager.getRingtone(context, notificationUri)
                if (ringtone != null) {
                    ringtone.play()
                } else {
                    ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85).apply {
                        startTone(ToneGenerator.TONE_PROP_ACK, 350)
                    }
                }
            } catch (_: Exception) {
                try {
                    ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85).startTone(ToneGenerator.TONE_PROP_BEEP2, 300)
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Distinct warning tone when an order is cancelled.
     */
    fun playOrderCancelAlert() {
        scope.launch {
            try {
                val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 95)
                toneGen.startTone(ToneGenerator.TONE_PROP_NACK, 500)
            } catch (_: Exception) {
                try {
                    ToneGenerator(AudioManager.STREAM_NOTIFICATION, 95).startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400)
                } catch (_: Exception) {}
            }
        }
    }

    /** Urgent order: three quick high beeps, clearly different from the normal chime. */
    fun playUrgentAlert() {
        scope.launch {
            try {
                val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
                repeat(3) {
                    tg.startTone(ToneGenerator.TONE_PROP_BEEP, 180)
                    kotlinx.coroutines.delay(260)
                }
                tg.release()
            } catch (_: Exception) {
                playOrderChime()
            }
        }
    }

    /** Waiter call (help, bill, cleaning): a two-tone ding-dong. */
    fun playCallAlert() {
        scope.launch {
            try {
                val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
                tg.startTone(ToneGenerator.TONE_DTMF_9, 220)
                kotlinx.coroutines.delay(280)
                tg.startTone(ToneGenerator.TONE_DTMF_1, 320)
                kotlinx.coroutines.delay(380)
                tg.release()
            } catch (_: Exception) {
                playOrderChime()
            }
        }
    }

    /** Sound for a new order, picked by its kind. */
    fun playForNewOrder(isUrgent: Boolean, isCall: Boolean) {
        when {
            isCall -> playCallAlert()
            isUrgent -> playUrgentAlert()
            else -> playOrderChime()
        }
    }

    fun release() {
        stopCurrentPlayback()
    }
}
