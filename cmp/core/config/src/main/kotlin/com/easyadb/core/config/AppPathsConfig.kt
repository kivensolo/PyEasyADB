package com.easyadb.core.config

import java.io.File

/**
 * 应用路径常量。
 * 对应 Python 中的 src/settings.py。
 */
object AppPathsConfig {

    private const val APP_VERSION = "1.0.5"

    /** EasyADB 的 LocalAppData 根目录 */
    val localAppDataPath: String by lazy {
        val appData = System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home")
        File(appData, "EasyADB").also { it.mkdirs() }.absolutePath
    }

    /** Platform-tools 目录 */
    val platformToolsPath: String by lazy {
        File(localAppDataPath, "platform-tools").absolutePath
    }

    /** Tools 目录 */
    val toolsPath: String by lazy {
        File(localAppDataPath, "tools").absolutePath
    }

    /** 临时数据路径 */
    val tempPath: String by lazy {
        File(localAppDataPath, "tmp").also { it.mkdirs() }.absolutePath
    }

    /** 应用数据路径 */
    val dataPath: String by lazy {
        File(localAppDataPath, "data").also { it.mkdirs() }.absolutePath
    }

    /** SQLite 数据库文件 */
    val dbFile: String by lazy {
        File(dataPath, "easyADB.db").absolutePath
    }

    /** 应用屏幕比例 */
    const val appScreenRatio: Double = 0.75

    /** 基础路径（工作目录） */
    val basePath: String by lazy {
        System.getProperty("user.dir")
    }

    /** 日志路径 */
    val logsPath: String by lazy {
        File(basePath, "logs").absolutePath
    }

    /** Scrcpy 路径 */
    val scrcpyPath: String by lazy {
        File(basePath, "tool/scrcpy-win64").absolutePath
    }

    /** 配置目录 */
    val configDir: String by lazy {
        File(basePath, "res/config").absolutePath
    }

    /** ADB 命令配置文件 */
    val cmdConfigFile: String by lazy {
        File(configDir, "cmdConfig.xml").absolutePath
    }

    /** 功能模板配置文件 */
    val functionTemplatesFile: String by lazy {
        File(configDir, "function_templates.xml").absolutePath
    }

    /** 菜单 UI 配置文件 */
    val menusUiFile: String by lazy {
        File(configDir, "menus_ui.xml").absolutePath
    }

    /** AppConfig.ini 文件 */
    val appConfigIniFile: String by lazy {
        File(configDir, "AppConfig.ini").absolutePath
    }

    /** 调试打印标志 */
    const val debugPrint: Boolean = true

    /** QSetting 缓存键 */
    const val keyAppActivityClasspath: String = "line_edit_classpath"
    const val keyAppAction: String = "line_edit_action"
    const val keyAppExtParams: String = "edit_ext_params"

    /** 实时 logcat 配置 */
    const val liveLogDefaultFilterPid: Boolean = false
    const val liveLogCountLimits: Int = 3000

    /** 中央窗口配置 */
    const val centerWindowEveryRowSize: Int = 5
}
