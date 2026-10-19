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
 * 老版 toolbox（Android 5.x 真机实测，如 KKHi3751V553 电视）**缺 links 列**，且目录行
 * 连 size 列也没有：
 * ```
 * drwxr-x--- root root  2024-06-19 11:05 .android          （目录 6 列，无 size）
 * -rw------- root root      2 1970-01-01 08:00 .layout_version（文件 7 列）
 * lrwxrwxrwx root root  1970-01-01 08:00 sdcard -> /storage/sdcard（链接 6 列）
 * ```
 * 另有老版月份名日期变体：`... 4096 Sep 28 09:41 name`（近 6 个月，无年份补当前年）
 * 或 `... 4096 Sep 28  2021 name`（更早，无时分补 00:00）。
 * 解析按「列数 + 日期列位置」分流：现代 8 列 / 老版 7 列（文件）/ 老版 6 列（目录、链接）。
 */
object DeviceFileLister {

    /** 设备文件树根（整机根目录，可逐层进入 /data /system 等；受限目录列举为空由 UI 空态呈现） */
    const val DEVICE_ROOT = "/"

    /** 传输默认落点（设备树未选中目录时的 push 目标——根目录只读不可写） */
    const val DEFAULT_TRANSFER_DIR = "/sdcard"

    /** 目录列举超时（大目录 shell 响应预留，正常目录 <1s） */
    private const val LIST_TIMEOUT_MS = 15_000L

    private val TIME_24H = Regex("\\d{2}:\\d{2}")

    /**
     * 列举设备目录一层。失败（设备离线/路径不存在/超时）返回 [DirListResult.error]；
     * 命令执行成功但无有效行时（典型：`ls: xxx: Permission denied` 走 stderr
     * 混入输出、解析得 0 行），从输出提取首条非 total 行作为失败原因。
     *
     * 路径**强制带尾斜杠**（`ls -la '<dir>/'`）：多数设备 `/sdcard` 是符号链接
     * （→ /storage/self/primary），无尾斜杠时 ls 只返回链接自身一行，不列内容
     * （实机 emulator-5554 踩坑归档）。
     */
    suspend fun list(deviceIp: String, dirPath: String): DirListResult = withContext(Dispatchers.IO) {
        val target = dirPath.trimEnd('/') + "/"
        val cmd = "adb -s $deviceIp shell \"ls -la '${target.replace("'", "")}'\""
        val result = ExecUtils.execCmd(cmd, timeoutMs = LIST_TIMEOUT_MS, shell = true)
        if (!result.success) {
            return@withContext DirListResult(error = result.output.take(200).ifBlank { "命令执行失败（超时或 adb 异常）" })
        }
        val entries = parseLsOutput(result.output, dirPath)
        val error = if (entries.isEmpty()) firstErrorLine(result.output, dirPath) else null
        DirListResult(entries, error)
    }

    /**
     * 首条**无法解析**的行 = 列举失败原因（`ls: xxx: Permission denied` 走 stderr
     * 混入输出）。仅含 total / `.` / `..` 的真空目录不算失败（走查反馈：空目录曾把
     * `.` 行误当原因显示成红色错误）。
     */
    internal fun firstErrorLine(output: String, dirPath: String): String? =
        output.lineSequence()
            .map { it.trimEnd() }
            .firstOrNull { it.isNotEmpty() && !it.startsWith("total") && parseLine(it, dirPath) == null }
            ?.take(200)

    /** `shell mkdir` 命令构建（目录路径内空格由单引号包裹，文件名撇号防御性剔除） */
    fun mkdirCmd(deviceIp: String, dirPath: String): String =
        "adb -s $deviceIp shell \"mkdir '${dirPath.replace("'", "")}'\""

    /** `shell rm -f` 命令构建（文件删除） */
    fun rmCmd(deviceIp: String, filePath: String): String =
        "adb -s $deviceIp shell \"rm -f '${filePath.replace("'", "")}'\""

    /** `shell rm -rf` 命令构建（目录递归删除，调用方须先经用户确认） */
    fun rmDirCmd(deviceIp: String, dirPath: String): String =
        "adb -s $deviceIp shell \"rm -rf '${dirPath.replace("'", "")}'\""

