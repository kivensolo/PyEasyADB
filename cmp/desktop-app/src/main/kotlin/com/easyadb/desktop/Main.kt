package com.easyadb.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.easyadb.platform.AppPaths
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File

fun main() {
    val logsDir = File(AppPaths.appDataDir, "logs").apply { mkdirs() }
    System.setProperty(
        "org.slf4j.simpleLogger.logFile",
        File(logsDir, "easyadb.log").absolutePath
    )
    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "info")

    val logger = KotlinLogging.logger {}
    logger.info { "EasyADB CMP v2.0.0 - P0 scaffolding starting up" }
    logger.info { "AppData dir: ${AppPaths.appDataDir.absolutePath}" }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "EasyADB"
        ) {
            MaterialTheme {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("EasyADB CMP v2.0.0 - P0 scaffolding")
                }
            }
        }
    }
}
