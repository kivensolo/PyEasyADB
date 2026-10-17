package com.easyadb.core.log

import java.io.File

/**
 * 日志落盘自检（开发工具，`gradlew :core:log:run`）：在临时目录真实跑
 * RollingFileHandler + AppLogger 共享写入，断言文件生成、UTF-8 内容、
 * 大小滚动备份、多 logger 共目录写入，结果打印 PASS/FAIL。
 */
fun main() {
    val out: (String) -> Unit = { println("[LogSelfCheck] $it") }
    var failed = false
    fun check(name: Boolean, desc: String) {
        if (name) out("PASS  $desc") else { out("FAIL  $desc"); failed = true }
    }

    // ── 1) 默认目录解析（统一当前运行目录/logs，不用 LOCALAPPDATA） ──
    val defaultDir = LogDirs.default()
    out("default dir = $defaultDir")
    check(defaultDir == File(System.getProperty("user.dir"), "logs").absolutePath, "默认目录 = user.dir/logs：$defaultDir")

    // ── 2) AppLogger 写入共享 handler（临时目录） ──
    val tmp = File(System.getProperty("java.io.tmpdir"), "easyadb-log-selfcheck-${System.nanoTime()}")
    tmp.mkdirs()
    val today = java.time.LocalDate.now().toString()
    val logFile = File(tmp, "log_$today.log")

    val loggerA = AppLogger.getLogger("SelfCheckA", logDir = tmp.absolutePath)
    val loggerB = AppLogger.getLogger("SelfCheckB", logDir = tmp.absolutePath)
    loggerA.info { "中文消息甲-A" }
    loggerB.warn { "中文消息乙-B" }
    loggerA.infoWithStamp("带戳消息")

    check(logFile.exists() && logFile.length() > 0, "日志文件已生成且有内容：${logFile.name}")
    val content = logFile.readText(Charsets.UTF_8)
    check(content.contains("INFO [SelfCheckA] 中文消息甲-A"), "A 的行含 logger 名与中文（UTF-8）")
    check(content.contains("WARN [SelfCheckB] 中文消息乙-B"), "B 的行落到同一文件（共享 handler）")
    check(Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3} INFO \[SelfCheckA\]""").containsMatchIn(content), "行带时间戳+级别前缀")
    check(content.contains("[STAMP]带戳消息"), "infoWithStamp 落盘")

    // ── 3) 大小滚动 ──
    val rollDir = File(tmp, "roll").apply { mkdirs() }
    val small = RollingFileHandler(basePath = rollDir.absolutePath, maxBytes = 120, backupCount = 3)
    repeat(10) { small.append("${"x".repeat(60)} line-$it") }
    small.close()
    val backups = rollDir.listFiles { f -> f.name.endsWith(".log.1") } ?: emptyArray()
    check(backups.isNotEmpty(), "超限后产生 .1 备份")
    check((rollDir.listFiles()?.size ?: 0) <= 5, "备份数受 backupCount 约束（log + .1/.2/.3）")
    out("roll dir 文件: ${rollDir.listFiles()?.joinToString { it.name }}")

    // ── 4) 收尾 ──
    AppLogger.closeAll()
    tmp.deleteRecursively()
    out(if (failed) "RESULT: FAIL ❌" else "RESULT: PASS ✅")
    if (failed) kotlin.system.exitProcess(1)
}
