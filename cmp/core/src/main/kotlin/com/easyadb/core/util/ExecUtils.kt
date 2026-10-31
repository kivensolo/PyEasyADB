package com.easyadb.core.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
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
        shell: Boolean = true,
        /** 子进程工作目录（null = 继承当前进程）；盘根拉取等场景用于锚定相对路径解析 */
        workingDir: File? = null
    ): ExecResult = withContext(Dispatchers.IO) {
        try {
            val processBuilder = ProcessBuilder().apply {
                workingDir?.let { directory(it) }
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

            // 2026-10-31 录屏 bug2 实测定因：success 只看「未超时」不看退出码，
            // adb pull 失败（exit 1）仍报 success=true → UI 误提示「拉取成功」。
            // 退出码非 0 即失败，stderr/adb 错误信息在 output 里供 UI 展示。
            ExecResult(process.exitValue() == 0, output, process.exitValue())
        } catch (e: Exception) {
            ExecResult(false, "CMD [$command] error: ${e.message}", -1)
        }
    }

    /**
     * 以参数数组直启命令（不经 shell），避免引号/反斜杠转义损坏参数
     * （见 AdbExecutor.execAdbArgs 注释——cmd.exe /c 引号内反斜杠被吃）。
     *
     * @param workingDir 指定即将 start 出来的子进程的工作目录（null = 继承当前进程）
     *                  目前用于盘符根拉取锚定场景（见 AdbExecutor.pullFile）
     */
    suspend fun execArgs(
        args: List<String>,
        timeoutMs: Long = 4000,
        workingDir: File? = null
    ): ExecResult = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder(args).apply {
                workingDir?.let { directory(it) } // 设置启动时的当前工作目录(CWD)
            }.start()

            // 并发读取 stdout / stderr，避免缓冲区写满导致死锁
            val stdoutDeferred = async(Dispatchers.IO) { readStream(process.inputStream) }
            val stderrDeferred = async(Dispatchers.IO) { readStream(process.errorStream) }

            val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                stdoutDeferred.cancel()
                stderrDeferred.cancel()
                return@withContext ExecResult(false, "CMD [${args.joinToString(" ")}] timeout", -1)
            }

            val stdout = stdoutDeferred.await()
            val stderr = stderrDeferred.await()

            val output = if (stderr.isNotBlank()) {
                stdout.trim() + "\n" + stderr.trim()
            } else {
                stdout.trim()
            }

            ExecResult(process.exitValue() == 0, output, process.exitValue())
        } catch (e: Exception) {
            ExecResult(false, "CMD [${args.joinToString(" ")}] error: ${e.message}", -1)
        }
    }

    /**
     * 以 UTF-8 读取输入流的全部内容。
     */
    private fun readStream(stream: InputStream): String {
        return BufferedReader(InputStreamReader(stream, "utf-8")).readText()
    }
}
