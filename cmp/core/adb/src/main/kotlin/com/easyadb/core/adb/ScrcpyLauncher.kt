package com.easyadb.core.adb

import com.easyadb.core.config.AppPathsConfig
import com.easyadb.core.log.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.concurrent.thread

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
        if (_state.value is MirrorState.Running) return@withContext MirrorStartResult.AlreadyRunning

        val exe = findScrcpyExe()
            ?: return@withContext MirrorStartResult.BinaryMissing(primaryScrcpyExe.absolutePath).also {
                logger.warn { "scrcpy binary not found (searched app tool/tools dirs and run dir)" }
            }

        try {
            val process = ProcessBuilder(
                exe.absolutePath,
                "-s", deviceIp,
                "--window-title", WINDOW_TITLE
            ).start()
            currentProcess = process
            // 排空 stdout/stderr：管道缓冲区（Windows 约 4KB）写满会阻塞子进程（P7 PullApk 死锁教训）
            drainSilently(process)
            // 用户直接关 scrcpy 窗口 / 异常退出 → 状态回 Idle，UI 按钮（开启）随之恢复
            process.onExit().thenAccept {
                _state.value = MirrorState.Idle
                logger.info { "scrcpy exited: pid=${process.pid()} exitCode=${process.exitValue()}" }
            }
            _state.value = MirrorState.Running(deviceIp)
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
        try {
            process.destroy()
            logger.info { "scrcpy stop requested: pid=${process.pid()}" }
        } catch (e: Exception) {
            // 兜底强杀，避免句柄失效后进程残留
            runCatching { process.destroyForcibly() }
            logger.error { "scrcpy stop failed: ${e.message}" }
        }
    }

    private fun drainSilently(process: Process) {
        thread(isDaemon = true, name = "scrcpy-stdout") {
            runCatching { process.inputStream.bufferedReader().forEachLine { } }
        }
        thread(isDaemon = true, name = "scrcpy-stderr") {
            runCatching { process.errorStream.bufferedReader().forEachLine { } }
        }
    }

    companion object {
        /** scrcpy 独立窗口标题（对齐 Python ScrcpyEmbedWidget.windowTitle） */
        const val WINDOW_TITLE = "EasyADB Device Mirror"
    }
}
