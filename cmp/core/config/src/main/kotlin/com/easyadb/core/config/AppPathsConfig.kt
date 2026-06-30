package com.easyadb.core.config

import java.io.File

/**
 * Application path constants.
 * Maps to src/settings.py in Python.
 */
object AppPathsConfig {

    private const val APP_VERSION = "1.0.5"

    /** LocalAppData/EasyADB root */
    val localAppDataPath: String by lazy {
        val appData = System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home")
        File(appData, "EasyADB").also { it.mkdirs() }.absolutePath
    }

    /** Platform-tools directory */
    val platformToolsPath: String by lazy {
        File(localAppDataPath, "platform-tools").absolutePath
    }

    /** Tools directory */
    val toolsPath: String by lazy {
        File(localAppDataPath, "tools").absolutePath
    }

    /** Temporary data path */
    val tempPath: String by lazy {
        File(localAppDataPath, "tmp").also { it.mkdirs() }.absolutePath
    }

    /** Application data path */
    val dataPath: String by lazy {
        File(localAppDataPath, "data").also { it.mkdirs() }.absolutePath
    }

    /** SQLite database file */
    val dbFile: String by lazy {
        File(dataPath, "easyADB.db").absolutePath
    }

    /** Application screen ratio */
    const val appScreenRatio: Double = 0.75

    /** Base path (working directory) */
    val basePath: String by lazy {
        System.getProperty("user.dir")
    }

    /** Logs path */
    val logsPath: String by lazy {
        File(basePath, "logs").absolutePath
    }

    /** Scrcpy path */
    val scrcpyPath: String by lazy {
        File(basePath, "tool/scrcpy-win64").absolutePath
    }

    /** Config directory */
    val configDir: String by lazy {
        File(basePath, "res/config").absolutePath
    }

    /** ADB commands config file */
    val cmdConfigFile: String by lazy {
        File(configDir, "cmdConfig.xml").absolutePath
    }

    /** Function templates config file */
    val functionTemplatesFile: String by lazy {
        File(configDir, "function_templates.xml").absolutePath
    }

    /** Menu UI config file */
    val menusUiFile: String by lazy {
        File(configDir, "menus_ui.xml").absolutePath
    }

    /** AppConfig.ini file */
    val appConfigIniFile: String by lazy {
        File(configDir, "AppConfig.ini").absolutePath
    }

    /** Debug print flag */
    const val debugPrint: Boolean = true

    /** QSetting cache keys */
    const val keyAppActivityClasspath: String = "line_edit_classpath"
    const val keyAppAction: String = "line_edit_action"
    const val keyAppExtParams: String = "edit_ext_params"

    /** Live logcat config */
    const val liveLogDefaultFilterPid: Boolean = false
    const val liveLogCountLimits: Int = 3000

    /** Center window config */
    const val centerWindowEveryRowSize: Int = 5
}
