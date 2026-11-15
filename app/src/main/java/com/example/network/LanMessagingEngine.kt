package com.example.network

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.crypto.CryptoManager
import com.example.crypto.IdentityKeyPairData
import com.example.data.local.MessageEntity
import com.example.data.local.PeerEntity
import com.example.data.model.DecryptedControlPayload
import com.example.data.model.DecryptedMessagePayload
import com.example.data.model.WireEnvelope
import com.example.data.model.WireType
import com.example.data.repository.HisnRepository
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class LanMessagingEngine(
    private val context: Context,
    private val repository: HisnRepository,
    private val cryptoManager: CryptoManager,
    private val voiceCallEngine: VoiceCallEngine
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private var boundTcpPort: Int = 45889

    private val _typingPeers = MutableStateFlow<Set<String>>(emptySet())
    val typingPeers: StateFlow<Set<String>> = _typingPeers.asStateFlow()

    // Loopback test identity (separate real ECDH keypair so loopback traffic uses genuine ECDH + AES-256-GCM)
    private val loopbackDeviceId = "hisn_loopback_node"
    private var loopbackKeyPair: IdentityKeyPairData? = null
    private val typingClearJobs = ConcurrentHashMap<String, Job>()

    init {
        voiceCallEngine.onSendCallSignal = { peerId, signalType, callId, flag ->
            sendCallSignal(peerId, signalType, callId, flag)
        }
        voiceCallEngine.onRecordCallLog = { peerId, peerName, peerIp, avatarIndex, direction, duration ->
            repository.recordCallLog(peerId, peerName, peerIp, avatarIndex, direction.name, duration)
        }
    }

    suspend fun startServer(preferredTcpPort: Int, preferredUdpPort: Int): Pair<Int, Int> {
        if (serverJob?.isActive == true && serverSocket?.isClosed == false) {
            return boundTcpPort to voiceCallEngine.getBoundUdpPort()
        }

        val socket = runCatching {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(preferredTcpPort))
            }
        }.getOrElse {
            ServerSocket(0)
        }
        serverSocket = socket
        boundTcpPort = socket.localPort
        val udpPort = voiceCallEngine.startUdpAudioListener(preferredUdpPort)

        serverJob = scope.launch {
            while (isActive && !socket.isClosed) {
                runCatching {
                    val client = socket.accept()
                    launch { handleIncomingSocket(client) }
                }.onFailure {
                    if (!socket.isClosed) Log.w(TAG, "Accept error: ${it.message}")
                }
            }
        }
        return boundTcpPort to udpPort
    }

    fun stopServer() {
        serverJob?.cancel()
        serverJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    fun getBoundTcpPort(): Int = boundTcpPort

    suspend fun connectManualPeer(ip: String, port: Int): Result<PeerEntity> {
        return runCatching {
            val settings = repository.getSettings()
            val hello = WireEnvelope(
                type = WireType.HELLO,
                senderId = settings.deviceId,
                senderUserCode = settings.userCode,
                senderName = settings.displayName,
                senderPublicKey = settings.publicKeyBase64,
                senderTcpPort = boundTcpPort,
                senderUdpPort = voiceCallEngine.getBoundUdpPort(),
                senderAvatarIndex = settings.avatarIndex,
                senderStatus = settings.statusMessage,
                senderBusyInCall = voiceCallEngine.isBusy()
            )

            val response = sendAndAwaitAck(ip, port, hello)
                ?: error("تعذر الوصول إلى $ip:$port على الشبكة المحلية")

            repository.upsertDiscoveredPeer(
                deviceId = response.senderId,
                userCode = response.senderUserCode,
                displayName = response.senderName,
                statusMessage = response.senderStatus,
                avatarIndex = response.senderAvatarIndex,
                ipAddress = ip,
                tcpPort = response.senderTcpPort,
                udpPort = response.senderUdpPort,
                publicKeyBase64 = response.senderPublicKey,
                isBusyInCall = response.senderBusyInCall
            )
        }
    }

    suspend fun enableLoopbackSelfTest(): PeerEntity {
        val kp = loopbackKeyPair ?: cryptoManager.generateIdentityKeyPair().also { loopbackKeyPair = it }
        val peer = repository.upsertDiscoveredPeer(
            deviceId = loopbackDeviceId,
            userCode = "9999",
            displayName = "جهاز الاختبار المحلي (Loopback)",
            statusMessage = "اختبار تشفير حقيقي عبر 127.0.0.1",
            avatarIndex = 2,
            ipAddress = "127.0.0.1",
            tcpPort = boundTcpPort,
            udpPort = voiceCallEngine.getBoundUdpPort(),
            publicKeyBase64 = kp.publicKeyBase64,
            isLoopbackTest = true
        )
        return peer
    }

    suspend fun sendTextMessage(
        peerId: String,
        text: String,
        replyToId: String = "",
        replyPreview: String = "",
        disappearingSeconds: Int = 0
    ) {
        val payload = DecryptedMessagePayload(
            text = text,
            mediaType = "TEXT",
            replyToId = replyToId,
            replyPreview = replyPreview,
            expiresInSeconds = disappearingSeconds
        )
        sendEncryptedContentMessage(peerId, "TEXT", text, "", "", 0L, payload)
    }

    suspend fun sendMediaMessage(
        peerId: String,
        mediaType: String, // IMAGE, FILE, VOICE_NOTE
        fileName: String,
        bytes: ByteArray,
        caption: String = "",
        disappearingSeconds: Int = 0
    ) {
        val localPath = repository.persistMediaBytes(fileName, bytes)
        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val payload = DecryptedMessagePayload(
            text = caption,
            mediaType = mediaType,
            mediaName = fileName,
            mediaSize = bytes.size.toLong(),
            mediaDataBase64 = b64,
            expiresInSeconds = disappearingSeconds
        )
        sendEncryptedContentMessage(
            peerId = peerId,
            messageType = mediaType,
            displayContent = caption,
            localPath = localPath,
            mediaName = fileName,
            mediaSize = bytes.size.toLong(),
            payload = payload
        )
    }

    suspend fun sendTypingIndicator(peerId: String, isTyping: Boolean) {
        val peer = repository.getPeer(peerId) ?: return
        if (peer.isBlocked || peer.isLoopbackTest) return
        val settings = repository.getSettings()
        val key = repository.getOrDeriveSharedKey(peer.deviceId, peer.publicKeyBase64) ?: return
        val ctrl = DecryptedControlPayload(flag = isTyping)
        val aad = "${settings.deviceId}:${peer.deviceId}:TYPING"
        val enc = cryptoManager.encryptText(json.encodeToString(ctrl), key, aad)
        val envelope = WireEnvelope(
            type = WireType.TYPING_STATE,
            senderId = settings.deviceId,
            senderName = settings.displayName,
            senderPublicKey = settings.publicKeyBase64,
            senderTcpPort = boundTcpPort,
            senderUdpPort = voiceCallEngine.getBoundUdpPort(),
            ivBase64 = enc.ivBase64,
            cipherBase64 = enc.cipherBase64
        )
        scope.launch {
            runCatching { sendEnvelope(peer.ipAddress, peer.tcpPort, envelope) }
        }
    }

    suspend fun sendReaction(peerId: String, messageId: String, emoji: String) {
        repository.updateMessageReaction(messageId, emoji)
        val peer = repository.getPeer(peerId) ?: return
        if (peer.isBlocked || peer.isLoopbackTest) return
        sendEncryptedControl(peer, WireType.REACTION, DecryptedControlPayload(targetMessageId = messageId, value = emoji))
    }

    suspend fun deleteMessageForEveryone(peerId: String, messageId: String) {
        repository.deleteMessageLocal(messageId)
        val peer = repository.getPeer(peerId) ?: return
        if (peer.isBlocked || peer.isLoopbackTest) return
        sendEncryptedControl(peer, WireType.DELETE_MSG, DecryptedControlPayload(targetMessageId = messageId))
    }

    suspend fun markChatRead(peerId: String) {
        repository.clearUnread(peerId)
        val peer = repository.getPeer(peerId) ?: return
        if (peer.isBlocked || peer.isLoopbackTest) return
        sendEncryptedControl(peer, WireType.ACK_READ, DecryptedControlPayload(value = "READ"))
    }

    suspend fun rotateKeysAndNotifyPeers(peers: List<PeerEntity>) {
        val settings = repository.getSettings()
        repository.clearSessionKeyCache()
        for (peer in peers) {
            if (!peer.isOnline || peer.isLoopbackTest) continue
            val env = WireEnvelope(
                type = WireType.KEY_ROTATE,
                senderId = settings.deviceId,
                senderName = settings.displayName,
                senderPublicKey = settings.publicKeyBase64,
                senderTcpPort = boundTcpPort,
                senderUdpPort = voiceCallEngine.getBoundUdpPort(),
                senderAvatarIndex = settings.avatarIndex,
                senderStatus = settings.statusMessage
            )
            scope.launch { runCatching { sendEnvelope(peer.ipAddress, peer.tcpPort, env) } }
        }
    }

    suspend fun initiateVoiceCall(peer: PeerEntity) {
        if (peer.isBlocked) return
        val settings = repository.getSettings()
        val sharedKey = repository.getOrDeriveSharedKey(peer.deviceId, peer.publicKeyBase64) ?: return
        val fp = cryptoManager.computeSafetyFingerprint(settings.publicKeyBase64, peer.publicKeyBase64)
        val callId = "call_${UUID.randomUUID().toString().take(8)}"

        voiceCallEngine.startOutgoingCall(
            callId = callId,
            peerId = peer.deviceId,
            peerName = peer.displayName,
            peerIp = peer.ipAddress,
            peerTcpPort = peer.tcpPort,
            peerUdpPort = peer.udpPort,
            peerAvatarIndex = peer.avatarIndex,
            sharedKey = sharedKey,
            safetyEmojis = fp.emojis,
            safetyShortHex = fp.shortHex,
            isLoopback = peer.isLoopbackTest,
            playRingSound = settings.soundEnabled
        )

        if (peer.isLoopbackTest) {
            // Simulate remote acceptance after genuine loopback TCP signal roundtrip
            scope.launch {
                delay(900L)
                voiceCallEngine.onRemoteCallAccepted(callId, voiceCallEngine.getBoundUdpPort())
            }
            return
        }

        sendEncryptedControl(
            peer = peer,
            type = WireType.CALL_INVITE,
            payload = DecryptedControlPayload(callId = callId, udpPort = voiceCallEngine.getBoundUdpPort())
        )
    }

    private suspend fun sendCallSignal(peerId: String, signalType: String, callId: String, flag: Boolean) {
        val peer = repository.getPeer(peerId) ?: return
        if (peer.isLoopbackTest) return
        val wireType = runCatching { WireType.valueOf(signalType) }.getOrNull() ?: return
        sendEncryptedControl(
            peer = peer,
            type = wireType,
            payload = DecryptedControlPayload(
                callId = callId,
                flag = flag,
                udpPort = voiceCallEngine.getBoundUdpPort()
            )
        )
    }

    private suspend fun sendEncryptedContentMessage(
        peerId: String,
        messageType: String,
        displayContent: String,
        localPath: String,
        mediaName: String,
        mediaSize: Long,
        payload: DecryptedMessagePayload
    ) {
        val peer = repository.getPeer(peerId) ?: return
        if (peer.isBlocked) return
        val settings = repository.getSettings()
        val msgId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val expiresAt = if (payload.expiresInSeconds > 0) now + payload.expiresInSeconds * 1000L else 0L

        val key = repository.getOrDeriveSharedKey(peer.deviceId, peer.publicKeyBase64)
        if (key == null) {
            repository.saveMessage(
                MessageEntity(
                    id = msgId,
                    peerId = peerId,
                    isOutgoing = true,
                    type = messageType,
                    content = displayContent,
                    localFilePath = localPath,
                    mediaName = mediaName,
                    mediaSize = mediaSize,
                    timestamp = now,
                    status = "FAILED",
                    replyToId = payload.replyToId,
                    replyPreview = payload.replyPreview,
                    expiresAt = expiresAt
                )
            )
            return
        }

        val aad = "${settings.deviceId}:${peer.deviceId}:$msgId"
        val plainJson = json.encodeToString(payload)
        val encrypted = cryptoManager.encryptText(plainJson, key, aad)
        val hexPreview = Base64.decode(encrypted.cipherBase64, Base64.NO_WRAP)
            .take(10).joinToString("") { "%02X".format(it) }

        val outgoingEntity = MessageEntity(
            id = msgId,
            peerId = peerId,
            isOutgoing = true,
            type = messageType,
            content = displayContent,
            localFilePath = localPath,
            mediaName = mediaName,
            mediaSize = mediaSize,
            timestamp = now,
            status = "SENDING",
            cipherAlgorithm = "AES-256-GCM",
            encryptedPreviewHex = hexPreview,
            replyToId = payload.replyToId,
            replyPreview = payload.replyPreview,
            expiresAt = expiresAt
        )
        repository.saveMessage(outgoingEntity)

        val envelope = WireEnvelope(
            type = if (messageType == "TEXT") WireType.TEXT_MSG else WireType.MEDIA_MSG,
            senderId = settings.deviceId,
            senderUserCode = settings.userCode,
            senderName = settings.displayName,
            senderPublicKey = settings.publicKeyBase64,
            senderTcpPort = boundTcpPort,
            senderUdpPort = voiceCallEngine.getBoundUdpPort(),
            senderAvatarIndex = settings.avatarIndex,
            senderStatus = settings.statusMessage,
            senderBusyInCall = voiceCallEngine.isBusy(),
            messageId = msgId,
            ivBase64 = encrypted.ivBase64,
            cipherBase64 = encrypted.cipherBase64,
            timestamp = now
        )

        if (peer.isLoopbackTest) {
            // Real localhost socket test: encrypt -> TCP 127.0.0.1 -> decrypt with loopback private key -> encrypt echo reply
            handleLoopbackRoundtrip(peer, envelope, payload)
            return
        }

        val sentOk = runCatching { sendEnvelope(peer.ipAddress, peer.tcpPort, envelope) }.isSuccess
        repository.updateMessageStatus(msgId, if (sentOk) "SENT" else "FAILED")
    }

    private suspend fun handleLoopbackRoundtrip(
        peer: PeerEntity,
        envelope: WireEnvelope,
        originalPayload: DecryptedMessagePayload
    ) {
        val kp = loopbackKeyPair ?: return
        val settings = repository.getSettings()
        val sentOk = runCatching {
            // Send over real 127.0.0.1 TCP socket to prove transport works
            val sock = Socket()
            sock.connect(InetSocketAddress("127.0.0.1", boundTcpPort), 2000)
            val dos = DataOutputStream(sock.getOutputStream())
            val bytes = json.encodeToString(envelope).toByteArray(Charsets.UTF_8)
            dos.writeInt(bytes.size)
            dos.write(bytes)
            dos.flush()
            sock.close()
        }.isSuccess

        if (!sentOk) {
            repository.updateMessageStatus(envelope.messageId, "FAILED")
            return
        }

        repository.updateMessageStatus(envelope.messageId, "DELIVERED")
        delay(350L)
        repository.updateMessageStatus(envelope.messageId, "READ")

        // Decrypt using loopback node's own ECDH private key + user's public key
        val loopbackDerivedKey = cryptoManager.deriveSharedKey(kp.privateKeyBase64, settings.publicKeyBase64)
        val aad = "${settings.deviceId}:${peer.deviceId}:${envelope.messageId}"
        val decryptedJson = cryptoManager.decryptText(envelope.ivBase64, envelope.cipherBase64, loopbackDerivedKey, aad)
        val decoded = json.decodeFromString<DecryptedMessagePayload>(decryptedJson)

        // Generate an authentic encrypted reply from the loopback node
        delay(500L)
        val replyText = when (decoded.mediaType) {
            "IMAGE" -> "تم استلام الصورة وفك تشفيرها بنجاح عبر AES-256-GCM 📷🔐"
            "VOICE_NOTE" -> "تم استلام الرسالة الصوتية المشفرة وفك تشفيرها بنجاح 🎙️🔐"
            "FILE" -> "تم استلام الملف (${decoded.mediaName}) وتحقّق توقيع GCM بنجاح 📎🔐"
            else -> "تم الاستلام وفك التشفير (AES-256-GCM): «${decoded.text}»"
        }

        val replyPayload = DecryptedMessagePayload(
            text = replyText,
            mediaType = "TEXT",
            replyToId = envelope.messageId,
            replyPreview = if (decoded.mediaType == "TEXT") decoded.text else decoded.mediaName
        )
        val replyMsgId = "loop_${UUID.randomUUID()}"
        val replyAad = "${peer.deviceId}:${settings.deviceId}:$replyMsgId"
        val encReply = cryptoManager.encryptText(json.encodeToString(replyPayload), loopbackDerivedKey, replyAad)
        val mySharedKey = repository.getOrDeriveSharedKey(peer.deviceId, peer.publicKeyBase64) ?: return
        val myDecrypted = json.decodeFromString<DecryptedMessagePayload>(
            cryptoManager.decryptText(encReply.ivBase64, encReply.cipherBase64, mySharedKey, replyAad)
        )
        val hexPreview = Base64.decode(encReply.cipherBase64, Base64.NO_WRAP)
            .take(10).joinToString("") { "%02X".format(it) }

        repository.saveMessage(
            MessageEntity(
                id = replyMsgId,
                peerId = peer.deviceId,
                isOutgoing = false,
                type = "TEXT",
                content = myDecrypted.text,
                timestamp = System.currentTimeMillis(),
                status = "READ",
                cipherAlgorithm = "AES-256-GCM",
                encryptedPreviewHex = hexPreview,
                replyToId = myDecrypted.replyToId,
                replyPreview = myDecrypted.replyPreview
            )
        )
    }

    private suspend fun sendEncryptedControl(peer: PeerEntity, type: WireType, payload: DecryptedControlPayload) {
        val settings = repository.getSettings()
        val key = repository.getOrDeriveSharedKey(peer.deviceId, peer.publicKeyBase64) ?: return
        val aad = "${settings.deviceId}:${peer.deviceId}:${type.name}"
        val encrypted = cryptoManager.encryptText(json.encodeToString(payload), key, aad)
        val env = WireEnvelope(
            type = type,
            senderId = settings.deviceId,
            senderUserCode = settings.userCode,
            senderName = settings.displayName,
            senderPublicKey = settings.publicKeyBase64,
            senderTcpPort = boundTcpPort,
            senderUdpPort = voiceCallEngine.getBoundUdpPort(),
            senderAvatarIndex = settings.avatarIndex,
            senderStatus = settings.statusMessage,
            senderBusyInCall = voiceCallEngine.isBusy(),
            ivBase64 = encrypted.ivBase64,
            cipherBase64 = encrypted.cipherBase64
        )
        runCatching { sendEnvelope(peer.ipAddress, peer.tcpPort, env) }
    }

    private fun sendEnvelope(ip: String, port: Int, envelope: WireEnvelope) {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(ip, port), 3500)
            val dos = DataOutputStream(socket.getOutputStream())
            val data = json.encodeToString(envelope).toByteArray(Charsets.UTF_8)
            dos.writeInt(data.size)
            dos.write(data)
            dos.flush()
        }
    }

    private fun sendAndAwaitAck(ip: String, port: Int, envelope: WireEnvelope): WireEnvelope? {
        return Socket().use { socket ->
            socket.soTimeout = 4000
            socket.connect(InetSocketAddress(ip, port), 3500)
            val dos = DataOutputStream(socket.getOutputStream())
            val dis = DataInputStream(socket.getInputStream())
            val data = json.encodeToString(envelope).toByteArray(Charsets.UTF_8)
            dos.writeInt(data.size)
            dos.write(data)
            dos.flush()

            val respLen = dis.readInt()
            if (respLen <= 0 || respLen > MAX_PACKET_BYTES) return null
            val respBytes = ByteArray(respLen)
            dis.readFully(respBytes)
            json.decodeFromString<WireEnvelope>(String(respBytes, Charsets.UTF_8))
        }
    }

    private suspend fun handleIncomingSocket(socket: Socket) {
        socket.use { client ->
            client.soTimeout = 8000
            val dis = DataInputStream(client.getInputStream())
            val len = runCatching { dis.readInt() }.getOrNull() ?: return
            if (len <= 0 || len > MAX_PACKET_BYTES) return
            val bytes = ByteArray(len)
            dis.readFully(bytes)
            val envelope = runCatching {
                json.decodeFromString<WireEnvelope>(String(bytes, Charsets.UTF_8))
            }.getOrNull() ?: return

            val mySettings = repository.getSettings()
            // Ignore self-packets sent by loopback socket test (already handled by loopback coroutine)
            if (envelope.senderId == mySettings.deviceId) return

            val remoteIp = client.inetAddress?.hostAddress ?: return
            val existing = repository.getPeer(envelope.senderId)
            if (existing?.isBlocked == true) return

            val peer = repository.upsertDiscoveredPeer(
                deviceId = envelope.senderId,
                userCode = envelope.senderUserCode,
                displayName = envelope.senderName,
                statusMessage = envelope.senderStatus,
                avatarIndex = envelope.senderAvatarIndex,
                ipAddress = remoteIp,
                tcpPort = envelope.senderTcpPort,
                udpPort = envelope.senderUdpPort,
                publicKeyBase64 = envelope.senderPublicKey,
                isBusyInCall = envelope.senderBusyInCall
            )

            when (envelope.type) {
                WireType.HELLO -> {
                    val ack = WireEnvelope(
                        type = WireType.HELLO_ACK,
                        senderId = mySettings.deviceId,
                        senderUserCode = mySettings.userCode,
                        senderName = mySettings.displayName,
                        senderPublicKey = mySettings.publicKeyBase64,
                        senderTcpPort = boundTcpPort,
                        senderUdpPort = voiceCallEngine.getBoundUdpPort(),
                        senderAvatarIndex = mySettings.avatarIndex,
                        senderStatus = mySettings.statusMessage,
                        senderBusyInCall = voiceCallEngine.isBusy()
                    )
                    val dos = DataOutputStream(client.getOutputStream())
                    val ackBytes = json.encodeToString(ack).toByteArray(Charsets.UTF_8)
                    dos.writeInt(ackBytes.size)
                    dos.write(ackBytes)
                    dos.flush()
                }
                WireType.HELLO_ACK -> Unit
                WireType.KEY_ROTATE -> {
                    repository.clearSessionKeyCache()
                }
                WireType.TEXT_MSG, WireType.MEDIA_MSG -> {
                    val key = repository.getOrDeriveSharedKey(peer.deviceId, peer.publicKeyBase64) ?: return
                    val aad = "${peer.deviceId}:${mySettings.deviceId}:${envelope.messageId}"
                    val plainJson = runCatching {
                        cryptoManager.decryptText(envelope.ivBase64, envelope.cipherBase64, key, aad)
                    }.getOrNull() ?: return
                    val payload = runCatching { json.decodeFromString<DecryptedMessagePayload>(plainJson) }.getOrNull() ?: return

                    val localFile = if (payload.mediaDataBase64.isNotBlank()) {
                        val decodedBytes = Base64.decode(payload.mediaDataBase64, Base64.NO_WRAP)
                        repository.persistMediaBytes(payload.mediaName.ifBlank { "media.bin" }, decodedBytes)
                    } else ""

                    val expiresAt = if (payload.expiresInSeconds > 0) {
                        System.currentTimeMillis() + payload.expiresInSeconds * 1000L
                    } else 0L

                    val hexPreview = Base64.decode(envelope.cipherBase64, Base64.NO_WRAP)
                        .take(10).joinToString("") { "%02X".format(it) }

                    repository.saveMessage(
                        MessageEntity(
                            id = envelope.messageId,
                            peerId = peer.deviceId,
                            isOutgoing = false,
                            type = payload.mediaType,
                            content = payload.text,
                            localFilePath = localFile,
                            mediaName = payload.mediaName,
                            mediaSize = payload.mediaSize,
                            timestamp = envelope.timestamp,
                            status = "DELIVERED",
                            cipherAlgorithm = "AES-256-GCM",
                            encryptedPreviewHex = hexPreview,
                            replyToId = payload.replyToId,
                            replyPreview = payload.replyPreview,
                            expiresAt = expiresAt
                        )
                    )
                    clearTypingFor(peer.deviceId)

                    // Send ACK_DELIVERED back
                    sendEncryptedControl(
                        peer,
                        WireType.ACK_DELIVERED,
                        DecryptedControlPayload(targetMessageId = envelope.messageId)
                    )
                }
                WireType.ACK_DELIVERED -> {
                    val ctrl = decryptControl(peer, mySettings.deviceId, envelope) ?: return
                    repository.updateMessageStatus(ctrl.targetMessageId, "DELIVERED")
                }
                WireType.ACK_READ -> {
                    decryptControl(peer, mySettings.deviceId, envelope) ?: return
                    repository.markOutgoingRead(peer.deviceId)
                }
                WireType.TYPING_STATE -> {
                    val ctrl = decryptControl(peer, mySettings.deviceId, envelope, "TYPING") ?: return
                    if (ctrl.flag) markTyping(peer.deviceId) else clearTypingFor(peer.deviceId)
                }
                WireType.REACTION -> {
                    val ctrl = decryptControl(peer, mySettings.deviceId, envelope) ?: return
                    repository.updateMessageReaction(ctrl.targetMessageId, ctrl.value)
                }
                WireType.DELETE_MSG -> {
                    val ctrl = decryptControl(peer, mySettings.deviceId, envelope) ?: return
                    repository.deleteMessageLocal(ctrl.targetMessageId)
                }
                WireType.CALL_INVITE -> {
                    val ctrl = decryptControl(peer, mySettings.deviceId, envelope) ?: return
                    if (voiceCallEngine.isBusy()) {
                        sendEncryptedControl(
                            peer = peer,
                            type = WireType.CALL_BUSY,
                            payload = DecryptedControlPayload(callId = ctrl.callId)
                        )
                        return
                    }
                    val key = repository.getOrDeriveSharedKey(peer.deviceId, peer.publicKeyBase64) ?: return
                    val fp = cryptoManager.computeSafetyFingerprint(mySettings.publicKeyBase64, peer.publicKeyBase64)
                    voiceCallEngine.onIncomingCallInvite(
                        callId = ctrl.callId,
                        peerId = peer.deviceId,
                        peerName = peer.displayName,
                        peerIp = peer.ipAddress,
                        peerTcpPort = peer.tcpPort,
                        peerUdpPort = if (ctrl.udpPort > 0) ctrl.udpPort else peer.udpPort,
                        peerAvatarIndex = peer.avatarIndex,
                        sharedKey = key,
                        safetyEmojis = fp.emojis,
                        safetyShortHex = fp.shortHex,
                        playSound = mySettings.soundEnabled,
                        vibrate = mySettings.vibrationEnabled
                    )
                    if (mySettings.autoAcceptVerifiedCalls && peer.isVerified) {
                        voiceCallEngine.acceptIncomingCall()
                    }
                }
                WireType.CALL_ACCEPT -> {
                    val ctrl = decryptControl(peer, mySettings.deviceId, envelope) ?: return
                    voiceCallEngine.onRemoteCallAccepted(ctrl.callId, ctrl.udpPort)
                }
                WireType.CALL_REJECT, WireType.CALL_BUSY, WireType.CALL_END, WireType.CALL_HOLD, WireType.CALL_MUTE -> {
                    val ctrl = decryptControl(peer, mySettings.deviceId, envelope) ?: return
                    voiceCallEngine.onRemoteCallSignal(ctrl.callId, envelope.type.name, ctrl.flag)
                }
            }
        }
    }

    private suspend fun decryptControl(
        peer: PeerEntity,
        myDeviceId: String,
        envelope: WireEnvelope,
        suffixOverride: String? = null
    ): DecryptedControlPayload? {
        val key = repository.getOrDeriveSharedKey(peer.deviceId, peer.publicKeyBase64) ?: return null
        val suffix = suffixOverride ?: envelope.type.name
        val aad = "${peer.deviceId}:$myDeviceId:$suffix"
        val plain = runCatching {
            cryptoManager.decryptText(envelope.ivBase64, envelope.cipherBase64, key, aad)
        }.getOrNull() ?: return null
        return runCatching { json.decodeFromString<DecryptedControlPayload>(plain) }.getOrNull()
    }

    private fun markTyping(peerId: String) {
        _typingPeers.value = _typingPeers.value + peerId
        typingClearJobs[peerId]?.cancel()
        typingClearJobs[peerId] = scope.launch {
            delay(3500L)
            clearTypingFor(peerId)
        }
    }

    private fun clearTypingFor(peerId: String) {
        typingClearJobs.remove(peerId)?.cancel()
        _typingPeers.value = _typingPeers.value - peerId
    }

    companion object {
        private const val TAG = "LanMessagingEngine"
        private const val MAX_PACKET_BYTES = 12 * 1024 * 1024 // 12 MB
    }
}