    /**
     * 受保护目录（删除守卫，SP-5001 走查追加）：整机根、用户存储入口与系统分区——
     * 递归删除将导致设备不可用/数据全失。UI 在打开删除确认前拦截并提示。
     */
    private val PROTECTED_DIRS = setOf(
        "/", "/sdcard", "/storage", "/storage/emulated", "/storage/self",
        "/data", "/system", "/vendor", "/product", "/system_ext",
        "/etc", "/dev", "/proc", "/sys", "/oem", "/bin", "/sbin", "/mnt"
    )

    /** 是否受保护目录（尾部斜杠归一） */
    fun isProtectedDir(path: String): Boolean {
        val norm = if (path.length > 1) path.trimEnd('/') else "/"
        return norm in PROTECTED_DIRS
    }

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
            val entry = parseLine(line, dirPath) ?: return@forEach
            // "."/".." 目录自引用行在解析层保留（供错误判定识别），此处跳过不入列
            if (entry.name == "." || entry.name == "..") return@forEach
            entries += entry
        }
        // 目录优先，同组按名称（对齐 H5/CMP 文件树排序口径）
        return entries.sortedWith(compareByDescending<FileEntry> { it.isDir }.thenBy { it.name.lowercase() })
    }

    /** ISO 日期（yyyy-MM-dd，老版 toolbox 的日期列口径） */
    private val DATE_ISO = Regex("\\d{4}-\\d{2}-\\d{2}")

    /** 单行解析：按列数/日期列位置分流现代/老版布局，返回 null 表示无法识别 */
    private fun parseLine(line: String, dirPath: String): FileEntry? {
        val perm = line.substringBefore(' ')
        if (!perm.startsWith("d") && !perm.startsWith("l") && !perm.startsWith("-")) return null

        val name: String
        val time: String
        var sizeBytes = 0L

        // 老版 toolbox（Android 5.x 真机实测，如 KKHi3751V553 电视）：perm owner group
        // [size] date time name——**缺 links 列**，目录/链接行无 size（6 列），文件行有（7 列）。
        // 与现代格式（第 2 列是 links 数字）的分流判据：第 2 列非数字 + 倒数第 2/3 列为 ISO 日期 + HH:mm
        val cols7 = line.split(Regex("\\s+"), limit = 7)
        val legacy = cols7.size >= 6 &&
            cols7[1].toLongOrNull() == null &&                    // 无 links 列（现代此位是数字）
            DATE_ISO.matches(cols7[cols7.size - 3]) &&            // 倒数第 3 列 yyyy-MM-dd
            TIME_24H.matches(cols7[cols7.size - 2])               // 倒数第 2 列 HH:mm
        if (legacy) {
            time = cols7[cols7.size - 3].replace('-', '/') + " " + cols7[cols7.size - 2]
            name = cols7[cols7.size - 1]
            if (cols7.size == 7) sizeBytes = cols7[3].toLongOrNull() ?: 0  // 文件行（7 列）才有 size
        } else {
            // 现代 toybox：perm links owner group size date time name（limit 8，name=第 8 列后剩余串）
            val cols = line.split(Regex("\\s+"), limit = 8)
            if (cols.size < 8) return null
            sizeBytes = cols[4].toLongOrNull() ?: return null
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
        }
        // 符号链接名形如 "sdcard -> /storage/self/primary"：拆出显示名与指向目标
        val arrowIdx = name.indexOf(" -> ")
        val displayName = if (arrowIdx >= 0) name.substring(0, arrowIdx) else name
        val linkTarget = if (arrowIdx >= 0) name.substring(arrowIdx + 4) else null
        // 目录符号链接按目录展开（isDir=true）：设备根的 sdcard/etc/vendor 等均为
        // 链接，按文件处理会不可展开（走查反馈：/ 下看不到 sdcard）；展开走尾斜杠
        // ls 自动跟随目标，文件链接展开则以错误行呈现（优雅降级）
        val isDir = perm.startsWith("d") || perm.startsWith("l")
        val isSymlink = perm.startsWith("l")
        return FileEntry(
            path = if (dirPath.endsWith("/")) "$dirPath$displayName" else "$dirPath/$displayName",
            name = displayName,
            isDir = isDir,
            perm = perm,
            modifiedAt = time,
            sizeText = if (isDir) "--" else humanizeSize(sizeBytes),
            sizeBytes = sizeBytes,
            isSymlink = isSymlink,
            linkTarget = linkTarget
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
