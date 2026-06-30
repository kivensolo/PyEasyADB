package com.easyadb.core.database

data class DeviceRecord(
    val ip: String,
    val port: String = "0",
    val alias: String = "",
    val deviceInfo: String = "",
    val active: Int = 0
)
