package com.easyadb.core.adb

import com.easyadb.core.log.AppLogger
import com.easyadb.core.util.ExecUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 从设备加载已安装的应用列表。
 * 对应 Python 中的 GetAppListThread（位于 utils/ADBTools.py）
 */
object AppListLoader {

    private val logger = AppLogger.getLogger(AppListLoader::class.java)

    /**
     * 应用信息数据类。
     */
    data class AppInfo(
        val packageName: String,
        val apkPath: String,
        val type: String // 第三方, 系统, 未知
    )

    /**
     * 从指定设备加载已安装的应用。
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

            // 排序：第三方优先，然后是系统，最后是未知
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
