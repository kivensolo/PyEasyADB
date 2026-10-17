package com.easyadb.core.database

data class DeviceRecord(
    val ip: String,
    val port: String = "0",
    val alias: String = "",
    val deviceInfo: String = "",
    val active: Int = 0,
    /** 连接次数（使用率）：每次连接成功 +1，设备列表按此降序排列 */
    val connectCount: Int = 0
)
