package com.example.network

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.example.HisnApp
import com.example.data.model.DiscoveryBeacon
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress

class NetworkMonitorService : Service() {

    inner class LocalBinder : Binder() {
        fun getService(): NetworkMonitorService = this@NetworkMonitorService
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    private val app by lazy { application as HisnApp }

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var udpSocket: DatagramSocket? = null

    private var monitorLoopJob: Job? = null
    private var resolverJob: Job? = null
    private val resolveQueue = Channel<NsdServiceInfo>(Channel.UNLIMITED)

    private var activeTcpPort: Int = 45889
    private var activeUdpPort: Int = 45890
    private var lastRegisteredBusyState: Boolean? = null
    private var lastRegisteredName: String? = null
    private var lastRegisteredCode: String? = null

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        _isServiceRunning.value = true
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        nsdManager = getSystemService(Context.NSD_SERVICE) as? NsdManager
        registerNetworkCallback()
        startResolverWorker()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopMonitoring()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_REFRESH -> {
                val tcp = intent.getIntExtra(EXTRA_TCP_PORT, activeTcpPort)
                val udp = intent.getIntExtra(EXTRA_UDP_PORT, activeUdpPort)
                startOrRefreshMonitoring(tcp, udp)
            }
            else -> {
                val tcp = intent?.getIntExtra(EXTRA_TCP_PORT, activeTcpPort) ?: activeTcpPort
                val udp = intent?.getIntExtra(EXTRA_UDP_PORT, activeUdpPort) ?: activeUdpPort
                startOrRefreshMonitoring(tcp, udp)
            }
        }
        return START_STICKY
    }

    fun startOrRefreshMonitoring(tcpPort: Int, udpPort: Int) {
        activeTcpPort = tcpPort
        activeUdpPort = udpPort
        _localNetworkInfo.value = NetworkUtils.getLocalNetworkInfo(this)

        if (multicastLock == null) {
            multicastLock = NetworkUtils.acquireMulticastLock(this)
        }
        _isScanning.value = true

        if (monitorLoopJob?.isActive != true) {
            monitorLoopJob = serviceScope.launch {
                launch { runUdpListener() }
                launch { runBroadcastAndHealthLoop() }
            }
        }

        serviceScope.launch {
            val settings = app.repository.getSettings()
            if (settings.nsdEnabled && !settings.stealthMode) {
                startOrUpdateNsd()
            } else {
                stopNsd()
            }
            sendUdpBeaconOnce()
        }
    }

    fun refreshPresenceNow() {
        serviceScope.launch {
            _localNetworkInfo.value = NetworkUtils.getLocalNetworkInfo(this@NetworkMonitorService)
            val settings = app.repository.getSettings()
            if (settings.nsdEnabled && !settings.stealthMode) {
                startOrUpdateNsd(forceReregister = true)
            } else {
                stopNsd()
            }
            sendUdpBeaconOnce()
        }
    }

    fun stopMonitoring() {
        _isScanning.value = false
        _udpActive.value = false
        _monitorStatus.value = "خدمة مراقبة الشبكة متوقفة"
        monitorLoopJob?.cancel()
        monitorLoopJob = null
        runCatching { udpSocket?.close() }
        udpSocket = null
        stopNsd()
        runCatching {
            if (multicastLock?.isHeld == true) multicastLock?.release()
        }
        multicastLock = null
    }

    override fun onDestroy() {
        _isServiceRunning.value = false
        stopMonitoring()
        unregisterNetworkCallback()
        resolverJob?.cancel()
        resolveQueue.close()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun registerNetworkCallback() {
        val cm = connectivityManager ?: return
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .build()

        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                onNetworkChanged("تم الاتصال بشبكة Wi-Fi — جاري الفحص عبر mDNS")
            }

            override fun onLost(network: Network) {
                _localNetworkInfo.value = NetworkUtils.getLocalNetworkInfo(this@NetworkMonitorService)
                _monitorStatus.value = "انقطع اتصال Wi-Fi"
                serviceScope.launch {
                    app.repository.markStalePeersOffline(0L)
                }
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                onNetworkChanged("تحديث عنوان الشبكة — مراقبة mDNS نشطة")
            }
        }
        networkCallback = cb
        runCatching { cm.registerNetworkCallback(request, cb) }
    }

    private fun unregisterNetworkCallback() {
        val cb = networkCallback ?: return
        runCatching { connectivityManager?.unregisterNetworkCallback(cb) }
        networkCallback = null
    }

    private fun onNetworkChanged(statusText: String) {
        _localNetworkInfo.value = NetworkUtils.getLocalNetworkInfo(this)
        _monitorStatus.value = statusText
        if (_isScanning.value) {
            refreshPresenceNow()
        }
    }

    private fun startResolverWorker() {
        resolverJob = serviceScope.launch {
            for (service in resolveQueue) {
                if (!isActive) break
                resolveSingleService(service)
            }
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun resolveSingleService(service: NsdServiceInfo) {
        val manager = nsdManager ?: return
        val settings = app.repository.getSettings()
        val deferred = CompletableDeferred<NsdServiceInfo?>()

        runCatching {
            manager.resolveService(service, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    deferred.complete(null)
                }

                override fun onServiceResolved(resolved: NsdServiceInfo) {
                    deferred.complete(resolved)
                }
            })
        }.onFailure {
            deferred.complete(null)
        }

        val resolved = withTimeoutOrNull(3000L) { deferred.await() } ?: return
        val host = resolved.host?.hostAddress ?: return
        val attrs = resolved.attributes

        val uid = attrs["uid"]?.toString(Charsets.UTF_8)
            ?: resolved.serviceName.removePrefix(SERVICE_NAME_PREFIX)
        if (uid.isBlank() || uid == settings.deviceId) return

        val name = attrs["name"]?.toString(Charsets.UTF_8)?.ifBlank { null }
            ?: resolved.serviceName
        val code = attrs["code"]?.toString(Charsets.UTF_8) ?: ""
        val statusAttr = attrs["status"]?.toString(Charsets.UTF_8) ?: "online"
        val avatarIdx = attrs["av"]?.toString(Charsets.UTF_8)?.toIntOrNull() ?: 0
        val udpPort = attrs["udp"]?.toString(Charsets.UTF_8)?.toIntOrNull() ?: (resolved.port + 1)
        val pubKey = attrs["pk"]?.toString(Charsets.UTF_8) ?: ""

        if (statusAttr == "offline") {
            app.repository.markPeerOffline(uid)
            return
        }

        app.repository.upsertDiscoveredPeer(
            deviceId = uid,
            userCode = code,
            displayName = name,
            statusMessage = if (statusAttr == "busy") "مشغول في مكالمة أخرى" else "متاح عبر Wi-Fi mDNS",
            avatarIndex = avatarIdx,
            ipAddress = host,
            tcpPort = resolved.port,
            udpPort = udpPort,
            publicKeyBase64 = pubKey,
            isBusyInCall = statusAttr == "busy"
        )
    }

    private suspend fun startOrUpdateNsd(forceReregister: Boolean = false) {
        val manager = nsdManager ?: return
        val settings = app.repository.getSettings()
        val isBusy = app.voiceCallEngine.isBusy()

        val needsReregister = forceReregister ||
            registrationListener == null ||
            lastRegisteredBusyState != isBusy ||
            lastRegisteredName != settings.displayName ||
            lastRegisteredCode != settings.userCode

        if (needsReregister) {
            registrationListener?.let { oldListener ->
                runCatching { manager.unregisterService(oldListener) }
                registrationListener = null
                delay(180L)
            }

            val serviceInfo = NsdServiceInfo().apply {
                serviceName = "$SERVICE_NAME_PREFIX${settings.deviceId}"
                serviceType = NSD_SERVICE_TYPE
                port = activeTcpPort
                setAttribute("uid", settings.deviceId)
                setAttribute("code", settings.userCode.take(24))
                setAttribute("name", settings.displayName.take(48))
                setAttribute("status", if (isBusy) "busy" else "online")
                setAttribute("av", settings.avatarIndex.toString())
                setAttribute("udp", activeUdpPort.toString())
                if (settings.publicKeyBase64.length <= 240) {
                    setAttribute("pk", settings.publicKeyBase64)
                }
            }

            val regListener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: NsdServiceInfo) {
                    lastRegisteredBusyState = isBusy
                    lastRegisteredName = settings.displayName
                    lastRegisteredCode = settings.userCode
                    _nsdActive.value = true
                    _monitorStatus.value = "مراقبة mDNS نشطة (${if (isBusy) "مشغول" else "متاح"})"
                }

                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    _nsdActive.value = false
                    Log.w(TAG, "NSD registration failed: $errorCode")
                }

                override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                    _nsdActive.value = false
                }

                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            }
            registrationListener = regListener
            runCatching {
                manager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, regListener)
            }
        }

        if (discoveryListener == null) {
            val discListener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) {
                    _nsdActive.value = true
                    _monitorStatus.value = "مراقبة mDNS نشطة"
                }

                override fun onServiceFound(service: NsdServiceInfo) {
                    if (!service.serviceType.contains("_hisn")) return
                    if (service.serviceName == "$SERVICE_NAME_PREFIX${settings.deviceId}") return
                    resolveQueue.trySend(service)
                }

                override fun onServiceLost(service: NsdServiceInfo) {
                    if (!service.serviceType.contains("_hisn")) return
                    val lostId = service.serviceName.removePrefix(SERVICE_NAME_PREFIX)
                    if (lostId.isNotBlank() && lostId != settings.deviceId) {
                        serviceScope.launch {
                            app.repository.markPeerOffline(lostId)
                        }
                    }
                }

                override fun onDiscoveryStopped(serviceType: String) {
                    discoveryListener = null
                }

                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    discoveryListener = null
                    Log.w(TAG, "NSD start discovery failed: $errorCode")
                }

                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                    discoveryListener = null
                }
            }
            discoveryListener = discListener
            runCatching {
                manager.discoverServices(NSD_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discListener)
            }
        }
    }

    private fun stopNsd() {
        _nsdActive.value = false
        val manager = nsdManager ?: return
        runCatching { registrationListener?.let { manager.unregisterService(it) } }
        runCatching { discoveryListener?.let { manager.stopServiceDiscovery(it) } }
        registrationListener = null
        discoveryListener = null
        lastRegisteredBusyState = null
    }

    private suspend fun runBroadcastAndHealthLoop() {
        while (serviceScope.isActive && _isScanning.value) {
            _localNetworkInfo.value = NetworkUtils.getLocalNetworkInfo(this@NetworkMonitorService)
            sendUdpBeaconOnce()
            app.repository.markStalePeersOffline(16_000L)
            app.repository.purgeExpiredMessages()
            delay(3_500L)
        }
    }

    private suspend fun sendUdpBeaconOnce() {
        val settings = app.repository.getSettings()
        if (!settings.udpBroadcastEnabled || settings.stealthMode) return

        val beacon = DiscoveryBeacon(
            deviceId = settings.deviceId,
            userCode = settings.userCode,
            displayName = settings.displayName,
            statusMessage = settings.statusMessage,
            avatarIndex = settings.avatarIndex,
            tcpPort = activeTcpPort,
            udpAudioPort = activeUdpPort,
            publicKeyBase64 = settings.publicKeyBase64,
            isBusyInCall = app.voiceCallEngine.isBusy()
        )
        val payload = json.encodeToString(beacon).toByteArray(Charsets.UTF_8)
        val targets = NetworkUtils.getLocalNetworkInfo(this).broadcastAddresses

        runCatching {
            val sock = udpSocket ?: return
            for (addr in targets) {
                runCatching {
                    sock.send(DatagramPacket(payload, payload.size, addr, DISCOVERY_UDP_PORT))
                }
            }
        }
    }

    private suspend fun runUdpListener() {
        runCatching {
            val sock = DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                bind(InetSocketAddress(DISCOVERY_UDP_PORT))
            }
            udpSocket = sock
            _udpActive.value = true

            val buffer = ByteArray(8192)
            while (serviceScope.isActive && _isScanning.value) {
                val packet = DatagramPacket(buffer, buffer.size)
                sock.receive(packet)
                val senderIp = packet.address?.hostAddress ?: continue
                val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                val beacon = runCatching { json.decodeFromString<DiscoveryBeacon>(text) }.getOrNull() ?: continue
                val mySettings = app.repository.getSettings()
                if (beacon.deviceId == mySettings.deviceId) continue

                app.repository.upsertDiscoveredPeer(
                    deviceId = beacon.deviceId,
                    userCode = beacon.userCode,
                    displayName = beacon.displayName,
                    statusMessage = beacon.statusMessage,
                    avatarIndex = beacon.avatarIndex,
                    ipAddress = senderIp,
                    tcpPort = beacon.tcpPort,
                    udpPort = beacon.udpAudioPort,
                    publicKeyBase64 = beacon.publicKeyBase64,
                    isBusyInCall = beacon.isBusyInCall
                )
            }
        }.onFailure {
            Log.w(TAG, "UDP listener stopped: ${it.message}")
            _udpActive.value = false
        }
    }

    companion object {
        private const val TAG = "NetworkMonitorService"
        const val DISCOVERY_UDP_PORT = 45888
        const val NSD_SERVICE_TYPE = "_hisn._tcp."
        const val SERVICE_NAME_PREFIX = "WifiCall-"
        const val ACTION_START = "com.example.action.START_MONITOR"
        const val ACTION_REFRESH = "com.example.action.REFRESH_MONITOR"
        const val ACTION_STOP = "com.example.action.STOP_MONITOR"
        const val EXTRA_TCP_PORT = "extra_tcp_port"
        const val EXTRA_UDP_PORT = "extra_udp_port"

        private val _localNetworkInfo = MutableStateFlow(LocalNetworkInfo())
        val localNetworkInfo: StateFlow<LocalNetworkInfo> = _localNetworkInfo.asStateFlow()

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        private val _isScanning = MutableStateFlow(false)
        val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

        private val _nsdActive = MutableStateFlow(false)
        val nsdActive: StateFlow<Boolean> = _nsdActive.asStateFlow()

        private val _udpActive = MutableStateFlow(false)
        val udpActive: StateFlow<Boolean> = _udpActive.asStateFlow()

        private val _monitorStatus = MutableStateFlow("جاري تهيئة خدمة مراقبة الشبكة...")
        val monitorStatus: StateFlow<String> = _monitorStatus.asStateFlow()
    }
}
