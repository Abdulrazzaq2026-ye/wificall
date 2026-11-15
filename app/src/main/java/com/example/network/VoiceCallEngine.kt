package com.example.network

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.example.crypto.CryptoManager
import com.example.data.model.ActiveCallInfo
import com.example.data.model.CallDirection
import com.example.data.model.CallStatus
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
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import kotlin.math.abs

class VoiceCallEngine(
    private val context: Context,
    private val cryptoManager: CryptoManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _callInfo = MutableStateFlow(ActiveCallInfo())
    val callInfo: StateFlow<ActiveCallInfo> = _callInfo.asStateFlow()

    var onSendCallSignal: (suspend (peerId: String, signalType: String, callId: String, flag: Boolean) -> Unit)? = null
    var onRecordCallLog: (suspend (peerId: String, peerName: String, peerIp: String, avatarIndex: Int, direction: CallDirection, durationSeconds: Int) -> Unit)? = null
    var onBusyStateChanged: ((Boolean) -> Unit)? = null

    fun isBusy(): Boolean = _callInfo.value.status != CallStatus.IDLE

    private var udpSocket: DatagramSocket? = null
    private var boundUdpPort: Int = 45890
    private var streamingJob: Job? = null
    private var timerJob: Job? = null
    private var ringerJob: Job? = null
    private var callAesKey: ByteArray? = null
    private var isLoopbackCall: Boolean = false
    private var loopbackEchoSocket: DatagramSocket? = null

    fun startUdpAudioListener(preferredPort: Int): Int {
        runCatching { udpSocket?.close() }
        val socket = runCatching {
            DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(preferredPort))
            }
        }.getOrElse {
            DatagramSocket(0)
        }
        udpSocket = socket
        boundUdpPort = socket.localPort
        return boundUdpPort
    }

    fun getBoundUdpPort(): Int = boundUdpPort

    fun startOutgoingCall(
        callId: String,
        peerId: String,
        peerName: String,
        peerIp: String,
        peerTcpPort: Int,
        peerUdpPort: Int,
        peerAvatarIndex: Int,
        sharedKey: ByteArray,
        safetyEmojis: List<String>,
        safetyShortHex: String,
        isLoopback: Boolean,
        playRingSound: Boolean
    ) {
        if (_callInfo.value.status != CallStatus.IDLE) return
        callAesKey = cryptoManager.deriveCallKey(sharedKey, callId)
        isLoopbackCall = isLoopback

        _callInfo.value = ActiveCallInfo(
            callId = callId,
            peerId = peerId,
            peerName = peerName,
            peerIp = peerIp,
            peerTcpPort = peerTcpPort,
            peerUdpPort = peerUdpPort,
            peerAvatarIndex = peerAvatarIndex,
            safetyEmojis = safetyEmojis,
            safetyShortHex = safetyShortHex,
            status = CallStatus.OUTGOING_RINGING,
            isOutgoing = true
        )
        onBusyStateChanged?.invoke(true)

        if (playRingSound) {
            startRinger(isIncoming = false, vibrate = false)
        }
    }

    fun onIncomingCallInvite(
        callId: String,
        peerId: String,
        peerName: String,
        peerIp: String,
        peerTcpPort: Int,
        peerUdpPort: Int,
        peerAvatarIndex: Int,
        sharedKey: ByteArray,
        safetyEmojis: List<String>,
        safetyShortHex: String,
        playSound: Boolean,
        vibrate: Boolean
    ) {
        if (_callInfo.value.status != CallStatus.IDLE) return
        callAesKey = cryptoManager.deriveCallKey(sharedKey, callId)
        isLoopbackCall = false

        _callInfo.value = ActiveCallInfo(
            callId = callId,
            peerId = peerId,
            peerName = peerName,
            peerIp = peerIp,
            peerTcpPort = peerTcpPort,
            peerUdpPort = peerUdpPort,
            peerAvatarIndex = peerAvatarIndex,
            safetyEmojis = safetyEmojis,
            safetyShortHex = safetyShortHex,
            status = CallStatus.INCOMING_RINGING,
            isOutgoing = false
        )
        onBusyStateChanged?.invoke(true)

        if (playSound || vibrate) {
            startRinger(isIncoming = true, vibrate = vibrate)
        }
    }

    fun acceptIncomingCall() {
        val current = _callInfo.value
        if (current.status != CallStatus.INCOMING_RINGING) return
        stopRinger()
        _callInfo.value = current.copy(status = CallStatus.ACTIVE)
        scope.launch {
            onSendCallSignal?.invoke(current.peerId, "CALL_ACCEPT", current.callId, false)
        }
        startEncryptedMediaStreaming()
    }

    fun onRemoteCallAccepted(callId: String, remoteUdpPort: Int) {
        val current = _callInfo.value
        if (current.callId != callId || current.status != CallStatus.OUTGOING_RINGING) return
        stopRinger()
        val updatedPort = if (remoteUdpPort > 0) remoteUdpPort else current.peerUdpPort
        _callInfo.value = current.copy(
            status = CallStatus.ACTIVE,
            peerUdpPort = updatedPort
        )
        startEncryptedMediaStreaming()
    }

    fun rejectIncomingCall() {
        val current = _callInfo.value
        if (current.status != CallStatus.INCOMING_RINGING) return
        stopRinger()
        scope.launch {
            onSendCallSignal?.invoke(current.peerId, "CALL_REJECT", current.callId, false)
            onRecordCallLog?.invoke(
                current.peerId,
                current.peerName,
                current.peerIp,
                current.peerAvatarIndex,
                CallDirection.MISSED,
                0
            )
        }
        finishCall("تم رفض المكالمة")
    }

    fun endCall(sendSignal: Boolean = true, reason: String = "انتهت المكالمة") {
        val current = _callInfo.value
        if (current.status == CallStatus.IDLE) return
        stopRinger()
        stopMediaStreaming()

        val direction = when {
            current.isOutgoing -> CallDirection.OUTGOING
            current.durationSeconds > 0 -> CallDirection.INCOMING
            else -> CallDirection.MISSED
        }

        if (sendSignal) {
            scope.launch {
                onSendCallSignal?.invoke(current.peerId, "CALL_END", current.callId, false)
            }
        }
        scope.launch {
            onRecordCallLog?.invoke(
                current.peerId,
                current.peerName,
                current.peerIp,
                current.peerAvatarIndex,
                direction,
                current.durationSeconds
            )
        }
        finishCall(reason)
    }

    fun onRemoteCallSignal(callId: String, signalType: String, flag: Boolean) {
        val current = _callInfo.value
        if (current.callId != callId && current.status == CallStatus.IDLE) return
        when (signalType) {
            "CALL_REJECT" -> {
                if (current.callId == callId) endCall(sendSignal = false, reason = "رفض الطرف الآخر المكالمة")
            }
            "CALL_BUSY" -> {
                if (current.callId == callId) {
                    playBusyTone()
                    endCall(sendSignal = false, reason = "المستخدم مشغول في مكالمة أخرى")
                }
            }
            "CALL_END" -> {
                if (current.callId == callId) endCall(sendSignal = false, reason = "أنهى الطرف الآخر المكالمة")
            }
            "CALL_MUTE" -> {
                if (current.callId == callId) {
                    _callInfo.value = current.copy(isRemoteMuted = flag)
                }
            }
            "CALL_HOLD" -> {
                if (current.callId == callId) {
                    _callInfo.value = current.copy(isRemoteOnHold = flag)
                }
            }
        }
    }

    fun toggleMute() {
        val current = _callInfo.value
        val next = !current.isMuted
        _callInfo.value = current.copy(isMuted = next)
        scope.launch {
            onSendCallSignal?.invoke(current.peerId, "CALL_MUTE", current.callId, next)
        }
    }

    fun toggleSpeaker() {
        val current = _callInfo.value
        val next = !current.isSpeakerOn
        _callInfo.value = current.copy(isSpeakerOn = next)
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager?.apply {
            mode = AudioManager.MODE_IN_COMMUNICATION
            @Suppress("DEPRECATION")
            isSpeakerphoneOn = next
        }
    }

    fun toggleHold() {
        val current = _callInfo.value
        if (current.status != CallStatus.ACTIVE && current.status != CallStatus.ON_HOLD) return
        val nextStatus = if (current.status == CallStatus.ON_HOLD) CallStatus.ACTIVE else CallStatus.ON_HOLD
        _callInfo.value = current.copy(status = nextStatus)
        scope.launch {
            onSendCallSignal?.invoke(current.peerId, "CALL_HOLD", current.callId, nextStatus == CallStatus.ON_HOLD)
        }
    }

    private fun finishCall(reason: String) {
        stopRinger()
        stopMediaStreaming()
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager?.apply {
            @Suppress("DEPRECATION")
            isSpeakerphoneOn = false
            mode = AudioManager.MODE_NORMAL
        }
        val prev = _callInfo.value
        _callInfo.value = prev.copy(status = CallStatus.ENDED, endReason = reason)
        onBusyStateChanged?.invoke(false)
        scope.launch {
            delay(1800L)
            if (_callInfo.value.status == CallStatus.ENDED) {
                _callInfo.value = ActiveCallInfo()
                callAesKey = null
            }
        }
    }

    private fun playBusyTone() {
        scope.launch {
            val toneGen = runCatching { ToneGenerator(AudioManager.STREAM_VOICE_CALL, 75) }.getOrNull()
            runCatching { toneGen?.startTone(ToneGenerator.TONE_SUP_BUSY, 1400) }
            delay(1450L)
            runCatching { toneGen?.release() }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startEncryptedMediaStreaming() {
        stopMediaStreaming()

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager?.apply {
            mode = AudioManager.MODE_IN_COMMUNICATION
            @Suppress("DEPRECATION")
            isSpeakerphoneOn = _callInfo.value.isSpeakerOn
        }

        timerJob = scope.launch {
            while (isActive && (_callInfo.value.status == CallStatus.ACTIVE || _callInfo.value.status == CallStatus.ON_HOLD)) {
                delay(1000L)
                _callInfo.value = _callInfo.value.copy(durationSeconds = _callInfo.value.durationSeconds + 1)
            }
        }

        streamingJob = scope.launch {
            val sampleRate = 16000
            val frameSamples = 320 // 20ms at 16kHz
            val frameBytes = frameSamples * 2 // 640 bytes PCM-16

            val minRecBuf = AudioRecord.getMinBufferSize(
                sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(frameBytes * 4)

            val minPlayBuf = AudioTrack.getMinBufferSize(
                sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(frameBytes * 4)

            val recorder = runCatching {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minRecBuf
                )
            }.getOrNull()

            var aec: AcousticEchoCanceler? = null
            var ns: NoiseSuppressor? = null
            if (recorder != null && recorder.state == AudioRecord.STATE_INITIALIZED) {
                if (AcousticEchoCanceler.isAvailable()) {
                    aec = runCatching { AcousticEchoCanceler.create(recorder.audioSessionId)?.apply { enabled = true } }.getOrNull()
                }
                if (NoiseSuppressor.isAvailable()) {
                    ns = runCatching { NoiseSuppressor.create(recorder.audioSessionId)?.apply { enabled = true } }.getOrNull()
                }
            }

            val track = runCatching {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .build()
                    )
                    .setBufferSizeInBytes(minPlayBuf)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            }.getOrNull()

            val socket = udpSocket ?: startUdpAudioListener(boundUdpPort).let { udpSocket }
            val key = callAesKey ?: return@launch

            if (isLoopbackCall) {
                // Dedicated localhost echo socket so packets travel real UDP + AES-256-GCM
                loopbackEchoSocket = runCatching { DatagramSocket(0) }.getOrNull()
            }

            // Receiver job
            val rxJob = launch {
                val recvSocket = socket ?: return@launch
                runCatching { track?.play() }
                val rxBuf = ByteArray(2048)
                while (isActive && (_callInfo.value.status == CallStatus.ACTIVE || _callInfo.value.status == CallStatus.ON_HOLD)) {
                    val packet = DatagramPacket(rxBuf, rxBuf.size)
                    runCatching {
                        recvSocket.receive(packet)
                        if (packet.length <= 36) return@runCatching
                        val raw = packet.data.copyOfRange(0, packet.length)
                        val seq = ByteBuffer.wrap(raw, 0, 8).long
                        val encFrame = raw.copyOfRange(8, raw.size)
                        val aad = ByteBuffer.allocate(8).putLong(seq).array()
                        val pcm = cryptoManager.decryptBytes(encFrame, key, aad)

                        val level = computeRms(pcm, pcm.size)
                        _callInfo.value = _callInfo.value.copy(
                            packetsReceived = _callInfo.value.packetsReceived + 1,
                            remoteAudioLevel = level
                        )

                        if (_callInfo.value.status == CallStatus.ACTIVE && !_callInfo.value.isRemoteOnHold && !isLoopbackCall) {
                            track?.write(pcm, 0, pcm.size)
                        }
                    }.onFailure {
                        delay(10L)
                    }
                }
            }

            // Transmitter job
            val txJob = launch {
                val sendSocket = if (isLoopbackCall) loopbackEchoSocket else socket
                val targetAddress = runCatching { InetAddress.getByName(_callInfo.value.peerIp) }.getOrNull()
                val targetPort = if (isLoopbackCall) boundUdpPort else _callInfo.value.peerUdpPort
                val pcmBuf = ByteArray(frameBytes)
                var seq = 1L

                runCatching { recorder?.startRecording() }

                while (isActive && (_callInfo.value.status == CallStatus.ACTIVE || _callInfo.value.status == CallStatus.ON_HOLD)) {
                    val read = if (recorder != null && recorder.state == AudioRecord.STATE_INITIALIZED) {
                        recorder.read(pcmBuf, 0, frameBytes)
                    } else {
                        delay(20L)
                        frameBytes // Fallback silence frame on virtual devices without mic hardware
                    }

                    if (read > 0) {
                        val current = _callInfo.value
                        val mutedOrHold = current.isMuted || current.status == CallStatus.ON_HOLD
                        val level = if (mutedOrHold) 0f else computeRms(pcmBuf, read)
                        val frameToSend = if (mutedOrHold) ByteArray(read) else pcmBuf.copyOf(read)

                        val aad = ByteBuffer.allocate(8).putLong(seq).array()
                        val encrypted = cryptoManager.encryptBytes(frameToSend, key, aad)
                        val packetBytes = ByteBuffer.allocate(8 + encrypted.size)
                            .putLong(seq)
                            .put(encrypted)
                            .array()

                        if (sendSocket != null && targetAddress != null && targetPort > 0) {
                            runCatching {
                                sendSocket.send(DatagramPacket(packetBytes, packetBytes.size, targetAddress, targetPort))
                            }
                        }

                        _callInfo.value = _callInfo.value.copy(
                            packetsSent = current.packetsSent + 1,
                            localAudioLevel = level
                        )
                        seq++
                    }
                }
            }

            txJob.join()
            rxJob.cancel()

            runCatching { recorder?.stop() }
            runCatching { recorder?.release() }
            runCatching { aec?.release() }
            runCatching { ns?.release() }
            runCatching { track?.stop() }
            runCatching { track?.release() }
            runCatching { loopbackEchoSocket?.close() }
            loopbackEchoSocket = null
        }
    }

    private fun stopMediaStreaming() {
        timerJob?.cancel()
        timerJob = null
        streamingJob?.cancel()
        streamingJob = null
        runCatching { loopbackEchoSocket?.close() }
        loopbackEchoSocket = null
    }

    private fun startRinger(isIncoming: Boolean, vibrate: Boolean) {
        stopRinger()
        ringerJob = scope.launch {
            val toneGen = runCatching { ToneGenerator(AudioManager.STREAM_RING, 65) }.getOrNull()
            val vibrator = getVibrator()
            while (isActive && (_callInfo.value.status == CallStatus.INCOMING_RINGING || _callInfo.value.status == CallStatus.OUTGOING_RINGING)) {
                if (isIncoming) {
                    runCatching { toneGen?.startTone(ToneGenerator.TONE_SUP_RINGTONE, 900) }
                    if (vibrate) vibratePulse(vibrator)
                    delay(2000L)
                } else {
                    runCatching { toneGen?.startTone(ToneGenerator.TONE_SUP_DIAL, 600) }
                    delay(2200L)
                }
            }
            runCatching { toneGen?.release() }
        }
    }

    private fun stopRinger() {
        ringerJob?.cancel()
        ringerJob = null
    }

    private fun getVibrator(): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private fun vibratePulse(vibrator: Vibrator?) {
        if (vibrator == null || !vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(400L, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(400L)
        }
    }

    private fun computeRms(bytes: ByteArray, length: Int): Float {
        var maxSample = 0
        var i = 0
        while (i + 1 < length) {
            val sample = (bytes[i].toInt() and 0xFF) or (bytes[i + 1].toInt() shl 8)
            val absVal = abs(sample.toShort().toInt())
            if (absVal > maxSample) maxSample = absVal
            i += 2
        }
        return (maxSample / 10000f).coerceIn(0f, 1f)
    }
}
