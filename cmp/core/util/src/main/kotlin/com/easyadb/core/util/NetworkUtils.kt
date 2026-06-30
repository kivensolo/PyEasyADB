package com.easyadb.core.util

object NetworkUtils {

    private val IP_REGEX = Regex(
        """^((2[0-4]\d|25[0-5]|[01]?\d\d?)\.){3}(2[0-4]\d|25[0-5]|[01]?\d\d?)(:?\d{1,5})?$"""
    )

    /**
     * 验证 IP 地址格式（支持可选端口）。
     * 对应 Python 中的 isIpMatches()（位于 utils/Tools.py）
     */
    fun isIpMatches(ip: String): Boolean = IP_REGEX.matches(ip)

    /**
     * 将 IP:端口 字符串解析为对。
     */
    fun parseIpPort(addr: String): Pair<String, String> {
        if (!addr.contains(":")) return Pair(addr, "5555")
        val colonIndex = addr.lastIndexOf(':')
        return Pair(addr.substring(0, colonIndex), addr.substring(colonIndex + 1))
    }
}
