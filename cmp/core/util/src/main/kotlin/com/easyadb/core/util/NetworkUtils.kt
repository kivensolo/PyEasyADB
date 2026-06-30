package com.easyadb.core.util

object NetworkUtils {

    private val IP_REGEX = Regex(
        """^((2[0-4]\d|25[0-5]|[01]?\d\d?)\.){3}(2[0-4]\d|25[0-5]|[01]?\d\d?)(:?\d{1,5})?$"""
    )

    /**
     * Validate IP address format (with optional port).
     * Maps to Python isIpMatches() in utils/Tools.py
     */
    fun isIpMatches(ip: String): Boolean = IP_REGEX.matches(ip)

    /**
     * Parse IP:port string into pair.
     */
    fun parseIpPort(addr: String): Pair<String, String> {
        if (!addr.contains(":")) return Pair(addr, "5555")
        val colonIndex = addr.lastIndexOf(':')
        return Pair(addr.substring(0, colonIndex), addr.substring(colonIndex + 1))
    }
}
