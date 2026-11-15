package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.crypto.SafetyFingerprint
import com.example.data.local.PeerEntity
import com.example.ui.theme.CyanCipher
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val AvatarGradients = listOf(
    listOf(Color(0xFF10B981), Color(0xFF047857)),
    listOf(Color(0xFF06B6D4), Color(0xFF0369A1)),
    listOf(Color(0xFF8B5CF6), Color(0xFF5B21B6)),
    listOf(Color(0xFFF59E0B), Color(0xFFB45309)),
    listOf(Color(0xFFEC4899), Color(0xFF9D174D)),
    listOf(Color(0xFF14B8A6), Color(0xFF0F766E))
)

@Composable
fun PeerAvatar(
    name: String,
    avatarIndex: Int,
    isOnline: Boolean,
    isVerified: Boolean = false,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier
) {
    val gradient = AvatarGradients[avatarIndex.mod(AvatarGradients.size)]
    val initial = name.trim().firstOrNull()?.uppercase() ?: "؟"

    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(Brush.linearGradient(gradient)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.4f).sp
            )
        }
        if (isOnline) {
            Box(
                modifier = Modifier
                    .size(size * 0.28f)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(EmeraldBright)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
            )
        }
        if (isVerified) {
            Box(
                modifier = Modifier
                    .size(size * 0.34f)
                    .align(Alignment.TopEnd)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(1.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.VerifiedUser,
                    contentDescription = "موثّق",
                    tint = CyanCipher,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
fun CipherBadgePill(
    text: String = "AES-256-GCM",
    isVerified: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = if (isVerified) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (isVerified) EmeraldPrimary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (isVerified) Icons.Filled.Shield else Icons.Filled.Lock,
                contentDescription = null,
                tint = if (isVerified) EmeraldPrimary else MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = if (isVerified) EmeraldBright else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SafetyVerificationDialog(
    peer: PeerEntity,
    fingerprint: SafetyFingerprint,
    onToggleVerify: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.Security,
                contentDescription = null,
                tint = EmeraldPrimary,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "التحقق من التشفير طرف لطرف",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "قارن الرموز والأرقام التالية مع ${peer.displayName} للتأكد من عدم وجود أي وسيط على الشبكة المحلية:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        fingerprint.emojis.forEach { emoji ->
                            Text(text = emoji, fontSize = 24.sp)
                        }
                    }
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = fingerprint.safetyNumber,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 13.sp,
                        lineHeight = 22.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Text(
                    text = "البصمة المختصرة: ${fingerprint.shortHex}  •  ECDH P-256 + AES-256-GCM",
                    style = MaterialTheme.typography.labelSmall,
                    color = EmeraldPrimary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onToggleVerify(!peer.isVerified)
                    onDismiss()
                },
                modifier = Modifier.testTag("confirm_verify_peer_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (peer.isVerified) MaterialTheme.colorScheme.error else EmeraldPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(if (peer.isVerified) "إلغاء التوثيق" else "تأكيد ومطابقة الهوية")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق")
            }
        }
    )
}

@Composable
fun ManualConnectDialog(
    onConnect: (ip: String, port: String) -> Unit,
    onDismiss: () -> Unit
) {
    var ip by remember { mutableStateOf("192.168.1.") }
    var port by remember { mutableStateOf("45889") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("اتصال مباشر عبر عنوان IP") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "أدخل عنوان الشبكة المحلية (IPv4) ومنفذ TCP للجهاز الآخر لإتمام مصافحة المفاتيح فوراً:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text("عنوان IP المحلي") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_ip_input")
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter { ch -> ch.isDigit() } },
                    label = { Text("منفذ TCP") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_port_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConnect(ip, port)
                    onDismiss()
                },
                modifier = Modifier.testTag("manual_connect_submit_button")
            ) {
                Text("اتصال ومصافحة")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
fun ProfileEditDialog(
    currentName: String,
    currentUserCode: String,
    currentStatus: String,
    currentAvatar: Int,
    onSave: (String, String, String, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    var userCode by remember { mutableStateOf(currentUserCode) }
    var status by remember { mutableStateOf(currentStatus) }
    var avatar by remember { mutableIntStateOf(currentAvatar) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعديل الهوية والرقم التعريفي") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    AvatarGradients.indices.forEach { idx ->
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .border(
                                    width = if (avatar == idx) 3.dp else 0.dp,
                                    color = if (avatar == idx) EmeraldPrimary else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { avatar = idx }
                                .padding(3.dp)
                        ) {
                            PeerAvatar(name = name, avatarIndex = idx, isOnline = false, size = 38.dp)
                        }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("الاسم الظاهر على الشبكة") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = userCode,
                    onValueChange = { userCode = it.take(16) },
                    label = { Text("الرقم التعريفي") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = status,
                    onValueChange = { status = it },
                    label = { Text("رسالة الحالة") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(name, userCode, status, avatar)
                onDismiss()
            }) {
                Text("حفظ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
fun ExitAppConfirmDialog(
    onConfirmExit: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.PowerSettingsNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(34.dp)
            )
        },
        title = { Text("إغلاق البرنامج") },
        text = {
            Text("هل تريد إيقاف الاتصال بالشبكة المحلية وإغلاق تطبيق «اتصال wifi»؟")
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    onConfirmExit()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag("confirm_exit_app_button")
            ) {
                Text("إغلاق البرنامج")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
fun DeveloperCreditCard(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.75f),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = EmeraldPrimary.copy(alpha = 0.35f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Code,
                contentDescription = null,
                tint = EmeraldPrimary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "تطوير م/عبدالرزاق عزي",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun AppLockOverlay(
    pinError: Boolean,
    onUnlock: (String) -> Unit
) {
    var pinInput by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = "تطبيق مقفل",
                    tint = EmeraldPrimary,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = "اتصال wifi مقفل للخصوصية",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "أدخل رمز الحماية لفتح المحادثات والمفاتيح المحلية",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = pinInput,
                onValueChange = { pinInput = it.filter { c -> c.isDigit() }.take(8) },
                label = { Text("رمز PIN") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                isError = pinError,
                supportingText = if (pinError) {
                    { Text("الرمز غير صحيح، حاول مرة أخرى") }
                } else null,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .testTag("app_lock_pin_input")
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onUnlock(pinInput) },
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .height(50.dp)
                    .testTag("app_lock_unlock_button")
            ) {
                Text("فتح القفل")
            }
        }
    }
}

fun formatTimeShort(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    val fmt = SimpleDateFormat("hh:mm a", Locale("ar"))
    return fmt.format(Date(timestamp))
}

fun formatDuration(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return "%02d:%02d".format(mins, secs)
}

fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f))
    }
}
