package com.easyadb.core.device

/**
 * 封装 IP:端口、emulator-xxxx 或序列号设备地址。
 */
sealed class DeviceAddress {
    abstract val raw: String

    data class IpPort(val ip: String, val port: String = "5555") : DeviceAddress() {
        override val raw: String get() = "$ip:$port"
    }

    data class Emulator(val serial: String) : DeviceAddress() {
        override val raw: String get() = serial
    }

    data class Serial(val serial: String) : DeviceAddress() {
        override val raw: String get() = serial
    }

    companion object {
        private val IP_PORT_REGEX = Regex("""^\d{1,3}(\.\d{1,3}){3}:\d+$""")
        private val EMULATOR_REGEX = Regex("""^emulator-\d+$""")

        fun parse(address: String): DeviceAddress {
            return when {
                IP_PORT_REGEX.matches(address) -> {
                    val parts = address.split(":")
                    IpPort(ip = parts[0], port = parts.getOrElse(1) { "5555" })
                }
                EMULATOR_REGEX.matches(address) -> Emulator(address)
                else -> Serial(address)
            }
        }
    }
}
