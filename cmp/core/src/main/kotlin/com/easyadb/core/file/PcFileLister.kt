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
 * 根视图为「此电脑」虚拟根（[THIS_PC_ROOT] 空路径），子级为盘符列表
 * （`File.listRoots()`）；盘符/目录逐层懒加载展开。
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

    /** 「此电脑」虚拟根（空路径，UI 树根），子级为盘符 */
    const val THIS_PC_ROOT = ""

    /** 传输落点兜底目录（PC 树未选中时的拉取目标） */
    val DEFAULT_SAVE_DIR: File = File(System.getProperty("user.home"))

    private val TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")
        .withZone(ZoneId.systemDefault())

    /** 列举一层：[path] 为空串返回盘符列表；目录不可读时以 [DirListResult.error] 呈现原因 */
    suspend fun list(path: String): DirListResult = withContext(Dispatchers.IO) {
        if (path.isBlank()) {
            return@withContext DirListResult(
                entries = File.listRoots()
                    .sortedBy { it.absolutePath }
                    .map { root ->
                        FileEntry(
                            path = root.absolutePath,
                            name = root.absolutePath,
                            isDir = true,
                            perm = null,
                            modifiedAt = null,
                            sizeText = "--"
                        )
                    }
            )
        }
        val entries = mutableListOf<FileEntry>()
        val failure = runCatching {
            Files.newDirectoryStream(Paths.get(path)).use { stream ->
                for (file in stream) {
                    val entry = runCatching { toEntry(file.toFile()) }.getOrNull() ?: continue
                    // Windows 隐藏/系统项过滤（DosFileAttributes 读失败时保守显示）
                    val dos = runCatching {
                        Files.readAttributes(file, DosFileAttributes::class.java)
                    }.getOrNull()
                    if (dos != null && (dos.isHidden || dos.isSystem)) continue
                    entries += entry
                }
            }
        }.exceptionOrNull()
        if (failure != null) {
            return@withContext DirListResult(error = failure.message?.take(200) ?: "无法读取目录")
        }
        DirListResult(entries.sortedWith(compareByDescending<FileEntry> { it.isDir }.thenBy { it.name.lowercase() }))
    }

    private fun toEntry(file: File): FileEntry {
        val attrs = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
        val isSymlink = attrs.isSymbolicLink
        // PC 符号链接按链接本身呈现（不跟进目标），perm 无 Linux 权限位——仅图标角标区分
        val isDir = attrs.isDirectory || isSymlink && Files.isDirectory(file.toPath())
        return FileEntry(
            path = file.absolutePath,
            name = file.name,
            isDir = isDir,
            perm = null,
            modifiedAt = TIME_FORMAT.format(attrs.lastModifiedTime().toInstant()),
            sizeText = if (isDir) "--" else DeviceFileLister.humanizeSize(attrs.size()),
            sizeBytes = if (isDir) -1L else attrs.size(),
            isSymlink = isSymlink
        )
    }
}
