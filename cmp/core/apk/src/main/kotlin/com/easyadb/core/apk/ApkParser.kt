package com.easyadb.core.apk

import com.easyadb.core.config.AppPathsConfig
import com.easyadb.core.log.AppLogger
import com.easyadb.core.util.ExecUtils
import com.easyadb.core.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipFile
import javax.imageio.ImageIO

/**
 * APK 解析结果。
 * 对应 Python parse_apk 的 success/reason 快速失败语义：失败时携带用户可读原因。
 */
sealed class ApkParseResult {

    /** 解析成功，携带完整 [ApkInfo] */
    data class Success(val info: ApkInfo) : ApkParseResult()

    /** 解析失败，reason 用于对话框状态栏红字展示 */
    data class Failure(val reason: String) : ApkParseResult()
}

/**
 * 使用 aapt 和 apksigner 解析 APK 文件。
 * 对应 Python 中的 FileUtils.parse_apk()（utils/Utils.py）：
 * 1. `aapt dump badging` — 包名 / 版本 / 名称 / Min.SDK / 权限 / 图标路径 / 启动入口（一次调用解析全部字段）
 * 2. `aapt dump xmltree AndroidManifest.xml` — 应用特征（系统应用 / Launcher 应用 / 桌面图标）
 * 3. `apksigner verify --print-certs -v` — 证书 MD5 + 签名方案版本
 * 另附纯文件信息（文件名 / 大小 / MD5 / 修改时间），并从 APK 中抽取图标到临时目录。
 */
object ApkParser {

    private val logger = AppLogger.getLogger(ApkParser::class.java)

    /** 文件时间展示格式，对齐原版 "%Y-%m-%d %H:%M:%S" */
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    // ── aapt / apksigner 输出提取正则（对齐原版 re.search 写法） ──
    private val packageNameRegex = Regex("name='(.*?)'")
    private val versionCodeRegex = Regex("versionCode='(.*?)'")
    private val versionNameRegex = Regex("versionName='(.*?)'")
    private val sdkVersionRegex = Regex("sdkVersion:'(.*?)'")
    private val appLabelRegex = Regex("application: label='(.*?)'")
    private val usesPermissionRegex = Regex("uses-permission: name='(.*?)'")
    private val iconPathRegex = Regex("application-icon-(\\d+):'(.*?)'")
    private val launchableNameRegex = Regex("name='([^']*)'")
    private val attrValueRegex = Regex("=\"([^\"]+)\"")
    private val signerMd5Regex = Regex("Signer #1 certificate MD5 digest: (.*)")
    private val signSchemeRegex = Regex("Verified using v(\\d+) scheme.*: (\\w+)")

    /** 工具命令执行超时（毫秒）。原版 subprocess.run 无超时，这里给足余量防止挂死 */
    private const val TOOL_TIMEOUT_MS = 30_000L

    /** 环境错误提示，对齐原版文案 */
    private const val MSG_ENV_ERROR = "解析失败！请检查AAPT环境配置是否正确！"

