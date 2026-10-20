package com.easyadb.ui.filetransfer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.easyadb.core.file.DirListResult
import com.easyadb.core.file.FileEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 文件树状态（SP-5001）——双栏各持一实例，懒加载一层 + 展开/选中。
 *
 * [scope] 由面板注入（rememberCoroutineScope，Compose UI 调度器）：加载协程随
 * 面板销毁自动取消，无跨面板泄漏；**不可用 Dispatchers.Main 自建作用域**——
 * Desktop JVM 无 Main dispatcher 服务（报 "Module with the Main dispatcher is
 * missing"，那是 Android 平台的提供者）。lister 内部自切 IO，结果写回
 * Compose 快照状态线程安全。
 *
 * 加载完成后回填父行目录「N 项」计数（H5 .dx-size 口径）。
 *
 * 状态驱动局部重组：展开集合/选中路径/子级映射均为 Compose 状态，
 * [visibleRows] 为派生扁平列表（LazyColumn 直接消费，key = path）。
 */
class FileTreeState(
    val rootPath: String,
    private val scope: CoroutineScope,
    /** 未选中时 [currentDir] 的兜底目录（PC 侧为用户主目录——虚拟根不可作为传输落点） */
    private val defaultDir: String = rootPath,
    private val lister: suspend (path: String) -> DirListResult
) {
    /** 树根显示名（头部路径栏未选中时展示；PC 虚拟根显示「此电脑」） */
    val rootDisplay: String = rootPath.ifBlank { "此电脑" }
    /** 目录路径 → 子级列表（已加载层） */
    private val children = mutableStateMapOf<String, List<FileEntry>>()

    /** 目录路径 → 列举失败原因（权限拒绝/超时等；null = 成功或未加载，不静默吞掉） */
    private val errors = mutableStateMapOf<String, String?>()

    /** 已展开目录路径集合 */
    var expandedPaths by mutableStateOf<Set<String>>(emptySet())
        private set

    /** 当前选中节点（null = 未选中） */
    var selectedEntry by mutableStateOf<FileEntry?>(null)

    /** 正在列举的目录路径（行尾 spinner 锚点，null = 空闲） */
    var loadingPath by mutableStateOf<String?>(null)
        private set

    /** 就地新建编辑态：非 null = [creatingIn] 目录下渲染名称编辑行（文件/文件夹共用） */
    var creatingIn by mutableStateOf<String?>(null)
        private set

    /** 编辑中的新节点名称 */
    var creatingName by mutableStateOf("")

    /** 编辑中的新节点种类（true = 文件夹 / false = 文件，决定编辑行图标与提交命令） */
    var creatingIsDir by mutableStateOf(true)
        private set

    /**
     * 进入就地新建：在 [parentPath] 下插入名称编辑行（同一时刻仅一个编辑行）。
     * [isDir] 区分新建文件夹/新建文件（预填名与提交命令随之不同）；
     * [siblingNames] 供预填名自动避开同级重名（「新建文件夹 2/3…」，不改名直接
     * 提交也不会撞名失败）。
     */
    fun beginCreate(parentPath: String, isDir: Boolean = true, siblingNames: List<String> = emptyList()) {
        var name = if (isDir) "新建文件夹" else "新建文件"
        var n = 2
        while (name in siblingNames) {
            name = "${if (isDir) "新建文件夹" else "新建文件"} $n"
            n++
        }
        creatingIn = parentPath
        creatingIsDir = isDir
        creatingName = name
    }

    /** 取消新建（Esc / 校验失败） */
    fun cancelCreate() {
        creatingIn = null
        creatingName = ""
    }

    /**
     * 展开从根到 [dirPath] 的每一层（设备路径 `/a/b/c` 按段逐层加载），
     * 使该目录在树中可见——就地新建的编辑行需要挂在可见父目录下。
     */
    fun ensureExpandedChain(dirPath: String) {
        var prefix = ""
        dirPath.split('/').filter { it.isNotBlank() }.forEach { seg ->
            prefix = "$prefix/$seg"
            if (children[prefix] == null) load(prefix)
            expandedPaths = expandedPaths + prefix
        }
    }

    init {
        load(rootPath)
    }

    /** 行种类：普通节点 / 列举失败提示行（展示原因）/ 空目录提示行 / 就地新建名称编辑行 */
    enum class RowKind { NORMAL, ERROR, EMPTY, CREATING }

    /** 扁平化可见行（DFS 展开结果，深度供缩进；空/失败目录注入提示行，新建挂编辑行） */
    data class VisibleRow(val entry: FileEntry, val depth: Int, val kind: RowKind = RowKind.NORMAL)

    val visibleRows: List<VisibleRow>
        get() {
            val rows = mutableListOf<VisibleRow>()
            fun walk(path: String, depth: Int) {
                val list = children[path]
                if (list != null && list.isEmpty()) {
                    // 已加载且无子级：优先展示失败原因，否则明确「空目录」（不静默）；
                    // 就地新建编辑期间跳过提示行（空目录也要能挂编辑行）
                    if (creatingIn != path) {
                        val err = errors[path]
                        rows += if (err != null) {
                            VisibleRow(FileEntry(path = "$path/<error>", name = err, isDir = false), depth, RowKind.ERROR)
                        } else {
                            VisibleRow(FileEntry(path = "$path/<empty>", name = "（空目录）", isDir = false), depth, RowKind.EMPTY)
                        }
                    }
                    if (creatingIn == path) {
                        rows += VisibleRow(FileEntry(path = "$path/<creating>", name = creatingName, isDir = creatingIsDir), depth + 1, RowKind.CREATING)
                    }
                    return
                }
                list?.forEach { entry ->
                    rows += VisibleRow(entry, depth)
                    if (entry.isDir && entry.path in expandedPaths) walk(entry.path, depth + 1)
                }
                if (creatingIn == path) {
                    rows += VisibleRow(FileEntry(path = "$path/<creating>", name = creatingName, isDir = creatingIsDir), depth + 1, RowKind.CREATING)
                }
            }
            walk(rootPath, 0)
            return rows
        }

    /** 目录列举失败原因（权限拒绝/超时等；null = 正常） */
    fun errorOf(path: String): String? = errors[path]

    /** 某目录已加载的子级（未加载/根外路径返回 null；新建文件夹重名检查用） */
    fun childrenOf(path: String): List<FileEntry>? = children[path]

    /** 行单击：仅选中（展开/收起改由双击或折叠箭头承担，SP-5001 走查交互） */
    fun select(entry: FileEntry) {
        selectedEntry = entry
    }

    /** 展开/收起目录（未加载层在展开时懒加载）；文件节点无操作 */
    fun toggleExpand(entry: FileEntry) {
        if (!entry.isDir) return
        if (entry.path in expandedPaths) {
            expandedPaths = expandedPaths - entry.path
        } else {
            expandedPaths = expandedPaths + entry.path
            if (!children.containsKey(entry.path)) load(entry.path)
        }
    }

    /** 投递/推送目标目录：选中目录→其路径；选中文件→父目录；未选中→[defaultDir] */
    fun currentDir(): String {
        val sel = selectedEntry ?: return defaultDir
        return if (sel.isDir) sel.path else parentOf(sel.path)
    }

    /** 刷新：清缓存重载已加载的层（保留展开与选中） */
    fun refresh() {
        val paths = children.keys.toList()
        children.clear()
        paths.forEach { load(it) }
    }

    /** 整树重置（设备切换）：清空展开/选中/缓存后重载根 */
    fun reset() {
        expandedPaths = emptySet()
        selectedEntry = null
        children.clear()
        errors.clear()
        load(rootPath)
    }

    /** 外部变更后局部失效（mkdir/rm/拖放落盘后刷新某目录层） */
    fun reloadPath(path: String) {
        load(path)
    }

    /** 移除某目录下的节点（rm 后本地同步，免一次 round-trip） */
    fun removeEntry(parentPath: String, entry: FileEntry) {
        val list = children[parentPath] ?: return
        children[parentPath] = list - entry
        if (selectedEntry == entry) selectedEntry = null
    }

    /** 在某目录下插入节点（mkdir 后本地同步） */
    fun insertEntry(parentPath: String, entry: FileEntry) {
        val list = children[parentPath] ?: return
        children[parentPath] = (list + entry).sortedWith(
            compareByDescending<FileEntry> { it.isDir }.thenBy { it.name.lowercase() }
        )
    }

    private fun load(path: String) {
        loadingPath = path
        scope.launch {
            val result = lister(path)
            children[path] = result.entries
            errors[path] = result.error
            if (loadingPath == path) loadingPath = null
            backfillDirCount(path, result.entries.size)
        }
    }

    /** 子级加载后回填父行「N 项」（父层各列表中路径匹配的目录项） */
    private fun backfillDirCount(childDirPath: String, count: Int) {
        val text = "$count 项"
        children.keys.toList().forEach { parent ->
            val list = children[parent] ?: return@forEach
            val idx = list.indexOfFirst { it.isDir && it.path == childDirPath }
            if (idx >= 0) children[parent] = list.toMutableList().apply { set(idx, list[idx].copy(sizeText = text)) }
        }
    }

    companion object {
        /**
         * 取父目录路径（设备 `/` 与 PC `\` 两种分隔符兼容）。
         * 盘符根（`C:\`）的父 = 此电脑虚拟根（空串）；`C:\Users` 的父补全为 `C:\`。
         */
        fun parentOf(path: String): String {
            if (path.length >= 2 && path[1] == ':' &&
                (path.length == 2 || (path.length == 3 && (path[2] == '\\' || path[2] == '/')))
            ) return ""
            val i = maxOf(path.lastIndexOf('/'), path.lastIndexOf('\\'))
            if (i < 0) return path
            // Unix 绝对路径的一级（/default.prop）：父目录即根 `/`——返回 path 自身
            // 会让 currentDir() 把文件路径当目录用（推送落点=覆盖该文件、根目录禁用判据失效）
            if (i == 0) return "/"
            var parent = path.substring(0, i)
            if (parent.length == 2 && parent[1] == ':') parent += "\\"
            return parent
        }
    }
}
