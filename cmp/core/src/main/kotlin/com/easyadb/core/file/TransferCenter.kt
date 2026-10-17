package com.easyadb.core.file

import com.easyadb.core.util.ExecUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
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
        val toPath = saveDir.trimEnd('\\', '/') + "\\" + fileName
        run(
            direction = Direction.DEV_TO_PC,
            fileName = fileName,
            command = "adb -s $deviceIp pull \"$deviceFilePath\" \"$toPath\"",
            successLog = "[拉取成功] $deviceFilePath -> $toPath",
            failPrefix = "[拉取失败]"
        )
    }

    private fun run(direction: Direction, fileName: String, command: String, successLog: String, failPrefix: String) {
        val job = TransferJob(nextId.incrementAndGet(), direction, fileName, "", "", State.RUNNING)
        jobs.value = listOf(job) + jobs.value
        log("> $command", 2)
        scope.launch {
            // shell=true（cmd/sh 包装）：引号包裹的带空格路径由 shell 正确解析；
            // ExecUtils 的直连模式按空格切分会截断引号路径
            val result = ExecUtils.execCmd(command, timeoutMs = TRANSFER_TIMEOUT_MS, shell = true)
            if (result.success) {
                update(job.id) { it.copy(state = State.DONE) }
                log(successLog, 2)
                // 完成项 1.2s 后移除（对齐 H5 队列卡片淡出节奏）
                delay(1200)
                jobs.value = jobs.value.filterNot { it.id == job.id }
            } else {
                update(job.id) { it.copy(state = State.FAILED, error = result.output.take(120)) }
                log("$failPrefix ${result.output.take(200)}", 4)
            }
        }
    }

    private fun update(id: Long, transform: (TransferJob) -> TransferJob) {
        jobs.value = jobs.value.map { if (it.id == id) transform(it) else it }
    }
}
