package com.example.network

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.StateFlow

/**
 * Facade coordinating the Android [NetworkMonitorService] lifecycle and exposing
 * reactive network monitoring state to the ViewModel layer.
 */
class LanDiscoveryManager(private val context: Context) {

    val localNetworkInfo: StateFlow<LocalNetworkInfo> = NetworkMonitorService.localNetworkInfo
    val isServiceRunning: StateFlow<Boolean> = NetworkMonitorService.isServiceRunning
    val isScanning: StateFlow<Boolean> = NetworkMonitorService.isScanning
    val nsdActive: StateFlow<Boolean> = NetworkMonitorService.nsdActive
    val udpActive: StateFlow<Boolean> = NetworkMonitorService.udpActive
    val monitorStatus: StateFlow<String> = NetworkMonitorService.monitorStatus

    fun start(tcpPort: Int, udpAudioPort: Int) {
        val intent = Intent(context, NetworkMonitorService::class.java).apply {
            action = NetworkMonitorService.ACTION_START
            putExtra(NetworkMonitorService.EXTRA_TCP_PORT, tcpPort)
            putExtra(NetworkMonitorService.EXTRA_UDP_PORT, udpAudioPort)
        }
        runCatching { context.startService(intent) }
    }

    fun triggerImmediateScan() {
        val intent = Intent(context, NetworkMonitorService::class.java).apply {
            action = NetworkMonitorService.ACTION_REFRESH
        }
        runCatching { context.startService(intent) }
    }

    fun updateDiscoveryConfig(nsdEnabled: Boolean, stealthMode: Boolean) {
        triggerImmediateScan()
    }

    fun stop() {
        val intent = Intent(context, NetworkMonitorService::class.java).apply {
            action = NetworkMonitorService.ACTION_STOP
        }
        runCatching { context.startService(intent) }
    }
}
