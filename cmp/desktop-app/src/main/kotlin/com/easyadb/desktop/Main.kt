package com.easyadb.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.easyadb.core.config.AppConfigManager
import com.easyadb.core.config.AppPathsConfig
import com.easyadb.core.database.DbManager
import com.easyadb.core.device.DevicesWatcher
import com.easyadb.core.log.AppLogger
import com.easyadb.core.log.LogConfig
import com.easyadb.core.log.LogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

fun main() {
    // ── Step 1: Logging ──
    val logsDir = File(AppPathsConfig.logsPath).apply { mkdirs() }
    System.setProperty(
        "org.slf4j.simpleLogger.logFile",
        File(logsDir, "easyadb.log").absolutePath
    )
    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "info")
    System.setProperty("easyadb.log.dir", logsDir.absolutePath)

    val appLogger = AppLogger(LogConfig(name = "EasyADB", logDir = logsDir.absolutePath))
    appLogger.info { "EasyADB CMP v2.0.0 - P1 Core Layer active" }
    appLogger.info { "AppData dir: ${AppPathsConfig.localAppDataPath}" }

    // ── Step 2: Config ──
    val configFile = File(AppPathsConfig.appConfigIniFile)
    if (configFile.exists()) {
        AppConfigManager.initialize(configFile)
        appLogger.info { "AppConfig.ini loaded" }
    } else {
        appLogger.warn { "AppConfig.ini not found, skipping" }
    }

    // ── Step 3: Database ──
    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    scope.launch {
        val result = DbManager.initialize(AppPathsConfig.dbFile)
        if (result.success) {
            appLogger.info { "Database initialized at ${AppPathsConfig.dbFile}" }
        } else {
            appLogger.error { "Database init failed: ${result.error}" }
        }
    }

    // ── Step 4: Device Watcher ──
    val watcher = DevicesWatcher()
    watcher.start(intervalSeconds = 2, scope = scope)
    appLogger.info { "DevicesWatcher started" }

    // ── UI ──
    application {
        Window(
            onCloseRequest = {
                watcher.stop()
                DbManager.close()
                appLogger.close()
                exitApplication()
            },
            title = "EasyADB"
        ) {
            MaterialTheme {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("EasyADB CMP v2.0.0 - P1 Core Layer active")
                }
            }
        }
    }
}
