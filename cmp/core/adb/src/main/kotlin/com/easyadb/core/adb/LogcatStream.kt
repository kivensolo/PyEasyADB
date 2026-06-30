package com.easyadb.core.adb

import com.easyadb.core.log.LogConstants
import com.easyadb.core.log.LogLevel
import com.easyadb.core.log.LogLevelMapper
import com.easyadb.core.log.LogUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 带过滤功能的持续 logcat 流。
 * 对应 Python 中的 LiveLogAdbThread + LogCatFilter（位于 utils/ADBTools.py）
 */

data class LogcatEntry(
    val raw: String,
    val pid: String,
    val level: LogLevel,
    val formatted: String
)

class LogcatFilter {

    var filteredLevel: LogLevel = LogLevel.INFO
    var selectedPid: String = ""
    var onlyShowSelectedApp: Boolean = LogConstants.LIVE_LOG_DEFAULT_FILTER_PID
    var filterContent: String = ""

    private val logCache = mutableListOf<String>()

    private val LOGCAT_PATTERN = Regex(""".+\s([VIDWE])\/.+\(\s*(\d+)\)\:.+$""")

    fun changeFilterOptions(enableFilter: Boolean) {
        onlyShowSelectedApp = enableFilter
    }

    fun changeFilterLevelByName(levelName: String) {
        filteredLevel = if (levelName.length == 1) {
            LogLevel.fromShortName(levelName)
        } else {
            LogLevel.fromName(levelName)
        }
    }

    fun onSelectedPidChanged(pid: String = "") {
        selectedPid = pid
    }

    fun onFilterContentChanged(content: String = "") {
        filterContent = content
    }

    fun filter(logMsg: String): Triple<Boolean, String, LogLevel> {
        val match = LOGCAT_PATTERN.find(logMsg)
        val (pid, levelName) = if (match != null) {
            match.groupValues[2] to match.groupValues[1]
        } else {
            "-1" to "I"
        }

        val level = LogLevel.fromShortName(levelName)

        // 级别 1：日志级别过滤
        if (level.value < filteredLevel.value) return Triple(true, pid, level)

        // 级别 2：进程过滤
        if (onlyShowSelectedApp && pid != selectedPid) return Triple(true, pid, level)

        // 级别 3：关键字过滤
        if (filterContent.isNotEmpty() && filterContent !in logMsg) return Triple(true, pid, level)

        return Triple(false, pid, level)
    }

    fun record(logMsg: String) {
        logCache.add(logMsg)
    }

    fun clear() {
        logCache.clear()
    }

    fun getCachedLogs(): List<String> = logCache.toList()

    fun getFilteredHistoryLogs(): List<String> {
        val result = mutableListOf<String>()
        for (log in logCache) {
            val (filtered, _, level) = filter(log)
            if (!filtered) {
                val highlighted = LogUtils.highlightLinkAddr(log)
                result.add(LogUtils.changeLogColor(appendPrefix = false, level = level, log = highlighted))
            }
        }
        return result
    }
}

/**
 * 使用协程 Flow 的持续 logcat 流。
 * 对应 Python 中的 LiveLogAdbThread（位于 utils/ADBTools.py）
 */
class LogcatStream {

    private var process: java.lang.Process? = null
    private val filter = LogcatFilter()

    fun getFilter(): LogcatFilter = filter

    fun logcatFlow(ip: String, filterArgs: String = "-v time"): Flow<LogcatEntry> = callbackFlow {
        val cmd = "adb -s $ip logcat $filterArgs"
        val processBuilder = ProcessBuilder().apply {
            val os = System.getProperty("os.name").lowercase()
            if (os.contains("win")) {
                command("cmd.exe", "/c", cmd)
            } else {
                command("sh", "-c", cmd)
            }
        }

        val proc = processBuilder.start()
        process = proc
        val reader = BufferedReader(InputStreamReader(proc.inputStream, "utf-8"))

        var line: String?
        while (reader.readLine().also { line = it } != null) {
            if (!isActive) {
                proc.destroyForcibly()
                break
            }
            val msg = line!!
            if (msg.isEmpty()) continue

            // 记录原始数据
            filter.record(msg)

            // 应用过滤
            val (isFiltered, pid, level) = filter.filter(msg)
            if (isFiltered) continue

            // 格式化用于 UI 显示
            val highlighted = LogUtils.highlightLinkAddr(msg)
            val uiLog = LogUtils.changeLogColor(appendPrefix = false, level = level, log = highlighted)

            trySend(LogcatEntry(raw = msg, pid = pid, level = level, formatted = uiLog))
        }

        // 读取 stderr
        val errReader = BufferedReader(InputStreamReader(proc.errorStream, "utf-8"))
        if (errReader.ready()) {
            val err = errReader.readText()
            if (err.isNotBlank()) {
                trySend(LogcatEntry(raw = err, pid = "-1", level = LogLevel.ERROR, formatted = err))
            }
        }
    }

    fun stop() {
        process?.destroyForcibly()
        process = null
    }

    fun clearFilter() {
        filter.clear()
    }
}
