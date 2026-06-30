package com.easyadb.core.log

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File

class AppLogger(config: LogConfig = LogConfig()) {

    private val logger = KotlinLogging.logger(config.name)
    private val fileHandler: RollingFileHandler? = if (config.logDir.isNotBlank()) {
        RollingFileHandler(
            basePath = config.logDir,
            maxBytes = config.maxBytes,
            backupCount = config.backupCount
        )
    } else {
        null
    }

    fun debug(message: () -> String) {
        logger.debug(message)
        fileHandler?.append("DEBUG: ${message()}")
    }

    fun info(message: () -> String) {
        logger.info(message)
        fileHandler?.append("INFO: ${message()}")
    }

    fun warn(message: () -> String) {
        logger.warn(message)
        fileHandler?.append("WARN: ${message()}")
    }

    fun error(message: () -> String) {
        logger.error(message)
        fileHandler?.append("ERROR: ${message()}")
    }

    fun infoWithStamp(message: String) {
        logger.info { "[STAMP]$message" }
        fileHandler?.append("INFO: [STAMP]$message")
    }

    fun close() {
        fileHandler?.close()
    }

    companion object {
        /**
         * Create a lightweight logger for a class, writing to log directory.
         */
        fun getLogger(clazz: Class<*>): AppLogger {
            val logDir = System.getProperty("easyadb.log.dir", "")
            return AppLogger(LogConfig(name = clazz.simpleName, logDir = logDir))
        }

        /**
         * Create a logger with custom config.
         */
        fun getLogger(name: String, logDir: String = ""): AppLogger {
            return AppLogger(LogConfig(name = name, logDir = logDir))
        }
    }
}
