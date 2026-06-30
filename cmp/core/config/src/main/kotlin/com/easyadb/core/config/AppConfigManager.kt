package com.easyadb.core.config

import com.easyadb.core.log.AppLogger
import java.io.File

/**
 * 单例管理 AppConfig.ini 配置。
 * 对应 Python 中的 AppConfigManager（位于 AppConfigManager.py）
 */
object AppConfigManager {

    private const val ANDROID_PERMISSIONS_SECTION = "AndroidPermissions"
    private val logger = AppLogger.getLogger(AppConfigManager::class.java)
    private val parser = IniParser()
    private var initialized = false

    fun initialize(configFile: File) {
        if (!configFile.exists()) {
            logger.warn { "AppConfig.ini not found at ${configFile.absolutePath}" }
            return
        }
        parser.load(configFile)
        initialized = true
        logger.info { "AppConfig.ini loaded from ${configFile.absolutePath}" }
    }

    fun permissions(key: String): String {
        if (!initialized) return key
        return parser.get(ANDROID_PERMISSIONS_SECTION, key) ?: key
    }

    fun allPermissions(): Map<String, String> {
        if (!initialized) return emptyMap()
        return parser.getSection(ANDROID_PERMISSIONS_SECTION)
    }
}
