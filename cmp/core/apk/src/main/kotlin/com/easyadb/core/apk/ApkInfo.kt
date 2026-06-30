package com.easyadb.core.apk

/**
 * 解析后的 APK 信息数据类。
 * 对应 Python 中 FileUtils.parse_apk() 返回的字典。
 */
data class ApkInfo(
    val packageName: String = "",
    val versionName: String = "",
    val versionCode: String = "",
    val minSdk: String = "",
    val targetSdk: String = "",
    val label: String = "",
    val permissions: List<String> = emptyList(),
    val signatureDigest: String = "",
    val filePath: String = ""
)
