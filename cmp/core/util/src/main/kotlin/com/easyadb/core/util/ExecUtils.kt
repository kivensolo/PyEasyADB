package com.easyadb.core.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

object ExecUtils {

    /**
     * Execute a command and return its output.
     * Maps to Python exec_cmd() in utils/Tools.py
     */
    suspend fun execCmd(
        command: String,
        timeoutMs: Long = 4000,
        shell: Boolean = true
    ): ExecResult = withContext(Dispatchers.IO) {
        try {
            val processBuilder = ProcessBuilder().apply {
                if (shell) {
                    val os = System.getProperty("os.name").lowercase()
                    if (os.contains("win")) {
                        command("cmd.exe", "/c", command)
                    } else {
                        command("sh", "-c", command)
                    }
                } else {
                    command(command.split(" "))
                }
            }

            val process = processBuilder.start()

            val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                return@withContext ExecResult(false, "CMD [$command] timeout", -1)
            }

            val stdout = BufferedReader(InputStreamReader(process.inputStream, "utf-8")).readText()
            val stderr = BufferedReader(InputStreamReader(process.errorStream, "utf-8")).readText()

            val output = if (stderr.isNotBlank()) {
                stdout.trim() + "\n" + stderr.trim()
            } else {
                stdout.trim()
            }

            ExecResult(true, output, process.exitValue())
        } catch (e: Exception) {
            ExecResult(false, "CMD [$command] error: ${e.message}", -1)
        }
    }
}
