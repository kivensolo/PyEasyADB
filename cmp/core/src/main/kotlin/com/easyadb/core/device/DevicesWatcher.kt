package com.easyadb.core.device

import com.easyadb.core.log.AppLogger
import com.easyadb.core.util.ExecUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/**
 * 通过 `adb devices` 监控设备连接状态。
 * 对应 Python 中的 DevicesWatcher（位于 src/DevicesWatcher.py）
 * 将 threading.Thread + pyqtSignal 替换为协程 + Flow。
 */
class DevicesWatcher {

    private val logger = AppLogger.getLogger(DevicesWatcher::class.java)
    private var job: Job? = null
    private val _devices = MutableStateFlow<List<DeviceInfo>>(emptyList())
    /** 立即刷新信号：连接/断开等操作完成后触发，跳过剩余 delay 立刻轮询一次 */
    private val refreshSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * 设备列表 Flow。
     * StateFlow 本身按 `==` 做内容去重——DeviceInfo 是 data class，
     * 即使每次轮询都构造新 List，只要内容相同就不会推送给下游。
     */
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
                        // MutableStateFlow 赋值时自动用 `==` 比较，DeviceInfo 是 data class，
                        // 内容未变时不会触发下游 collectAsState 重组。
                        _devices.value = parseDevicesOutput(result.output)
                    }
                } catch (e: Exception) {
                    logger.error { "[DeviceWatcher] error: ${e.message}" }
                }
                // 正常按间隔轮询；等待期间收到刷新信号则立即进行下一轮，
                // 让连接/断开后的 UI 即时更新（不必等满一个周期）
                val waitStart = System.currentTimeMillis()
                var remainingMs = intervalSeconds * 1000
                while (isActive && remainingMs > 0) {
                    val signaled = withTimeoutOrNull(remainingMs) { refreshSignal.first() }
                    if (signaled != null) break
                    remainingMs = intervalSeconds * 1000 - (System.currentTimeMillis() - waitStart)
                }
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
        requestRefresh()
    }

    /**
     * 请求立即刷新设备列表：跳过剩余轮询等待马上执行一次 `adb devices`。
     * 连接/断开等会改变设备状态的操作完成后由宿主调用，消除最长一个轮询
     * 周期的 UI 延迟（控制台已打印成功但设备卡状态慢半拍）。
     */
    fun requestRefresh() {
        refreshSignal.tryEmit(Unit)
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
