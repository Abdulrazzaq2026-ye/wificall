package com.example

import android.app.Application
import com.example.crypto.CryptoManager
import com.example.data.local.HisnDatabase
import com.example.data.prefs.SettingsRepository
import com.example.data.repository.HisnRepository
import com.example.network.LanDiscoveryManager
import com.example.network.LanMessagingEngine
import com.example.network.VoiceCallEngine
import com.example.network.VoiceNoteRecorder

class HisnApp : Application() {
    lateinit var database: HisnDatabase
        private set
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var cryptoManager: CryptoManager
        private set
    lateinit var repository: HisnRepository
        private set
    lateinit var discoveryManager: LanDiscoveryManager
        private set
    lateinit var messagingEngine: LanMessagingEngine
        private set
    lateinit var voiceCallEngine: VoiceCallEngine
        private set
    lateinit var voiceNoteRecorder: VoiceNoteRecorder
        private set

    override fun onCreate() {
        super.onCreate()
        database = HisnDatabase.getInstance(this)
        settingsRepository = SettingsRepository(this)
        cryptoManager = CryptoManager()
        repository = HisnRepository(
            context = this,
            peerDao = database.peerDao(),
            messageDao = database.messageDao(),
            callLogDao = database.callLogDao(),
            settingsRepository = settingsRepository,
            cryptoManager = cryptoManager
        )
        voiceCallEngine = VoiceCallEngine(
            context = this,
            cryptoManager = cryptoManager
        )
        messagingEngine = LanMessagingEngine(
            context = this,
            repository = repository,
            cryptoManager = cryptoManager,
            voiceCallEngine = voiceCallEngine
        )
        discoveryManager = LanDiscoveryManager(context = this)
        voiceCallEngine.onBusyStateChanged = {
            discoveryManager.triggerImmediateScan()
        }
        voiceNoteRecorder = VoiceNoteRecorder(this)
    }
}
