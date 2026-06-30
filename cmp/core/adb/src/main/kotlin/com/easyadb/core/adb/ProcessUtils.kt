package com.easyadb.core.adb

import com.easyadb.core.log.AppLogger
import com.easyadb.core.util.ExecResult
import com.easyadb.core.util.ExecUtils

/**
 * 进程信息数据类。
 */
data class ProcessInfo(
    val user: String,
    val pid: Int,
    val name: String
)

/**
 * 解析并过滤 `adb shell ps` 的输出。
 * 对应 Python 中的 get_filter_processes()（位于 utils/ADBTools.py）
 */
object ProcessUtils {

    private val logger = AppLogger.getLogger(ProcessUtils::class.java)

    private val FILTER_NAMES = setOf("sh", "ping")
    private val FILTER_PREFIXES = listOf("[", "android.", "/system", "com.android", "sysyem_server", "libcpu", "/data/")

    /**
     * 解析并过滤 `adb shell ps` 的输出。
     */
    fun getFilteredProcesses(output: String): List<ProcessInfo> {
        val processes = mutableListOf<ProcessInfo>()

        for (line in output.lines().drop(1)) {
            if (line.isBlank()) continue

            val columns = line.split("\\s+".toRegex())
            if (columns.size < 9) continue

            val user = columns[0]
            if (!processUserNameCheck(user)) continue

            val pid = columns[1].toIntOrNull() ?: continue
            if (pid <= 1000) continue

            val name = columns.last()

            // 过滤特定名称
            if (name in FILTER_NAMES) continue

            // 按前缀过滤
            if (FILTER_PREFIXES.any { name.startsWith(it) }) continue

            processes.add(ProcessInfo(user = user, pid = pid, name = name))
        }

        return processes.sortedBy { it.name }
    }

    /**
     * 检查用户是否应包含在内。
     * 对应 Python 中的 process_user_name_check()
     */
    fun processUserNameCheck(user: String): Boolean {
        return user.startsWith("u0_") || user == "system" || user == "bluetooth"
    }
}
