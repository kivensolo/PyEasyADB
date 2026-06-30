package com.easyadb.core.adb

import com.easyadb.core.log.AppLogger
import com.easyadb.core.util.ExecResult
import com.easyadb.core.util.ExecUtils

/**
 * Process information data class.
 */
data class ProcessInfo(
    val user: String,
    val pid: Int,
    val name: String
)

/**
 * Parse filtered process list from `adb shell ps` output.
 * Maps to Python get_filter_processes() in utils/ADBTools.py
 */
object ProcessUtils {

    private val logger = AppLogger.getLogger(ProcessUtils::class.java)

    private val FILTER_NAMES = setOf("sh", "ping")
    private val FILTER_PREFIXES = listOf("[", "android.", "/system", "com.android", "sysyem_server", "libcpu", "/data/")

    /**
     * Parse and filter `adb shell ps` output.
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

            // Filter specific names
            if (name in FILTER_NAMES) continue

            // Filter by prefix
            if (FILTER_PREFIXES.any { name.startsWith(it) }) continue

            processes.add(ProcessInfo(user = user, pid = pid, name = name))
        }

        return processes.sortedBy { it.name }
    }

    /**
     * Check if the user should be included.
     * Maps to Python process_user_name_check()
     */
    fun processUserNameCheck(user: String): Boolean {
        return user.startsWith("u0_") || user == "system" || user == "bluetooth"
    }
}
