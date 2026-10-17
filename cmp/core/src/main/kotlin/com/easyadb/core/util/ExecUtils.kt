package com.easyadb.core.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

object ExecUtils {

    /**
     * 执行命令并返回输出结果。
     * 对应 Python 中的 exec_cmd()（位于 utils/Tools.py）
     *
     * 注意：必须在 [Process.waitFor] 之前并发消费 stdout/stderr，
     * 否则当子进程输出超过 OS pipe 缓冲区（Windows ~4KB）时，
     * 子进程会阻塞在写流，导致 waitFor 永不返回（经典死锁）。
     * 例如 `pm list packages -f` 在设备上有数十个应用时即会触发。
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

            // 并发读取 stdout / stderr，避免缓冲区写满导致死锁
            val stdoutDeferred = async(Dispatchers.IO) { readStream(process.inputStream) }
            val stderrDeferred = async(Dispatchers.IO) { readStream(process.errorStream) }

            val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                stdoutDeferred.cancel()
                stderrDeferred.cancel()
                return@withContext ExecResult(false, "CMD [$command] timeout", -1)
            }

            val stdout = stdoutDeferred.await()
            val stderr = stderrDeferred.await()

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

    /**
     * 以 UTF-8 读取输入流的全部内容。
     */
    private fun readStream(stream: InputStream): String {
        return BufferedReader(InputStreamReader(stream, "utf-8")).readText()
    }
}
