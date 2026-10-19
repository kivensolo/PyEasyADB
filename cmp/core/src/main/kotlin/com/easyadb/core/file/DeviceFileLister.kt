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
 * 解析按**行格式策略列表**分流（[ROW_FORMATS]）：现代 toybox 8 列 / 月份名变体 9 列 /
 * 老版缺列 6~7 列——每种列布局一个独立解析器（局部变量按语义命名），新增设备格式时
 * 追加一个策略并注册进列表即可，不在单一函数里堆 if-else 补丁。
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

    /** ISO 日期（yyyy-MM-dd，toybox 与老版 toolbox 共用的日期列口径） */
    private val DATE_ISO = Regex("\\d{4}-\\d{2}-\\d{2}")

    /** 单行解析结果：[perm]/[name]/[time]/[sizeBytes] 已按语义拆列，调用方不再关心列下标 */
    private data class ParsedFields(
        val perm: String,
        val name: String,
        val time: String,
        val sizeBytes: Long
    )

    /**
     * 一种 ls -la 行格式的列解析策略。
     *
     * 设备端 ls 实现版本不同（toybox / 老版 toolbox），同一行数据的**列布局**不同；
     * 每种布局实现一个 [parse]，[parseLine] 按注册顺序逐一尝试，首个命中即生效。
     * 新增设备格式 = 新增一个实现 + 注册进 [ROW_FORMATS]，不改既有代码。
     */
    private fun interface RowFormat {
        /** 能解析该行返回字段；不匹配返回 null（交下一个策略尝试） */
        fun parse(line: String): ParsedFields?
    }

    /**
     * 现代 toybox（Android 6+）格式，8 列：
     * `drwxrwx--x 2 root sdcard 4096 2026-09-28 09:41 Download`
     * ```
     * [0]perm [1]links [2]owner [3]group [4]size [5]date(yyyy-MM-dd) [6]time(HH:mm) [7..]name
     * ```
     */
    private val ToyboxModern = RowFormat { line ->
        val cols = line.split(Regex("\\s+"), limit = 8)
        if (cols.size < 8) return@RowFormat null
        val perm = cols[0]
        val links = cols[1]
        val size = cols[4]
        val date = cols[5]
        val time = cols[6]
        val name = cols[7]
        if (links.toLongOrNull() == null) return@RowFormat null          // links 列必须是数字
        if (!DATE_ISO.matches(date)) return@RowFormat null
        if (!TIME_24H.matches(time)) return@RowFormat null
        ParsedFields(perm, name, "${date.replace('-', '/')} $time", size.toLongOrNull() ?: return@RowFormat null)
            .takeIf { perm.startsWith("d") || perm.startsWith("l") || perm.startsWith("-") }
    }

    /**
     * 老版月份名日期变体，9 列（时间或年份占两列）：
     * ```
     * -rw-rw---- root sdcard 4096 Sep 28 09:41 name   （近 6 个月，无年份补当前年）
     * -rw-rw---- root sdcard 4096 Sep 28  2021 name   （更早，无时分补 00:00）
     * ```
     * [0]perm [1]links [2]owner [3]group [4]size [5]month [6]day [7]time|year [8..]name
     */
    private val ToolboxMonthName = RowFormat { line ->
        val cols = line.split(Regex("\\s+"), limit = 9)
        if (cols.size < 9) return@RowFormat null
        val perm = cols[0]
        val links = cols[1]
        val size = cols[4]
        val month = cols[5]
        val day = cols[6]
        val lastTimeCol = cols[7]
        val name = cols[8]
        if (links.toLongOrNull() == null) return@RowFormat null
        val monthNum = MONTHS[month] ?: return@RowFormat null
        val dayNum = day.toIntOrNull() ?: return@RowFormat null
        val isRecentHalfYear = TIME_24H.matches(lastTimeCol)     // 近 6 个月：无年份补当前年
        val year: Int?
        val timeOfDay: String
        if (isRecentHalfYear) {
            year = Year.now().value
            timeOfDay = lastTimeCol
        } else {
            year = lastTimeCol.toIntOrNull() ?: return@RowFormat null  // 更早：年份无时分
            timeOfDay = "00:00"
        }
        val time = "%04d/%02d/%02d %s".format(year, monthNum, dayNum, timeOfDay)
        ParsedFields(perm, name, time, size.toLongOrNull() ?: return@RowFormat null)
            .takeIf { perm.startsWith("d") || perm.startsWith("l") || perm.startsWith("-") }
    }

    /**
     * 老版 toolbox（Android 5.x 真机实测，如 KKHi3751V553 电视）格式，**缺 links 列**：
     * ```
     * drwxr-x--- root root 2024-06-19 11:05 .android                    （目录 6 列，无 size）
     * -rw------- root root      2 1970-01-01 08:00 .layout_version      （文件 7 列）
     * lrwxrwxrwx root root 1970-01-01 08:00 sdcard -> /storage/sdcard   （链接 6 列）
     * ```
     * [0]perm [1]owner [2]group [3]?(size) [4]date [5]time [6..]name——size 列仅文件行有（cols[3] 数字判定）。
     */
    private val ToolboxLegacy = RowFormat { line ->
        val cols = line.split(Regex("\\s+"), limit = 7)
        if (cols.size < 6) return@RowFormat null
        val perm = cols[0]
        val owner = cols[1]
        // cols[2] 是 group，分流不依赖（owner 非 links 数字已排除现代格式）
        // size 列判定：文件行 cols[3] 是数字 size；目录/链接行 cols[3] 是日期（非数字）。
        // 不能用「倒数第 N 列」定位——name 可含空格（符号链接行 "name -> target"）
        val sizeCol = cols[3]
        val hasSize = sizeCol.toLongOrNull() != null
        val date = if (hasSize) cols[4] else cols[3]
        val time = if (hasSize) cols[5] else cols[4]
        val nameStart = if (hasSize) 6 else 5
        if (cols.size <= nameStart) return@RowFormat null
        // limit=7 截断：name 自身含空格（链接行 "name -> target"）时剩余列拼回
        val name = cols.drop(nameStart).joinToString(" ")
        // 分流判据：owner 非 links 数字（现代格式此位是数字）+ 日期列为 ISO 格式
        if (owner.toLongOrNull() != null) return@RowFormat null
        if (!DATE_ISO.matches(date)) return@RowFormat null
        if (!TIME_24H.matches(time)) return@RowFormat null
        val sizeBytes = if (hasSize) sizeCol.toLongOrNull() ?: 0 else 0
        ParsedFields(perm, name, "${date.replace('-', '/')} $time", sizeBytes)
            .takeIf { perm.startsWith("d") || perm.startsWith("l") || perm.startsWith("-") }
    }

    /** 行格式策略注册表（[parseLine] 按序尝试，首个命中生效；新设备格式追加至此） */
    private val ROW_FORMATS = listOf(ToyboxModern, ToolboxMonthName, ToolboxLegacy)

    /** 单行解析：按注册序尝试各格式策略，首个命中生效 */
    private fun parseLine(line: String, dirPath: String): FileEntry? {
        val fields = ROW_FORMATS.firstNotNullOfOrNull { format ->
            runCatching { format.parse(line) }.getOrNull()
        } ?: return null
        // 符号链接名形如 "sdcard -> /storage/self/primary"：拆出显示名与指向目标
        val arrowIdx = fields.name.indexOf(" -> ")
        val displayName = if (arrowIdx >= 0) fields.name.substring(0, arrowIdx) else fields.name
        val linkTarget = if (arrowIdx >= 0) fields.name.substring(arrowIdx + 4) else null
        // 目录符号链接按目录展开（isDir=true）：设备根的 sdcard/etc/vendor 等均为
        // 链接，按文件处理会不可展开（走查反馈：/ 下看不到 sdcard）；展开走尾斜杠
        // ls 自动跟随目标，文件链接展开则以错误行呈现（优雅降级）
        val isDir = fields.perm.startsWith("d") || fields.perm.startsWith("l")
        val isSymlink = fields.perm.startsWith("l")
        return FileEntry(
            path = if (dirPath.endsWith("/")) "$dirPath$displayName" else "$dirPath/$displayName",
            name = displayName,
            isDir = isDir,
            perm = fields.perm,
            modifiedAt = fields.time,
            sizeText = if (isDir) "--" else humanizeSize(fields.sizeBytes),
            sizeBytes = fields.sizeBytes,
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
