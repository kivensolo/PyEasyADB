package com.easyadb.core.file

import com.easyadb.core.util.ExecUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * 传输任务中心（SP-5001）——push/pull 任务队列的状态与执行，**宿主持有**。
 *
 * 生命周期关键约束：内容区 Tab 走 AnimatedContent，切走即销毁文件传输面板
 * （面板协程随取消），传输任务必须独立于面板存活——与 consoleLogFlow 同层级，
 * 由 Main.kt 创建一次注入，切 Tab 传输不中断、切回队列仍在。
 *
 * 进度为 v1 口径（不确定动画 + 成功/失败终态）：adb 命令行通道无流式进度输出，
 * 真实百分比待 v2 改走 adb sync 协议（SP-5001 §7）。
 *
 * @param log 控制台回显（宿主 logAppender：message + level，2=info/4=error），
 *   命令先行 `> adb ...` 回显、成功/失败方括号消息（Main.kt 既有格式）
 */
class TransferCenter(private val log: (String, Int) -> Unit) {

    enum class Direction(val label: String) { PC_TO_DEV("PC → 设备"), DEV_TO_PC("设备 → PC") }

    enum class State { RUNNING, DONE, FAILED }

    data class TransferJob(
        val id: Long,
        val direction: Direction,
        val fileName: String,
        val fromPath: String,
        val toPath: String,
        val state: State = State.RUNNING,
        val error: String? = null
    )

    /** 传输队列（新任务插队首）；DONE 项 1.2s 后移除、FAILED 项保留供查看错误 */
    val jobs = MutableStateFlow<List<TransferJob>>(emptyList())

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val nextId = AtomicLong(0)

    /** 大文件传输预留（1GB 局域网约 1~2 分钟；adb 命令行无流式进度，超时即判失败） */
    private val TRANSFER_TIMEOUT_MS = 30 * 60_000L

    /** RUNNING 最小可见时长（ms）：小文件秒完也让方向动画被看见（走查反馈） */
    private val MIN_VISIBLE_MS = 1000L

    /** DONE 态驻留时长（ms）：完成后队列卡片停留再淡出移除 */
    private val DONE_DWELL_MS = 1800L

    /** 推送本机文件到设备目录（targetDeviceDir 需为目录路径，文件名保留原名） */
    fun submitPush(deviceIp: String, localPath: String, targetDeviceDir: String) {
        val fileName = localPath.substringAfterLast('\\').substringAfterLast('/')
        val toPath = targetDeviceDir.trimEnd('/') + "/" + fileName
        run(
            direction = Direction.PC_TO_DEV,
            fileName = fileName,
            command = "adb -s $deviceIp push \"$localPath\" \"$toPath\"",
            successLog = "[推送成功] $fileName -> $toPath",
            failPrefix = "[推送失败]"
        )
    }

    /** 拉取设备文件到本机（targetName 支持另存改名；saveDir 为本机目录） */
    fun submitPull(deviceIp: String, deviceFilePath: String, saveDir: String, targetName: String? = null) {
        val fileName = targetName ?: deviceFilePath.trimEnd('/').substringAfterLast('/')
        // 本地落盘名剔除 Windows 非法字符（设备文件名可含 : ? 等本地不允许的字符）
        val safeName = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val dir = saveDir.trimEnd('\\', '/')
        if (dir.length == 2 && dir[1] == ':') {
            // 盘符根目标：adb 对 E:\name 报 cannot create（拆父目录缺陷）；E:name 是
            // 驱动器相对路径，落点随进程隐藏变量 =E:（当前目录）漂移——App 从项目目录
            // 启动时 =E: 指向项目目录，文件落错位置（走查实测落到了 desktop-app\）。
            // 确定性做法：子进程 CWD 设为盘根 + 目标用纯文件名（相对路径走真实 CWD，
            // 实测稳定落盘根）。执行命令保持裸文件名形态，回显展示解析后的绝对路径。
            run(
                direction = Direction.DEV_TO_PC,
                fileName = safeName,
                command = "adb -s $deviceIp pull \"$deviceFilePath\" \"$safeName\"",
                echoCommand = "adb -s $deviceIp pull \"$deviceFilePath\" \"$dir\\$safeName\"",
                successLog = "[拉取成功] $deviceFilePath -> $dir\\$safeName",
                failPrefix = "[拉取失败]",
                workingDir = File("$dir\\")
            )
            return
        }
        val toPath = dir + "\\" + safeName
        run(
            direction = Direction.DEV_TO_PC,
            fileName = safeName,
            command = "adb -s $deviceIp pull \"$deviceFilePath\" \"$toPath\"",
            successLog = "[拉取成功] $deviceFilePath -> $toPath",
            failPrefix = "[拉取失败]"
        )
    }

    private fun run(
        direction: Direction,
        fileName: String,
        command: String,
        successLog: String,
        failPrefix: String,
        workingDir: File? = null,
        /** 回显用命令文本（默认 = 实际命令；盘根等形态实际命令带解析技巧时，回显展示绝对路径） */
        echoCommand: String? = null
    ) {
        val job = TransferJob(nextId.incrementAndGet(), direction, fileName, "", "", State.RUNNING)
        jobs.value = listOf(job) + jobs.value
        log("> ${echoCommand ?: command}", 2)
        scope.launch {
            val startedAt = System.currentTimeMillis()
            val result = ExecUtils.execCmd(command, timeoutMs = TRANSFER_TIMEOUT_MS, shell = true, workingDir = workingDir)
            val ok = result.success && result.exitCode == 0
            if (ok) {
                // 最小可见时长：小文件秒完也让方向动画被看见（走查反馈），
                // 完成态再驻留 1.8s 后移除
                val elapsed = System.currentTimeMillis() - startedAt
                if (elapsed < MIN_VISIBLE_MS) delay(MIN_VISIBLE_MS - elapsed)
                update(job.id) { it.copy(state = State.DONE) }
                log(successLog, 2)
                delay(DONE_DWELL_MS)
                jobs.value = jobs.value.filterNot { it.id == job.id }
            } else {
                val reason = result.output.lineSequence()
                    .lastOrNull { it.contains("error", ignoreCase = true) || it.contains("failed", ignoreCase = true) }
                    ?: result.output.take(120)
                update(job.id) { it.copy(state = State.FAILED, error = reason?.take(120)) }
                log("$failPrefix $reason", 4)
            }
        }
    }

    private fun update(id: Long, transform: (TransferJob) -> TransferJob) {
        jobs.value = jobs.value.map { if (it.id == id) transform(it) else it }
    }
}
