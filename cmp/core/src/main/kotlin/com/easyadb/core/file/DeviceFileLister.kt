package com.easyadb.core.file

import com.easyadb.core.util.ExecUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.Year
import java.time.format.DateTimeFormatter

/**
 * 设备侧文件列举（SP-5001）——`adb shell ls -la` 懒加载一层。
 *
 * 对应 AppListLoader 的执行模式（ExecUtils 一次性进程 + 显式 UTF-8 读取 +
 * 失败返回空列表由 UI 呈现），不做 `ls -laR` 全量倾泻（数千条目秒级卡顿，
 * 且 shell 单次开销 50~200ms，懒加载交互秒回）。
 *
 * ## ls -la 输出解析
 * 现代 toybox（Android 6+，本工具目标设备口径）：
 * ```
 * total 24
 * drwxrwx--x  2 root sdcard  4096 2026-09-28 09:41 Download
 * -rw-rw----  1 root sdcard  4096 2026-10-09 11:02 device-info.txt
 * ```
 * 老版 toolbox（Android <6，防御性兼容）：`... 4096 Sep 28 09:41 name`（近 6 个月，
 * 无年份补当前年）或 `... 4096 Sep 28  2021 name`（更早，无时分补 00:00）。
 * 两种格式**列数不同**（时间列占位差异），解析按「时间列是否 HH:mm」分流。
 */
object DeviceFileLister {

    /** 设备文件树默认根（对齐 H5 参照稿与现网推送落点习惯） */
    const val DEVICE_ROOT = "/sdcard"

    /** 目录列举超时（大目录 shell 响应预留，正常目录 <1s） */
    private const val LIST_TIMEOUT_MS = 15_000L

    private val TIME_24H = Regex("\\d{2}:\\d{2}")

    /**
     * 列举设备目录一层。失败（设备离线/路径不存在/超时）返回空列表并记录日志，
     * 不抛出——UI 以空态呈现，与 AppListLoader 行为一致。
     */
    suspend fun list(deviceIp: String, dirPath: String): List<FileEntry> = withContext(Dispatchers.IO) {
        val cmd = "adb -s $deviceIp shell \"ls -la '${dirPath.replace("'", "")}'\""
        val result = ExecUtils.execCmd(cmd, timeoutMs = LIST_TIMEOUT_MS, shell = true)
        if (!result.success) {
            println("[DeviceFileLister] list failed: ${result.output.take(200)}")
            return@withContext emptyList()
        }
        parseLsOutput(result.output, dirPath)
    }

    /** `shell mkdir` 命令构建（目录路径内空格由单引号包裹，文件名撇号防御性剔除） */
    fun mkdirCmd(deviceIp: String, dirPath: String): String =
        "adb -s $deviceIp shell \"mkdir '${dirPath.replace("'", "")}'\""

    /** `shell rm -f` 命令构建（v1 仅文件删除） */
    fun rmCmd(deviceIp: String, filePath: String): String =
        "adb -s $deviceIp shell \"rm -f '${filePath.replace("'", "")}'\""

    /**
     * 解析 `ls -la` 输出为节点列表（包内可见，供 fixture 校验）。
     * [dirPath] 用于拼接子节点完整路径；无法识别的行（stderr 混入等）静默跳过。
     */
    internal fun parseLsOutput(output: String, dirPath: String): List<FileEntry> {
        val entries = mutableListOf<FileEntry>()
        output.lineSequence().forEach { rawLine ->
            val line = rawLine.trimEnd()
            if (line.isEmpty()) return@forEach
            // 权限位必须以 d/-/l 开头：借此跳过 total 行与 stderr 附加信息
            val first = line.first()
            if (first != 'd' && first != '-' && first != 'l') return@forEach
            parseLine(line, dirPath)?.let { entries += it }
        }
        // 目录优先，同组按名称（对齐 H5/CMP 文件树排序口径）
        return entries.sortedWith(compareByDescending<FileEntry> { it.isDir }.thenBy { it.name.lowercase() })
    }

    /** 单行解析：按时间列格式分流现代/老版布局，返回 null 表示无法识别 */
    private fun parseLine(line: String, dirPath: String): FileEntry? {
        // 现代 toybox：perm links owner group size date time name（limit 8，name=第 8 列后剩余串）
        val cols = line.split(Regex("\\s+"), limit = 8)
        if (cols.size < 8) return null
        val perm = cols[0]
        val sizeBytes = cols[4].toLongOrNull() ?: return null

        val name: String
        val time: String
        if (TIME_24H.matches(cols[6]) && cols[5].length == 10 && cols[5][4] == '-') {
            // 现代：yyyy-MM-dd + HH:mm
            name = cols[7]
            time = cols[5].replace('-', '/') + " " + cols[6]
        } else if (MONTHS.containsKey(cols[5]) && cols[6].toIntOrNull() != null) {
            // 老版：month day 之后还有一列时间或年份，文件名整体再右移一列
            val cols9 = line.split(Regex("\\s+"), limit = 9)
            if (cols9.size < 9) return null
            name = cols9[8]
            val month = MONTHS[cols[5]]!!
            val day = cols[6].toInt()
            time = when {
                TIME_24H.matches(cols9[7]) ->  // 近 6 个月：无年份补当前年
                    "%04d/%02d/%02d %s".format(Year.now().value, month, day, cols9[7])
                cols9[7].toIntOrNull() != null ->  // 更早：年份无时分
                    "%s/%02d/%02d 00:00".format(cols9[7], month, day)
                else -> return null
            }
        } else {
            return null
        }
        if (name == "." || name == "..") return null
        // 符号链接显示名去 " -> target" 尾部，按链接本身呈现
        val displayName = name.substringBefore(" ->")
        val isDir = perm.startsWith("d")
        return FileEntry(
            path = if (dirPath.endsWith("/")) "$dirPath$displayName" else "$dirPath/$displayName",
            name = displayName,
            isDir = isDir,
            perm = perm,
            modifiedAt = time,
            sizeText = if (isDir) "--" else humanizeSize(sizeBytes),
            sizeBytes = sizeBytes
        )
    }

    private val MONTHS = mapOf(
        "Jan" to 1, "Feb" to 2, "Mar" to 3, "Apr" to 4, "May" to 5, "Jun" to 6,
        "Jul" to 7, "Aug" to 8, "Sep" to 9, "Oct" to 10, "Nov" to 11, "Dec" to 12
    )

    /** 字节数人性化（1 位小数，B/KB/MB/GB） */
    internal fun humanizeSize(bytes: Long): String {
        if (bytes < 0) return "--"
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.1f KB".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024) return "%.1f MB".format(mb)
        return "%.1f GB".format(mb / 1024.0)
    }
}
