package com.example.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.data.local.PeerEntity
import com.example.data.model.CallStatus
import com.example.data.model.MainTab
import com.example.ui.components.AppLockOverlay
import com.example.ui.components.ExitAppConfirmDialog
import com.example.ui.screens.CallScreen
import com.example.ui.screens.CallsScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.ChatsScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.NetworkRadarScreen
import com.example.ui.screens.SecuritySettingsScreen
import com.example.ui.theme.HisnTheme
import com.example.ui.viewmodel.HisnViewModel
import kotlinx.serialization.Serializable

@Serializable
object MainRoute

@Serializable
data class ChatRoute(val peerId: String)

private data class TabItem(
    val tab: MainTab,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val MainTabs = listOf(
    TabItem(MainTab.CHATS, "المحادثات", Icons.Filled.Chat, Icons.Outlined.Chat, "nav_tab_chats"),
    TabItem(MainTab.NETWORK, "المتصلون", Icons.Filled.Radar, Icons.Outlined.Radar, "nav_tab_network"),
    TabItem(MainTab.CALLS, "المكالمات", Icons.Filled.Call, Icons.Outlined.Call, "nav_tab_calls"),
    TabItem(MainTab.SECURITY, "الخصوصية", Icons.Filled.Security, Icons.Outlined.Security, "nav_tab_security")
)

@Composable
fun HisnRootApp(viewModel: HisnViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val peers by viewModel.peers.collectAsStateWithLifecycle()
    val callLogs by viewModel.callLogs.collectAsStateWithLifecycle()
    val networkInfo by viewModel.localNetworkInfo.collectAsStateWithLifecycle()
    val isMonitorServiceRunning by viewModel.isMonitorServiceRunning.collectAsStateWithLifecycle()
    val monitorStatusMessage by viewModel.monitorStatusMessage.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val nsdActive by viewModel.nsdActive.collectAsStateWithLifecycle()
    val udpActive by viewModel.udpActive.collectAsStateWithLifecycle()
    val typingPeers by viewModel.typingPeers.collectAsStateWithLifecycle()
    val activeCall by viewModel.activeCall.collectAsStateWithLifecycle()
    val isAppLocked by viewModel.isAppLocked.collectAsStateWithLifecycle()
    val pinError by viewModel.pinError.collectAsStateWithLifecycle()

    val isRecordingVoiceNote by viewModel.isRecordingVoiceNote.collectAsStateWithLifecycle()
    val recordingSeconds by viewModel.recordingSeconds.collectAsStateWithLifecycle()
    val recordingLevel by viewModel.recordingLevel.collectAsStateWithLifecycle()
    val playingVoiceMessageId by viewModel.playingVoiceMessageId.collectAsStateWithLifecycle()
    val voicePlaybackProgress by viewModel.voicePlaybackProgress.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val navController = rememberNavController()
    var currentTab by rememberSaveable { mutableStateOf(MainTab.CHATS) }
    var showExitDialog by remember { mutableStateOf(false) }

    fun closeApplication() {
        viewModel.shutdownForExit()
        (context as? Activity)?.finishAndRemoveTask()
    }

    var pendingCallPeer by remember { mutableStateOf<PeerEntity?>(null) }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingCallPeer?.let { viewModel.startVoiceCall(it) }
        }
        pendingCallPeer = null
    }

    fun startVoiceCallWithPermission(peer: PeerEntity) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.startVoiceCall(peer)
        } else {
            pendingCallPeer = peer
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val totalUnread = remember(peers) { peers.sumOf { it.unreadCount } }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        HisnTheme(darkTheme = settings.isDarkTheme) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (!settings.isLoggedIn) {
                    LoginScreen(
                        settings = settings,
                        networkInfo = networkInfo,
                        onLogin = viewModel::login,
                        onExitApp = { showExitDialog = true }
                    )
                } else {
                    NavHost(
                        navController = navController,
                        startDestination = MainRoute
                    ) {
                        composable<MainRoute> {
                            BackHandler {
                                if (currentTab != MainTab.CHATS) {
                                    currentTab = MainTab.CHATS
                                } else {
                                    showExitDialog = true
                                }
                            }

                            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                val useRail = maxWidth >= 600.dp

                                Scaffold(
                                    contentWindowInsets = WindowInsets.safeDrawing,
                                    snackbarHost = { SnackbarHost(snackbarHostState) },
                                    bottomBar = {
                                        if (!useRail) {
                                            NavigationBar(
                                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                                            ) {
                                                MainTabs.forEach { item ->
                                                    val selected = currentTab == item.tab
                                                    NavigationBarItem(
                                                        selected = selected,
                                                        onClick = { currentTab = item.tab },
                                                        icon = {
                                                            BadgedBox(
                                                                badge = {
                                                                    if (item.tab == MainTab.CHATS && totalUnread > 0) {
                                                                        Badge { Text(totalUnread.coerceAtMost(99).toString()) }
                                                                    }
                                                                }
                                                            ) {
                                                                Icon(
                                                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                                                    contentDescription = item.label
                                                                )
                                                            }
                                                        },
                                                        label = { Text(item.label) },
                                                        modifier = Modifier.testTag(item.testTag)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                ) { innerPadding ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(innerPadding)
                                    ) {
                                        if (useRail) {
                                            NavigationRail(
                                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                                modifier = Modifier.fillMaxHeight()
                                            ) {
                                                MainTabs.forEach { item ->
                                                    val selected = currentTab == item.tab
                                                    NavigationRailItem(
                                                        selected = selected,
                                                        onClick = { currentTab = item.tab },
                                                        icon = {
                                                            BadgedBox(
                                                                badge = {
                                                                    if (item.tab == MainTab.CHATS && totalUnread > 0) {
                                                                        Badge { Text(totalUnread.coerceAtMost(99).toString()) }
                                                                    }
                                                                }
                                                            ) {
                                                                Icon(
                                                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                                                    contentDescription = item.label
                                                                )
                                                            }
                                                        },
                                                        label = { Text(item.label) },
                                                        modifier = Modifier.testTag(item.testTag)
                                                    )
                                                }
                                            }
                                        }

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                        ) {
                                            when (currentTab) {
                                                MainTab.CHATS -> ChatsScreen(
                                                    peers = peers,
                                                    settings = settings,
                                                    typingPeers = typingPeers,
                                                    networkInfo = networkInfo,
                                                    tcpPort = settings.tcpPort,
                                                    onOpenChat = { peerId ->
                                                        navController.navigate(ChatRoute(peerId))
                                                    },
                                                    onStartCall = ::startVoiceCallWithPermission,
                                                    onOpenNetworkTab = { currentTab = MainTab.NETWORK },
                                                    onConnectManual = { ip, port ->
                                                        viewModel.connectManualPeer(ip, port) { peerId ->
                                                            navController.navigate(ChatRoute(peerId))
                                                        }
                                                    },
                                                    onStartLoopbackTest = {
                                                        viewModel.startLoopbackSelfTest { peerId ->
                                                            navController.navigate(ChatRoute(peerId))
                                                        }
                                                    },
                                                    onRequestExitApp = { showExitDialog = true }
                                                )

                                                MainTab.NETWORK -> NetworkRadarScreen(
                                                    peers = peers,
                                                    settings = settings,
                                                    networkInfo = networkInfo,
                                                    isServiceRunning = isMonitorServiceRunning,
                                                    monitorStatusMessage = monitorStatusMessage,
                                                    isScanning = isScanning,
                                                    nsdActive = nsdActive,
                                                    udpActive = udpActive,
                                                    onBack = { currentTab = MainTab.CHATS },
                                                    onRequestExitApp = { showExitDialog = true },
                                                    onScanNow = viewModel::triggerLanScan,
                                                    onToggleNsd = viewModel::setNsdEnabled,
                                                    onToggleUdp = viewModel::setUdpBroadcastEnabled,
                                                    onToggleStealth = viewModel::setStealthMode,
                                                    onConnectManual = { ip, port ->
                                                        viewModel.connectManualPeer(ip, port) { peerId ->
                                                            navController.navigate(ChatRoute(peerId))
                                                        }
                                                    },
                                                    onStartLoopback = {
                                                        viewModel.startLoopbackSelfTest { peerId ->
                                                            navController.navigate(ChatRoute(peerId))
                                                        }
                                                    },
                                                    onOpenChat = { peerId ->
                                                        navController.navigate(ChatRoute(peerId))
                                                    },
                                                    onStartCall = ::startVoiceCallWithPermission
                                                )

                                                MainTab.CALLS -> CallsScreen(
                                                    callLogs = callLogs,
                                                    peers = peers,
                                                    onBack = { currentTab = MainTab.CHATS },
                                                    onRequestExitApp = { showExitDialog = true },
                                                    onRedialPeer = ::startVoiceCallWithPermission,
                                                    onClearAllLogs = viewModel::clearAllCallLogs
                                                )

                                                MainTab.SECURITY -> SecuritySettingsScreen(
                                                    settings = settings,
                                                    publicKeyShortHex = viewModel.getPublicKeyShortHex(settings.publicKeyBase64),
                                                    onBack = { currentTab = MainTab.CHATS },
                                                    onRequestExitApp = { showExitDialog = true },
                                                    onLogout = viewModel::logout,
                                                    onUpdateProfile = viewModel::updateProfile,
                                                    onRotateKeys = viewModel::rotateIdentityKeys,
                                                    onSetDisappearingSeconds = viewModel::setDisappearingSeconds,
                                                    onSetDarkTheme = viewModel::setDarkTheme,
                                                    onSetAutoAcceptVerified = viewModel::setAutoAcceptVerifiedCalls,
                                                    onSetSoundEnabled = viewModel::setSoundEnabled,
                                                    onSetVibrationEnabled = viewModel::setVibrationEnabled,
                                                    onSetAppLockPin = viewModel::setAppLockPin,
                                                    onPanicWipe = viewModel::panicWipeAllData
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        composable<ChatRoute> { backStackEntry ->
                            val route = backStackEntry.toRoute<ChatRoute>()
                            val peer by viewModel.observePeer(route.peerId)
                                .collectAsStateWithLifecycle(initialValue = null)
                            val messages by viewModel.observeMessages(route.peerId)
                                .collectAsStateWithLifecycle(initialValue = emptyList())

                            LaunchedEffect(route.peerId, messages.size) {
                                viewModel.markChatRead(route.peerId)
                            }

                            val safety = remember(settings.publicKeyBase64, peer?.publicKeyBase64) {
                                viewModel.computeSafetyFingerprint(
                                    settings.publicKeyBase64,
                                    peer?.publicKeyBase64 ?: ""
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .statusBarsPadding()
                            ) {
                                ChatScreen(
                                    peer = peer,
                                    messages = messages,
                                    safetyFingerprint = safety,
                                    isPeerTyping = typingPeers.contains(route.peerId),
                                    defaultDisappearingSeconds = settings.disappearingSeconds,
                                    isRecordingVoiceNote = isRecordingVoiceNote,
                                    recordingSeconds = recordingSeconds,
                                    recordingLevel = recordingLevel,
                                    playingVoiceMessageId = playingVoiceMessageId,
                                    voicePlaybackProgress = voicePlaybackProgress,
                                    onBack = { navController.popBackStack() },
                                    onStartCall = ::startVoiceCallWithPermission,
                                    onSendText = { text, replyId, replyPreview, sec ->
                                        viewModel.sendTextMessage(route.peerId, text, replyId, replyPreview, sec)
                                    },
                                    onSendImageUri = { uri, sec ->
                                        viewModel.sendImageUri(route.peerId, uri, sec)
                                    },
                                    onSendFileUri = { uri, sec ->
                                        viewModel.sendFileUri(route.peerId, uri, sec)
                                    },
                                    onStartRecording = viewModel::startVoiceNoteRecording,
                                    onCancelRecording = viewModel::cancelVoiceNoteRecording,
                                    onSendVoiceNote = { sec -> viewModel.stopAndSendVoiceNote(route.peerId, sec) },
                                    onToggleVoicePlayback = viewModel::toggleVoiceNotePlayback,
                                    onTypingChanged = { typing -> viewModel.notifyTyping(route.peerId, typing) },
                                    onReactMessage = { msgId, emoji -> viewModel.reactToMessage(route.peerId, msgId, emoji) },
                                    onDeleteLocal = viewModel::deleteMessageLocal,
                                    onDeleteForEveryone = { msgId -> viewModel.deleteMessageForEveryone(route.peerId, msgId) },
                                    onToggleVerify = { verified -> viewModel.toggleVerifyPeer(route.peerId, verified) },
                                    onTogglePin = { pinned -> viewModel.togglePinPeer(route.peerId, pinned) },
                                    onToggleBlock = { blocked -> viewModel.toggleBlockPeer(route.peerId, blocked) },
                                    onClearChat = { viewModel.clearChatHistory(route.peerId) }
                                )
                            }
                        }
                    }
                }

                // Active / Incoming Encrypted Call Overlay
                AnimatedVisibility(
                    visible = activeCall.status != CallStatus.IDLE,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    CallScreen(
                        callInfo = activeCall,
                        onAccept = viewModel::acceptCall,
                        onReject = viewModel::rejectCall,
                        onRejectWithMessage = viewModel::rejectCallWithQuickMessage,
                        onEndCall = viewModel::endCall,
                        onToggleMute = viewModel::toggleMute,
                        onToggleSpeaker = viewModel::toggleSpeaker,
                        onToggleHold = viewModel::toggleHold
                    )
                }

                // App Lock PIN Gate
                AnimatedVisibility(
                    visible = isAppLocked,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    AppLockOverlay(
                        pinError = pinError,
                        onUnlock = viewModel::unlockWithPin
                    )
                }

                if (showExitDialog) {
                    ExitAppConfirmDialog(
                        onConfirmExit = ::closeApplication,
                        onDismiss = { showExitDialog = false }
                    )
                }
            }
        }
    }
}
