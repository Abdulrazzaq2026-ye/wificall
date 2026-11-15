package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "peers")
data class PeerEntity(
    @PrimaryKey val deviceId: String,
    val userCode: String = "",
    val displayName: String,
    val statusMessage: String = "متاح على الشبكة المحلية",
    val avatarIndex: Int = 0,
    val ipAddress: String,
    val tcpPort: Int,
    val udpPort: Int,
    val publicKeyBase64: String,
    val safetyShortHex: String,
    val isVerified: Boolean = false,
    val isPinned: Boolean = false,
    val isBlocked: Boolean = false,
    val isOnline: Boolean = false,
    val isBusyInCall: Boolean = false,
    val isLoopbackTest: Boolean = false,
    val unreadCount: Int = 0,
    val lastMessagePreview: String = "",
    val lastMessageTime: Long = 0L,
    val lastSeenAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "messages",
    indices = [Index(value = ["peerId", "timestamp"])]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val peerId: String,
    val isOutgoing: Boolean,
    val type: String, // TEXT, IMAGE, FILE, VOICE_NOTE, SYSTEM_SECURITY
    val content: String,
    val localFilePath: String = "",
    val mediaName: String = "",
    val mediaSize: Long = 0L,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "SENT", // SENDING, SENT, DELIVERED, READ, FAILED
    val cipherAlgorithm: String = "AES-256-GCM",
    val encryptedPreviewHex: String = "",
    val replyToId: String = "",
    val replyPreview: String = "",
    val reaction: String = "",
    val expiresAt: Long = 0L
)

@Entity(tableName = "call_logs")
data class CallLogEntity(
    @PrimaryKey val id: String,
    val peerId: String,
    val peerName: String,
    val peerIp: String,
    val peerAvatarIndex: Int = 0,
    val direction: String, // INCOMING, OUTGOING, MISSED
    val timestamp: Long = System.currentTimeMillis(),
    val durationSeconds: Int = 0,
    val cipherSuite: String = "ECDH P-256 + AES-256-GCM"
)
