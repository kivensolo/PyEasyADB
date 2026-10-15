package com.easyadb.core.device

import com.easyadb.core.log.AppLogger
import com.easyadb.core.util.ExecUtils

/**
 * Copyright (c) 2026, 北京视达科科技有限责任公司 All rights reserved.
 * author：ZekeWang
 * date：2026/10/9
 * description：设备属性查询器——为设备卡片提供 Android 版本号等附加信息。
 *              设备上线后异步执行 getprop 查询，结果由宿主缓存（内存 Map），
 *              查询失败返回 null（UI 侧降级为只显示 IP）。
 */
class DevicePropFetcher {

    private val logger = AppLogger.getLogger(DevicePropFetcher::class.java)

    /**
     * 查询设备 Android 版本号 + API 级别（ro.build.version.release / ro.build.version.sdk）。
     *
     * 一次 `getprop`（全量属性）后本地解析两键，而非两次单键查询或 `&&` 拼接——
     * ExecUtils 在 Windows 走 `cmd.exe /c`，拼接命令的引号/元字符会被 cmd 二次解释，不可靠。
     *
     * @param deviceIp 设备地址（ip:port）
     * @return 如 "11 (API 30)"；sdk 缺失时仅返回版本号（如 "11"）；查询失败返回 null
     */
    suspend fun fetchVersion(deviceIp: String): String? {
        return try {
            val result = ExecUtils.execCmd("adb -s $deviceIp shell getprop", timeoutMs = 8000)
            if (!result.success) {
                logger.warn { "[DeviceProp] fetchVersion failed for $deviceIp: ${result.output.take(100)}" }
                return null
            }
            val props = parseProps(result.output)
            val release = props["ro.build.version.release"]?.trim().orEmpty()
            if (release.isEmpty()) {
                logger.warn { "[DeviceProp] release prop missing for $deviceIp" }
                return null
            }
            val sdk = props["ro.build.version.sdk"]?.trim().orEmpty()
            if (sdk.isEmpty()) release else "$release (API $sdk)"
        } catch (e: Exception) {
            logger.error { "[DeviceProp] fetchVersion error for $deviceIp: ${e.message}" }
            null
        }
    }

    /** 解析 getprop 全量输出：每行 `[key]: [value]`（adb 统一格式）。 */
    private fun parseProps(output: String): Map<String, String> {
        val props = mutableMapOf<String, String>()
        val regex = Regex("""^\[(.+?)]:\s*\[(.*)]\s*$""")
        for (line in output.lines()) {
            regex.find(line.trim())?.let { m ->
                props[m.groupValues[1]] = m.groupValues[2]
            }
        }
        return props
    }
}
