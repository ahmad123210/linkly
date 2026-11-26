package com.example.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import com.example.model.LinklyMessage
import com.example.network.NetworkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

class AudioStreamManager(
    private val context: Context,
    private val networkManager: NetworkManager
) {
    companion object {
        private const val TAG = "AudioStreamManager"
        const val SAMPLE_RATE = 16000
        const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val _isTransmitting = MutableStateFlow(false)
    val isTransmitting: StateFlow<Boolean> = _isTransmitting.asStateFlow()

    private val _isReceiving = MutableStateFlow(false)
    val isReceiving: StateFlow<Boolean> = _isReceiving.asStateFlow()

    private val _currentAmplitude = MutableStateFlow(0f)
    val currentAmplitude: StateFlow<Float> = _currentAmplitude.asStateFlow()

    private var recordJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    // Dedicated sequential low-latency playback channel (small bounded capacity to prevent audio delay accumulation)
    private val playbackChannel = Channel<ByteArray>(capacity = 6)

    init {
        initAudioTrack()
        // Single dedicated playback loop
        scope.launch {
            for (chunk in playbackChannel) {
                try {
                    if (audioTrack == null || audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                        initAudioTrack()
                    }
                    if (audioTrack?.playState != AudioTrack.PLAYSTATE_PLAYING) {
                        audioTrack?.play()
                    }
                    audioTrack?.write(chunk, 0, chunk.size)
                } catch (e: Throwable) {
                    Log.e(TAG, "AudioTrack write error", e)
                }
            }
        }
    }

    @Synchronized
    private fun initAudioTrack() {
        try {
            audioTrack?.release()
            val minBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, ENCODING)
            val bufferSize = (minBufferSize * 2).coerceAtLeast(2048)

            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .setFlags(AudioAttributes.FLAG_LOW_LATENCY)
                .build()

            val format = AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(CHANNEL_OUT)
                .setEncoding(ENCODING)
                .build()

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()

            audioTrack?.play()
            Log.d(TAG, "Low-latency AudioTrack successfully initialized")
        } catch (e: Throwable) {
            Log.e(TAG, "AudioTrack init error", e)
        }
    }

    fun togglePushToTalk(senderRole: String = "PHONE") {
        if (_isTransmitting.value) {
            stopPushToTalk(senderRole)
        } else {
            startPushToTalk(senderRole)
        }
    }

    @SuppressLint("MissingPermission")
    fun startPushToTalk(senderRole: String = "PHONE") {
        if (_isTransmitting.value) return
        _isTransmitting.value = true

        networkManager.sendMessage(
            LinklyMessage(
                action = "VOICE_PTT",
                senderRole = senderRole,
                payload = "START"
            )
        )

        recordJob = scope.launch {
            try {
                val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, ENCODING)
                val internalBufferSize = (minBufferSize * 2).coerceAtLeast(2048)
                // Small chunk size: 640 bytes = 20ms at 16000Hz 16-bit mono for instant real-time streaming
                val chunkBuffer = ByteArray(640)

                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_IN,
                    ENCODING,
                    internalBufferSize
                )

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord not initialized")
                    _isTransmitting.value = false
                    return@launch
                }

                audioRecord?.startRecording()

                while (isActive && _isTransmitting.value) {
                    val read = audioRecord?.read(chunkBuffer, 0, chunkBuffer.size) ?: 0
                    if (read > 0) {
                        var maxAmp = 0
                        var i = 0
                        while (i < read - 1) {
                            val sample = (chunkBuffer[i + 1].toInt() shl 8) or (chunkBuffer[i].toInt() and 0xFF)
                            val absSample = abs(sample.toShort().toInt())
                            if (absSample > maxAmp) maxAmp = absSample
                            i += 2
                        }
                        _currentAmplitude.value = (maxAmp / 32767f).coerceIn(0f, 1f)

                        val base64Chunk = Base64.encodeToString(chunkBuffer, 0, read, Base64.NO_WRAP)
                        networkManager.sendMessage(
                            LinklyMessage(
                                action = "VOICE_CHUNK",
                                senderRole = senderRole,
                                payload = base64Chunk
                            )
                        )
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Recording exception", e)
            } finally {
                cleanUpRecorder()
            }
        }
    }

    fun stopPushToTalk(senderRole: String = "PHONE") {
        _isTransmitting.value = false
        _currentAmplitude.value = 0f
        recordJob?.cancel()
        recordJob = null
        cleanUpRecorder()

        networkManager.sendMessage(
            LinklyMessage(
                action = "VOICE_PTT",
                senderRole = senderRole,
                payload = "STOP"
            )
        )
    }

    private fun cleanUpRecorder() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
    }

    fun handleIncomingAudioChunk(base64Payload: String) {
        if (base64Payload.isBlank()) return
        try {
            val pcmData = Base64.decode(base64Payload, Base64.NO_WRAP)
            playbackChannel.trySend(pcmData)
        } catch (e: Throwable) {
            Log.e(TAG, "Audio decode error", e)
        }
    }

    fun handlePttState(state: String) {
        val isStarting = (state == "START")
        _isReceiving.value = isStarting
        if (isStarting) {
            // Flush any stale audio chunks so speech starts immediately in real-time
            while (playbackChannel.tryReceive().isSuccess) {}
            try {
                audioTrack?.flush()
            } catch (_: Exception) {}
        }
    }

    fun release() {
        stopPushToTalk()
        try {
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
        } catch (_: Exception) {}
    }
}
