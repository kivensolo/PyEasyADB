package com.easyadb.core.apk

/**
 * 解析后的 APK 信息数据类。
 * 对应 Python 中 FileUtils.parse_apk() 返回的字典（utils/Utils.py）。
 */
data class ApkInfo(
    /** 包名。对应 package_name */
    val packageName: String = "",
    /** 应用名称。对应 app_name */
    val label: String = "",
    /** App 版本号。对应 version_name */
    val versionName: String = "",
    /** 代码版本号。对应 version_code */
    val versionCode: String = "",
    /** Min.SDK。对应 min_sdk */
    val minSdk: String = "",
    /** 启动入口 Activity。对应 launchable_activity，无入口时为 "N/A" */
    val launchableActivity: String = "N/A",
    /** 证书 MD5（大写）。对应 sign_md5 */
    val signatureMd5: String = "",
    /** 签名方案版本列表（如 ["v1","v2"]）。对应 sign_md5_version */
    val signatureVersions: List<String> = emptyList(),
    /** 权限列表（原始完整权限名）。对应 permissions */
    val permissions: List<String> = emptyList(),
    /** 是否 Launcher 应用（intent.category.HOME）。对应 is_launcher_app */
    val isLauncherApp: Boolean = false,
    /** 是否展示桌面图标（intent.category.LAUNCHER）。对应 is_show_launch_icon */
    val isShowLaunchIcon: Boolean = false,
    /** 是否系统应用（sharedUserId == android.uid.system）。对应 is_sys_app */
    val isSystemApp: Boolean = false,
    /** 从 APK 抽取的图标本地文件路径（tmp/icon 下）。对应 icon_path（抽取后） */
    val iconFilePath: String = "",
    /** 图标原始尺寸描述（如 "192x192"），空表示无图标或读取失败 */
    val iconSizeInfo: String = "",
    /** APK 文件绝对路径 */
    val filePath: String = "",
    /** 文件名。对应 file_name */
    val fileName: String = "",
    /** 文件大小（字节）。对应 file_bytes */
    val fileBytes: Long = 0L,
    /** 文件 MD5（大写）。对应 file_md5 */
    val fileMd5: String = "",
    /** 文件最后修改时间（yyyy-MM-dd HH:mm:ss）。对应 last_modified */
    val lastModified: String = ""
)
