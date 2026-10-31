package com.easyadb.core.adb

import com.easyadb.core.log.AppLogger
import com.easyadb.core.util.ExecResult
import com.easyadb.core.util.ExecUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.currentCoroutineContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * ADB 命令执行器，包装进程执行。
 * 对应 Python 中的 CmdExecutor（位于 utils/CmdExecutor.py）
 */
class CmdExecutor {

    private val logger = AppLogger.getLogger(CmdExecutor::class.java)

    /**
     * 执行单个 ADB 命令并返回结果。
     */
    suspend fun execute(
        cmd: String,
        useShell: Boolean = true,
        returnCode: Boolean = false
    ): String {
        val result = ExecUtils.execCmd(cmd, timeoutMs = 20000, shell = useShell)
        return if (returnCode) {
            result.exitCode.toString()
        } else {
            result.output
        }
    }

    /**
     * 执行命令并通过 Flow 发送输出行。
     * 对应 Python 中的 AsyncAdbThread 行为。
     *
     * producer 结束后必须 [close] channel（2026-10-31 录屏 bug1 实测定因）：
     * callbackFlow 的 producer 块跑完不等于 flow 结束——channel 不关，
     * collect 永久挂起等下一条数据 → 录屏 recordJob 永不结束 →
     * isRecording 恒 true，「开始/拉取」按钮永久禁用。
     * 进程/读流本身没问题（EOF、waitFor 均正常），纯粹是漏关 channel。
     */
    fun executeFlow(cmd: String, useShell: Boolean = true): Flow<String> = callbackFlow {
        try {
            val processBuilder = ProcessBuilder().apply {
                val os = System.getProperty("os.name").lowercase()
                if (os.contains("win")) {
                    command("cmd.exe", "/c", cmd)
                } else {
                    command("sh", "-c", cmd)
                }
            }

            val process = processBuilder.start()
            val reader = BufferedReader(InputStreamReader(process.inputStream, "utf-8"))

            // 发送输入命令标记
            trySend("input: $cmd")

            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (!isActive) {
                    process.destroyForcibly()
                    break
                }
                trySend(line!!)
            }

            val errReader = BufferedReader(InputStreamReader(process.errorStream, "utf-8"))
            while (errReader.readLine().also { line = it } != null) {
                if (!isActive) break
                trySend(line!!)
            }

            process.waitFor()
        } catch (e: Exception) {
            logger.error { "Execute flow error: ${e.message}" }
        } finally {
            // stdout/stderr 读尽、进程退出后关闭 channel——collect 才能正常返回
            close()
        }
    }
}
