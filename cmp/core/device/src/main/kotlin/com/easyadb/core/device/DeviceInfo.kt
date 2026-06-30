package com.easyadb.core.device

/**
 * Device state enum.
 * Maps to Python DeviceInfo.state in src/DevicesWatcher.py
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
 * Device information data class.
 * Maps to Python DeviceInfo in src/DevicesWatcher.py
 */
data class DeviceInfo(
    val name: String,
    val state: DeviceState
) {
    /**
     * Whether the device is connected (device or offline).
     */
    fun isConnected(): Boolean = state == DeviceState.DEVICE || state == DeviceState.OFFLINE

    /**
     * Check if device state is normal.
     */
    fun isDeviceStateNormal(): Pair<Boolean, String> {
        val normal = state == DeviceState.DEVICE
        return Pair(normal, if (normal) "正常" else "异常")
    }
}
