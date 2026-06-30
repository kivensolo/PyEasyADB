package com.easyadb.platform

import java.io.IOException

actual object TerminalOpener {
    actual fun openInTerminal(command: String) {
        val os = System.getProperty("os.name").lowercase()
        try {
            val runtime = Runtime.getRuntime()
            when {
                os.contains("win") -> {
                    runtime.exec("cmd.exe /c start cmd.exe /K \"$command\"")
                }
                os.contains("mac") || os.contains("darwin") -> {
                    runtime.exec(arrayOf("open", "-a", "Terminal", command))
                }
                else -> {
                    runtime.exec(arrayOf("x-terminal-emulator", "-e", command))
                }
            }
        } catch (e: IOException) {
            System.err.println("Failed to open terminal: ${e.message}")
        }
    }
}
