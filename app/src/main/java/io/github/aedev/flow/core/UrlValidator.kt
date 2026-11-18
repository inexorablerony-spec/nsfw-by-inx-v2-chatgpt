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

        // IPv6 unique local (fc00::/7)
        if (bytes.size == 16 && (bytes[0].toInt() and 0xFE) == 0xFC) return true

        // IPv4-mapped IPv6 (::ffff:x.x.x.x)
        if (bytes.size == 16 && isIpv4Mapped(bytes)) {
            val ipv4 = bytes.copyOfRange(12, 16)
            return isBlockedIpv4(ipv4)
        }

        if (bytes.size == 4) {
            return isBlockedIpv4(bytes)
        }
        return false
    }

    private fun isIpv4Mapped(bytes: ByteArray): Boolean {
        // ::ffff:x.x.x.x has first 10 bytes zero, then 0xff 0xff
        for (i in 0 until 10) {
            if (bytes[i] != 0.toByte()) return false
        }
        return bytes[10] == 0xff.toByte() && bytes[11] == 0xff.toByte()
    }

    private fun isBlockedIpv4(bytes: ByteArray): Boolean {
        val b0 = bytes[0].toInt() and 0xFF
        val b1 = bytes[1].toInt() and 0xFF
        if (b0 == 0) return true
        if (b0 == 10) return true
        if (b0 == 127) return true
        if (b0 == 169 && b1 == 254) return true
        if (b0 == 172 && b1 in 16..31) return true
        if (b0 == 192 && b1 == 168) return true
        if (b0 in 224..239) return true
        if (b0 >= 240) return true
        return false
    }
}
