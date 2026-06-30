package com.easyadb.core.adb

import com.easyadb.core.log.AppLogger
import com.easyadb.core.util.ExecUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Load installed app list from a device.
 * Maps to Python GetAppListThread in utils/ADBTools.py
 */
object AppListLoader {

    private val logger = AppLogger.getLogger(AppListLoader::class.java)

    /**
     * App info data class.
     */
    data class AppInfo(
        val packageName: String,
        val apkPath: String,
        val type: String // 第三方, 系统, 未知
    )

    /**
     * Load installed apps from the given device.
     */
    suspend fun loadAppList(deviceIp: String): List<AppInfo> = withContext(Dispatchers.IO) {
        logger.info { "Loading app list from $deviceIp..." }
        try {
            val cmd = "adb -s $deviceIp shell pm list packages -f"
            val result = ExecUtils.execCmd(cmd, timeoutMs = 30000)

            if (!result.success) {
                logger.error { "Failed to load app list: ${result.output}" }
                return@withContext emptyList()
            }

            val apps = mutableListOf<AppInfo>()

            for (line in result.output.lines()) {
                val trimmed = line.trim()
                if (!trimmed.startsWith("package:") || "=" !in trimmed) continue

                val content = trimmed.removePrefix("package:")
                val equalsIndex = content.lastIndexOf('=')
                if (equalsIndex < 0) continue

                val apkPath = content.substring(0, equalsIndex)
                val packageName = content.substring(equalsIndex + 1)

                val type = when {
                    apkPath.startsWith("/data/app/") -> "第三方"
                    apkPath.startsWith("/system/") || apkPath.startsWith("/vendor/") ||
                        apkPath.startsWith("/product/") || apkPath.startsWith("/system_ext/") -> "系统"
                    else -> "未知"
                }

                apps.add(AppInfo(packageName = packageName, apkPath = apkPath, type = type))
            }

            // Sort: 3rd-party first, then system, then unknown
            val typePriority = mapOf("第三方" to 0, "系统" to 1, "未知" to 2)
            apps.sortWith(compareBy({ typePriority[it.type] ?: 2 }, { it.packageName.lowercase() }))

            logger.info { "App list loaded: ${apps.size} apps" }
            apps
        } catch (e: Exception) {
            logger.error { "Load app list failed: ${e.message}" }
            emptyList()
        }
    }
}
