package com.easyadb.core.device

import com.easyadb.core.log.AppLogger
import com.easyadb.core.util.ExecUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Monitors device connection status via `adb devices`.
 * Maps to Python DevicesWatcher in src/DevicesWatcher.py
 * Replaces threading.Thread + pyqtSignal with coroutine + Flow.
 */
class DevicesWatcher {

    private val logger = AppLogger.getLogger(DevicesWatcher::class.java)
    private var job: Job? = null
    private var _devices = MutableStateFlow<List<DeviceInfo>>(emptyList())

    val devices: Flow<List<DeviceInfo>> = _devices.asStateFlow()

    /**
     * Start watching devices at the given interval.
     */
    fun start(intervalSeconds: Long = 2, scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())) {
        if (job?.isActive == true) return

        job = scope.launch {
            logger.info { "[DeviceWatcher] started with interval=${intervalSeconds}s" }
            while (isActive) {
                try {
                    val result = ExecUtils.execCmd("adb devices", timeoutMs = 25000)
                    if (result.success) {
                        val devicesList = parseDevicesOutput(result.output)
                        _devices.value = devicesList
                    }
                } catch (e: Exception) {
                    logger.error { "[DeviceWatcher] error: ${e.message}" }
                }
                delay(intervalSeconds * 1000)
            }
        }
    }

    /**
     * Stop watching devices.
     */
    fun stop() {
        job?.cancel()
        job = null
        logger.info { "[DeviceWatcher] stopped" }
    }

    /**
     * Force refresh on next poll (e.g., after device deletion).
     */
    fun onDeviceDeleted() {
        // Force re-trigger by emitting empty first
        _devices.value = emptyList()
    }

    private fun parseDevicesOutput(output: String): List<DeviceInfo> {
        val devices = mutableListOf<DeviceInfo>()
        for (line in output.lines()) {
            if (line.startsWith("List")) continue
            val parts = line.split("\t")
            if (parts.size < 2) continue
            devices.add(
                DeviceInfo(
                    name = parts[0].trim(),
                    state = DeviceState.fromValue(parts[1].trim())
                )
            )
        }
        return devices
    }
}
