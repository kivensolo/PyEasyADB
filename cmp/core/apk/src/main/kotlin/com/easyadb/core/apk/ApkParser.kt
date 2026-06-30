package com.easyadb.core.apk

import com.easyadb.core.log.AppLogger
import com.easyadb.core.util.ExecUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Parse APK files using aapt and apksigner.
 * Maps to Python FileUtils.parse_apk().
 */
object ApkParser {

    private val logger = AppLogger.getLogger(ApkParser::class.java)

    /**
     * Parse APK file and return ApkInfo.
     */
    suspend fun parse(filePath: String): ApkInfo = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists()) {
            logger.error { "APK file not found: $filePath" }
            return@withContext ApkInfo(filePath = filePath)
        }

        val packageName = extractPackageName(filePath)
        val label = extractLabel(filePath)
        val versionName = extractVersionName(filePath)
        val versionCode = extractVersionCode(filePath)
        val minSdk = extractMinSdk(filePath)
        val targetSdk = extractTargetSdk(filePath)
        val permissions = extractPermissions(filePath)
        val signatureDigest = extractSignature(filePath)

        ApkInfo(
            packageName = packageName,
            versionName = versionName,
            versionCode = versionCode,
            minSdk = minSdk,
            targetSdk = targetSdk,
            label = label,
            permissions = permissions,
            signatureDigest = signatureDigest,
            filePath = filePath
        )
    }

    private suspend fun execAapt(arguments: String): String {
        val cmd = "aapt dump $arguments"
        val result = ExecUtils.execCmd(cmd, timeoutMs = 10000)
        return result.output
    }

    private suspend fun extractPackageName(filePath: String): String {
        try {
            val output = execAapt("badging \"$filePath\"")
            val prefix = "package: name='"
            val start = output.indexOf(prefix)
            if (start >= 0) {
                val end = output.indexOf("'", start + prefix.length)
                return output.substring(start + prefix.length, end)
            }
        } catch (e: Exception) {
            logger.debug { "extractPackageName failed: ${e.message}" }
        }
        return ""
    }

    private suspend fun extractLabel(filePath: String): String {
        try {
            val output = execAapt("badging \"$filePath\"")
            val prefix = "application-label:'"
            val start = output.indexOf(prefix)
            if (start >= 0) {
                val end = output.indexOf("'", start + prefix.length)
                return output.substring(start + prefix.length, end)
            }
        } catch (e: Exception) {
            logger.debug { "extractLabel failed: ${e.message}" }
        }
        return ""
    }

    private suspend fun extractVersionName(filePath: String): String {
        try {
            val output = execAapt("badging \"$filePath\"")
            val marker = "versionName='"
            val start = output.indexOf(marker)
            if (start >= 0) {
                val end = output.indexOf("'", start + marker.length)
                return output.substring(start + marker.length, end)
            }
        } catch (e: Exception) {
            logger.debug { "extractVersionName failed: ${e.message}" }
        }
        return ""
    }

    private suspend fun extractVersionCode(filePath: String): String {
        try {
            val output = execAapt("badging \"$filePath\"")
            val marker = "versionCode='"
            val start = output.indexOf(marker)
            if (start >= 0) {
                val end = output.indexOf("'", start + marker.length)
                return output.substring(start + marker.length, end)
            }
        } catch (e: Exception) {
            logger.debug { "extractVersionCode failed: ${e.message}" }
        }
        return ""
    }

    private suspend fun extractMinSdk(filePath: String): String {
        try {
            val output = execAapt("badging \"$filePath\"")
            val marker = "sdkVersion:'"
            val start = output.indexOf(marker)
            if (start >= 0) {
                val end = output.indexOf("'", start + marker.length)
                return output.substring(start + marker.length, end)
            }
        } catch (e: Exception) {
            logger.debug { "extractMinSdk failed: ${e.message}" }
        }
        return ""
    }

    private suspend fun extractTargetSdk(filePath: String): String {
        try {
            val output = execAapt("badging \"$filePath\"")
            val marker = "targetSdkVersion:'"
            val start = output.indexOf(marker)
            if (start >= 0) {
                val end = output.indexOf("'", start + marker.length)
                return output.substring(start + marker.length, end)
            }
        } catch (e: Exception) {
            logger.debug { "extractTargetSdk failed: ${e.message}" }
        }
        return ""
    }

    private suspend fun extractPermissions(filePath: String): List<String> {
        try {
            val output = execAapt("permissions \"$filePath\"")
            val permissions = mutableListOf<String>()
            for (line in output.lines()) {
                val trimmed = line.trim()
                if (trimmed.startsWith("permission:")) {
                    val perm = trimmed.removePrefix("permission:").trim()
                    if (perm.isNotBlank()) permissions.add(perm)
                }
            }
            return permissions
        } catch (e: Exception) {
            logger.debug { "extractPermissions failed: ${e.message}" }
        }
        return emptyList()
    }

    private suspend fun extractSignature(filePath: String): String {
        try {
            val cmd = "apksigner verify --print-certs \"$filePath\""
            val result = ExecUtils.execCmd(cmd, timeoutMs = 10000)
            val lines = result.output.lines()
            for (line in lines) {
                if (line.contains("SHA-256 digest:")) {
                    return line.substringAfter("SHA-256 digest:").trim()
                }
            }
        } catch (e: Exception) {
            logger.debug { "extractSignature failed: ${e.message}" }
        }
        return ""
    }
}
