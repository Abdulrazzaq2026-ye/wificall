package com.example.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

data class LocalNetworkInfo(
    val ipAddress: String = "127.0.0.1",
    val interfaceName: String = "lo",
    val isWifiConnected: Boolean = false,
    val broadcastAddresses: List<InetAddress> = emptyList()
)

object NetworkUtils {
    fun getLocalNetworkInfo(context: Context): LocalNetworkInfo {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNet = cm?.activeNetwork
        val caps = cm?.getNetworkCapabilities(activeNet)
        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true

        var bestIp = "127.0.0.1"
        var bestIface = "lo"
        val broadcasts = mutableListOf<InetAddress>()

        runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces != null && interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp || iface.isLoopback) continue

                for (addr in iface.interfaceAddresses) {
                    val inet = addr.address
                    if (inet is Inet4Address && !inet.isLoopbackAddress) {
                        val host = inet.hostAddress ?: continue
                        // Prefer wlan/eth/ap interfaces over cellular rmnet
                        val name = iface.name.lowercase()
                        val isLanLike = name.startsWith("wlan") || name.startsWith("eth") ||
                            name.startsWith("ap") || name.startsWith("swlan") ||
                            host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172.")

                        if (isLanLike || bestIp == "127.0.0.1") {
                            bestIp = host
                            bestIface = iface.name
                        }
                        addr.broadcast?.let { broadcasts.add(it) }
                    }
                }
            }
            runCatching { broadcasts.add(InetAddress.getByName("255.255.255.255")) }
        }

        return LocalNetworkInfo(
            ipAddress = bestIp,
            interfaceName = bestIface,
            isWifiConnected = isWifi || bestIp != "127.0.0.1",
            broadcastAddresses = broadcasts.distinct()
        )
    }

    fun acquireMulticastLock(context: Context): WifiManager.MulticastLock? {
        return runCatching {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifi?.createMulticastLock("hisn_lan_multicast_lock")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        }.getOrNull()
    }
}
