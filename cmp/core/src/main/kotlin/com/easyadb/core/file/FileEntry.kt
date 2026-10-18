package com.easyadb.core.file

/**
 * 文件树节点（SP-5001）——PC 本地与设备侧统一数据模型。
 *
 * 双栏共用一套模型：PC 侧 [perm] 恒为 null（Windows 无权限位，UI 渲染三列），
 * 设备侧来自 `ls -la` 解析（权限/时间/大小齐备，UI 渲染四列）。
 *
 * @param path 完整路径（设备侧 `/sdcard/...`，PC 侧 File 绝对路径）
 * @param name 显示名（保留空格/中文）
 * @param isDir 是否目录
 * @param perm Linux 权限位（`drwxrwx--x` / `-rw-rw----`），PC 侧为 null
 * @param modifiedAt 修改时间（`yyyy/MM/dd HH:mm` 统一格式化，双列同构）
 * @param sizeText 大小展示文本（人性化 B/KB/MB/GB；目录为「N 项」或未加载 `--`）
 * @param isSymlink 符号链接（设备 `l` 权限位 / PC NIO isSymbolicLink）——
 *   目录链接可展开但 UI 需角标区分（青色文件夹 + 右上箭头）
 * @param linkTarget 符号链接指向的真实路径（`ls -la` 名称列 `name -> target` 的
 *   target 段；设备侧目录链接在名称右侧展示「→ 指向」）
 */
data class FileEntry(
    val path: String,
    val name: String,
    val isDir: Boolean,
    val perm: String? = null,
    val modifiedAt: String? = null,
    val sizeText: String = "",
    val sizeBytes: Long = -1,
    val isSymlink: Boolean = false,
    val linkTarget: String? = null
)

/**
 * 目录列举结果（SP-5001）：[entries] 为子级列表；[error] 非空表示列举失败
 * （权限拒绝 / 命令失败 / 超时 / 离线），此时 entries 为空——UI 需把原因
 * 展示给用户而非误报「空目录」（走查反馈：/storage/emulated Permission
 * denied 被静默吞掉）。
 */
data class DirListResult(
    val entries: List<FileEntry> = emptyList(),
    val error: String? = null
)
