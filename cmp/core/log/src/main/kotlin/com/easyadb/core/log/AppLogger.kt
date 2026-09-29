package com.easyadb.core.log

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

/**
 * 应用日志器：kotlin-logging（控制台）+ 共享 [RollingFileHandler]（文件落盘）。
 *
 * 文件行格式：`yyyy-MM-dd HH:mm:ss.SSS LEVEL [name] message`（对齐 Python 版
 * `%(asctime)s %(levelname)s (...) - %(message)s` 的时间戳+级别习惯）。
 *
 * ## 落盘修复要点（2026-09-18）
 * - 默认目录此前读系统属性、缺省为空 → fileHandler 恒为 null，P1 起从未落盘；
 *   现缺省解析到**当前运行目录下的 logs**（见 [LogDirs]，统一不用 LOCALAPPDATA）；
 * - 同一目录的所有 logger 共享一个 RollingFileHandler（多实例各写各的句柄会让
 *   滚动计数失真、Windows renameTo 被占用句柄卡死）。
 */
class AppLogger(config: LogConfig = LogConfig()) {

    private val logger = KotlinLogging.logger(config.name)
    private val loggerName = config.name
    private val fileHandler: RollingFileHandler? = if (config.logDir.isNotBlank()) {
        sharedHandler(config.logDir, config.maxBytes, config.backupCount)
    } else {
        null
    }

    fun debug(message: () -> String) {
        logger.debug(message)
        fileHandler?.append(format("DEBUG", message()))
    }

    fun info(message: () -> String) {
        logger.info(message)
        fileHandler?.append(format("INFO", message()))
    }

    fun warn(message: () -> String) {
        logger.warn(message)
        fileHandler?.append(format("WARN", message()))
    }

    fun error(message: () -> String) {
        logger.error(message)
        fileHandler?.append(format("ERROR", message()))
    }

    fun infoWithStamp(message: String) {
        logger.info { "[STAMP]$message" }
        fileHandler?.append(format("INFO", "[STAMP]$message"))
    }

    private fun format(level: String, message: String): String =
        "${timestampFormatter.format(LocalDateTime.now())} $level [$loggerName] $message"

    companion object {
        private val timestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

        /** 目录 → 共享 handler（同目录多 logger 复用同一文件句柄与滚动状态） */
        private val sharedHandlers = ConcurrentHashMap<String, RollingFileHandler>()

        private fun sharedHandler(logDir: String, maxBytes: Long, backupCount: Int): RollingFileHandler =
            sharedHandlers.computeIfAbsent(logDir) {
                RollingFileHandler(basePath = logDir, maxBytes = maxBytes, backupCount = backupCount)
            }

        /** 全部共享 handler 关闭（应用退出时调用） */
        fun closeAll() {
            sharedHandlers.values.forEach { it.close() }
            sharedHandlers.clear()
        }

        /**
         * 为类创建轻量级日志记录器，写入日志目录。
         */
        fun getLogger(clazz: Class<*>): AppLogger {
            return AppLogger(LogConfig(name = clazz.simpleName, logDir = LogDirs.default()))
        }

        /**
         * 使用自定义配置创建日志记录器。logDir 缺省解析到默认日志目录（[LogDirs.default]）。
         */
        fun getLogger(name: String, logDir: String = LogDirs.default()): AppLogger {
            return AppLogger(LogConfig(name = name, logDir = logDir))
        }
    }
}
