package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.PeerEntity
import com.example.data.prefs.UserSettings
import com.example.network.LocalNetworkInfo
import com.example.ui.components.CipherBadgePill
import com.example.ui.components.DeveloperCreditCard
import com.example.ui.components.ManualConnectDialog
import com.example.ui.components.PeerAvatar
import com.example.ui.components.formatTimeShort
import com.example.ui.theme.AmberSecurity
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CyanCipher
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldPrimary

private enum class ChatFilter(val label: String) {
    ALL("الكل"),
    ONLINE("متصل الآن"),
    VERIFIED("موثّق"),
    UNREAD("غير مقروء")
}

@Composable
fun ChatsScreen(
    peers: List<PeerEntity>,
    settings: UserSettings,
    typingPeers: Set<String>,
    networkInfo: LocalNetworkInfo,
    tcpPort: Int,
    onOpenChat: (String) -> Unit,
    onStartCall: (PeerEntity) -> Unit,
    onOpenNetworkTab: () -> Unit,
    onConnectManual: (String, String) -> Unit,
    onStartLoopbackTest: () -> Unit,
    onRequestExitApp: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(ChatFilter.ALL) }
    var showManualDialog by remember { mutableStateOf(false) }

    val filteredPeers = remember(peers, searchQuery, selectedFilter) {
        peers.filter { peer ->
            val matchesQuery = searchQuery.isBlank() ||
                peer.displayName.contains(searchQuery, ignoreCase = true) ||
                peer.userCode.contains(searchQuery, ignoreCase = true) ||
                peer.ipAddress.contains(searchQuery)
            val matchesFilter = when (selectedFilter) {
                ChatFilter.ALL -> true
                ChatFilter.ONLINE -> peer.isOnline
                ChatFilter.VERIFIED -> peer.isVerified
                ChatFilter.UNREAD -> peer.unreadCount > 0
            }
            matchesQuery && matchesFilter
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "اتصال wifi",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        CipherBadgePill(text = "E2EE LAN", isVerified = true)
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${settings.displayName} • المعرّف: ${settings.userCode}",
                        style = MaterialTheme.typography.labelMedium,
                        color = EmeraldBright,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Wifi,
                            contentDescription = null,
                            tint = if (networkInfo.isWifiConnected) EmeraldPrimary else MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "${networkInfo.ipAddress}:$tcpPort • تطوير م/عبدالرزاق عزي",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(
                        onClick = { showManualDialog = true },
                        modifier = Modifier.testTag("chats_manual_connect_button")
                    ) {
                        Icon(Icons.Filled.AddLink, contentDescription = "اتصال بالـ IP")
                    }
                    IconButton(
                        onClick = onOpenNetworkTab,
                        modifier = Modifier.testTag("chats_open_radar_button")
                    ) {
                        Icon(Icons.Filled.Radar, contentDescription = "رادار الشبكة")
                    }
                    IconButton(
                        onClick = onRequestExitApp,
                        modifier = Modifier.testTag("chats_exit_app_button")
                    ) {
                        Icon(
                            Icons.Filled.PowerSettingsNew,
                            contentDescription = "إغلاق البرنامج",
                            tint = CrimsonAlert
                        )
                    }
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("بحث بالاسم، الرقم التعريفي، أو IP...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "مسح البحث")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("chats_search_input")
            )

            // Filter Chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(ChatFilter.entries) { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter.label) },
                        modifier = Modifier.testTag("filter_${filter.name.lowercase()}")
                    )
                }
            }

            if (filteredPeers.isEmpty()) {
                EmptyChatsState(
                    hasAnyPeers = peers.isNotEmpty(),
                    onScanNetwork = onOpenNetworkTab,
                    onManualConnect = { showManualDialog = true },
                    onStartLoopback = onStartLoopbackTest
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredPeers, key = { it.deviceId }) { peer ->
                        PeerChatCard(
                            peer = peer,
                            isTyping = typingPeers.contains(peer.deviceId),
                            onClick = { onOpenChat(peer.deviceId) },
                            onCallClick = { onStartCall(peer) }
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showManualDialog = true },
            containerColor = EmeraldPrimary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("chats_fab_connect")
        ) {
            Icon(Icons.Filled.AddLink, contentDescription = "اتصال جديد")
        }
    }

    if (showManualDialog) {
        ManualConnectDialog(
            onConnect = onConnectManual,
            onDismiss = { showManualDialog = false }
        )
    }
}

