package com.easyadb.core.adb

import com.easyadb.core.log.LogLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 持续 logcat 流：全量发射（级别/PID 解析后随 [LogcatEntry] 携带），不做前置过滤。
 * 对应 Python 中的 LiveLogAdbThread（utils/ADBTools.py）——Python 的 LogCatFilter
 * 前置过滤在 Compose 版改为「面板侧过滤视图」：全量存入 [LogcatBuffer]，UI 按当前
 * 条件（级别/关键字）派生显示子序列，切换条件可全量重滤，被过滤的行不丢失。
 */

data class LogcatEntry(
    /** 单调递增序号：预算缓冲从头部淘汰导致列表位置持续漂移，查找/跳转用它锚定 */
    val seq: Long,
    val raw: String,
    val pid: String,
    val level: LogLevel
)

/**
 * 按内存预算（KB/MB 级字节值）管理的日志环形缓冲：append 时累加估算字节，超预算即从
 * 最旧的开始淘汰。用字节预算而非条数上限，是因为行长浮动大（50~500 字符），条数无法
 * 稳定表征内存；LazyColumn 虚拟化下渲染成本只与可见行数相关，缓冲总量的唯一约束就是内存。
 *
 * 字节估算取保守值（字符 ×2 按 UTF-16 计 + 每条固定开销），真实占用只低不高。
 */
class LogcatBuffer(private val budgetBytes: Long = DEFAULT_BUDGET_BYTES) {

    private val entries = ArrayDeque<LogcatEntry>()
    private var usedBytes = 0L

    val size: Int get() = entries.size
    val used: Long get() = usedBytes
    val budget: Long get() = budgetBytes

    /**
     * 追加一批条目，超预算时从最旧的开始淘汰。
     * @return 本轮淘汰的条数（淘汰先吃旧条目，越过旧条目后吃追加批里最老的部分）
     */
    fun append(newEntries: List<LogcatEntry>): Int {
        newEntries.forEach {
            entries.addLast(it)
            usedBytes += estimateBytes(it)
        }
        var evicted = 0
        while (usedBytes > budgetBytes && entries.size > 1) {
            usedBytes -= estimateBytes(entries.removeFirst())
            evicted++
        }
        return evicted
    }

    fun clear() {
        entries.clear()
        usedBytes = 0
    }

    /** 缓冲内最老条目的 seq（空缓冲返回 null）——显示视图头部淘汰的同步阈值 */
    fun oldestSeq(): Long? = entries.firstOrNull()?.seq

    /** 全量只读快照（过滤条件变化时全量重滤用；引用拷贝，2 万条约 160KB/次） */
    fun snapshot(): List<LogcatEntry> = entries.toList()

    private fun estimateBytes(entry: LogcatEntry): Long =
        ENTRY_OVERHEAD_BYTES + (entry.raw.length + entry.pid.length) * 2L

    companion object {
        /** 默认 8MB：按典型 logcat 行（~120 字符）约 1.5~2 万条，旧方案 3000 条的 6 倍以上 */
        const val DEFAULT_BUDGET_BYTES: Long = 8L * 1024 * 1024

        /** 单条摊销开销：LogcatEntry 对象头 + 字段引用 + ArrayDeque 槽位的近似值 */
        private const val ENTRY_OVERHEAD_BYTES = 96L
    }
}

/**
 * 使用协程 Flow 的持续 logcat 流。
 * 对应 Python 中的 LiveLogAdbThread（位于 utils/ADBTools.py）
 *
 * 读取循环固定跑在 Dispatchers.IO：callbackFlow 的 block 运行在收集方上下文中，
 * 若直接在 block 里 readLine()，阻塞读会挂住收集方线程（UI）——logcat 启动时
 * 先倾泻整个历史缓冲（数千行瞬间到达），UI 线程会被占死（整窗卡住）。
 *
 * @param budgetBytes 实时日志的内存预算，超预算从最旧开始淘汰；后期可由设置功能注入用户值
 */
class LogcatStream(
    budgetBytes: Long = LogcatBuffer.DEFAULT_BUDGET_BYTES
) {

    private var process: java.lang.Process? = null
    private val seqCounter = java.util.concurrent.atomic.AtomicLong(0)
    private val logcatPattern = Regex(""".+\s([VIDWE])\/.+\(\s*(\d+)\)\:.+$""")
    val buffer = LogcatBuffer(budgetBytes)

    /** 行级 PID/级别解析（与 Python LogCatFilter 相同的格式匹配）；无法解析按 pid=-1/INFO */
    private fun parsePidLevel(msg: String): Pair<String, LogLevel> {
        val match = logcatPattern.find(msg)
        return if (match != null) {
            match.groupValues[2] to LogLevel.fromShortName(match.groupValues[1])
        } else {
            "-1" to LogLevel.INFO
        }
    }

    /**
     * 读取循环固定跑在 Dispatchers.IO：callbackFlow 的 block 运行在收集方上下文中，
     * 若直接在 block 里 readLine()，阻塞读会挂住收集方线程（UI）——logcat 启动时
     * 先倾泻整个历史缓冲（数千行瞬间到达），UI 线程会被占死（整窗卡住）。
     */
    fun logcatFlow(ip: String, filterArgs: String = "-v time"): Flow<LogcatEntry> = callbackFlow {
        val cmd = "adb -s $ip logcat $filterArgs"
        val processBuilder = ProcessBuilder().apply {
            val os = System.getProperty("os.name").lowercase()
            if (os.contains("win")) {
                command("cmd.exe", "/c", cmd)
            } else {
                command("sh", "-c", cmd)
            }
        }

        val proc = processBuilder.start()
        process = proc

        launch(Dispatchers.IO) {
            try {
                val reader = BufferedReader(InputStreamReader(proc.inputStream, Charsets.UTF_8))
                while (true) {
                    val msg = reader.readLine() ?: break
                    if (msg.isEmpty()) continue

                    // 全量发射：过滤职责在面板侧（显示视图派生），此处只解析 level/pid
                    val (pid, level) = parsePidLevel(msg)
                    trySend(LogcatEntry(seq = seqCounter.incrementAndGet(), raw = msg, pid = pid, level = level))
                }

                // 进程退出后读取 stderr（设备离线 / adb 报错等）
                val err = proc.errorStream.bufferedReader(Charsets.UTF_8).readText()
                if (err.isNotBlank()) {
                    trySend(LogcatEntry(seq = seqCounter.incrementAndGet(), raw = err.trim(), pid = "-1", level = LogLevel.ERROR))
                }
            } catch (_: Exception) {
                // 收集方取消 → awaitClose 销毁进程，readLine 抛出的 IO 中断属正常退出路径
            } finally {
                if (process === proc) process = null
                close()
            }
        }

        // 收集方取消（停止按钮 / 面板销毁）→ 立即销毁进程，阻塞中的 readLine 随之返回
        awaitClose {
            proc.destroyForcibly()
        }
    }.buffer(capacity = 1024, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    fun stop() {
        process?.destroyForcibly()
        process = null
    }

    /** 清空已显示的历史日志（缓冲区），不影响级别/关键字等过滤设置 */
    fun clearDisplay() {
        buffer.clear()
    }
}
