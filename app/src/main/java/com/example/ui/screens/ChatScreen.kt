package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.crypto.SafetyFingerprint
import com.example.data.local.MessageEntity
import com.example.data.local.PeerEntity
import com.example.ui.components.CipherBadgePill
import com.example.ui.components.PeerAvatar
import com.example.ui.components.SafetyVerificationDialog
import com.example.ui.components.formatDuration
import com.example.ui.components.formatFileSize
import com.example.ui.components.formatTimeShort
import com.example.ui.theme.AmberSecurity
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CyanCipher
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldDeep
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.io.File

private val QuickReactions = listOf("👍", "❤️", "😂", "🔐", "🔥", "👏")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    peer: PeerEntity?,
    messages: List<MessageEntity>,
    safetyFingerprint: SafetyFingerprint,
    isPeerTyping: Boolean,
    defaultDisappearingSeconds: Int,
    isRecordingVoiceNote: Boolean,
    recordingSeconds: Int,
    recordingLevel: Float,
    playingVoiceMessageId: String?,
    voicePlaybackProgress: Float,
    onBack: () -> Unit,
    onStartCall: (PeerEntity) -> Unit,
    onSendText: (text: String, replyToId: String, replyPreview: String, disappearingSec: Int) -> Unit,
    onSendImageUri: (android.net.Uri, Int) -> Unit,
    onSendFileUri: (android.net.Uri, Int) -> Unit,
    onStartRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    onSendVoiceNote: (Int) -> Unit,
    onToggleVoicePlayback: (String, String) -> Unit,
    onTypingChanged: (Boolean) -> Unit,
    onReactMessage: (String, String) -> Unit,
    onDeleteLocal: (String) -> Unit,
    onDeleteForEveryone: (String) -> Unit,
    onToggleVerify: (Boolean) -> Unit,
    onTogglePin: (Boolean) -> Unit,
    onToggleBlock: (Boolean) -> Unit,
    onClearChat: () -> Unit
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()

    var inputText by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<MessageEntity?>(null) }
    var selectedMessage by remember { mutableStateOf<MessageEntity?>(null) }
    var inspectedMessage by remember { mutableStateOf<MessageEntity?>(null) }
    var previewImagePath by remember { mutableStateOf<String?>(null) }
    var showVerifyDialog by remember { mutableStateOf(false) }
    var showAttachSheet by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showTimerDialog by remember { mutableStateOf(false) }
    var chatDisappearingSeconds by remember(defaultDisappearingSeconds) {
        mutableIntStateOf(defaultDisappearingSeconds)
    }
    var fileToExport by remember { mutableStateOf<MessageEntity?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { onSendImageUri(it, chatDisappearingSeconds) }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { onSendFileUri(it, chatDisappearingSeconds) }
    }

    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { targetUri ->
        val srcPath = fileToExport?.localFilePath
        if (targetUri != null && !srcPath.isNullOrBlank()) {
            runCatching {
                val srcFile = File(srcPath)
                if (srcFile.exists()) {
                    context.contentResolver.openOutputStream(targetUri)?.use { out ->
                        srcFile.inputStream().use { it.copyTo(out) }
                    }
                }
            }
        }
        fileToExport = null
    }

    var pendingAudioAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingAudioAction?.invoke()
        }
        pendingAudioAction = null
    }

    fun runWithMicPermission(action: () -> Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            action()
        } else {
            pendingAudioAction = action
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("chat_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                title = {
                    if (peer != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { showVerifyDialog = true }
                        ) {
                            PeerAvatar(
                                name = peer.displayName,
                                avatarIndex = peer.avatarIndex,
                                isOnline = peer.isOnline,
                                isVerified = peer.isVerified,
                                size = 40.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = peer.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = when {
                                        isPeerTyping -> "يكتب الآن (مشفر)..."
                                        peer.isOnline -> "${peer.ipAddress} • AES-256-GCM"
                                        else -> "غير متصل • ${peer.ipAddress}"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isPeerTyping) EmeraldBright else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (peer != null) {
                        IconButton(
                            onClick = { showVerifyDialog = true },
                            modifier = Modifier.testTag("chat_verify_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Shield,
                                contentDescription = "التحقق من التشفير",
                                tint = if (peer.isVerified) CyanCipher else EmeraldPrimary
                            )
                        }
                        IconButton(
                            onClick = { runWithMicPermission { onStartCall(peer) } },
                            enabled = !peer.isBlocked,
                            modifier = Modifier.testTag("chat_voice_call_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Call,
                                contentDescription = "اتصال صوتي مشفر",
                                tint = EmeraldPrimary
                            )
                        }
                        Box {
                            IconButton(
                                onClick = { showMoreMenu = true },
                                modifier = Modifier.testTag("chat_more_menu_button")
                            ) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "المزيد")
                            }
                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("مؤقت الاختفاء الذاتي") },
                                    leadingIcon = { Icon(Icons.Filled.Timer, contentDescription = null) },
                                    onClick = {
                                        showMoreMenu = false
                                        showTimerDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (peer.isPinned) "إلغاء التثبيت" else "تثبيت المحادثة") },
                                    leadingIcon = { Icon(Icons.Filled.PushPin, contentDescription = null) },
                                    onClick = {
                                        showMoreMenu = false
                                        onTogglePin(!peer.isPinned)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (peer.isBlocked) "إلغاء حظر الجهاز" else "حظر الجهاز") },
                                    leadingIcon = { Icon(Icons.Filled.Block, contentDescription = null) },
                                    onClick = {
                                        showMoreMenu = false
                                        onToggleBlock(!peer.isBlocked)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("مسح المحادثة", color = CrimsonAlert) },
                                    leadingIcon = { Icon(Icons.Filled.DeleteSweep, contentDescription = null, tint = CrimsonAlert) },
                                    onClick = {
                                        showMoreMenu = false
                                        onClearChat()
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                AnimatedVisibility(visible = replyingTo != null) {
                    replyingTo?.let { reply ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Reply,
                                contentDescription = null,
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (reply.isOutgoing) "رد على رسالتك" else "رد على ${peer?.displayName ?: "المرسل"}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = EmeraldPrimary
                                )
                                Text(
                                    text = reply.content.ifBlank { reply.mediaName },
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { replyingTo = null }) {
                                Icon(Icons.Filled.Close, contentDescription = "إلغاء الرد", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                if (peer?.isBlocked == true) {
                    Text(
                        text = "هذا الجهاز محظور. قم بإلغاء الحظر من القائمة لإرسال الرسائل.",
                        style = MaterialTheme.typography.bodySmall,
                        color = CrimsonAlert,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    )
                } else if (isRecordingVoiceNote) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onCancelRecording,
                            modifier = Modifier.testTag("cancel_voice_note_button")
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "إلغاء التسجيل", tint = CrimsonAlert)
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "جاري تسجيل رسالة صوتية مشفرة... ${formatDuration(recordingSeconds)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = CrimsonAlert
                            )
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { recordingLevel.coerceIn(0.08f, 1f) },
                                color = EmeraldPrimary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = { onSendVoiceNote(chatDisappearingSeconds) },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(EmeraldPrimary)
                                .testTag("send_voice_note_button")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "إرسال الصوت المشفر",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { showAttachSheet = true },
                            modifier = Modifier.testTag("chat_attach_button")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "إرفاق ملف أو صورة", tint = EmeraldPrimary)
                        }

                        OutlinedTextField(
                            value = inputText,
                            onValueChange = {
                                inputText = it
                                onTypingChanged(it.isNotBlank())
                            },
                            placeholder = { Text("رسالة مشفرة...") },
                            maxLines = 4,
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chat_message_input")
                        )

                        Spacer(Modifier.width(8.dp))

                        if (inputText.isBlank()) {
                            IconButton(
                                onClick = { runWithMicPermission { onStartRecording() } },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .testTag("chat_mic_button")
                            ) {
                                Icon(
                                    Icons.Filled.Mic,
                                    contentDescription = "تسجيل رسالة صوتية",
                                    tint = EmeraldPrimary
                                )
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    val r = replyingTo
                                    onSendText(
                                        inputText,
                                        r?.id ?: "",
                                        r?.content?.ifBlank { r.mediaName } ?: "",
                                        chatDisappearingSeconds
                                    )
                                    inputText = ""
                                    replyingTo = null
                                    onTypingChanged(false)
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(EmeraldPrimary)
                                    .testTag("chat_send_button")
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "إرسال مشفر",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { showVerifyDialog = true }
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CipherBadgePill(
                                text = if (peer?.isVerified == true) "هوية موثّقة • AES-256-GCM" else "مشفر طرف لطرف • اضغط للتحقق",
                                isVerified = peer?.isVerified == true
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "جميع الرسائل والملفات والمكالمات في هذه الجلسة مشفرة بمفاتيح ECDH P-256 محلياً ولا تمر عبر أي خادم خارجي.",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (chatDisappearingSeconds > 0) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "⏳ مؤقت الاختفاء الذاتي نشط: ${chatDisappearingSeconds} ثانية",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AmberSecurity
                                )
                            }
                        }
                    }
                }

                items(messages, key = { it.id }) { msg ->
                    if (msg.type == "SYSTEM_SECURITY") {
                        SecurityEventPill(text = msg.content)
                    } else {
                        MessageBubble(
                            message = msg,
                            isPlayingVoice = playingVoiceMessageId == msg.id,
                            voiceProgress = if (playingVoiceMessageId == msg.id) voicePlaybackProgress else 0f,
                            onPlayVoice = { onToggleVoicePlayback(msg.id, msg.localFilePath) },
                            onPreviewImage = { previewImagePath = msg.localFilePath },
                            onExportFile = {
                                fileToExport = msg
                                saveFileLauncher.launch(msg.mediaName.ifBlank { "hisn_file" })
                            },
                            onClickOrLongPress = { selectedMessage = msg }
                        )
                    }
                }
            }
        }
    }

    if (showVerifyDialog && peer != null) {
        SafetyVerificationDialog(
            peer = peer,
            fingerprint = safetyFingerprint,
            onToggleVerify = onToggleVerify,
            onDismiss = { showVerifyDialog = false }
        )
    }

    if (showAttachSheet) {
        ModalBottomSheet(onDismissRequest = { showAttachSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("إرسال مرفق مشفر", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            showAttachSheet = false
                            photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                        .testTag("attach_image_option")
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Image, contentDescription = null, tint = EmeraldPrimary)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("صورة من المعرض", style = MaterialTheme.typography.titleMedium)
                            Text("تُشفر بالكامل بـ AES-256-GCM قبل الإرسال", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            showAttachSheet = false
                            filePickerLauncher.launch(arrayOf("*/*"))
                        }
                        .testTag("attach_file_option")
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AttachFile, contentDescription = null, tint = CyanCipher)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("مستند أو ملف", style = MaterialTheme.typography.titleMedium)
                            Text("نقل مباشر عبر TCP المشفر حتى 10 ميجابايت", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    selectedMessage?.let { msg ->
        ModalBottomSheet(onDismissRequest = { selectedMessage = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Quick Emoji Reactions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    QuickReactions.forEach { emoji ->
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .clickable {
                                    onReactMessage(msg.id, emoji)
                                    selectedMessage = null
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(text = emoji, fontSize = 20.sp)
                            }
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                SheetActionRow(Icons.AutoMirrored.Filled.Reply, "رد على الرسالة") {
                    replyingTo = msg
                    selectedMessage = null
                }
                if (msg.content.isNotBlank()) {
                    SheetActionRow(Icons.Filled.ContentCopy, "نسخ النص") {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("hisn_message", msg.content))
                        selectedMessage = null
                    }
                }
                SheetActionRow(Icons.Filled.Security, "فحص بيانات التشفير (Cipher Inspector)") {
                    inspectedMessage = msg
                    selectedMessage = null
                }
                SheetActionRow(Icons.Filled.Delete, "حذف من جهازي فقط") {
                    onDeleteLocal(msg.id)
                    selectedMessage = null
                }
                if (msg.isOutgoing) {
                    SheetActionRow(Icons.Filled.DeleteSweep, "حذف لدى الطرفين", tint = CrimsonAlert) {
                        onDeleteForEveryone(msg.id)
                        selectedMessage = null
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    inspectedMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { inspectedMessage = null },
            icon = { Icon(Icons.Filled.Lock, contentDescription = null, tint = EmeraldPrimary) },
            title = { Text("تفاصيل تشفير الرسالة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("الخوارزمية: ${msg.cipherAlgorithm}", style = MaterialTheme.typography.bodyMedium)
                    Text("معرّف الحزمة: ${msg.id.take(16)}", style = MaterialTheme.typography.labelSmall)
                    Text(
                        text = "معاينة النص المشفر (Hex):\n${msg.encryptedPreviewHex.ifBlank { "AES-GCM-AUTH" }}",
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.bodySmall,
                        color = EmeraldPrimary
                    )
                    Text("الحالة: ${msg.status}", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { inspectedMessage = null }) { Text("إغلاق") }
            }
        )
    }

    previewImagePath?.let { path ->
        AlertDialog(
            onDismissRequest = { previewImagePath = null },
            title = { Text("صورة مشفرة") },
            text = {
                AsyncImage(
                    model = File(path),
                    contentDescription = "معاينة الصورة",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
            },
            confirmButton = {
                TextButton(onClick = { previewImagePath = null }) { Text("إغلاق") }
            }
        )
    }

    if (showTimerDialog) {
        val options = listOf(0 to "معطل", 15 to "15 ثانية", 60 to "دقيقة", 300 to "5 دقائق", 3600 to "ساعة")
        AlertDialog(
            onDismissRequest = { showTimerDialog = false },
            title = { Text("مؤقت الرسائل ذاتية الاختفاء") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    options.forEach { (sec, label) ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (chatDisappearingSeconds == sec) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    chatDisappearingSeconds = sec
                                    showTimerDialog = false
                                }
                        ) {
                            Text(
                                text = label,
                                modifier = Modifier.padding(14.dp),
                                fontWeight = if (chatDisappearingSeconds == sec) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTimerDialog = false }) { Text("إغلاق") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: MessageEntity,
    isPlayingVoice: Boolean,
    voiceProgress: Float,
    onPlayVoice: () -> Unit,
    onPreviewImage: () -> Unit,
    onExportFile: () -> Unit,
    onClickOrLongPress: () -> Unit
) {
    val isOut = message.isOutgoing
    val bubbleColor = if (isOut) {
        EmeraldDeep.copy(alpha = 0.88f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val textColor = if (isOut) Color(0xFFECFDF5) else MaterialTheme.colorScheme.onSurface
    val shape = if (isOut) {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp)
    } else {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 20.dp)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOut) Arrangement.End else Arrangement.Start
    ) {
        Column(
            horizontalAlignment = if (isOut) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 310.dp)
        ) {
            Surface(
                shape = shape,
                color = bubbleColor,
                modifier = Modifier
                    .clip(shape)
                    .combinedClickable(
                        onClick = {
                            if (message.type == "IMAGE" && message.localFilePath.isNotBlank()) onPreviewImage()
                            else if (message.type == "VOICE_NOTE" && message.localFilePath.isNotBlank()) onPlayVoice()
                            else onClickOrLongPress()
                        },
                        onLongClick = onClickOrLongPress
                    )
                    .testTag("message_bubble_${message.id}")
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    if (message.replyPreview.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color.Black.copy(alpha = 0.22f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                        ) {
                            Text(
                                text = "↩ ${message.replyPreview}",
                                style = MaterialTheme.typography.bodySmall,
                                color = EmeraldBright,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    when (message.type) {
                        "IMAGE" -> {
                            if (message.localFilePath.isNotBlank()) {
                                AsyncImage(
                                    model = File(message.localFilePath),
                                    contentDescription = "صورة مشفرة",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(190.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                )
                            }
                            if (message.content.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(text = message.content, color = textColor, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        "FILE" -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable(onClick = onExportFile)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(EmeraldPrimary.copy(alpha = 0.22f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.Download, contentDescription = "حفظ الملف", tint = EmeraldBright)
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = message.mediaName.ifBlank { "ملف مشفر" },
                                        color = textColor,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${formatFileSize(message.mediaSize)} • اضغط للحفظ",
                                        color = textColor.copy(alpha = 0.72f),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                        "VOICE_NOTE" -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.widthIn(min = 200.dp)
                            ) {
                                IconButton(
                                    onClick = onPlayVoice,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(EmeraldPrimary)
                                ) {
                                    Icon(
                                        imageVector = if (isPlayingVoice) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = "تشغيل الصوت",
                                        tint = Color.Black
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    LinearProgressIndicator(
                                        progress = { if (isPlayingVoice) voiceProgress else 0f },
                                        color = EmeraldBright,
                                        trackColor = textColor.copy(alpha = 0.2f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(5.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "🎙️ رسالة صوتية مشفرة • ${message.content}",
                                        color = textColor.copy(alpha = 0.8f),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                        else -> {
                            Text(
                                text = message.content,
                                color = textColor,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (message.expiresAt > 0L) {
                            Icon(
                                imageVector = Icons.Filled.Timer,
                                contentDescription = "رسالة مؤقتة",
                                tint = AmberSecurity,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "مشفر",
                            tint = textColor.copy(alpha = 0.6f),
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = formatTimeShort(message.timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = textColor.copy(alpha = 0.7f)
                        )
                        if (isOut) {
                            val (statusIcon, statusTint) = when (message.status) {
                                "READ" -> Icons.Filled.DoneAll to CyanCipher
                                "DELIVERED" -> Icons.Filled.DoneAll to textColor.copy(alpha = 0.75f)
                                "SENT" -> Icons.Filled.Check to textColor.copy(alpha = 0.75f)
                                "FAILED" -> Icons.Filled.ErrorOutline to CrimsonAlert
                                else -> Icons.Filled.Schedule to textColor.copy(alpha = 0.6f)
                            }
                            Icon(
                                imageVector = statusIcon,
                                contentDescription = message.status,
                                tint = statusTint,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            if (message.reaction.isNotBlank()) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = 2.dp,
                    modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 2.dp)
                ) {
                    Text(
                        text = message.reaction,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SecurityEventPill(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Text(
                text = "🛡️ $text",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun SheetActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(text = label, color = tint, style = MaterialTheme.typography.bodyLarge)
    }
}
