package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.local.CallLogEntity
import com.example.data.local.PeerEntity
import com.example.ui.components.CipherBadgePill
import com.example.ui.components.PeerAvatar
import com.example.ui.components.formatDuration
import com.example.ui.components.formatTimeShort
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CyanCipher
import com.example.ui.theme.EmeraldPrimary

@Composable
fun CallsScreen(
    callLogs: List<CallLogEntity>,
    peers: List<PeerEntity>,
    onBack: () -> Unit,
    onRequestExitApp: () -> Unit,
    onRedialPeer: (PeerEntity) -> Unit,
    onClearAllLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("calls_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                    Column {
                        Text(
                            text = "سجل المكالمات المشفرة",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "مكالمات UDP مشفرة بـ AES-256-GCM",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (callLogs.isNotEmpty()) {
                        TextButton(
                            onClick = onClearAllLogs,
                            modifier = Modifier.testTag("clear_call_logs_button")
                        ) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("مسح")
                        }
                    }
                    IconButton(
                        onClick = onRequestExitApp,
                        modifier = Modifier.testTag("calls_exit_button")
                    ) {
                        Icon(Icons.Filled.PowerSettingsNew, contentDescription = "إغلاق البرنامج", tint = CrimsonAlert)
                    }
                }
            }

            if (callLogs.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(78.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Call,
                            contentDescription = null,
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "لا توجد مكالمات مسجلة",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "جميع مكالماتك الصوتية عبر الشبكة المحلية مشفرة طرف لطرف ولا تغادر شبكتك أبداً.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 84.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(callLogs, key = { it.id }) { log ->
                        val matchingPeer = peers.find { it.deviceId == log.peerId }
                        CallLogCard(
                            log = log,
                            canRedial = matchingPeer != null && !matchingPeer.isBlocked,
                            onRedial = { matchingPeer?.let(onRedialPeer) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CallLogCard(
    log: CallLogEntity,
    canRedial: Boolean,
    onRedial: () -> Unit
) {
    val (dirIcon, dirColor, dirText) = when (log.direction) {
        "OUTGOING" -> Triple(Icons.AutoMirrored.Filled.CallMade, EmeraldPrimary, "مكالمة صادرة")
        "INCOMING" -> Triple(Icons.AutoMirrored.Filled.CallReceived, CyanCipher, "مكالمة واردة")
        else -> Triple(Icons.AutoMirrored.Filled.CallMissed, CrimsonAlert, "مكالمة فائتة")
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PeerAvatar(
                name = log.peerName,
                avatarIndex = log.peerAvatarIndex,
                isOnline = false,
                size = 46.dp
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = log.peerName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(dirIcon, contentDescription = null, tint = dirColor, modifier = Modifier.size(15.dp))
                    Text(
                        text = "$dirText • ${formatTimeShort(log.timestamp)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CipherBadgePill(text = if (log.durationSeconds > 0) formatDuration(log.durationSeconds) else "00:00", isVerified = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(11.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = log.peerIp,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            IconButton(
                onClick = onRedial,
                enabled = canRedial,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            ) {
                Icon(Icons.Filled.Call, contentDescription = "إعادة الاتصال", tint = EmeraldPrimary)
            }
        }
    }
}
