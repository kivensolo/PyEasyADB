package com.easyadb.core.adb

import com.easyadb.core.log.AppLogger
import com.easyadb.core.util.ExecResult
import com.easyadb.core.util.ExecUtils
import kotlinx.coroutines.flow.Flow

/**
 * 核心 ADB 操作封装。
 * 对应 Python 中的 ADBTools（位于 utils/ADBTools.py）
 */
class AdbExecutor {

    private val logger = AppLogger.getLogger(AdbExecutor::class.java)
    private val executor = CmdExecutor()
    var currentCmd: String = ""

    /**
     * 执行 ADB 命令并返回结果。
     */
    suspend fun execAdbCmd(
        cmd: String,
        useShell: Boolean = false,
        useReturnCode: Boolean = false
    ): ExecResult {
        logger.infoWithStamp(cmd)
        currentCmd = cmd
        return ExecUtils.execCmd(cmd, timeoutMs = 20000, shell = useShell)
    }

    /**
     * 通过 Flow 异步执行 ADB 命令。
     */
    fun asyncExecAdbCmd(cmds: List<String>): Flow<String> = executor.executeFlow(cmds.joinToString(" && "))

    /**
     * 连接到设备。
     */
    suspend fun connectDevice(deviceIp: String): ExecResult {
        val cmd = "adb connect $deviceIp"
        return execAdbCmd(cmd)
    }

    /**
     * 断开设备连接。
     */
    suspend fun disconnectDevice(deviceIp: String): ExecResult {
        val cmd = "adb disconnect $deviceIp"
        return execAdbCmd(cmd)
    }

    /**
     * 获取设备属性。
     */
    suspend fun getDeviceInfo(ip: String): ExecResult {
        val cmd = "adb -s $ip shell getprop"
        return execAdbCmd(cmd)
    }

    /**
     * 截取屏幕截图。
     */
    suspend fun getScreenShoot(deviceIp: String, savePath: String): ExecResult {
        val cmd = "adb -s $deviceIp exec-out screencap -p > $savePath"
        return execAdbCmd(cmd, useShell = true, useReturnCode = true)
    }

    /**
     * 通过类路径启动应用页面。
     */
    suspend fun startAppPage(ip: String, classPath: String): ExecResult {
        val cmd = "adb -s $ip shell am start $classPath"
        logger.info { "Start app: $classPath" }
        return execAdbCmd(cmd)
    }

    /**
     * 获取设备上正在运行的进程列表。
     */
    suspend fun getRunningProcesses(deviceName: String): List<ProcessInfo> {
        val cmd = "adb -s $deviceName shell ps"
        val result = ExecUtils.execCmd(cmd, timeoutMs = 15000)
        if (!result.success) {
            logger.error { "Failed to get processes: ${result.output}" }
            return emptyList()
        }
        return ProcessUtils.getFilteredProcesses(result.output)
    }

    /**
     * 开始屏幕录制（返回用于异步执行的命令列表）。
     */
    fun getScreenRecordCommands(deviceIp: String, recordCmd: String, tmpPath: String, pullPath: String): List<String> {
        return listOf(
            "adb -s $deviceIp exec-out $recordCmd",
            "adb -s $deviceIp shell sleep 5",
            "adb pull $tmpPath $pullPath",
            "adb shell rm $tmpPath"
        )
    }

    companion object {
        fun onScreenShotFinished(code: String) {
            val logger = AppLogger.getLogger(AdbExecutor::class.java)
            if (code == "0") {
                logger.info { "截图已成功捕获！" }
            } else {
                logger.error { "截图捕获失败。" }
            }
        }
    }
}
