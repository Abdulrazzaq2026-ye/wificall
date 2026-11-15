package com.example.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.prefs.UserSettings
import com.example.ui.components.CipherBadgePill
import com.example.ui.components.DeveloperCreditCard
import com.example.ui.components.PeerAvatar
import com.example.ui.components.ProfileEditDialog
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CyanCipher
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldPrimary

private val DisappearingOptions = listOf(
    0 to "معطل",
    30 to "30 ثانية",
    300 to "5 دقائق",
    3600 to "ساعة",
    86400 to "24 ساعة"
)

@Composable
fun SecuritySettingsScreen(
    settings: UserSettings,
    publicKeyShortHex: String,
    onBack: () -> Unit,
    onRequestExitApp: () -> Unit,
    onLogout: () -> Unit,
    onUpdateProfile: (String, String, String, Int) -> Unit,
    onRotateKeys: () -> Unit,
    onSetDisappearingSeconds: (Int) -> Unit,
    onSetDarkTheme: (Boolean) -> Unit,
    onSetAutoAcceptVerified: (Boolean) -> Unit,
    onSetSoundEnabled: (Boolean) -> Unit,
    onSetVibrationEnabled: (Boolean) -> Unit,
    onSetAppLockPin: (String) -> Unit,
    onPanicWipe: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showProfileDialog by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showWipeConfirm by remember { mutableStateOf(false) }

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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("security_back_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                        }
                        Text(
                            text = "الأمان والخصوصية",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(
                        onClick = onRequestExitApp,
                        modifier = Modifier.testTag("security_exit_button")
                    ) {
                        Icon(Icons.Filled.PowerSettingsNew, contentDescription = "إغلاق البرنامج", tint = CrimsonAlert)
                    }
                }
            }

            // Identity Card
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PeerAvatar(
                                name = settings.displayName,
                                avatarIndex = settings.avatarIndex,
                                isOnline = true,
                                isVerified = true,
                                size = 58.dp
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = settings.displayName,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "الرقم التعريفي: ${settings.userCode}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = EmeraldBright
                                )
                                Text(
                                    text = settings.statusMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(4.dp))
                                CipherBadgePill(
                                    text = "ECDH #${publicKeyShortHex}",
                                    isVerified = true
                                )
                            }
                            IconButton(
                                onClick = { showProfileDialog = true },
                                modifier = Modifier.testTag("edit_profile_button")
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = "تعديل الهوية")
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        OutlinedButton(
                            onClick = onRotateKeys,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("rotate_keys_button")
                        ) {
                            Icon(Icons.Filled.Autorenew, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("توليد وتدوير مفاتيح التشفير (ECDH P-256)")
                        }

                        Spacer(Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = onLogout,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("logout_switch_identity_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("العودة لشاشة الدخول (تغيير الاسم أو الرقم التعريفي)")
                        }
                    }
                }
            }

            // Disappearing Messages Timer
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Timer, contentDescription = null, tint = CyanCipher)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "المؤقت الافتراضي للاختفاء الذاتي",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "حذف الرسائل تلقائياً لدى الطرفين بعد الوقت المحدد",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(DisappearingOptions) { (seconds, label) ->
                                FilterChip(
                                    selected = settings.disappearingSeconds == seconds,
                                    onClick = { onSetDisappearingSeconds(seconds) },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                }
            }

            // Security Toggles
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        SettingSwitchRow(
                            icon = Icons.Filled.DarkMode,
                            title = "المظهر الداكن (Obsidian Cipher)",
                            subtitle = "وضع التشفير الليلي عالي التباين",
                            checked = settings.isDarkTheme,
                            onCheckedChange = onSetDarkTheme,
                            testTag = "switch_dark_theme"
                        )
                        SettingSwitchRow(
                            icon = Icons.Filled.VerifiedUser,
                            title = "رد تلقائي للمكالمات الموثقة",
                            subtitle = "قبول المكالمات الواردة من الأجهزة الموثقة تلقائياً",
                            checked = settings.autoAcceptVerifiedCalls,
                            onCheckedChange = onSetAutoAcceptVerified,
                            testTag = "switch_auto_accept"
                        )
                        SettingSwitchRow(
                            icon = Icons.Filled.NotificationsActive,
                            title = "نغمة الرنين والتنبيه",
                            subtitle = "تشغيل صوت رنين للمكالمات الواردة والصادرة",
                            checked = settings.soundEnabled,
                            onCheckedChange = onSetSoundEnabled,
                            testTag = "switch_sound"
                        )
                        SettingSwitchRow(
                            icon = Icons.Filled.Vibration,
                            title = "الاهتزاز عند المكالمة",
                            subtitle = "اهتزاز تنبيهي عند رنين الشبكة المحلية",
                            checked = settings.vibrationEnabled,
                            onCheckedChange = onSetVibrationEnabled,
                            testTag = "switch_vibration"
                        )
                    }
                }
            }

            // App Lock PIN
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = EmeraldPrimary)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "قفل التطبيق برمز PIN",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (settings.appLockPin.isBlank()) "غير مفعّل حالياً" else "مفعّل • يطلب الرمز عند الفتح",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = { showPinDialog = true },
                            modifier = Modifier.testTag("configure_pin_button")
                        ) {
                            Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (settings.appLockPin.isBlank()) "تعيين" else "تعديل")
                        }
                    }
                }
            }

            // Developer Credit Card
            item {
                DeveloperCreditCard()
            }

            // Panic Wipe & Exit App Buttons
            item {
                Button(
                    onClick = { showWipeConfirm = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = CrimsonAlert
                    ),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("panic_wipe_button")
                ) {
                    Icon(Icons.Filled.DeleteForever, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("إتلاف فوري لجميع البيانات والمفاتيح (Panic Wipe)", fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onRequestExitApp,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonAlert),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("settings_exit_app_button")
                ) {
                    Icon(Icons.Filled.PowerSettingsNew, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("إغلاق البرنامج نهائياً", fontWeight = FontWeight.Bold)
                }
            }

            item { Spacer(Modifier.height(76.dp)) }
        }
    }

    if (showProfileDialog) {
        ProfileEditDialog(
            currentName = settings.displayName,
            currentUserCode = settings.userCode,
            currentStatus = settings.statusMessage,
            currentAvatar = settings.avatarIndex,
            onSave = onUpdateProfile,
            onDismiss = { showProfileDialog = false }
        )
    }

    if (showPinDialog) {
        var pinText by remember { mutableStateOf(settings.appLockPin) }
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("إعداد رمز قفل التطبيق") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "أدخل 4 إلى 8 أرقام لقفل التطبيق، أو اتركه فارغاً لتعطيل القفل.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = pinText,
                        onValueChange = { pinText = it.filter { c -> c.isDigit() }.take(8) },
                        label = { Text("رمز PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (pinText.isEmpty() || pinText.length >= 4) {
                        onSetAppLockPin(pinText)
                        showPinDialog = false
                    }
                }) {
                    Text("حفظ")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) { Text("إلغاء") }
            }
        )
    }

    if (showWipeConfirm) {
        AlertDialog(
            onDismissRequest = { showWipeConfirm = false },
            title = { Text("تأكيد الإتلاف الفوري") },
            text = {
                Text("سيتم حذف جميع المحادثات والوسائط وسجل المكالمات نهائياً وتدوير مفاتيح التشفير. لا يمكن التراجع عن هذا الإجراء.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onPanicWipe()
                        showWipeConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonAlert)
                ) {
                    Text("إتلاف الآن")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWipeConfirm = false }) { Text("إلغاء") }
            }
        )
    }
}

@Composable
private fun SettingSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
