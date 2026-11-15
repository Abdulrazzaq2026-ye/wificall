package com.example.data.prefs

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.crypto.CryptoManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "hisn_secure_prefs")

data class UserSettings(
    val deviceId: String = "",
    val userCode: String = "",
    val isLoggedIn: Boolean = false,
    val displayName: String = "",
    val statusMessage: String = "متاح على الشبكة المحلية",
    val avatarIndex: Int = 0,
    val publicKeyBase64: String = "",
    val privateKeyBase64: String = "",
    val tcpPort: Int = 45889,
    val udpAudioPort: Int = 45890,
    val disappearingSeconds: Int = 0,
    val isDarkTheme: Boolean = true,
    val stealthMode: Boolean = false,
    val nsdEnabled: Boolean = true,
    val udpBroadcastEnabled: Boolean = true,
    val autoAcceptVerifiedCalls: Boolean = false,
    val appLockPin: String = "",
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val DEVICE_ID = stringPreferencesKey("device_id")
        val USER_CODE = stringPreferencesKey("user_code")
        val IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val STATUS_MESSAGE = stringPreferencesKey("status_message")
        val AVATAR_INDEX = intPreferencesKey("avatar_index")
        val PUBLIC_KEY = stringPreferencesKey("identity_public_key")
        val PRIVATE_KEY = stringPreferencesKey("identity_private_key")
        val TCP_PORT = intPreferencesKey("tcp_port")
        val UDP_AUDIO_PORT = intPreferencesKey("udp_audio_port")
        val DISAPPEARING_SECONDS = intPreferencesKey("disappearing_seconds")
        val IS_DARK_THEME = booleanPreferencesKey("is_dark_theme")
        val STEALTH_MODE = booleanPreferencesKey("stealth_mode")
        val NSD_ENABLED = booleanPreferencesKey("nsd_enabled")
        val UDP_BROADCAST_ENABLED = booleanPreferencesKey("udp_broadcast_enabled")
        val AUTO_ACCEPT_VERIFIED = booleanPreferencesKey("auto_accept_verified")
        val APP_LOCK_PIN = stringPreferencesKey("app_lock_pin")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        UserSettings(
            deviceId = prefs[Keys.DEVICE_ID] ?: "",
            userCode = prefs[Keys.USER_CODE] ?: "",
            isLoggedIn = prefs[Keys.IS_LOGGED_IN] ?: false,
            displayName = prefs[Keys.DISPLAY_NAME] ?: defaultDeviceLabel(),
            statusMessage = prefs[Keys.STATUS_MESSAGE] ?: "متاح على الشبكة المحلية",
            avatarIndex = prefs[Keys.AVATAR_INDEX] ?: 0,
            publicKeyBase64 = prefs[Keys.PUBLIC_KEY] ?: "",
            privateKeyBase64 = prefs[Keys.PRIVATE_KEY] ?: "",
            tcpPort = prefs[Keys.TCP_PORT] ?: 45889,
            udpAudioPort = prefs[Keys.UDP_AUDIO_PORT] ?: 45890,
            disappearingSeconds = prefs[Keys.DISAPPEARING_SECONDS] ?: 0,
            isDarkTheme = prefs[Keys.IS_DARK_THEME] ?: true,
            stealthMode = prefs[Keys.STEALTH_MODE] ?: false,
            nsdEnabled = prefs[Keys.NSD_ENABLED] ?: true,
            udpBroadcastEnabled = prefs[Keys.UDP_BROADCAST_ENABLED] ?: true,
            autoAcceptVerifiedCalls = prefs[Keys.AUTO_ACCEPT_VERIFIED] ?: false,
            appLockPin = prefs[Keys.APP_LOCK_PIN] ?: "",
            soundEnabled = prefs[Keys.SOUND_ENABLED] ?: true,
            vibrationEnabled = prefs[Keys.VIBRATION_ENABLED] ?: true
        )
    }

    suspend fun ensureInitialized(cryptoManager: CryptoManager): UserSettings {
        val current = settingsFlow.first()
        if (current.deviceId.isNotBlank() && current.publicKeyBase64.isNotBlank()) {
            return current
        }
        val keyPair = cryptoManager.generateIdentityKeyPair()
        val newId = current.deviceId.ifBlank { UUID.randomUUID().toString().take(12) }
        val defaultName = current.displayName.ifBlank { defaultDeviceLabel() }
        val defaultCode = current.userCode.ifBlank { (1000 + (Math.abs(newId.hashCode()) % 8999)).toString() }
        context.dataStore.edit { prefs ->
            prefs[Keys.DEVICE_ID] = newId
            prefs[Keys.USER_CODE] = defaultCode
            prefs[Keys.DISPLAY_NAME] = defaultName
            prefs[Keys.PUBLIC_KEY] = keyPair.publicKeyBase64
            prefs[Keys.PRIVATE_KEY] = keyPair.privateKeyBase64
        }
        return settingsFlow.first()
    }

    suspend fun login(name: String, userCode: String, avatarIndex: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DISPLAY_NAME] = name.trim()
            prefs[Keys.USER_CODE] = userCode.trim()
            prefs[Keys.AVATAR_INDEX] = avatarIndex
            prefs[Keys.IS_LOGGED_IN] = true
        }
    }

    suspend fun logout() {
        context.dataStore.edit { prefs ->
            prefs[Keys.IS_LOGGED_IN] = false
        }
    }

    suspend fun rotateIdentityKeys(cryptoManager: CryptoManager): UserSettings {
        val newPair = cryptoManager.generateIdentityKeyPair()
        context.dataStore.edit { prefs ->
            prefs[Keys.PUBLIC_KEY] = newPair.publicKeyBase64
            prefs[Keys.PRIVATE_KEY] = newPair.privateKeyBase64
        }
        return settingsFlow.first()
    }

    suspend fun updateProfile(name: String, userCode: String, status: String, avatarIndex: Int) {
        context.dataStore.edit { prefs ->
            if (name.isNotBlank()) prefs[Keys.DISPLAY_NAME] = name.trim()
            if (userCode.isNotBlank()) prefs[Keys.USER_CODE] = userCode.trim()
            prefs[Keys.STATUS_MESSAGE] = status.trim()
            prefs[Keys.AVATAR_INDEX] = avatarIndex
        }
    }

    suspend fun setDisappearingSeconds(seconds: Int) {
        context.dataStore.edit { it[Keys.DISAPPEARING_SECONDS] = seconds }
    }

    suspend fun setDarkTheme(dark: Boolean) {
        context.dataStore.edit { it[Keys.IS_DARK_THEME] = dark }
    }

    suspend fun setStealthMode(stealth: Boolean) {
        context.dataStore.edit { it[Keys.STEALTH_MODE] = stealth }
    }

    suspend fun setNsdEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NSD_ENABLED] = enabled }
    }

    suspend fun setUdpBroadcastEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.UDP_BROADCAST_ENABLED] = enabled }
    }

    suspend fun setAutoAcceptVerifiedCalls(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_ACCEPT_VERIFIED] = enabled }
    }

    suspend fun setAppLockPin(pin: String) {
        context.dataStore.edit { it[Keys.APP_LOCK_PIN] = pin }
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SOUND_ENABLED] = enabled }
    }

    suspend fun setVibrationEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.VIBRATION_ENABLED] = enabled }
    }

    suspend fun updateRuntimePorts(tcpPort: Int, udpAudioPort: Int) {
        context.dataStore.edit {
            it[Keys.TCP_PORT] = tcpPort
            it[Keys.UDP_AUDIO_PORT] = udpAudioPort
        }
    }

    private fun defaultDeviceLabel(): String {
        val model = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Android"
        return "جهاز $model"
    }
}
