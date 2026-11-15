package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PeerDao {
    @Query("SELECT * FROM peers ORDER BY isPinned DESC, isOnline DESC, lastMessageTime DESC, lastSeenAt DESC")
    fun observeAllPeers(): Flow<List<PeerEntity>>

    @Query("SELECT * FROM peers WHERE deviceId = :deviceId LIMIT 1")
    fun observePeer(deviceId: String): Flow<PeerEntity?>

    @Query("SELECT * FROM peers WHERE deviceId = :deviceId LIMIT 1")
    suspend fun getPeer(deviceId: String): PeerEntity?

    @Query("SELECT * FROM peers WHERE ipAddress = :ip AND tcpPort = :port LIMIT 1")
    suspend fun getPeerByEndpoint(ip: String, port: Int): PeerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPeer(peer: PeerEntity)

    @Query("UPDATE peers SET isOnline = :online, isBusyInCall = 0, lastSeenAt = :seenAt WHERE deviceId = :deviceId")
    suspend fun updateOnlineStatus(deviceId: String, online: Boolean, seenAt: Long)

    @Query("UPDATE peers SET isOnline = 0, isBusyInCall = 0 WHERE deviceId = :deviceId")
    suspend fun markPeerOffline(deviceId: String)

    @Query("UPDATE peers SET isOnline = 0, isBusyInCall = 0 WHERE lastSeenAt < :cutoff AND isLoopbackTest = 0")
    suspend fun markStalePeersOffline(cutoff: Long)

    @Query("UPDATE peers SET isVerified = :verified WHERE deviceId = :deviceId")
    suspend fun setVerified(deviceId: String, verified: Boolean)

    @Query("UPDATE peers SET isPinned = :pinned WHERE deviceId = :deviceId")
    suspend fun setPinned(deviceId: String, pinned: Boolean)

    @Query("UPDATE peers SET isBlocked = :blocked WHERE deviceId = :deviceId")
    suspend fun setBlocked(deviceId: String, blocked: Boolean)

    @Query("UPDATE peers SET unreadCount = 0 WHERE deviceId = :deviceId")
    suspend fun clearUnread(deviceId: String)

    @Query("UPDATE peers SET unreadCount = unreadCount + 1, lastMessagePreview = :preview, lastMessageTime = :time WHERE deviceId = :deviceId")
    suspend fun recordIncomingPreview(deviceId: String, preview: String, time: Long)

    @Query("UPDATE peers SET lastMessagePreview = :preview, lastMessageTime = :time WHERE deviceId = :deviceId")
    suspend fun updateLastMessagePreview(deviceId: String, preview: String, time: Long)

    @Query("DELETE FROM peers WHERE deviceId = :deviceId")
    suspend fun deletePeer(deviceId: String)

    @Query("DELETE FROM peers")
    suspend fun deleteAllPeers()
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE peerId = :peerId ORDER BY timestamp ASC")
    fun observeMessagesForPeer(peerId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    suspend fun getMessage(id: String): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(message: MessageEntity)

    @Query("UPDATE messages SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("UPDATE messages SET status = 'READ' WHERE peerId = :peerId AND isOutgoing = 1 AND status != 'READ'")
    suspend fun markOutgoingAsRead(peerId: String)

    @Query("UPDATE messages SET reaction = :reaction WHERE id = :id")
    suspend fun updateReaction(id: String, reaction: String)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessage(id: String)

    @Query("DELETE FROM messages WHERE peerId = :peerId")
    suspend fun deleteMessagesForPeer(peerId: String)

    @Query("DELETE FROM messages WHERE expiresAt > 0 AND expiresAt <= :now")
    suspend fun deleteExpiredMessages(now: Long)

    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()
}

@Dao
interface CallLogDao {
    @Query("SELECT * FROM call_logs ORDER BY timestamp DESC")
    fun observeAllCallLogs(): Flow<List<CallLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallLog(log: CallLogEntity)

    @Query("DELETE FROM call_logs WHERE id = :id")
    suspend fun deleteCallLog(id: String)

    @Query("DELETE FROM call_logs")
    suspend fun deleteAllCallLogs()
}
