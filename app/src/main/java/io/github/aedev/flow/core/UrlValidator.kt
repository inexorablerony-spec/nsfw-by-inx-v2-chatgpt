package io.github.aedev.flow.core

import java.net.InetAddress
import java.net.URI

object UrlValidator {
    fun isAllowed(url: String): Boolean {
        val uri = try { URI(url) } catch (e: Exception) { return false }
        if (uri.scheme !in listOf("http", "https")) return false
        val host = uri.host ?: return false
        if (host.equals("localhost", ignoreCase = true)) return false

        val addresses = try {
            InetAddress.getAllByName(host)
        } catch (e: Exception) { return false }

        for (addr in addresses) {
            if (isBlockedAddress(addr)) return false
        }
        return true
    }

    private fun isBlockedAddress(addr: InetAddress): Boolean {
        if (addr.isLoopbackAddress) return true
        if (addr.isLinkLocalAddress) return true
        if (addr.isSiteLocalAddress) return true
        if (addr.isMulticastAddress) return true
        if (addr.isAnyLocalAddress) return true

        val bytes = addr.address
        // IPv4-mapped IPv6
        if (bytes.size == 16 && bytes.take(12).all { it == 0.toByte() || it == 0xff.toByte() }) {
            val ipv4 = bytes.takeLast(4).toByteArray()
            return isBlockedIpv4(ipv4)
        }
        if (bytes.size == 4) {
            return isBlockedIpv4(bytes)
        }
        return false
    }

    private fun isBlockedIpv4(bytes: ByteArray): Boolean {
        val b0 = bytes[0].toInt() and 0xFF
        val b1 = bytes[1].toInt() and 0xFF
        // 0.0.0.0/8
        if (b0 == 0) return true
        // 10.0.0.0/8
        if (b0 == 10) return true
        // 127.0.0.0/8
        if (b0 == 127) return true
        // 169.254.0.0/16
        if (b0 == 169 && b1 == 254) return true
        // 172.16.0.0/12
        if (b0 == 172 && b1 in 16..31) return true
        // 192.168.0.0/16
        if (b0 == 192 && b1 == 168) return true
        // 224.0.0.0/4 multicast
        if (b0 in 224..239) return true
        // 240.0.0.0/4 reserved
        if (b0 >= 240) return true
        return false
    }
}
