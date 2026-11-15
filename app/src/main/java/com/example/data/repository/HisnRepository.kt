package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.crypto.CryptoManager
import com.example.crypto.SafetyFingerprint
import com.example.data.local.CallLogDao
import com.example.data.local.CallLogEntity
import com.example.data.local.MessageDao
import com.example.data.local.MessageEntity
import com.example.data.local.PeerDao
import com.example.data.local.PeerEntity
import com.example.data.prefs.SettingsRepository
import com.example.data.prefs.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class HisnRepository(
    private val context: Context,
    private val peerDao: PeerDao,
    private val messageDao: MessageDao,
    private val callLogDao: CallLogDao,
    private val settingsRepository: SettingsRepository,
    private val cryptoManager: CryptoManager
) {
    private val sharedKeyCache = ConcurrentHashMap<String, ByteArray>()

    val peersFlow: Flow<List<PeerEntity>> = peerDao.observeAllPeers()
    val callLogsFlow: Flow<List<CallLogEntity>> = callLogDao.observeAllCallLogs()
    val settingsFlow: Flow<UserSettings> = settingsRepository.settingsFlow

    fun observePeer(peerId: String): Flow<PeerEntity?> = peerDao.observePeer(peerId)
    fun observeMessages(peerId: String): Flow<List<MessageEntity>> = messageDao.observeMessagesForPeer(peerId)

    suspend fun getPeer(peerId: String): PeerEntity? = peerDao.getPeer(peerId)
    suspend fun getSettings(): UserSettings = settingsRepository.ensureInitialized(cryptoManager)

    suspend fun getOrDeriveSharedKey(peerId: String, peerPublicKeyBase64: String): ByteArray? {
        if (peerPublicKeyBase64.isBlank()) return null
        val cacheKey = "$peerId:$peerPublicKeyBase64"
        sharedKeyCache[cacheKey]?.let { return it }
        val settings = getSettings()
        if (settings.privateKeyBase64.isBlank()) return null
        return runCatching {
            val derived = cryptoManager.deriveSharedKey(settings.privateKeyBase64, peerPublicKeyBase64)
            sharedKeyCache[cacheKey] = derived
            derived
        }.getOrNull()
    }

    fun clearSessionKeyCache() {
        sharedKeyCache.clear()
    }

    suspend fun computeSafetyFingerprint(peerPublicKeyBase64: String): SafetyFingerprint {
        val myPub = getSettings().publicKeyBase64
        return cryptoManager.computeSafetyFingerprint(myPub, peerPublicKeyBase64)
    }

    suspend fun upsertDiscoveredPeer(
        deviceId: String,
        userCode: String = "",
        displayName: String,
        statusMessage: String,
        avatarIndex: Int,
        ipAddress: String,
        tcpPort: Int,
        udpPort: Int,
        publicKeyBase64: String,
        isBusyInCall: Boolean = false,
        isLoopbackTest: Boolean = false
    ): PeerEntity {
        val existing = peerDao.getPeer(deviceId)
        val myPub = getSettings().publicKeyBase64
        val safetyHex = if (publicKeyBase64.isNotBlank() && myPub.isNotBlank()) {
            cryptoManager.computeSafetyFingerprint(myPub, publicKeyBase64).shortHex
        } else {
            existing?.safetyShortHex ?: "PENDING"
        }

        // If peer rotated its public key, reset verification until re-verified
        val keyChanged = existing != null &&
            existing.publicKeyBase64.isNotBlank() &&
            publicKeyBase64.isNotBlank() &&
            existing.publicKeyBase64 != publicKeyBase64

        val updated = PeerEntity(
            deviceId = deviceId,
            userCode = userCode.ifBlank { existing?.userCode ?: "" },
            displayName = displayName.ifBlank { existing?.displayName ?: ipAddress },
            statusMessage = statusMessage.ifBlank { existing?.statusMessage ?: "متاح على الشبكة المحلية" },
            avatarIndex = avatarIndex,
            ipAddress = ipAddress,
            tcpPort = tcpPort,
            udpPort = udpPort,
            publicKeyBase64 = publicKeyBase64.ifBlank { existing?.publicKeyBase64 ?: "" },
            safetyShortHex = safetyHex,
            isVerified = if (keyChanged) false else (existing?.isVerified ?: false),
            isPinned = existing?.isPinned ?: false,
            isBlocked = existing?.isBlocked ?: false,
            isOnline = true,
            isBusyInCall = isBusyInCall,
            isLoopbackTest = isLoopbackTest || (existing?.isLoopbackTest == true),
            unreadCount = existing?.unreadCount ?: 0,
            lastMessagePreview = existing?.lastMessagePreview ?: "",
            lastMessageTime = existing?.lastMessageTime ?: 0L,
            lastSeenAt = System.currentTimeMillis()
        )
        peerDao.upsertPeer(updated)

        if (keyChanged) {
            insertSecurityEvent(
                peerId = deviceId,
                text = "تنبيه أمان: قام ${updated.displayName} بتحديث مفتاح التشفير الخاص به. يُنصح بإعادة التحقق من رمز الأمان."
            )
        }
        return updated
    }

    suspend fun markPeerOffline(deviceId: String) {
        peerDao.markPeerOffline(deviceId)
    }

    suspend fun markStalePeersOffline(timeoutMs: Long = 14_000L) {
        peerDao.markStalePeersOffline(System.currentTimeMillis() - timeoutMs)
    }

    suspend fun saveMessage(message: MessageEntity, updatePreview: Boolean = true) {
        messageDao.upsertMessage(message)
        if (updatePreview) {
            val preview = when (message.type) {
                "IMAGE" -> "📷 صورة مشفرة"
                "FILE" -> "📎 ملف: ${message.mediaName}"
                "VOICE_NOTE" -> "🎙️ رسالة صوتية مشفرة"
                "SYSTEM_SECURITY" -> "🛡️ تحديث أمني"
                else -> message.content
            }
            if (message.isOutgoing) {
                peerDao.updateLastMessagePreview(message.peerId, preview, message.timestamp)
            } else {
                peerDao.recordIncomingPreview(message.peerId, preview, message.timestamp)
            }
        }
    }

    suspend fun insertSecurityEvent(peerId: String, text: String) {
        val msg = MessageEntity(
            id = "sec_${UUID.randomUUID()}",
            peerId = peerId,
            isOutgoing = false,
            type = "SYSTEM_SECURITY",
            content = text,
            status = "READ",
            cipherAlgorithm = "ECDH P-256"
        )
        messageDao.upsertMessage(msg)
    }

    suspend fun updateMessageStatus(messageId: String, status: String) {
        messageDao.updateStatus(messageId, status)
    }

    suspend fun markOutgoingRead(peerId: String) {
        messageDao.markOutgoingAsRead(peerId)
    }

    suspend fun updateMessageReaction(messageId: String, reaction: String) {
        messageDao.updateReaction(messageId, reaction)
    }

    suspend fun deleteMessageLocal(messageId: String) {
        messageDao.deleteMessage(messageId)
    }

    suspend fun clearChatHistory(peerId: String) {
        messageDao.deleteMessagesForPeer(peerId)
        peerDao.updateLastMessagePreview(peerId, "", 0L)
    }

    suspend fun deletePeerAndChat(peerId: String) {
        messageDao.deleteMessagesForPeer(peerId)
        peerDao.deletePeer(peerId)
    }

    suspend fun clearUnread(peerId: String) {
        peerDao.clearUnread(peerId)
    }

    suspend fun setPeerVerified(peerId: String, verified: Boolean) {
        peerDao.setVerified(peerId, verified)
        insertSecurityEvent(
            peerId = peerId,
            text = if (verified) "تم توثيق هوية الطرف الآخر ومطابقة بصمة التشفير بنجاح." else "تم إلغاء توثيق الهوية."
        )
    }

    suspend fun setPeerPinned(peerId: String, pinned: Boolean) {
        peerDao.setPinned(peerId, pinned)
    }

    suspend fun setPeerBlocked(peerId: String, blocked: Boolean) {
        peerDao.setBlocked(peerId, blocked)
    }

    suspend fun purgeExpiredMessages() {
        messageDao.deleteExpiredMessages(System.currentTimeMillis())
    }

    suspend fun recordCallLog(
        peerId: String,
        peerName: String,
        peerIp: String,
        peerAvatarIndex: Int,
        direction: String,
        durationSeconds: Int
    ) {
        callLogDao.insertCallLog(
            CallLogEntity(
                id = UUID.randomUUID().toString(),
                peerId = peerId,
                peerName = peerName,
                peerIp = peerIp,
                peerAvatarIndex = peerAvatarIndex,
                direction = direction,
                timestamp = System.currentTimeMillis(),
                durationSeconds = durationSeconds
            )
        )
    }

    suspend fun deleteCallLog(id: String) {
        callLogDao.deleteCallLog(id)
    }

    suspend fun clearAllCallLogs() {
        callLogDao.deleteAllCallLogs()
    }

    suspend fun panicWipeAllData() {
        sharedKeyCache.clear()
        messageDao.deleteAllMessages()
        callLogDao.deleteAllCallLogs()
        peerDao.deleteAllPeers()
        val mediaDir = File(context.filesDir, "hisn_media")
        if (mediaDir.exists()) mediaDir.deleteRecursively()
        settingsRepository.rotateIdentityKeys(cryptoManager)
    }

    fun persistMediaBytes(fileName: String, bytes: ByteArray): String {
        val dir = File(context.filesDir, "hisn_media")
        if (!dir.exists()) dir.mkdirs()
        val safeName = "${System.currentTimeMillis()}_${fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")}"
        val outFile = File(dir, safeName)
        outFile.writeBytes(bytes)
        return outFile.absolutePath
    }

    fun readUriBytesWithMeta(uri: Uri): Triple<String, Long, ByteArray>? {
        return runCatching {
            var name = "attachment"
            var size = 0L
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: name
                    if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
                }
            }
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            if (size <= 0L) size = bytes.size.toLong()
            Triple(name, size, bytes)
        }.getOrNull()
    }
}
