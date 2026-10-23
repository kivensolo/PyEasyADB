package com.easyadb.core.config

import com.easyadb.core.log.AppLogger
import java.io.File
import java.io.InputStream

/**
 * 单例管理 AppConfig.ini 配置。
 * 对应 Python 中的 AppConfigManager（位于 AppConfigManager.py）
 *
 * P11 起「静态内容 + 用户配置」分置：
 * - [initialize] 先载入默认内置数据（开发态：运行目录 res/config/AppConfig.ini；打包态：
 *   classpath 内 /config/AppConfig.ini），再叠加用户文件（不存在的段/键保留默认内置值）；
 * - 用户文件落在可写位置（`%LOCALAPPDATA%/EasyADB/data/AppConfig.ini`），承载
 *   运行时设置（`[General] Theme=system|light|dark`）；首次 [save] 时把内存中
 *   的全量内容（默认内置+用户）写进去，此后不再依赖默认内置文件；
 * - [set] 只改内存，[save] 落盘——调用方成对使用。
 */
object AppConfigManager {

    private const val ANDROID_PERMISSIONS_SECTION = "AndroidPermissions"

    /** 通用段：运行时设置（主题等） */
    const val GENERAL_SECTION = "General"

    private val logger = AppLogger.getLogger(AppConfigManager::class.java)
    private val parser = IniParser()
    private var initialized = false
    private var userConfigFile: File? = null

    /**
     * 初始化：[seedFile]（若存在）与 [seedStream]（若非 null）提供静态默认值，
     * [userConfigFile] 存在时其键值叠加覆盖。任何默认内置数据缺失都不算错误。
     */
    fun initialize(userConfigFile: File, seedFile: File? = null, seedStream: InputStream? = null) {
        this.userConfigFile = userConfigFile
        seedFile?.takeIf { it.exists() }?.let {
            parser.load(it)
            logger.info { "AppConfig.ini built-in defaults loaded from ${it.absolutePath}" }
        }
        seedStream?.let {
            it.bufferedReader(Charsets.UTF_8).use { reader -> parser.parse(reader) }
            logger.info { "AppConfig.ini built-in defaults loaded from classpath" }
        }
        if (userConfigFile.exists()) {
            parser.loadMerging(userConfigFile)
            logger.info { "AppConfig.ini user config loaded from ${userConfigFile.absolutePath}" }
        }
        initialized = true
    }

    fun getOrDefault(section: String, key: String, default: String): String {
        if (!initialized) return default
        return parser.getOrDefault(section, key, default)
    }

    /** 更新内存中的键值（段不存在自动创建），需再调 [save] 持久化 */
    fun set(section: String, key: String, value: String) {
        parser.set(section, key, value)
    }

    /** 全量写回用户配置文件；失败记 warn 返回 false（不抛出） */
    fun save(): Boolean {
        val file = userConfigFile ?: return false
        return try {
            parser.save(file)
            true
        } catch (e: Exception) {
            logger.warn { "AppConfig.ini save failed: ${e.message}" }
            false
        }
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
