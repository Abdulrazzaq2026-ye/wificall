package com.example.data.model

import kotlinx.serialization.Serializable

@Serializable
data class DiscoveryBeacon(
    val deviceId: String,
    val userCode: String = "",
    val displayName: String,
    val statusMessage: String,
    val avatarIndex: Int,
    val tcpPort: Int,
    val udpAudioPort: Int,
    val publicKeyBase64: String,
    val isBusyInCall: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
enum class WireType {
    HELLO,
    HELLO_ACK,
    TEXT_MSG,
    MEDIA_MSG,
    ACK_DELIVERED,
    ACK_READ,
    TYPING_STATE,
    REACTION,
    DELETE_MSG,
    KEY_ROTATE,
    CALL_INVITE,
    CALL_ACCEPT,
    CALL_REJECT,
    CALL_BUSY,
    CALL_END,
    CALL_HOLD,
    CALL_MUTE
}

@Serializable
data class WireEnvelope(
    val type: WireType,
    val senderId: String,
    val senderUserCode: String = "",
    val senderName: String,
    val senderPublicKey: String,
    val senderTcpPort: Int,
    val senderUdpPort: Int,
    val senderAvatarIndex: Int = 0,
    val senderStatus: String = "",
    val senderBusyInCall: Boolean = false,
    val messageId: String = "",
    val ivBase64: String = "",
    val cipherBase64: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class DecryptedMessagePayload(
    val text: String = "",
    val mediaType: String = "TEXT", // TEXT, IMAGE, FILE, VOICE_NOTE
    val mediaName: String = "",
    val mediaSize: Long = 0L,
    val mediaDataBase64: String = "",
    val replyToId: String = "",
    val replyPreview: String = "",
    val expiresInSeconds: Int = 0
)

@Serializable
data class DecryptedControlPayload(
    val targetMessageId: String = "",
    val value: String = "",
    val flag: Boolean = false,
    val callId: String = "",
    val udpPort: Int = 0
)

enum class CallStatus {
    IDLE,
    OUTGOING_RINGING,
    INCOMING_RINGING,
    ACTIVE,
    ON_HOLD,
    ENDED
}

data class ActiveCallInfo(
    val callId: String = "",
    val peerId: String = "",
    val peerName: String = "",
    val peerIp: String = "",
    val peerTcpPort: Int = 0,
    val peerUdpPort: Int = 0,
    val peerAvatarIndex: Int = 0,
    val safetyEmojis: List<String> = emptyList(),
    val safetyShortHex: String = "",
    val status: CallStatus = CallStatus.IDLE,
    val isOutgoing: Boolean = true,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isRemoteMuted: Boolean = false,
    val isRemoteOnHold: Boolean = false,
    val durationSeconds: Int = 0,
    val packetsSent: Long = 0L,
    val packetsReceived: Long = 0L,
    val localAudioLevel: Float = 0f,
    val remoteAudioLevel: Float = 0f,
    val endReason: String = ""
)

enum class MessageType {
    TEXT,
    IMAGE,
    FILE,
    VOICE_NOTE,
    SYSTEM_SECURITY
}

enum class MessageDeliveryStatus {
    SENDING,
    SENT,
    DELIVERED,
    READ,
    FAILED
}

enum class CallDirection {
    INCOMING,
    OUTGOING,
    MISSED
}

enum class MainTab {
    CHATS,
    NETWORK,
    CALLS,
    SECURITY
}
