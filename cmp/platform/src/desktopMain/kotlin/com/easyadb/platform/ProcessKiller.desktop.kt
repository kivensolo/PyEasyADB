package com.easyadb.platform

import java.io.IOException

actual object ProcessKiller {
    actual fun kill(pid: Long) {
        val os = System.getProperty("os.name").lowercase()
        try {
            val runtime = Runtime.getRuntime()
            if (os.contains("win")) {
                runtime.exec("taskkill /F /PID $pid")
            } else {
                runtime.exec("kill -9 $pid")
            }
        } catch (e: IOException) {
            System.err.println("Failed to kill process $pid: ${e.message}")
        }
    }

    actual fun kill(process: Process) {
        process.destroyForcibly()
    }
}
