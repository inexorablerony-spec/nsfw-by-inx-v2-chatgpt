package io.github.aedev.flow.core

import java.net.InetAddress
import java.net.URI

object UrlValidator {
    private val BLOCKED_PREFIXES = listOf(
        "0.", "10.", "127.", "169.254.", "172.16.", "172.17.", "172.18.",
        "172.19.", "172.20.", "172.21.", "172.22.", "172.23.", "172.24.",
        "172.25.", "172.26.", "172.27.", "172.28.", "172.29.", "172.30.",
        "172.31.", "192.168.", "fc", "fd", "fe80"
    )

    fun isAllowed(url: String): Boolean {
        val uri = try { URI(url) } catch (e: Exception) { return false }
        if (uri.scheme !in listOf("http", "https")) return false
        val host = uri.host ?: return false
        if (host == "localhost") return false
        
        val addresses = try {
            InetAddress.getAllByName(host)
        } catch (e: Exception) { return false }
        
        for (addr in addresses) {
            val ip = addr.hostAddress ?: continue
            if (BLOCKED_PREFIXES.any { ip.startsWith(it) }) return false
            if (ip == "::1") return false
        }
        return true
    }
}