    /**
     * 解析 APK 文件并返回结果。
     */
    suspend fun parse(filePath: String): ApkParseResult = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists()) {
            logger.error { "APK file not found: $filePath" }
            return@withContext ApkParseResult.Failure("文件不存在：$filePath")
        }

        // ── 文件信息（读取失败快速失败，对齐原版） ──
        val fileName = file.name
        val fileMd5 = try {
            FileUtils.calculateMd5(file).uppercase()
        } catch (e: Exception) {
            logger.error { "文件信息读取失败 $fileName: ${e.message}" }
            return@withContext ApkParseResult.Failure("文件信息读取失败！请检查文件完整性！")
        }
        val lastModified =
            dateFormat.format(Instant.ofEpochMilli(file.lastModified()).atZone(ZoneId.systemDefault()))

        // ── 1. aapt dump badging ──
        val badgingOutput = execTool("aapt dump badging \"$filePath\"")
            ?: return@withContext ApkParseResult.Failure(MSG_ENV_ERROR)
        if (!badgingOutput.contains("package:")) {
            // 输出中无 package 行：APK 本身损坏或 aapt 报错（stderr 已拼在 output 尾部）
            return@withContext ApkParseResult.Failure(badgingOutput.take(300).ifBlank { "APK 解析失败" })
        }
        val badging = parseBadging(badgingOutput)

        // ── 2. aapt dump xmltree（应用特征） ──
        val manifestXml = execTool("aapt dump xmltree \"$filePath\" AndroidManifest.xml")
            ?: return@withContext ApkParseResult.Failure(MSG_ENV_ERROR)
        val flags = parseManifestFlags(manifestXml)

        // ── 3. apksigner verify --print-certs -v ──
        val certsOutput = execTool("apksigner verify --print-certs -v \"$filePath\"")
            ?: return@withContext ApkParseResult.Failure(MSG_ENV_ERROR)
        val signature = parseSignature(certsOutput)

        // ── 图标抽取（对齐原版 extract_icon：解出到 tmp/icon） ──
        val (iconFilePath, iconSizeInfo) = extractIconToTemp(file, badging.iconEntryPath)

        ApkParseResult.Success(
            ApkInfo(
                packageName = badging.packageName,
                label = badging.appLabel,
                versionName = badging.versionName,
                versionCode = badging.versionCode,
                minSdk = badging.minSdk,
                launchableActivity = badging.launchableActivity,
                signatureMd5 = signature.first,
                signatureVersions = signature.second,
                permissions = badging.permissions,
                isLauncherApp = flags.isLauncherApp,
                isShowLaunchIcon = flags.isShowLaunchIcon,
                isSystemApp = flags.isSystemApp,
                iconFilePath = iconFilePath,
                iconSizeInfo = iconSizeInfo,
                filePath = filePath,
                fileName = fileName,
                fileBytes = file.length(),
                fileMd5 = fileMd5,
                lastModified = lastModified
            )
        )
    }

    /** badging 输出解析结果 */
    private data class BadgingData(
        val packageName: String,
        val versionCode: String,
        val versionName: String,
        val minSdk: String,
        val appLabel: String,
        val launchableActivity: String,
        val iconEntryPath: String,
        val permissions: List<String>
    )

    /**
     * 从 `aapt dump badging` 的输出中解析全部字段。
     * 对齐原版：application-icon- 循环覆盖取最后一行（最高 dpi）。
     */
    private fun parseBadging(output: String): BadgingData {
        var packageName = ""
        var versionCode = ""
        var versionName = ""
        var minSdk = ""
        var appLabel = ""
        var launchableActivity = "N/A"
        var iconEntryPath = ""
        val permissions = mutableListOf<String>()

        for (line in output.lines()) {
            when {
                line.startsWith("package:") -> {
                    packageName = packageNameRegex.find(line)?.groupValues?.get(1) ?: ""
                    versionCode = versionCodeRegex.find(line)?.groupValues?.get(1) ?: ""
                    versionName = versionNameRegex.find(line)?.groupValues?.get(1) ?: ""
                }
                line.startsWith("sdkVersion:") ->
                    minSdk = sdkVersionRegex.find(line)?.groupValues?.get(1) ?: ""
                line.startsWith("application: label=") ->
                    appLabel = appLabelRegex.find(line)?.groupValues?.get(1) ?: ""
                line.startsWith("uses-permission:") ->
                    usesPermissionRegex.find(line)?.groupValues?.get(1)?.let(permissions::add)
                line.startsWith("application-icon-") ->
                    iconEntryPath = iconPathRegex.find(line)?.groupValues?.get(2) ?: ""
                line.startsWith("launchable-activity:") ->
                    launchableActivity = launchableNameRegex.find(line)?.groupValues?.get(1) ?: "N/A"
            }
        }
        return BadgingData(
            packageName = packageName,
            versionCode = versionCode,
            versionName = versionName,
            minSdk = minSdk,
            appLabel = appLabel,
            launchableActivity = launchableActivity,
            iconEntryPath = iconEntryPath,
            permissions = permissions
        )
    }

    /** 应用特征标志 */
    private data class ManifestFlags(
        val isSystemApp: Boolean,
        val isLauncherApp: Boolean,
        val isShowLaunchIcon: Boolean
    )

    /**
     * 从 `aapt dump xmltree AndroidManifest.xml` 的输出中解析应用特征：
     * - sharedUserId == android.uid.system → 系统应用
     * - intent.category.HOME → Launcher 应用
     * - intent.category.LAUNCHER → 展示桌面图标
     */
    private fun parseManifestFlags(output: String): ManifestFlags {
        var isSystemApp = false
        var isLauncherApp = false
        var isShowLaunchIcon = false
        for (rawLine in output.lines()) {
            val line = rawLine.trimStart()
            if (line.startsWith("A: android:sharedUserId")) {
                if (attrValueRegex.find(line)?.groupValues?.get(1) == "android.uid.system") {
                    isSystemApp = true
                }
            } else if (line.startsWith("A: android:name(0x01010003)=")) {
                when (attrValueRegex.find(line)?.groupValues?.get(1)) {
                    "android.intent.category.LAUNCHER" -> isShowLaunchIcon = true
                    "android.intent.category.HOME" -> isLauncherApp = true
                }
            }
        }
        return ManifestFlags(isSystemApp, isLauncherApp, isShowLaunchIcon)
    }

    /**
     * 从 `apksigner verify --print-certs -v` 的输出中解析证书 MD5 与签名方案版本。
     * @return (证书 MD5 大写, 签名版本列表如 ["v1","v2"])
     */
    private fun parseSignature(output: String): Pair<String, List<String>> {
        var signatureMd5 = ""
        val versions = mutableListOf<String>()
        for (line in output.lines()) {
            signerMd5Regex.find(line)?.let { signatureMd5 = it.groupValues[1].trim().uppercase() }
            signSchemeRegex.find(line)?.let { match ->
                if (match.groupValues[2] == "true") versions.add("v${match.groupValues[1]}")
            }
        }
        return signatureMd5 to versions
    }

    /**
     * 从 APK（zip 包）中解出图标文件到临时目录。
     * 对齐原版 extract_icon：目标目录 `%LOCALAPPDATA%/EasyADB/tmp/icon`，每次解析前清空。
     * @return (图标本地绝对路径, 尺寸描述 "WxH")；无图标或解出失败返回 ("", "")
     */
    private fun extractIconToTemp(apkFile: File, iconEntryPath: String): Pair<String, String> {
        if (iconEntryPath.isBlank()) return "" to ""

        val iconRoot = File(AppPathsConfig.tempPath, "icon")
        if (iconRoot.exists()) FileUtils.clearDirectory(iconRoot)
        iconRoot.mkdirs()

        return try {
            ZipFile(apkFile).use { zip ->
                val entry = zip.getEntry(iconEntryPath) ?: return "" to ""
                val target = File(iconRoot, iconEntryPath.replace('/', File.separatorChar))
                target.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                val sizeInfo = readImageSize(target)
                target.absolutePath to sizeInfo
            }
        } catch (e: Exception) {
            logger.debug { "extract icon failed: ${e.message}" }
            "" to ""
        }
    }

    /** 读取图片的原始宽高，失败返回空串 */
    private fun readImageSize(imageFile: File): String {
        return try {
            val image = ImageIO.read(imageFile) ?: return ""
            "${image.width}x${image.height}"
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * 执行 aapt / apksigner 工具命令。
     * @return 正常结束时返回输出（含 stderr 拼接）；进程无法启动（工具不在 PATH、超时等）返回 null
     */
    private suspend fun execTool(command: String): String? {
        val result = ExecUtils.execCmd(command, timeoutMs = TOOL_TIMEOUT_MS)
        // exitCode == -1 表示进程未能启动或超时被杀，视为环境问题
        if (result.exitCode == -1) {
            logger.debug { "execTool cannot run [$command]: ${result.output}" }
            return null
        }
        return result.output
    }
}
