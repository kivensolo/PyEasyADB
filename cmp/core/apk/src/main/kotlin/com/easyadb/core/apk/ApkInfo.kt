package com.easyadb.core.apk

/**
 * Parsed APK information data class.
 * Maps to Python FileUtils.parse_apk() return dict.
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
