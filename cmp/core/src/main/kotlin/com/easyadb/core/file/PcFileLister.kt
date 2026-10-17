package com.easyadb.core.file

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.DosFileAttributes
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * PC 侧文件列举（SP-5001）——NIO 懒加载一层，Windows 权限/隐藏项过滤。
 *
 * ## Windows 特有处理
 * - **每条目独立 try-catch** 吞 `AccessDeniedException`：受限目录（系统/其他用户）
 *   列举时会对个别条目大量抛异常，不能让单条失败炸掉整层；
 * - `DosFileAttributes` 过滤隐藏/系统项；符号链接按链接本身呈现不跟进；
 * - 全部 [Dispatchers.IO]：网络驱动器/OneDrive 目录列举可能秒级阻塞，UI 只收结果。
 *
 * 时间统一 `yyyy/MM/dd HH:mm`（与设备列同构），大小与 [DeviceFileLister.humanizeSize]
 * 同口径。目录「N 项」由 UI 侧首次展开回填，此处目录 sizeText 为 `--`。
 */
object PcFileLister {

    /** PC 树默认根（用户主目录；盘符导航见 SP-5001 §7 演进） */
    val PC_ROOT: File = File(System.getProperty("user.home"))

    private val TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")
        .withZone(ZoneId.systemDefault())

    /** 列举本地目录一层；目录不可读时返回空列表（UI 空态呈现） */
    suspend fun list(dir: File): List<FileEntry> = withContext(Dispatchers.IO) {
        val entries = mutableListOf<FileEntry>()
        // newDirectoryStream 每条目独立 try-catch（受保护条目抛 AccessDeniedException）
        runCatching {
            Files.newDirectoryStream(Paths.get(dir.absolutePath)).use { stream ->
                for (path in stream) {
                    val entry = runCatching { toEntry(path.toFile()) }.getOrNull() ?: continue
                    // Windows 隐藏/系统项过滤（DosFileAttributes 读失败时保守显示）
                    val dos = runCatching {
                        Files.readAttributes(path, DosFileAttributes::class.java)
                    }.getOrNull()
                    if (dos != null && (dos.isHidden || dos.isSystem)) continue
                    entries += entry
                }
            }
        }.onFailure {
            println("[PcFileLister] list failed: ${dir.absolutePath} — ${it.message}")
        }
        entries.sortedWith(compareByDescending<FileEntry> { it.isDir }.thenBy { it.name.lowercase() })
    }

    private fun toEntry(file: File): FileEntry {
        val attrs = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
        val isDir = attrs.isDirectory
        return FileEntry(
            path = file.absolutePath,
            name = file.name,
            isDir = isDir,
            perm = null,
            modifiedAt = TIME_FORMAT.format(attrs.lastModifiedTime().toInstant()),
            sizeText = when {
                isDir -> "--"
                else -> DeviceFileLister.humanizeSize(attrs.size())
            },
            sizeBytes = if (isDir) -1L else attrs.size()
        )
    }
}
