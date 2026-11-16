package com.example.media

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Lightweight 8kHz mono Mu-law voice engine using standard Android AudioRecord & AudioTrack.
 * Compresses 16-bit PCM down to 8-bit G.711 Mu-law (~4.3 KB per 400ms chunk in Base64)
 * for live mic-to-mic voice exchange over Firestore.
 */
class MicAudioEngine {

    private val _micLevel = MutableStateFlow(0f)
    val micLevel: StateFlow<Float> = _micLevel.asStateFlow()

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    private var recordJob: Job? = null
    private var audioTrack: AudioTrack? = null
    private val playedChunkIds = LinkedHashSet<String>()

    @SuppressLint("MissingPermission")
    fun startMicCapture(
        scope: CoroutineScope,
        onChunkCaptured: suspend (base64MuLaw: String, durationMs: Long, seq: Long) -> Unit
    ) {
        if (recordJob?.isActive == true) return
        _isCapturing.value = true
        recordJob = scope.launch(Dispatchers.IO) {
            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(CHUNK_SAMPLES * 2)

            val recorder = try {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf * 2
                )
            } catch (e: Exception) {
                Log.w(TAG, "Unable to open AudioRecord", e)
                _isCapturing.value = false
                return@launch
            }

            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                _isCapturing.value = false
                return@launch
            }

            val pcmBuffer = ShortArray(CHUNK_SAMPLES)
            var sequence = 0L
            try {
                recorder.startRecording()
                while (isActive) {
                    var readTotal = 0
                    while (readTotal < CHUNK_SAMPLES && isActive) {
                        val read = recorder.read(pcmBuffer, readTotal, CHUNK_SAMPLES - readTotal)
                        if (read <= 0) break
                        readTotal += read
                    }
                    if (!isActive || readTotal <= 0) break

                    var sumSq = 0.0
                    for (i in 0 until readTotal) {
                        val s = pcmBuffer[i].toDouble()
                        sumSq += s * s
                    }
                    val rms = sqrt(sumSq / readTotal)
                    val normalized = (rms / 6000.0).toFloat().coerceIn(0f, 1f)
                    _micLevel.value = normalized

                    // Send voice chunk when mic signal exceeds quiet noise floor (or periodic keepalive)
                    if (normalized > 0.02f || sequence % 5L == 0L) {
                        val encoded = encodeMuLaw(pcmBuffer, readTotal)
                        val b64 = Base64.encodeToString(encoded, Base64.NO_WRAP)
                        val durationMs = ((readTotal * 1000L) / SAMPLE_RATE).coerceAtLeast(50L)
                        runCatching { onChunkCaptured(b64, durationMs, sequence) }
                    }
                    sequence++
                }
            } catch (e: Exception) {
                Log.w(TAG, "Mic capture loop ended", e)
            } finally {
                _micLevel.value = 0f
                _isCapturing.value = false
                runCatching { recorder.stop() }
                runCatching { recorder.release() }
            }
        }
    }

    fun stopMicCapture() {
        recordJob?.cancel()
        recordJob = null
        _isCapturing.value = false
        _micLevel.value = 0f
    }

    fun playIncomingChunk(chunkId: String, audioBase64: String) {
        if (chunkId.isBlank() || audioBase64.isBlank()) return
        synchronized(playedChunkIds) {
            if (!playedChunkIds.add(chunkId)) return
            if (playedChunkIds.size > 200) {
                val first = playedChunkIds.firstOrNull()
                if (first != null) playedChunkIds.remove(first)
            }
        }
        try {
            val muLawBytes = Base64.decode(audioBase64, Base64.DEFAULT)
            val pcmSamples = decodeMuLaw(muLawBytes)
            val track = ensureAudioTrack()
            if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                track.play()
            }
            track.write(pcmSamples, 0, pcmSamples.size)
        } catch (e: Exception) {
            Log.w(TAG, "Failed playing incoming voice chunk", e)
        }
    }

    fun release() {
        stopMicCapture()
        runCatching {
            audioTrack?.stop()
            audioTrack?.release()
        }
        audioTrack = null
        synchronized(playedChunkIds) { playedChunkIds.clear() }
    }

    private fun ensureAudioTrack(): AudioTrack {
        audioTrack?.let { return it }
        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(CHUNK_SAMPLES * 2)
        val created = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setSessionId(AudioManager.AUDIO_SESSION_ID_GENERATE)
            .build()
        audioTrack = created
        return created
    }

    private fun encodeMuLaw(samples: ShortArray, count: Int): ByteArray {
        val out = ByteArray(count)
        for (i in 0 until count) {
            out[i] = linearToMuLaw(samples[i].toInt())
        }
        return out
    }

    private fun decodeMuLaw(bytes: ByteArray): ShortArray {
        val out = ShortArray(bytes.size)
        for (i in bytes.indices) {
            out[i] = muLawToLinear(bytes[i])
        }
        return out
    }

    private fun linearToMuLaw(pcmVal: Int): Byte {
        var sample = pcmVal
        val sign = if (sample < 0) {
            sample = -sample
            0x80
        } else {
            0x00
        }
        if (sample > 32635) sample = 32635
        sample += 0x84
        var exponent = 7
        var expMask = 0x4000
        while ((sample and expMask) == 0 && exponent > 0) {
            exponent--
            expMask = expMask shr 1
        }
        val mantissa = (sample shr (exponent + 3)) and 0x0F
        return ((sign or (exponent shl 4) or mantissa).inv() and 0xFF).toByte()
    }

    private fun muLawToLinear(muLawByte: Byte): Short {
        val b = muLawByte.toInt().inv() and 0xFF
        val sign = b and 0x80
        val exponent = (b shr 4) and 0x07
        val mantissa = b and 0x0F
        var sample = ((mantissa shl 3) + 0x84) shl exponent
        sample -= 0x84
        return (if (sign != 0) -sample else sample).toShort()
    }

    companion object {
        private const val TAG = "MicAudioEngine"
        const val SAMPLE_RATE = 8000
        private const val CHUNK_SAMPLES = 3200 // 400ms at 8kHz
    }
}
