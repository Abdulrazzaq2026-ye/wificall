package com.example.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.PeerEntity
import com.example.data.prefs.UserSettings
import com.example.network.LocalNetworkInfo
import com.example.ui.components.CipherBadgePill
import com.example.ui.components.ManualConnectDialog
import com.example.ui.components.PeerAvatar
import com.example.ui.theme.AmberSecurity
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CyanCipher
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldPrimary
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun NetworkRadarScreen(
    peers: List<PeerEntity>,
    settings: UserSettings,
    networkInfo: LocalNetworkInfo,
    isServiceRunning: Boolean,
    monitorStatusMessage: String,
    isScanning: Boolean,
    nsdActive: Boolean,
    udpActive: Boolean,
    onBack: () -> Unit,
    onRequestExitApp: () -> Unit,
    onScanNow: () -> Unit,
    onToggleNsd: (Boolean) -> Unit,
    onToggleUdp: (Boolean) -> Unit,
    onToggleStealth: (Boolean) -> Unit,
    onConnectManual: (String, String) -> Unit,
    onStartLoopback: () -> Unit,
    onOpenChat: (String) -> Unit,
    onStartCall: (PeerEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var showManualDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                // Top Header with Back & Exit buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("network_back_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                        }
                        Text(
                            text = "المتصلون على الشبكة المحلية",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(
                        onClick = onRequestExitApp,
                        modifier = Modifier.testTag("network_exit_button")
                    ) {
                        Icon(Icons.Filled.PowerSettingsNew, contentDescription = "إغلاق البرنامج", tint = CrimsonAlert)
                    }
                }
            }

            item {
                // Hero Network Card
                Card(
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Image(
                            painter = painterResource(id = R.drawable.img_cipher_hero),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color(0xBB070D0C),
                                            Color(0xEE091412)
                                        )
                                    )
                                )
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                CipherBadgePill(
                                    text = if (settings.stealthMode) "وضع التخفي نشط" else "ECDH P-256 LAN",
                                    isVerified = !settings.stealthMode
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = "${settings.displayName} (#${settings.userCode})",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "IP: ${networkInfo.ipAddress} (${networkInfo.interfaceName})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldBright
                                )
                                Text(
                                    text = monitorStatusMessage,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFB7D6CC)
                                )
                                Spacer(Modifier.height(8.dp))
                                val onlineCount = peers.count { it.isOnline && !it.isBusyInCall }
                                val busyCount = peers.count { it.isOnline && it.isBusyInCall }
                                val offlineCount = peers.count { !it.isOnline }
                                Text(
                                    text = "متاح: $onlineCount • مشغول: $busyCount • غير متصل: $offlineCount",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = CyanCipher
                                )
                            }

                            RadarPulseCanvas(
                                peerCount = peers.count { it.isOnline },
                                isScanning = isScanning && !settings.stealthMode,
                                modifier = Modifier.size(112.dp)
                            )
                        }
                    }
                }
            }

            item {
                // Discovery protocol toggles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = settings.nsdEnabled && nsdActive,
                        onClick = { onToggleNsd(!settings.nsdEnabled) },
                        label = { Text("mDNS (NSD)") },
                        modifier = Modifier.testTag("toggle_nsd_chip")
                    )
                    FilterChip(
                        selected = settings.udpBroadcastEnabled && udpActive,
                        onClick = { onToggleUdp(!settings.udpBroadcastEnabled) },
                        label = { Text("بث UDP") },
                        modifier = Modifier.testTag("toggle_udp_chip")
                    )
                    FilterChip(
                        selected = settings.stealthMode,
                        onClick = { onToggleStealth(!settings.stealthMode) },
                        leadingIcon = {
                            Icon(Icons.Filled.VisibilityOff, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text("تخفّي") },
                        modifier = Modifier.testTag("toggle_stealth_chip")
                    )
                }
            }

            item {
                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onScanNow,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("radar_scan_now_button")
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("مسح الشبكة")
                    }
                    OutlinedButton(
                        onClick = { showManualDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("radar_manual_ip_button")
                    ) {
                        Icon(Icons.Filled.AddLink, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("إضافة IP")
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onStartLoopback,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("radar_loopback_button")
                ) {
                    Icon(Icons.Filled.Science, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("إنشاء عقدة اختبار محلي مشفرة (127.0.0.1)")
                }
            }

            item {
                Text(
                    text = "قائمة المتصلين على الشبكة (${peers.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (peers.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "لم يتم اكتشاف متصلين آخرين على شبكة الواي فاي بعد. تأكد من فتح تطبيق «اتصال wifi» على الجهاز الآخر أو استخدم زر «إضافة IP» أو «اختبار محلي».",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }
            } else {
                items(peers, key = { it.deviceId }) { peer ->
                    DiscoveredPeerCard(
                        peer = peer,
                        onChat = { onOpenChat(peer.deviceId) },
                        onCall = { onStartCall(peer) }
                    )
                }
            }

            item { Spacer(Modifier.height(72.dp)) }
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
private fun RadarPulseCanvas(
    peerCount: Int,
    isScanning: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "radar")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep"
    )

    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        listOf(0.35f, 0.65f, 0.95f).forEach { factor ->
            drawCircle(
                color = EmeraldPrimary.copy(alpha = 0.28f),
                radius = radius * factor,
                center = center,
                style = Stroke(width = 1.5f)
            )
        }

        if (isScanning) {
            drawArc(
                brush = Brush.sweepGradient(
                    0f to Color.Transparent,
                    0.75f to EmeraldPrimary.copy(alpha = 0.35f),
                    1f to EmeraldBright.copy(alpha = 0.8f),
                    center = center
                ),
                startAngle = angle,
                sweepAngle = 70f,
                useCenter = true,
                topLeft = Offset(center.x - radius * 0.95f, center.y - radius * 0.95f),
                size = Size(radius * 1.9f, radius * 1.9f)
            )
        }

        drawCircle(
            color = EmeraldBright,
            radius = 5.dp.toPx(),
            center = center
        )

        val nodes = peerCount.coerceAtMost(6)
        for (i in 0 until nodes) {
            val theta = Math.toRadians((i * 60 + 25).toDouble())
            val dist = radius * (0.45f + (i % 2) * 0.28f)
            val px = center.x + (dist * cos(theta)).toFloat()
            val py = center.y + (dist * sin(theta)).toFloat()
            drawCircle(
                color = CyanCipher,
                radius = 4.dp.toPx(),
                center = Offset(px, py)
            )
        }
    }
}

