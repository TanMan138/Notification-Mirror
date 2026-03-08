package com.tanmay.notificationmirror

import java.net.NetworkInterface

/**
 * Returns the device's local IPv4 address on Wi-Fi (or primary non-loopback interface).
 * Prefers wlan0 (Wi-Fi) when available so the Mac can reach the phone for dismiss callbacks.
 * Returns null if no suitable address is found.
 */
fun getLocalIpAddress(): String? {
    return try {
        var wifiIp: String? = null
        var fallbackIp: String? = null
        NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { iface ->
            if (!iface.isUp || iface.isLoopback) return@forEach
            val addr = iface.inetAddresses.toList().firstOrNull { a ->
                !a.isLoopbackAddress && a.hostAddress?.contains('.') == true
            }?.hostAddress ?: return@forEach
            when {
                iface.name.equals("wlan0", ignoreCase = true) -> wifiIp = addr
                fallbackIp == null -> fallbackIp = addr
            }
        }
        wifiIp ?: fallbackIp
    } catch (_: Exception) {
        null
    }
}
