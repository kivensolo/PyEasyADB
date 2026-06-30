package com.easyadb.core.log

data class LogConfig(
    val name: String = "EasyADB",
    val maxBytes: Long = 4 * 1024 * 1024,
    val backupCount: Int = 128,
    val logDir: String = "",
    val level: LogLevel = LogLevel.DEBUG
)
