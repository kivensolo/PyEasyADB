package com.easyadb.ui.filetransfer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.easyadb.core.file.FileEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 文件树状态（SP-5001）——双栏各持一实例，懒加载一层 + 展开/选中。
 *
 * [lister] 由面板注入（PC 侧 PcFileLister、设备侧 DeviceFileLister 包装），
 * 内部 scope 自持（面板销毁即随 GC 取消未完成加载，无副作用可安全丢弃）；
 * 加载完成后回填父行目录「N 项」计数（H5 .dx-size 口径）。
 *
 * 状态驱动局部重组：展开集合/选中路径/子级映射均为 Compose 状态，
 * [visibleRows] 为派生扁平列表（LazyColumn 直接消费，key = path）。
 */
class FileTreeState(
    val rootPath: String,
    private val lister: suspend (path: String) -> List<FileEntry>
) {
    /** 目录路径 → 子级列表（已加载层） */
    private val children = mutableStateMapOf<String, List<FileEntry>>()

    /** 已展开目录路径集合 */
    var expandedPaths by mutableStateOf<Set<String>>(emptySet())
        private set

    /** 当前选中节点（null = 未选中） */
    var selectedEntry by mutableStateOf<FileEntry?>(null)

    /** 正在列举的目录路径（行尾 spinner 锚点，null = 空闲） */
    var loadingPath by mutableStateOf<String?>(null)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        load(rootPath)
    }

    /** 扁平化可见行（DFS 展开结果，深度供缩进） */
    data class VisibleRow(val entry: FileEntry, val depth: Int)

    val visibleRows: List<VisibleRow>
        get() {
            val rows = mutableListOf<VisibleRow>()
            fun walk(path: String, depth: Int) {
                children[path]?.forEach { entry ->
                    rows += VisibleRow(entry, depth)
                    if (entry.isDir && entry.path in expandedPaths) walk(entry.path, depth + 1)
                }
            }
            walk(rootPath, 0)
            return rows
        }

    /** 某目录已加载的子级（未加载/根外路径返回 null；新建文件夹重名检查用） */
    fun childrenOf(path: String): List<FileEntry>? = children[path]

    /** 行点击：选中；目录行同时切换展开/收起（未加载层在展开时懒加载） */
    fun toggle(entry: FileEntry) {
        selectedEntry = entry
        if (!entry.isDir) return
        if (entry.path in expandedPaths) {
            expandedPaths = expandedPaths - entry.path
        } else {
            expandedPaths = expandedPaths + entry.path
            if (!children.containsKey(entry.path)) load(entry.path)
        }
    }

    /** 投递/推送目标目录：选中目录→其路径；选中文件→父目录；未选中→根 */
    fun currentDir(): String {
        val sel = selectedEntry ?: return rootPath
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
            val list = lister(path)
            children[path] = list
            if (loadingPath == path) loadingPath = null
            backfillDirCount(path, list.size)
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
        /** 取父目录路径（设备 `/` 与 PC `\` 两种分隔符兼容） */
        fun parentOf(path: String): String {
            val i = maxOf(path.lastIndexOf('/'), path.lastIndexOf('\\'))
            return if (i <= 0) path else path.substring(0, i).ifEmpty { path }
        }
    }
}
