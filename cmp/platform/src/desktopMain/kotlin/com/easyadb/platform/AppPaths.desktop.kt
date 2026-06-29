package com.easyadb.platform

import java.io.File
import java.lang.System.getenv
import java.nio.file.Paths

actual object AppPaths {
    actual val appDataDir: File by lazy { resolveAppDataDir() }

    private fun resolveAppDataDir(): File {
        val os = (getenv("OS") ?: System.getProperty("os.name") ?: "").lowercase()
        val isWindows = os.contains("win")
        val isMac = os.contains("mac") || os.contains("darwin")

        val base: String = when {
            isWindows ->
                getenv("LOCALAPPDATA")
                    ?: Paths.get(System.getProperty("user.home"), "AppData", "Local").toString()
            isMac ->
                Paths.get(System.getProperty("user.home"), "Library", "Application Support").toString()
            else -> {
                val xdg = getenv("XDG_DATA_HOME")
                if (!xdg.isNullOrBlank()) xdg
                else Paths.get(System.getProperty("user.home"), ".local", "share").toString()
            }
        }
        return File(base, "EasyADB").apply { mkdirs() }
    }
}
