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
 * ADB command executor wrapping process execution.
 * Maps to Python CmdExecutor in utils/CmdExecutor.py
 */
class CmdExecutor {

    private val logger = AppLogger.getLogger(CmdExecutor::class.java)

    /**
     * Execute a single ADB command and return the result.
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
     * Execute a command and emit output lines via Flow.
     * Maps to AsyncAdbThread behavior in Python.
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

            // Emit the input command marker
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
        }
    }
}
