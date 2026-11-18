package com.novarun.lite.server

import java.net.Inet4Address
import java.net.NetworkInterface

fun findLanIpv4Address(): String? {
    val candidates = mutableListOf<String>()
    val interfaces = runCatching { NetworkInterface.getNetworkInterfaces() }.getOrNull() ?: return null
    while (interfaces.hasMoreElements()) {
        val network = interfaces.nextElement()
        val interfaceName = network.name.lowercase()
        val wifiLike = interfaceName.startsWith("wlan") ||
            interfaceName.startsWith("ap") ||
            interfaceName.startsWith("swlan") ||
            interfaceName.startsWith("p2p") ||
            interfaceName.contains("wifi")
        if (!wifiLike) continue
        val usable = runCatching { network.isUp && !network.isLoopback && !network.isVirtual }.getOrDefault(false)
        if (!usable) continue
        val addresses = network.inetAddresses
        while (addresses.hasMoreElements()) {
            val address = addresses.nextElement()
            if (address is Inet4Address && address.isSiteLocalAddress &&
                !address.isLoopbackAddress && !address.isLinkLocalAddress
            ) {
                candidates += address.hostAddress ?: continue
            }
        }
    }
    return candidates.firstOrNull { it.startsWith("192.168.") }
        ?: candidates.firstOrNull { it.startsWith("10.") }
}