@Composable
private fun DiscoveredPeerCard(
    peer: PeerEntity,
    onChat: () -> Unit,
    onCall: () -> Unit
) {
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
                name = peer.displayName,
                avatarIndex = peer.avatarIndex,
                isOnline = peer.isOnline,
                isVerified = peer.isVerified,
                size = 48.dp
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
                        fontWeight = FontWeight.SemiBold
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
                }
                Text(
                    text = "${peer.ipAddress}:${peer.tcpPort} • #${peer.safetyShortHex}",
                    style = MaterialTheme.typography.labelSmall,
                    color = EmeraldPrimary
                )
                Spacer(Modifier.height(3.dp))
                if (!peer.isOnline) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "غير متصل (Offline)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (peer.isBusyInCall) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PhoneInTalk,
                            contentDescription = null,
                            tint = AmberSecurity,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "مشغول في مكالمة أخرى (Busy)",
                            style = MaterialTheme.typography.bodySmall,
                            color = AmberSecurity,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = null,
                            tint = if (peer.isVerified) CyanCipher else EmeraldPrimary,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "متصل ومتاح (Online)",
                            style = MaterialTheme.typography.bodySmall,
                            color = EmeraldPrimary
                        )
                    }
                }
            }
            IconButton(
                onClick = onChat,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .testTag("radar_chat_${peer.deviceId}")
            ) {
                Icon(Icons.Filled.Chat, contentDescription = "مراسلة", tint = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onCall,
                enabled = !peer.isBlocked,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (peer.isBusyInCall) AmberSecurity.copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.primaryContainer
                    )
                    .testTag("radar_call_${peer.deviceId}")
            ) {
                Icon(
                    Icons.Filled.Call,
                    contentDescription = if (peer.isBusyInCall) "مشغول" else "اتصال",
                    tint = if (peer.isBusyInCall) AmberSecurity else EmeraldPrimary
                )
            }
        }
    }
}
