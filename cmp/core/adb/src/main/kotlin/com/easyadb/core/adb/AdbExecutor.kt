package com.easyadb.core.adb

import com.easyadb.core.log.AppLogger
import com.easyadb.core.util.ExecResult
import com.easyadb.core.util.ExecUtils
import kotlinx.coroutines.flow.Flow

/**
 * Core ADB operations wrapper.
 * Maps to Python ADBTools in utils/ADBTools.py
 */
class AdbExecutor {

    private val logger = AppLogger.getLogger(AdbExecutor::class.java)
    private val executor = CmdExecutor()
    var currentCmd: String = ""

    /**
     * Execute an ADB command and return the result.
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
     * Execute ADB commands asynchronously via Flow.
     */
    fun asyncExecAdbCmd(cmds: List<String>): Flow<String> = executor.executeFlow(cmds.joinToString(" && "))

    /**
     * Connect to a device.
     */
    suspend fun connectDevice(deviceIp: String): ExecResult {
        val cmd = "adb connect $deviceIp"
        return execAdbCmd(cmd)
    }

    /**
     * Disconnect a device.
     */
    suspend fun disconnectDevice(deviceIp: String): ExecResult {
        val cmd = "adb disconnect $deviceIp"
        return execAdbCmd(cmd)
    }

    /**
     * Get device properties.
     */
    suspend fun getDeviceInfo(ip: String): ExecResult {
        val cmd = "adb -s $ip shell getprop"
        return execAdbCmd(cmd)
    }

    /**
     * Capture screenshot.
     */
    suspend fun getScreenShoot(deviceIp: String, savePath: String): ExecResult {
        val cmd = "adb -s $deviceIp exec-out screencap -p > $savePath"
        return execAdbCmd(cmd, useShell = true, useReturnCode = true)
    }

    /**
     * Start an app page via class path.
     */
    suspend fun startAppPage(ip: String, classPath: String): ExecResult {
        val cmd = "adb -s $ip shell am start $classPath"
        logger.info { "Start app: $classPath" }
        return execAdbCmd(cmd)
    }

    /**
     * Get running processes from a device.
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
     * Start screen recording (returns commands list for async execution).
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
                logger.info { "Screenshot captured successfully!" }
            } else {
                logger.error { "Failed to capture screenshot." }
            }
        }
    }
}
