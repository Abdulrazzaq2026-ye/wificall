package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.HisnApp
import com.example.crypto.SafetyFingerprint
import com.example.data.local.CallLogEntity
import com.example.data.local.MessageEntity
import com.example.data.local.PeerEntity
import com.example.data.model.ActiveCallInfo
import com.example.data.prefs.UserSettings
import com.example.network.LocalNetworkInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HisnViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as HisnApp
    private val repository = app.repository
    private val settingsRepo = app.settingsRepository
    private val cryptoManager = app.cryptoManager
    private val discoveryManager = app.discoveryManager
    private val messagingEngine = app.messagingEngine
    private val voiceCallEngine = app.voiceCallEngine
    private val voiceNoteRecorder = app.voiceNoteRecorder

    val peers: StateFlow<List<PeerEntity>> = repository.peersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val callLogs: StateFlow<List<CallLogEntity>> = repository.callLogsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<UserSettings> = repository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserSettings())

    val localNetworkInfo: StateFlow<LocalNetworkInfo> = discoveryManager.localNetworkInfo
    val isMonitorServiceRunning: StateFlow<Boolean> = discoveryManager.isServiceRunning
    val monitorStatusMessage: StateFlow<String> = discoveryManager.monitorStatus
    val isScanning: StateFlow<Boolean> = discoveryManager.isScanning
    val nsdActive: StateFlow<Boolean> = discoveryManager.nsdActive
    val udpActive: StateFlow<Boolean> = discoveryManager.udpActive
    val typingPeers: StateFlow<Set<String>> = messagingEngine.typingPeers
    val activeCall: StateFlow<ActiveCallInfo> = voiceCallEngine.callInfo

    val isRecordingVoiceNote: StateFlow<Boolean> = voiceNoteRecorder.isRecording
    val recordingSeconds: StateFlow<Int> = voiceNoteRecorder.recordingSeconds
    val recordingLevel: StateFlow<Float> = voiceNoteRecorder.recordingLevel
    val playingVoiceMessageId: StateFlow<String?> = voiceNoteRecorder.playingMessageId
    val voicePlaybackProgress: StateFlow<Float> = voiceNoteRecorder.playbackProgress

    private val _isAppLocked = MutableStateFlow(false)
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    private val _pinError = MutableStateFlow(false)
    val pinError: StateFlow<Boolean> = _pinError.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val initSettings = settingsRepo.ensureInitialized(cryptoManager)
            if (initSettings.appLockPin.isNotBlank()) {
                _isAppLocked.value = true
            }
            val (tcpPort, udpPort) = messagingEngine.startServer(
                preferredTcpPort = initSettings.tcpPort,
                preferredUdpPort = initSettings.udpAudioPort
            )
            settingsRepo.updateRuntimePorts(tcpPort, udpPort)
            discoveryManager.start(tcpPort, udpPort)
        }
    }

    fun observePeer(peerId: String): Flow<PeerEntity?> = repository.observePeer(peerId)
    fun observeMessages(peerId: String): Flow<List<MessageEntity>> = repository.observeMessages(peerId)

    fun computeSafetyFingerprint(myPub: String, peerPub: String): SafetyFingerprint {
        return cryptoManager.computeSafetyFingerprint(myPub, peerPub)
    }

    fun getPublicKeyShortHex(pub: String): String = cryptoManager.computePublicKeyShortHex(pub)

    fun unlockWithPin(pin: String) {
        viewModelScope.launch {
            val currentPin = settingsRepo.settingsFlow.first().appLockPin
            if (pin == currentPin) {
                _isAppLocked.value = false
                _pinError.value = false
            } else {
                _pinError.value = true
            }
        }
    }

    fun triggerLanScan() {
        discoveryManager.triggerImmediateScan()
        _snackbarEvents.tryEmit("جاري مسح الشبكة المحلية لاكتشاف الأجهزة...")
    }

    fun connectManualPeer(ip: String, portText: String, onSuccess: (String) -> Unit) {
        val cleanIp = ip.trim()
        val port = portText.trim().toIntOrNull() ?: 45889
        if (cleanIp.isBlank()) {
            _snackbarEvents.tryEmit("يرجى إدخال عنوان IP صحيح")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val res = messagingEngine.connectManualPeer(cleanIp, port)
            res.onSuccess { peer ->
                _snackbarEvents.tryEmit("تم الاتصال وتبادل المفاتيح مع ${peer.displayName}")
                launch(Dispatchers.Main) { onSuccess(peer.deviceId) }
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "تعذر الاتصال بالجهاز")
            }
        }
    }

    fun startLoopbackSelfTest(onReady: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val peer = messagingEngine.enableLoopbackSelfTest()
            _snackbarEvents.tryEmit("تم تفعيل عقدة الاختبار المحلي المشفر (127.0.0.1)")
            launch(Dispatchers.Main) { onReady(peer.deviceId) }
        }
    }

    fun markChatRead(peerId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            messagingEngine.markChatRead(peerId)
        }
    }

    fun sendTextMessage(
        peerId: String,
        text: String,
        replyToId: String = "",
        replyPreview: String = "",
        disappearingSeconds: Int = settings.value.disappearingSeconds
    ) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            messagingEngine.sendTextMessage(
                peerId = peerId,
                text = trimmed,
                replyToId = replyToId,
                replyPreview = replyPreview,
                disappearingSeconds = disappearingSeconds
            )
        }
    }

    fun sendImageUri(peerId: String, uri: Uri, disappearingSeconds: Int = settings.value.disappearingSeconds) {
        viewModelScope.launch(Dispatchers.IO) {
            val meta = repository.readUriBytesWithMeta(uri)
            if (meta == null) {
                _snackbarEvents.tryEmit("تعذر قراءة الصورة المختارة")
                return@launch
            }
            val (name, _, bytes) = meta
            if (bytes.size > 8 * 1024 * 1024) {
                _snackbarEvents.tryEmit("حجم الصورة يتجاوز الحد المسموح (8 ميجابايت)")
                return@launch
            }
            messagingEngine.sendMediaMessage(peerId, "IMAGE", name, bytes, "", disappearingSeconds)
        }
    }

    fun sendFileUri(peerId: String, uri: Uri, disappearingSeconds: Int = settings.value.disappearingSeconds) {
        viewModelScope.launch(Dispatchers.IO) {
            val meta = repository.readUriBytesWithMeta(uri)
            if (meta == null) {
                _snackbarEvents.tryEmit("تعذر قراءة الملف المختار")
                return@launch
            }
            val (name, _, bytes) = meta
            if (bytes.size > 10 * 1024 * 1024) {
                _snackbarEvents.tryEmit("حجم الملف يتجاوز 10 ميجابايت")
                return@launch
            }
            messagingEngine.sendMediaMessage(peerId, "FILE", name, bytes, "", disappearingSeconds)
        }
    }

    fun startVoiceNoteRecording() {
        voiceNoteRecorder.startRecording()
    }

    fun cancelVoiceNoteRecording() {
        voiceNoteRecorder.cancelRecording()
    }

    fun stopAndSendVoiceNote(peerId: String, disappearingSeconds: Int = settings.value.disappearingSeconds) {
        viewModelScope.launch(Dispatchers.IO) {
            val res = voiceNoteRecorder.stopAndBuildWav() ?: return@launch
            val (wavBytes, duration) = res
            val fileName = "voice_${System.currentTimeMillis()}.wav"
            messagingEngine.sendMediaMessage(
                peerId = peerId,
                mediaType = "VOICE_NOTE",
                fileName = fileName,
                bytes = wavBytes,
                caption = "${duration} ثانية",
                disappearingSeconds = disappearingSeconds
            )
        }
    }

    fun toggleVoiceNotePlayback(messageId: String, localPath: String) {
        voiceNoteRecorder.togglePlayback(messageId, localPath)
    }

    fun notifyTyping(peerId: String, isTyping: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            messagingEngine.sendTypingIndicator(peerId, isTyping)
        }
    }

    fun reactToMessage(peerId: String, messageId: String, emoji: String) {
        viewModelScope.launch(Dispatchers.IO) {
            messagingEngine.sendReaction(peerId, messageId, emoji)
        }
    }

    fun deleteMessageLocal(messageId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteMessageLocal(messageId)
        }
    }

    fun deleteMessageForEveryone(peerId: String, messageId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            messagingEngine.deleteMessageForEveryone(peerId, messageId)
        }
    }

    fun clearChatHistory(peerId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearChatHistory(peerId)
            _snackbarEvents.tryEmit("تم مسح سجل المحادثة محلياً")
        }
    }

    fun toggleVerifyPeer(peerId: String, verified: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.setPeerVerified(peerId, verified)
            _snackbarEvents.tryEmit(if (verified) "تم توثيق هوية الجهاز" else "تم إلغاء التوثيق")
        }
    }

    fun togglePinPeer(peerId: String, pinned: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.setPeerPinned(peerId, pinned)
        }
    }

    fun toggleBlockPeer(peerId: String, blocked: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.setPeerBlocked(peerId, blocked)
            _snackbarEvents.tryEmit(if (blocked) "تم حظر الجهاز" else "تم إلغاء حظر الجهاز")
        }
    }

    fun deletePeerAndChat(peerId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deletePeerAndChat(peerId)
        }
    }

    fun startVoiceCall(peer: PeerEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            messagingEngine.initiateVoiceCall(peer)
        }
    }

    fun acceptCall() = voiceCallEngine.acceptIncomingCall()
    fun rejectCall() = voiceCallEngine.rejectIncomingCall()
    fun rejectCallWithQuickMessage(text: String) {
        val peerId = activeCall.value.peerId
        voiceCallEngine.rejectIncomingCall()
        if (peerId.isNotBlank() && text.isNotBlank()) {
            sendTextMessage(peerId, text)
        }
    }
    fun endCall() = voiceCallEngine.endCall()
    fun toggleMute() = voiceCallEngine.toggleMute()
    fun toggleSpeaker() = voiceCallEngine.toggleSpeaker()
    fun toggleHold() = voiceCallEngine.toggleHold()

    fun deleteCallLog(id: String) {
        viewModelScope.launch(Dispatchers.IO) { repository.deleteCallLog(id) }
    }

    fun clearAllCallLogs() {
        viewModelScope.launch(Dispatchers.IO) { repository.clearAllCallLogs() }
    }

    fun login(name: String, userCode: String, avatarIndex: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.login(name, userCode, avatarIndex)
            discoveryManager.triggerImmediateScan()
            _snackbarEvents.tryEmit("أهلاً بك يا ${name.trim()} في الشبكة المحلية المشفرة")
        }
    }

    fun logout() {
        viewModelScope.launch(Dispatchers.IO) {
            voiceCallEngine.endCall(sendSignal = true)
            settingsRepo.logout()
        }
    }

    fun shutdownForExit() {
        voiceNoteRecorder.stopPlayback()
        voiceNoteRecorder.cancelRecording()
        voiceCallEngine.endCall(sendSignal = true)
        discoveryManager.stop()
        messagingEngine.stopServer()
    }

    fun updateProfile(name: String, userCode: String, status: String, avatarIndex: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.updateProfile(name, userCode, status, avatarIndex)
            discoveryManager.triggerImmediateScan()
            _snackbarEvents.tryEmit("تم تحديث ملف التعريف")
        }
    }

    fun rotateIdentityKeys() {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.rotateIdentityKeys(cryptoManager)
            messagingEngine.rotateKeysAndNotifyPeers(peers.value)
            discoveryManager.triggerImmediateScan()
            _snackbarEvents.tryEmit("تم توليد زوج مفاتيح ECDH P-256 جديد وتحديث الجلسات")
        }
    }

    fun setDisappearingSeconds(seconds: Int) {
        viewModelScope.launch(Dispatchers.IO) { settingsRepo.setDisappearingSeconds(seconds) }
    }

    fun setDarkTheme(dark: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { settingsRepo.setDarkTheme(dark) }
    }

    fun setStealthMode(stealth: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.setStealthMode(stealth)
            discoveryManager.updateDiscoveryConfig(settings.value.nsdEnabled, stealth)
        }
    }

    fun setNsdEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.setNsdEnabled(enabled)
            discoveryManager.updateDiscoveryConfig(enabled, settings.value.stealthMode)
        }
    }

    fun setUdpBroadcastEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { settingsRepo.setUdpBroadcastEnabled(enabled) }
    }

    fun setAutoAcceptVerifiedCalls(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { settingsRepo.setAutoAcceptVerifiedCalls(enabled) }
    }

    fun setSoundEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { settingsRepo.setSoundEnabled(enabled) }
    }

    fun setVibrationEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { settingsRepo.setVibrationEnabled(enabled) }
    }

    fun setAppLockPin(pin: String) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.setAppLockPin(pin)
            _snackbarEvents.tryEmit(if (pin.isBlank()) "تم تعطيل رمز قفل التطبيق" else "تم تفعيل رمز قفل التطبيق")
        }
    }

    fun panicWipeAllData() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.panicWipeAllData()
            _snackbarEvents.tryEmit("تم إتلاف جميع الرسائل والمفاتيح وسجلات المكالمات نهائياً")
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceNoteRecorder.stopPlayback()
        voiceNoteRecorder.cancelRecording()
    }
}
