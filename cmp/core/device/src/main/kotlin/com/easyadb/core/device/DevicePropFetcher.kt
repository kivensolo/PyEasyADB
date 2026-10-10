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
     * 查询设备 Android 版本号（ro.build.version.release）。
     *
     * @param deviceIp 设备地址（ip:port）
     * @return 版本号字符串（如 "11"），查询失败或输出为空返回 null
     */
    suspend fun fetchVersion(deviceIp: String): String? {
        return try {
            val result = ExecUtils.execCmd(
                "adb -s $deviceIp shell getprop ro.build.version.release",
                timeoutMs = 5000
            )
            val version = result.output.trim()
            if (result.success && version.isNotEmpty()) {
                version
            } else {
                logger.warn { "[DeviceProp] fetchVersion failed for $deviceIp: ${result.output.take(100)}" }
                null
            }
        } catch (e: Exception) {
            logger.error { "[DeviceProp] fetchVersion error for $deviceIp: ${e.message}" }
            null
        }
    }
}
