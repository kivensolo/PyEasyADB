package com.easyadb.core.device

/**
 * 设备状态枚举。
 * 对应 Python 中的 DeviceInfo.state（位于 src/DevicesWatcher.py）
 */
enum class DeviceState(val value: String) {
    DEVICE("device"),
    OFFLINE("offline"),
    UNAUTHORIZED("unauthorized"),
    UNKNOWN("unknown");

    companion object {
        fun fromValue(value: String): DeviceState =
            entries.find { it.value == value } ?: UNKNOWN
    }
}

/**
 * 设备信息数据类。
 * 对应 Python 中的 DeviceInfo（位于 src/DevicesWatcher.py）
 */
data class DeviceInfo(
    val name: String,
    val state: DeviceState
) {
    /**
     * 设备是否已连接（device 或 offline 状态）。
     */
    fun isConnected(): Boolean = state == DeviceState.DEVICE || state == DeviceState.OFFLINE

    /**
     * 检查设备状态是否正常。
     */
    fun isDeviceStateNormal(): Pair<Boolean, String> {
        val normal = state == DeviceState.DEVICE
        return Pair(normal, if (normal) "正常" else "异常")
    }
}
