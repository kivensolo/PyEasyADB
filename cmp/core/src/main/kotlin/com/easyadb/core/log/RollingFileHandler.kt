package com.easyadb.core.log

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.io.Writer
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 按天命名、按大小滚动的文件日志 handler（对齐 Python logging RotatingFileHandler：
 * 单文件 maxBytes=4MB、保留 backupCount=128 份）。
 *
 * ## 线程与共享约定
 * - [append] 全程 @Synchronized，单实例线程安全；
 * - **同一日志目录必须共享一个实例**（见 [AppLogger] 的共享表）——多个实例各持
 *   FileWriter 写同一文件时，各自的字节数互不可见，滚动判断失真，且 Windows 下
 *   roll 的 renameTo 会因其他句柄占用而失败。
 *
 * ## 编码
 * 固定 UTF-8（FileWriter 依赖平台默认编码，中文 Windows 上是 GBK）。
 */
class RollingFileHandler(
    private val basePath: String,
    private val maxBytes: Long = 4 * 1024 * 1024,
    private val backupCount: Int = 128
) {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private var currentWriter: Writer? = null
    private var currentSize: Long = 0
    private var currentDate: String = ""

    @Synchronized
    fun append(logLine: String) {
        val today = LocalDateTime.now().format(dateFormatter)
        if (today != currentDate) {
            close()
            currentDate = today
            currentSize = 0
        }

        if (currentWriter == null) {
            val file = File(basePath, "log_$today.log")
            file.parentFile?.mkdirs()
            currentWriter = OutputStreamWriter(FileOutputStream(file, true), Charsets.UTF_8)
            currentSize = file.length()
        }

        val line = logLine + System.lineSeparator()
        val lineBytes = line.toByteArray(Charsets.UTF_8).size.toLong()
        if (currentSize + lineBytes > maxBytes) {
            roll()
        }

        try {
            val writer = currentWriter
            if (writer != null) {
                writer.write(line)
                writer.flush()
                currentSize += lineBytes
            }
        } catch (e: IOException) {
            System.err.println("Log write failed: ${e.message}")
        }
    }

    private fun roll() {
        close()
        val today = currentDate

        // 逐级后移备份（.log → .1 → .2 ...）；rename 前先删目标——Windows 的 renameTo
        // 在目标已存在时直接失败，会导致滚动链从此断掉；最老溢出份直接删除
        File(basePath, "log_${today}.log.$backupCount").delete()
        for (i in backupCount - 1 downTo 1) {
            val src = File(basePath, "log_${today}.log.$i")
            val dst = File(basePath, "log_${today}.log.${i + 1}")
            if (src.exists()) {
                dst.delete()
                src.renameTo(dst)
            }
        }
        val baseFile = File(basePath, "log_$today.log")
        if (baseFile.exists()) {
            File(basePath, "log_${today}.log.1").delete()
            baseFile.renameTo(File(basePath, "log_${today}.log.1"))
        }
        currentWriter = OutputStreamWriter(FileOutputStream(baseFile, true), Charsets.UTF_8)
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
