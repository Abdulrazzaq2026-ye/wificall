package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.model.ActiveCallInfo
import com.example.data.model.CallStatus
import com.example.ui.components.CipherBadgePill
import com.example.ui.components.PeerAvatar
import com.example.ui.components.formatDuration
import com.example.ui.theme.AmberSecurity
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CyanCipher
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.JetBrainsMonoFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallScreen(
    callInfo: ActiveCallInfo,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onRejectWithMessage: (String) -> Unit,
    onEndCall: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleHold: () -> Unit
) {
    val context = LocalContext.current
    var showCipherDetails by remember { mutableStateOf(false) }
    var showQuickReplySheet by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onAccept()
    }

    val animatedLevel by animateFloatAsState(
        targetValue = maxOf(callInfo.localAudioLevel, callInfo.remoteAudioLevel),
        label = "audio_pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF061210),
                        Color(0xFF0A1D19),
                        Color(0xFF060C0B)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Encryption Header
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF102823),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { showCipherDetails = true }
                        .testTag("call_cipher_inspector_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = EmeraldBright,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "مكالمة مشفرة • AES-256-GCM",
                            style = MaterialTheme.typography.labelMedium,
                            color = EmeraldBright
                        )
                    }
                }

                if (callInfo.safetyEmojis.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White.copy(alpha = 0.06f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            callInfo.safetyEmojis.forEach { Text(it, fontSize = 16.sp) }
                            Text(
                                text = "#${callInfo.safetyShortHex}",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 11.sp,
                                color = CyanCipher
                            )
                        }
                    }
                }
            }

            // Center Avatar + Audio Reactive Pulse Rings + Name & Timer
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier.size(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val base = size.minDimension / 2f
                        drawCircle(
                            color = EmeraldPrimary.copy(alpha = 0.14f + animatedLevel * 0.18f),
                            radius = base * (0.68f + animatedLevel * 0.28f)
                        )
                        drawCircle(
                            color = CyanCipher.copy(alpha = 0.12f + animatedLevel * 0.14f),
                            radius = base * (0.56f + animatedLevel * 0.18f)
                        )
                    }
                    PeerAvatar(
                        name = callInfo.peerName.ifBlank { "مستخدم" },
                        avatarIndex = callInfo.peerAvatarIndex,
                        isOnline = true,
                        isVerified = true,
                        size = 112.dp
                    )
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = callInfo.peerName.ifBlank { "جهاز محلي" },
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${callInfo.peerIp}:${callInfo.peerUdpPort}",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 12.sp,
                    color = Color(0xFF94B5AA)
                )

                Spacer(Modifier.height(12.dp))

                val statusLabel = when (callInfo.status) {
                    CallStatus.OUTGOING_RINGING -> "جاري الرنين المشفر..."
                    CallStatus.INCOMING_RINGING -> "مكالمة صوتية مشفرة واردة"
                    CallStatus.ACTIVE -> formatDuration(callInfo.durationSeconds)
                    CallStatus.ON_HOLD -> "المكالمة قيد الانتظار • ${formatDuration(callInfo.durationSeconds)}"
                    CallStatus.ENDED -> callInfo.endReason.ifBlank { "انتهت المكالمة" }
                    CallStatus.IDLE -> ""
                }
                Text(
                    text = statusLabel,
                    style = if (callInfo.status == CallStatus.ACTIVE) {
                        MaterialTheme.typography.headlineSmall.copy(fontFamily = JetBrainsMonoFontFamily)
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                    color = when (callInfo.status) {
                        CallStatus.ON_HOLD -> AmberSecurity
                        CallStatus.ENDED -> CrimsonAlert
                        else -> EmeraldBright
                    }
                )

                if (callInfo.isRemoteMuted || callInfo.isRemoteOnHold) {
                    Spacer(Modifier.height(8.dp))
                    CipherBadgePill(
                        text = if (callInfo.isRemoteOnHold) "الطرف الآخر علّق المكالمة" else "كتم الطرف الآخر الميكروفون",
                        isVerified = false
                    )
                }

                if (callInfo.status == CallStatus.ACTIVE || callInfo.status == CallStatus.ON_HOLD) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "حزم مشفرة مرسلة: ${callInfo.packetsSent}  |  مستلمة: ${callInfo.packetsReceived}",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        color = Color(0xFF8FB8AC)
                    )
                }
            }

            // Bottom Action Controls
            if (callInfo.status == CallStatus.INCOMING_RINGING) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 18.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CallCircleAction(
                        icon = Icons.Filled.CallEnd,
                        label = "رفض",
                        containerColor = CrimsonAlert,
                        iconTint = Color.White,
                        size = 66.dp,
                        testTag = "call_reject_button",
                        onClick = onReject
                    )
                    CallCircleAction(
                        icon = Icons.AutoMirrored.Filled.Message,
                        label = "رد برسالة",
                        containerColor = Color(0xFF1A2D29),
                        iconTint = Color.White,
                        size = 56.dp,
                        testTag = "call_quick_reply_button",
                        onClick = { showQuickReplySheet = true }
                    )
                    CallCircleAction(
                        icon = Icons.Filled.Call,
                        label = "قبول",
                        containerColor = EmeraldPrimary,
                        iconTint = Color(0xFF022C22),
                        size = 66.dp,
                        testTag = "call_accept_button",
                        onClick = {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) onAccept() else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    )
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.padding(bottom = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        CallCircleAction(
                            icon = if (callInfo.isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                            label = if (callInfo.isMuted) "إلغاء الكتم" else "كتم",
                            containerColor = if (callInfo.isMuted) Color.White else Color(0xFF172824),
                            iconTint = if (callInfo.isMuted) Color.Black else Color.White,
                            testTag = "call_mute_button",
                            onClick = onToggleMute
                        )
                        CallCircleAction(
                            icon = Icons.AutoMirrored.Filled.VolumeUp,
                            label = "مكبر الصوت",
                            containerColor = if (callInfo.isSpeakerOn) EmeraldPrimary else Color(0xFF172824),
                            iconTint = if (callInfo.isSpeakerOn) Color.Black else Color.White,
                            testTag = "call_speaker_button",
                            onClick = onToggleSpeaker
                        )
                        CallCircleAction(
                            icon = if (callInfo.status == CallStatus.ON_HOLD) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                            label = if (callInfo.status == CallStatus.ON_HOLD) "استئناف" else "تعليق",
                            containerColor = if (callInfo.status == CallStatus.ON_HOLD) AmberSecurity else Color(0xFF172824),
                            iconTint = if (callInfo.status == CallStatus.ON_HOLD) Color.Black else Color.White,
                            testTag = "call_hold_button",
                            onClick = onToggleHold
                        )
                        CallCircleAction(
                            icon = Icons.Filled.Security,
                            label = "التشفير",
                            containerColor = Color(0xFF172824),
                            iconTint = CyanCipher,
                            testTag = "call_security_button",
                            onClick = { showCipherDetails = true }
                        )
                    }

                    CallCircleAction(
                        icon = Icons.Filled.CallEnd,
                        label = "إنهاء المكالمة",
                        containerColor = CrimsonAlert,
                        iconTint = Color.White,
                        size = 70.dp,
                        testTag = "call_end_button",
                        onClick = onEndCall
                    )
                }
            }
        }
    }

    if (showCipherDetails) {
        AlertDialog(
            onDismissRequest = { showCipherDetails = false },
            icon = { Icon(Icons.Filled.Security, contentDescription = null, tint = EmeraldPrimary) },
            title = { Text("مراقب تشفير المكالمة المباشر") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("تبادل المفاتيح: ECDH (secp256r1 / P-256)")
                    Text("تشفير الصوت: AES-256-GCM لكل إطار صوتي (20ms)")
                    Text("بروتوكول النقل: UDP Direct LAN (${callInfo.peerIp}:${callInfo.peerUdpPort})")
                    Text("حزم مرسلة: ${callInfo.packetsSent}")
                    Text("حزم مستلمة ومصادق عليها: ${callInfo.packetsReceived}")
                    Text("البصمة: #${callInfo.safetyShortHex}", fontFamily = JetBrainsMonoFontFamily)
                }
            },
            confirmButton = {
                TextButton(onClick = { showCipherDetails = false }) { Text("إغلاق") }
            }
        )
    }

    if (showQuickReplySheet) {
        val quickReplies = listOf(
            "لا أستطيع التحدث الآن، سأتصل بك لاحقاً",
            "أنا في اجتماع، أرسل لي رسالة",
            "سأعاود الاتصال خلال دقائق"
        )
        ModalBottomSheet(onDismissRequest = { showQuickReplySheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("رفض مع رسالة مشفرة", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                quickReplies.forEach { reply ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                showQuickReplySheet = false
                                onRejectWithMessage(reply)
                            }
                    ) {
                        Text(text = reply, modifier = Modifier.padding(14.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CallCircleAction(
    icon: ImageVector,
    label: String,
    containerColor: Color,
    iconTint: Color,
    size: androidx.compose.ui.unit.Dp = 58.dp,
    testTag: String,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(containerColor)
                .testTag(testTag)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(size * 0.45f)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFFD1EAE2)
        )
    }
}
