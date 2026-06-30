package com.easyadb.core.log

import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class RollingFileHandler(
    private val basePath: String,
    private val maxBytes: Long = 4 * 1024 * 1024,
    private val backupCount: Int = 128
) {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private var currentWriter: FileWriter? = null
    private var currentSize: Long = 0
    private var currentDate: String = ""

    @Synchronized
    fun append(logLine: String) {
        val today = LocalDateTime.now().format(dateFormatter)
        if (today != currentDate) {
            close()
            currentDate = today
            currentWriter = null
            currentSize = 0
        }

        if (currentWriter == null) {
            val file = File(basePath, "log_$today.log")
            file.parentFile?.mkdirs()
            currentWriter = FileWriter(file, true)
            currentSize = file.length()
        }

        val lineBytes = logLine.toByteArray().size
        if (currentSize + lineBytes > maxBytes) {
            roll()
        }

        try {
            currentWriter?.write(logLine)
            currentWriter?.write(System.lineSeparator())
            currentWriter?.flush()
            currentSize += lineBytes + System.lineSeparator().toByteArray().size
        } catch (e: IOException) {
            System.err.println("Log write failed: ${e.message}")
        }
    }

    private fun roll() {
        close()
        val today = currentDate
        val baseFile = File(basePath, "log_$today.log")

        // Shift backups: .backupCount-1 → .backupCount, ... .1 → .2, .log → .1
        for (i in backupCount - 1 downTo 1) {
            val src = File(basePath, "log_${today}.log.$i")
            val dst = File(basePath, "log_${today}.log.${i + 1}")
            if (src.exists()) src.renameTo(dst)
        }
        if (baseFile.exists()) {
            baseFile.renameTo(File(basePath, "log_${today}.log.1"))
        }
        currentWriter = FileWriter(baseFile, true)
        currentSize = 0
    }

    fun close() {
        try {
            currentWriter?.close()
        } catch (_: IOException) {
        }
        currentWriter = null
    }
}
