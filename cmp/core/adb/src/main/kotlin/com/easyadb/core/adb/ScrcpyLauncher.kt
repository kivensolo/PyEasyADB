package com.easyadb.core.adb

import com.easyadb.core.config.AppPathsConfig
import com.easyadb.core.log.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * 面向用户的镜像事件（启动/退出诊断/清理），宿主收集后转发到应用控制台——
 * 可见性不依赖文件日志（RollingFileHandler 落盘问题待修）。
 */
data class MirrorEvent(val message: String, val isError: Boolean = false)

/**
 * 镜像（实时预览）状态，驱动 MirrorPanel 按钮启用/禁用。
 */
sealed class MirrorState {
    /** 未在镜像（含从未启动、被手动关闭、scrcpy 窗口被用户直接关闭） */
    data object Idle : MirrorState()

    /** scrcpy 独立窗口运行中 */
    data class Running(val deviceIp: String) : MirrorState()
}

/**
 * 启动镜像的结果。
 */
sealed class MirrorStartResult {
    data object Success : MirrorStartResult()
    /** scrcpy 二进制不存在（附期望路径，宿主用于展示下载指引） */
    data class BinaryMissing(val expectedPath: String) : MirrorStartResult()
    /** 进程启动异常 */
    data class StartFailed(val reason: String) : MirrorStartResult()
    /** 已有镜像在运行，拒绝重复启动 */
    data object AlreadyRunning : MirrorStartResult()
}

/**
 * Scrcpy 独立窗口启动器（P8，对齐 Python `win32Wrapper.py::ScrcpyEmbedWidget` 中
 * 「启动独立 scrcpy 进程」部分；窗口嵌入是 P9 阶段内容，此处不涉及）。
 *
 * 与 Python 版的差异：
 * - Python 裸命令 `scrcpy ...` 依赖先 `addEnvVariable` 注入 PATH，这里直接用 exe 绝对路径；
 * - Python 启动后用 QTimer 2s + FindWindow 按标题找回窗口/PID（为嵌入和杀进程），独立窗口
 *   模式下直接持有 [Process] 引用即可，无需窗口查找。
 *
 * 线程模型：[start] 为 suspend（IO 派发）；进程退出监听用 `Process.onExit` 回写状态，
 * 用户直接关闭 scrcpy 窗口时 UI 也会自动回到 Idle（对齐 Python 找不到窗口即视为停止）。
 */
class ScrcpyLauncher {

    private val logger = AppLogger.getLogger(ScrcpyLauncher::class.java)

    private val _state = MutableStateFlow<MirrorState>(MirrorState.Idle)
    val state: StateFlow<MirrorState> = _state.asStateFlow()

    @Volatile
    private var currentProcess: Process? = null

    /** true 表示进程退出源于 [stop] 主动终止（Windows 下 destroy 即 TerminateProcess(1)，非异常） */
    @Volatile
    private var stopRequested = false

    private val _events = MutableSharedFlow<MirrorEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<MirrorEvent> = _events.asSharedFlow()

    /** 诊断直写文件（UTF-8 追加）：RollingFileHandler 落盘失效期间的可靠可复制通道 */
    private val debugLogFile: File by lazy { File(AppPathsConfig.logsPath, "scrcpy-debug.log") }

    /** 事件四通道：文件（UTF-8 可复制）+ 应用控制台 + stdout + 文件日志 */
    private fun emit(message: String, isError: Boolean = false) {
        appendDebug(message)
        println("[ScrcpyLauncher] $message")
        _events.tryEmit(MirrorEvent(message, isError))
        if (isError) logger.error { message } else logger.info { message }
    }

    @Synchronized
    private fun appendDebug(message: String) {
        runCatching {
            debugLogFile.appendText(
                "[${TIME_FORMATTER.format(java.time.LocalTime.now())}] $message\n\n"
            )
        }
    }

    private val TIME_FORMATTER = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")

    /** 首选手动安装位置（`{AppData}/tool/scrcpy-win64/scrcpy.exe`，AppPathsConfig 口径） */
    val primaryScrcpyExe: File
        get() = File(AppPathsConfig.scrcpyPath, "scrcpy.exe")

    /**
     * 搜索 scrcpy.exe，覆盖 Python 版的全部查找口径：
     * 1. `{AppData}/tool/scrcpy-win64` —— AppPathsConfig.scrcpyPath（手动安装位）
     * 2. `{AppData}/tools/scrcpy-win64` —— Python `Dependencies.ScrcpyChecker` 的自动下载位
     * 3. 运行目录相对 `tool/scrcpy-win64` —— 对齐 Python `settings.SCRCPY_PATH` 的 BASE_PATH 相对路径；
     *    `../`、`../../` 两级覆盖开发期三种工作目录（仓库根 / cmp / cmp:desktop-app）
     */
    fun findScrcpyExe(): File? = listOf(
        primaryScrcpyExe,
        File(AppPathsConfig.toolsPath, "scrcpy-win64/scrcpy.exe"),
        File("tool/scrcpy-win64/scrcpy.exe"),
        File("../tool/scrcpy-win64/scrcpy.exe"),
        File("../../tool/scrcpy-win64/scrcpy.exe")
    ).firstOrNull { it.isFile }

    /** 二进制是否就绪（任一搜索位命中即可；不就绪时宿主展示手动下载指引） */
    fun isBinaryReady(): Boolean = findScrcpyExe() != null

