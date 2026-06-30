package com.easyadb.core.device

import com.easyadb.core.log.AppLogger
import com.easyadb.core.util.ExecUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 通过 `adb devices` 监控设备连接状态。
 * 对应 Python 中的 DevicesWatcher（位于 src/DevicesWatcher.py）
 * 将 threading.Thread + pyqtSignal 替换为协程 + Flow。
 */
class DevicesWatcher {

    private val logger = AppLogger.getLogger(DevicesWatcher::class.java)
    private var job: Job? = null
    private var _devices = MutableStateFlow<List<DeviceInfo>>(emptyList())

    val devices: Flow<List<DeviceInfo>> = _devices.asStateFlow()

    /**
     * 以指定间隔开始监控设备。
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
     * 停止监控设备。
     */
    fun stop() {
        job?.cancel()
        job = null
        logger.info { "[DeviceWatcher] stopped" }
    }

    /**
     * 强制在下次轮询时刷新（例如删除设备后）。
     */
    fun onDeviceDeleted() {
        // 先发送空列表以强制重新触发
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