@Composable
private fun PeerChatCard(
    peer: PeerEntity,
    isTyping: Boolean,
    onClick: () -> Unit,
    onCallClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag("chat_peer_card_${peer.deviceId}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PeerAvatar(
                name = peer.displayName,
                avatarIndex = peer.avatarIndex,
                isOnline = peer.isOnline,
                isVerified = peer.isVerified,
                size = 52.dp
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = peer.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (peer.userCode.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "#${peer.userCode}",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyanCipher,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (peer.isPinned) {
                        Icon(
                            imageVector = Icons.Filled.PushPin,
                            contentDescription = "مثبت",
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(Modifier.height(3.dp))

                Text(
                    text = when {
                        isTyping -> "يكتب رسالة مشفرة الآن..."
                        peer.lastMessagePreview.isNotBlank() -> peer.lastMessagePreview
                        else -> "${peer.ipAddress}:${peer.tcpPort} • مفتاح #${peer.safetyShortHex}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isTyping) EmeraldBright else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(5.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!peer.isOnline) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "غير متصل • ${peer.ipAddress}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (peer.isBusyInCall) {
                        Surface(
                            color = AmberSecurity.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PhoneInTalk,
                                    contentDescription = null,
                                    tint = AmberSecurity,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = "مشغول بمكالمة",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AmberSecurity
                                )
                            }
                        }
                    } else if (peer.isBlocked) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "محظور",
                                style = MaterialTheme.typography.labelSmall,
                                color = CrimsonAlert,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else {
                        Icon(
                            imageVector = if (peer.isVerified) Icons.Filled.Shield else Icons.Filled.Lock,
                            contentDescription = null,
                            tint = if (peer.isVerified) CyanCipher else EmeraldPrimary,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "متصل • ${peer.ipAddress}",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldPrimary
                        )
                    }
                    if (peer.lastMessageTime > 0L) {
                        Text(
                            text = "• ${formatTimeShort(peer.lastMessageTime)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AnimatedVisibility(visible = peer.unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(EmeraldPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = peer.unreadCount.coerceAtMost(99).toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                IconButton(
                    onClick = onCallClick,
                    enabled = !peer.isBlocked,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (peer.isBusyInCall) AmberSecurity.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.primaryContainer
                        )
                        .testTag("quick_call_${peer.deviceId}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Call,
                        contentDescription = if (peer.isBusyInCall) "مشغول بمكالمة" else "اتصال صوتي مشفر",
                        tint = if (peer.isBusyInCall) AmberSecurity else EmeraldPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyChatsState(
    hasAnyPeers: Boolean,
    onScanNetwork: () -> Unit,
    onManualConnect: () -> Unit,
    onStartLoopback: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_empty_radar),
            contentDescription = "رادار الشبكة المحلية",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(28.dp))
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = if (hasAnyPeers) "لا توجد نتائج مطابقة" else "لا توجد محادثات أو متصلين بعد",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "اتصل بنفس شبكة الواي فاي مع جهاز آخر يستخدم «اتصال wifi» ليظهر في قائمة المتصلين مباشرة، أو استخدم الاتصال اليدوي.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onScanNetwork,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("empty_scan_network_button")
            ) {
                Icon(Icons.Filled.Radar, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("المتصلون بالشبكة")
            }
            OutlinedButton(
                onClick = onManualConnect,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("empty_manual_ip_button")
            ) {
                Icon(Icons.Filled.AddLink, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("إضافة IP")
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onStartLoopback,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("empty_loopback_test_button")
        ) {
            Icon(Icons.Filled.Science, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("اختبار التشفير والمكالمة محلياً (127.0.0.1)")
        }
        Spacer(Modifier.height(14.dp))
        DeveloperCreditCard()
    }
}
