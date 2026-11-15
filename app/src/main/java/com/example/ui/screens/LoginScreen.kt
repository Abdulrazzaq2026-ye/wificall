package com.example.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.prefs.UserSettings
import com.example.network.LocalNetworkInfo
import com.example.ui.components.CipherBadgePill
import com.example.ui.components.DeveloperCreditCard
import com.example.ui.components.PeerAvatar
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldPrimary

@Composable
fun LoginScreen(
    settings: UserSettings,
    networkInfo: LocalNetworkInfo,
    onLogin: (name: String, userCode: String, avatarIndex: Int) -> Unit,
    onExitApp: () -> Unit
) {
    var name by remember(settings.displayName) { mutableStateOf(settings.displayName) }
    var userCode by remember(settings.userCode) { mutableStateOf(settings.userCode) }
    var avatarIndex by remember(settings.avatarIndex) { mutableIntStateOf(settings.avatarIndex) }
    var nameError by remember { mutableStateOf(false) }
    var codeError by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Top Hero Header
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
                                .height(150.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0x99070D0C), Color(0xEE091412))
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CipherBadgePill(text = "ECDH P-256 • AES-256-GCM", isVerified = true)
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "اتصال wifi • الشبكة المحلية المشفرة",
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Wifi,
                                    contentDescription = null,
                                    tint = EmeraldBright,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "${networkInfo.ipAddress}:${settings.tcpPort}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldBright
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Login Form Card
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "بيانات الدخول إلى الشبكة المحلية",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "أدخل اسمك ورقمك التعريفي ليتمكن المتصلون على نفس الشبكة من التعرف عليك والاتصال بك بأمان.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        // Avatar Picker
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            (0..5).forEach { idx ->
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .border(
                                            width = if (avatarIndex == idx) 3.dp else 0.dp,
                                            color = if (avatarIndex == idx) EmeraldPrimary else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable { avatarIndex = idx }
                                        .padding(3.dp)
                                ) {
                                    PeerAvatar(
                                        name = name.ifBlank { "ح" },
                                        avatarIndex = idx,
                                        isOnline = false,
                                        size = 42.dp
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = name,
                            onValueChange = {
                                name = it
                                nameError = false
                            },
                            label = { Text("الاسم") },
                            placeholder = { Text("مثال: عبدالرزاق عزي") },
                            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                            isError = nameError,
                            supportingText = if (nameError) {
                                { Text("يرجى إدخال الاسم للدخول") }
                            } else null,
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_name_input")
                        )

                        OutlinedTextField(
                            value = userCode,
                            onValueChange = {
                                userCode = it.take(16)
                                codeError = false
                            },
                            label = { Text("الرقم التعريفي") },
                            placeholder = { Text("مثال: 1001") },
                            leadingIcon = { Icon(Icons.Filled.Badge, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                            isError = codeError,
                            supportingText = if (codeError) {
                                { Text("يرجى إدخال الرقم التعريفي") }
                            } else null,
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_id_input")
                        )

                        Button(
                            onClick = {
                                val cleanName = name.trim()
                                val cleanCode = userCode.trim()
                                nameError = cleanName.isBlank()
                                codeError = cleanCode.isBlank()
                                if (!nameError && !codeError) {
                                    onLogin(cleanName, cleanCode, avatarIndex)
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("login_submit_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("دخول إلى الشبكة المحلية", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onExitApp,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonAlert),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("login_exit_button")
                        ) {
                            Icon(Icons.Filled.PowerSettingsNew, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("إغلاق البرنامج")
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            DeveloperCreditCard()
        }
    }
}
