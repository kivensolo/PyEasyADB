package com.easyadb.core.log

import java.io.File

data class LogConfig(
    val name: String = "EasyADB",
    val maxBytes: Long = 4 * 1024 * 1024,
    val backupCount: Int = 128,
    val logDir: String = "",
    val level: LogLevel = LogLevel.DEBUG
)

/**
 * 日志目录解析：系统属性 [SYSTEM_PROPERTY] 优先，缺省 **当前运行目录（user.dir）下的
 * logs**——对齐 Python 版 `LOGS_PATH = BASE_PATH\logs`（BASE_PATH = os.getcwd()）。
 * 统一约定：哪怕将来打包 exe，日志也在 exe 运行时的工作目录下生成（`user.dir`）。
 */
object LogDirs {
    const val SYSTEM_PROPERTY = "easyadb.log.dir"

    fun default(): String = File(System.getProperty("user.dir"), "logs").absolutePath
}
