package com.easyadb.core.adb

import com.easyadb.core.log.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * 日志抓取状态。
 */
sealed class CaptureState {

    /** 未在抓取 */
    data object Idle : CaptureState()

    /** 抓取进行中：logcat 输出经 `Redirect.to` 实时写入 [file]（UI 侧轮询 File.length() 展示真实落盘大小） */
    data class Running(val file: File, val startedAtMs: Long) : CaptureState()

    /**
     * 抓取进程已退出。注意 Windows 下 destroy() 即 TerminateProcess(1)，退出码无法区分手动停止
     * 与异常退出——成功与否由 UI 侧按 [file] 是否存在且非空判定（stderr 仅供诊断回显）。
     */
    data class Stopped(val exitCode: Int, val stderrTail: String) : CaptureState()

    /** 进程未能启动（如 adb 不在 PATH） */
    data class Failed(val message: String, val stderrTail: String) : CaptureState()
}

/**
 * 日志抓取器：启动无超时的 `adb logcat` 长命令，将输出实时落盘到本机文件（SP-2003）。
 *
 * 与 [LogcatStream] 的区别：LogcatStream 面向实时日志面板（逐行解析为 LogcatEntry、环形缓冲），
 * 不适合文件抓取；本类是纯进程生命周期包装，不解析内容。
 *
 * 实现决策（承 SP-2001）：
 * - 直接 ProcessBuilder 参数数组启动 adb（裸 `adb` 依赖 PATH，与 [AdbExecutor.captureScreenBytes]
 *   一致），不经 `cmd.exe /c` 包裹，避免 CRLF/编码污染；
 * - `Redirect.to(file)` 由 JVM 以 CREATE+TRUNCATE+WRITE 打开——重新抓取天然覆盖旧文件，无需先删；
 * - stdout 直写文件不需要读线程；stderr 保留末 20 行，秒退时供 UI 诊断回显。
 *
 * 线程模型：[start] 为 suspend（IO 派发，仅覆盖 spawn，不等待退出）；退出经 `Process.onExit`
 * 回写状态；[stop] 为异步终止（destroy + 1s 看门狗 destroyForcibly，仿 [ScrcpyLauncher.stop]）。
 */
class LogcatCapture {

    private val logger = AppLogger.getLogger(LogcatCapture::class.java)

    private val _state = MutableStateFlow<CaptureState>(CaptureState.Idle)
    val state: StateFlow<CaptureState> = _state.asStateFlow()

    @Volatile
    private var currentProcess: Process? = null

    /** true 表示退出源于 [stop] 主动终止 */
    @Volatile
    private var stopRequested = false

    /** stderr 的末尾若干行（进程秒退时随失败诊断展示） */
    @Volatile
    private var stderrTail: String = ""

    /**
     * 启动抓取：`adb -s [deviceIp] logcat [opts]`，输出重定向到 [targetFile]（覆盖写）。
     * 启动失败（adb 缺失等）回 [CaptureState.Failed]；成功进入 [CaptureState.Running]。
     */
    suspend fun start(deviceIp: String, opts: List<String>, targetFile: File): Unit =
        withContext(Dispatchers.IO) {
            // 防重入：上一轮进程未回收时拒绝再次启动
            if (currentProcess?.isAlive == true) return@withContext

            stopRequested = false
            stderrTail = ""
            val cmd = "adb -s $deviceIp logcat ${opts.joinToString(" ")}"
            logger.infoWithStamp(cmd)
            try {
                val process = ProcessBuilder(
                    listOf("adb", "-s", deviceIp, "logcat") + opts
                ).redirectOutput(ProcessBuilder.Redirect.to(targetFile)).start()
                currentProcess = process
                drainSilently(process)
                process.onExit().thenAccept {
                    if (currentProcess === process) {
                        val code = runCatching { process.exitValue() }.getOrDefault(-1)
                        logger.info { "logcat capture exited: exitCode=$code stopRequested=$stopRequested" }
                        _state.value = CaptureState.Stopped(code, stderrTail)
                        currentProcess = null
                    }
                }
                // 竞态兜底：设置 Running 前进程已秒退且回调已先跑，以 Stopped 为准
                if (process.isAlive) {
                    _state.value = CaptureState.Running(targetFile, System.currentTimeMillis())
                } else if (_state.value !is CaptureState.Stopped) {
                    _state.value = CaptureState.Stopped(runCatching { process.exitValue() }.getOrDefault(-1), stderrTail)
                }
            } catch (e: Exception) {
                logger.error { "logcat capture start failed: ${e.message}" }
                _state.value = CaptureState.Failed(e.message ?: e.javaClass.simpleName, stderrTail)
            }
        }

    /**
     * 停止抓取：destroy + 1s 强杀看门狗（后台线程，不阻塞 UI）。状态由 onExit 统一回写
     * [CaptureState.Stopped]。未运行时空操作。
     */
    fun stop() {
        val process = currentProcess ?: return
        stopRequested = true
        runCatching {
            process.destroy()
            thread(isDaemon = true, name = "logcap-stop-escalate") {
                if (!process.waitFor(1, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                }
            }
        }.onFailure {
            runCatching { process.destroyForcibly() }
        }
    }

    /** 丢弃状态回 Idle（UI 关闭弹窗/重新配置时调用，进程退出回调仍会落地 Stopped 供查询） */
    fun reset() {
        if (currentProcess?.isAlive != true) {
            currentProcess = null
            _state.value = CaptureState.Idle
        }
    }

    private fun drainSilently(process: Process) {
        // stdout 已 Redirect.to 文件，无需读线程；仅排空 stderr 防管道写满阻塞
        thread(isDaemon = true, name = "logcap-stderr") {
            runCatching {
                val tail = ArrayDeque<String>()
                process.errorStream.bufferedReader().forEachLine { line ->
                    synchronized(tail) {
                        tail.addLast(line)
                        if (tail.size > 20) tail.removeFirst()
                    }
                }
                stderrTail = synchronized(tail) { tail.joinToString("\n") }
            }
        }
    }
}
