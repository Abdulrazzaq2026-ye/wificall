package com.example.network

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class VoiceNoteRecorder(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingSeconds = MutableStateFlow(0)
    val recordingSeconds: StateFlow<Int> = _recordingSeconds.asStateFlow()

    private val _recordingLevel = MutableStateFlow(0f)
    val recordingLevel: StateFlow<Float> = _recordingLevel.asStateFlow()

    private val _playingMessageId = MutableStateFlow<String?>(null)
    val playingMessageId: StateFlow<String?> = _playingMessageId.asStateFlow()

    private val _playbackProgress = MutableStateFlow(0f)
    val playbackProgress: StateFlow<Float> = _playbackProgress.asStateFlow()

    private var recordJob: Job? = null
    private var timerJob: Job? = null
    private var pcmBuffer = ByteArrayOutputStream()
    private var mediaPlayer: MediaPlayer? = null
    private var playbackJob: Job? = null

    @SuppressLint("MissingPermission")
    fun startRecording() {
        if (_isRecording.value) return
        stopPlayback()
        pcmBuffer = ByteArrayOutputStream()
        _recordingSeconds.value = 0
        _isRecording.value = true

        timerJob = scope.launch {
            while (_isRecording.value) {
                delay(1000L)
                if (_isRecording.value) {
                    _recordingSeconds.value += 1
                    if (_recordingSeconds.value >= 60) {
                        // Cap voice note at 60s for clean LAN transfer
                        break
                    }
                }
            }
        }

        recordJob = scope.launch {
            val sampleRate = 16000
            val minBuf = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(2048)

            val recorder = runCatching {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf
                )
            }.getOrNull()

            if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
                _isRecording.value = false
                return@launch
            }

            val buf = ByteArray(minBuf)
            runCatching {
                recorder.startRecording()
                while (isActive && _isRecording.value) {
                    val read = recorder.read(buf, 0, buf.size)
                    if (read > 0) {
                        pcmBuffer.write(buf, 0, read)
                        _recordingLevel.value = calculateRmsLevel(buf, read)
                    }
                }
                recorder.stop()
            }
            runCatching { recorder.release() }
        }
    }

    fun stopAndBuildWav(): Pair<ByteArray, Int>? {
        if (!_isRecording.value) return null
        val duration = _recordingSeconds.value.coerceAtLeast(1)
        _isRecording.value = false
        recordJob?.cancel()
        timerJob?.cancel()
        _recordingLevel.value = 0f

        val pcm = pcmBuffer.toByteArray()
        if (pcm.size < 640) return null
        return pcmToWav(pcm, 16000, 1, 16) to duration
    }

    fun cancelRecording() {
        _isRecording.value = false
        recordJob?.cancel()
        timerJob?.cancel()
        _recordingLevel.value = 0f
        _recordingSeconds.value = 0
        pcmBuffer.reset()
    }

    fun togglePlayback(messageId: String, filePath: String) {
        if (_playingMessageId.value == messageId) {
            stopPlayback()
            return
        }
        stopPlayback()
        val file = File(filePath)
        if (!file.exists()) return

        runCatching {
            val player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener {
                    stopPlayback()
                }
                start()
            }
            mediaPlayer = player
            _playingMessageId.value = messageId
            playbackJob = scope.launch {
                while (isActive && _playingMessageId.value == messageId) {
                    val pos = runCatching { player.currentPosition }.getOrDefault(0)
                    val dur = runCatching { player.duration }.getOrDefault(1).coerceAtLeast(1)
                    _playbackProgress.value = (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                    delay(80L)
                }
            }
        }
    }

    fun stopPlayback() {
        playbackJob?.cancel()
        runCatching {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        }
        mediaPlayer = null
        _playingMessageId.value = null
        _playbackProgress.value = 0f
    }

    private fun calculateRmsLevel(bytes: ByteArray, length: Int): Float {
        var maxSample = 0
        var i = 0
        while (i + 1 < length) {
            val sample = (bytes[i].toInt() and 0xFF) or (bytes[i + 1].toInt() shl 8)
            val absVal = abs(sample.toShort().toInt())
            if (absVal > maxSample) maxSample = absVal
            i += 2
        }
        return (maxSample / 12000f).coerceIn(0f, 1f)
    }

    private fun pcmToWav(pcm: ByteArray, sampleRate: Int, channels: Int, bitsPerSample: Int): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray())
        header.putInt(36 + pcm.size)
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16)
        header.putShort(1.toShort())
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort((channels * bitsPerSample / 8).toShort())
        header.putShort(bitsPerSample.toShort())
        header.put("data".toByteArray())
        header.putInt(pcm.size)

        val out = ByteArrayOutputStream(44 + pcm.size)
        out.write(header.array())
        out.write(pcm)
        return out.toByteArray()
    }
}