    /**
     * 启动 scrcpy 独立窗口镜像 [deviceIp]。
     * 命令对齐 Python：`scrcpy -s {addr} --window-title "{WINDOW_TITLE}"`。
     */
    suspend fun start(deviceIp: String): MirrorStartResult = withContext(Dispatchers.IO) {
        if (_state.value is MirrorState.Running) {
            // 僵尸自愈：状态卡 Running 但进程已死（秒退竞态遗留 / 外部杀进程未触发回调），
            // 直接回收状态允许重新启动，否则「开启」按钮永远灰死
            if (currentProcess?.isAlive == true) {
                return@withContext MirrorStartResult.AlreadyRunning
            }
            logger.warn { "mirror state is Running but process dead, self-healing to Idle" }
            emit("检测到镜像状态卡死（进程已亡），已自动恢复", isError = true)
            _state.value = MirrorState.Idle
            currentProcess = null
        }

        val exe = findScrcpyExe()
            ?: return@withContext MirrorStartResult.BinaryMissing(primaryScrcpyExe.absolutePath).also {
                logger.warn { "scrcpy binary not found (searched app tool/tools dirs and run dir)" }
            }

        try {
            val startedAt = System.currentTimeMillis()
            stopRequested = false
            val process = ProcessBuilder(
                exe.absolutePath,
                "-s", deviceIp,
                "--window-title", WINDOW_TITLE
            ).start()
            currentProcess = process
            // 排空 stdout/stderr：管道缓冲区（Windows 约 4KB）写满会阻塞子进程（P7 PullApk 死锁教训）；
            // stderr 保留末尾 20 行，进程秒退时写入日志定位原因
            drainSilently(process)
            // 退出回调：仅当仍是当前进程时回置 Idle——防止旧实例的回调覆盖新一轮运行
            process.onExit().thenAccept {
                val code = runCatching { process.exitValue() }.getOrDefault(-1)
                val aliveMs = System.currentTimeMillis() - startedAt
                if (currentProcess === process) {
                    _state.value = MirrorState.Idle
                }
                when {
                    // 主动关闭：destroy() 即 TerminateProcess(1)，退出码 1 且无 stderr 属正常
                    stopRequested ->
                        emit("scrcpy 已停止：pid=${process.pid()} 存活=${aliveMs}ms（主动关闭）")
                    code != 0 ->
                        emit(
                            "scrcpy 异常退出：exitCode=$code 存活=${aliveMs}ms\n" +
                                stderrTail.ifBlank { "(stderr 无输出)" },
                            isError = true
                        )
                    else ->
                        emit("scrcpy 退出：pid=${process.pid()} 存活=${aliveMs}ms")
                }
            }
            _state.value = MirrorState.Running(deviceIp)
            // 竞态兜底：若设置 Running 前进程已秒退且回调已先跑（Idle 被覆盖），此处再校验一次
            if (!process.isAlive && currentProcess === process) {
                _state.value = MirrorState.Idle
            }
            logger.info { "scrcpy started: pid=${process.pid()} device=$deviceIp" }
            MirrorStartResult.Success
        } catch (e: Exception) {
            currentProcess = null
            logger.error { "scrcpy start failed: ${e.message}" }
            MirrorStartResult.StartFailed(e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * 关闭镜像：销毁 scrcpy 进程（Windows 下 destroy 即 TerminateProcess，对齐 Python
     * `win32api.TerminateProcess`）。状态由 onExit 回调统一回置 Idle。未运行时为空操作。
     */
    fun stop() {
        val process = currentProcess ?: return
        stopRequested = true
        try {
            process.destroy()
            // destroy 为异步终止，1s 内未退出则强杀兜底（后台执行，不阻塞 UI 线程）
            thread(isDaemon = true, name = "scrcpy-stop-escalate") {
                if (!process.waitFor(1, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                }
            }
            emit("已请求关闭实时预览：pid=${process.pid()}")
        } catch (e: Exception) {
            runCatching { process.destroyForcibly() }
            emit("关闭实时预览异常：${e.message}", isError = true)
        }
        // 强杀客户端不会释放 adb forward，残留的 scrcpy 转发可能阻塞下次启动；尽力清理
        cleanupStaleForwards()
    }

    /** 清理 adb 中残留的 scrcpy 端口转发（`adb forward --list` 中含 scrcpy 的条目） */
    private fun cleanupStaleForwards() {
        thread(isDaemon = true, name = "scrcpy-forward-cleanup") {
            runCatching {
                val listProcess = ProcessBuilder("adb", "forward", "--list").start()
                val lines = listProcess.inputStream.bufferedReader().readLines()
                listProcess.waitFor(3, TimeUnit.SECONDS)
                val stale = lines.filter { it.contains("scrcpy", ignoreCase = true) }
                stale.forEach { line ->
                    // 格式：<serial> <local> <remote>
                    val parts = line.trim().split(Regex("\\s+"))
                    if (parts.size >= 2) {
                        runCatching {
                            ProcessBuilder("adb", "-s", parts[0], "forward", "--remove", parts[1])
                                .start().waitFor(3, TimeUnit.SECONDS)
                        }
                    }
                }
                if (stale.isNotEmpty()) {
                    emit("已清理 ${stale.size} 条残留的 scrcpy adb forward")
                }
            }
        }
    }

    /** scrcpy stderr 的末尾若干行（进程秒退时随错误日志输出，定位失败原因） */
    @Volatile
    private var stderrTail: String = ""

    private fun drainSilently(process: Process) {
        thread(isDaemon = true, name = "scrcpy-stdout") {
            runCatching { process.inputStream.bufferedReader().forEachLine { } }
        }
        thread(isDaemon = true, name = "scrcpy-stderr") {
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

    companion object {
        /** scrcpy 独立窗口标题（对齐 Python ScrcpyEmbedWidget.windowTitle） */
        const val WINDOW_TITLE = "EasyADB Device Mirror"
    }
}
