package com.easyadb.ui.devicelist

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easyadb.core.config.CmdGroup
import com.easyadb.core.config.CmdItem
import com.easyadb.core.database.DeviceRecord
import com.easyadb.core.device.DeviceInfo
import com.easyadb.ui.designsystem.EasyAdbColors

/**
 * 一次完整的「设备 + 命令」树面板交互回调集合。
 */
@Immutable
data class DeviceListCallbacks(
    val onDeviceClick: (DeviceRecord) -> Unit = {},
    val onDeviceDoubleClick: (DeviceRecord) -> Unit = {},
    val onDeviceAliasEdit: (DeviceRecord) -> Unit = {},
    val onDeviceDisconnect: (DeviceRecord) -> Unit = {},
    val onDeviceRemove: (DeviceRecord) -> Unit = {},
    val onCommandClick: (CmdItem) -> Unit = {},
    val onCommandDoubleClick: (CmdItem) -> Unit = {}
)

/**
 * 左侧面板：单一可滚动树（对齐 Python MainWindow.init_tree_view 中的 QTreeView）。
 *
 * 结构：
 * ```
 * Column {
 *   Header("功能区")
 *   LazyColumn {  // 单一垂直滚动
 *     设备列表（可折叠）
 *       ├─ 设备节点 ...
 *     命令列表（可折叠）
 *       ├─ 命令分组（可折叠）
 *       │     ├─ 子分组（可折叠）
 *       │     │     ├─ 命令项
 *       │     └─ 命令项
 *   }
 * }
 * ```
 *
 * Python 中的 header 文本是 "功能区"（`setHeaderData(0, Qt.Horizontal, '功能区')`），
 * 设备根节点和命令根节点都支持双击展开/折叠。
 */
@Composable
fun DeviceListPanel(
    dbDevices: List<DeviceRecord>,
    onlineDevices: List<DeviceInfo>,
    cmdGroups: List<CmdGroup>,
    selectedDeviceIp: String?,
    callbacks: DeviceListCallbacks,
    modifier: Modifier = Modifier
) {
    val deviceNodes = remember(dbDevices, onlineDevices) { buildDeviceNodes(dbDevices, onlineDevices) }
    val commandTree = remember(cmdGroups) { buildCommandNodes(cmdGroups) }
    val deviceStateIcons = rememberDeviceStateIcons()

    // 展开/折叠状态：默认所有节点全展开（对齐 Python expandAll）
    val expandedStates = remember { mutableStateMapOf<String, Boolean>() }
    fun isExpanded(id: String): Boolean = expandedStates[id] ?: true
    fun toggle(id: String) { expandedStates[id] = !isExpanded(id) }

    val deviceRootId = TreeNode.DeviceGroupRootNode().id
    val commandRootId = TreeNode.CommandGroupRootNode().id

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EasyAdbColors.SurfaceVariant)
    ) {
        // 顶部 header：对应 Python setHeaderData 的 "功能区"
        SectionHeader(text = "功能区")

        // 单一可滚动树 + 滚动条
        val listState = rememberLazyListState()
        // 最外层为Box，用于显示滚动条
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState, // 用于最终滚动位置
                modifier = Modifier.fillMaxSize()
            ) {
            // ── 设备列表根节点（可双击折叠） ──
            item(key = deviceRootId) {
                val root = TreeNode.DeviceGroupRootNode()
                TreeItem(
                    node = root,
                    isSelected = false,
                    isExpanded = isExpanded(deviceRootId),
                    hasChildren = deviceNodes.isNotEmpty(),
                    onClick = { toggle(deviceRootId) },
                    onDoubleClick = { toggle(deviceRootId) },
                    onToggleExpand = { toggle(deviceRootId) }
                )
            }
            if (isExpanded(deviceRootId)) {
                if (deviceNodes.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "暂无设备，点击「新建设备连接」",
                                fontSize = 11.sp,
                                color = EasyAdbColors.TextSecondary
                            )
                        }
                    }
                } else {
                    items(deviceNodes, key = { it.id }) { node ->
                        // 对齐 Python MainWindow.on_ip_menu_show：
                        // 设备在线 → "删除设备"禁用、"断开连接"可用；
                        // 设备离线 → "删除设备"可用、"断开连接"禁用。
                        val isOnline = node.isOnline
                        val contextMenuItems = remember(node.id, isOnline, node.record.alias) {
                            listOf(
                                ContextMenuItem(
                                    id = "alias_edit",
                                    name = "备注设置",
                                    enabled = true,
                                    onClick = { callbacks.onDeviceAliasEdit(node.record) }
                                ),
                                ContextMenuItem(
                                    id = "disconnect",
                                    name = "断开连接",
                                    enabled = isOnline,
                                    onClick = { callbacks.onDeviceDisconnect(node.record) }
                                ),
                                ContextMenuItem(
                                    id = "remove_device",
                                    name = "删除设备",
                                    enabled = !isOnline,
                                    onClick = { callbacks.onDeviceRemove(node.record) }
                                )
                            )
                        }
                        TreeItem(
                            node = node,
                            isSelected = selectedDeviceIp == node.record.ip,
                            isExpanded = false,
                            hasChildren = false,
                            onClick = { callbacks.onDeviceClick(node.record) },
                            onDoubleClick = { callbacks.onDeviceDoubleClick(node.record) },
                            onToggleExpand = {},
                            supportsDoubleClick = true,
                            deviceStateIcons = deviceStateIcons,
                            contextMenuItems = contextMenuItems
                        )
                    }
                }
            }

            // 根节点之间的细分隔线（轻量）
            item(key = "divider.between") {
                Divider(color = EasyAdbColors.Divider, thickness = 1.dp, modifier = Modifier.fillMaxWidth())
            }

            // ── 命令列表根节点（可双击折叠） ──
            item(key = commandRootId) {
                val root = TreeNode.CommandGroupRootNode()
                TreeItem(
                    node = root,
                    isSelected = false,
                    isExpanded = isExpanded(commandRootId),
                    hasChildren = commandTree.isNotEmpty(),
                    onClick = { toggle(commandRootId) },
                    onDoubleClick = { toggle(commandRootId) },
                    onToggleExpand = { toggle(commandRootId) }
                )
            }
            if (isExpanded(commandRootId)) {
                commandTree.forEach { (groupNode, children) ->
                    item(key = groupNode.id) {
                        TreeItem(
                            node = groupNode,
                            isSelected = false,
                            isExpanded = isExpanded(groupNode.id),
                            hasChildren = children.isNotEmpty(),
                            onClick = { toggle(groupNode.id) },
                            onDoubleClick = { toggle(groupNode.id) },
                            onToggleExpand = { toggle(groupNode.id) },
                            supportsDoubleClick = false
                        )
                    }
                    if (isExpanded(groupNode.id)) {
                        children.forEach { child ->
                            when (child) {
                                is TreeNode.CommandSubGroupNode -> {
                                    item(key = child.id) {
                                        TreeItem(
                                            node = child,
                                            isSelected = false,
                                            isExpanded = isExpanded(child.id),
                                            hasChildren = child.subGroup.items.isNotEmpty(),
                                            onClick = { toggle(child.id) },
                                            onDoubleClick = { toggle(child.id) },
                                            onToggleExpand = { toggle(child.id) },
                                            supportsDoubleClick = false
                                        )
                                    }
                                    if (isExpanded(child.id)) {
                                        items(child.subGroup.items, key = { "${child.id}.${it.name}" }) { item ->
                                            CommandLeafItem(
                                                parentGroupId = child.parentGroupName,
                                                item = item,
                                                depth = child.depth + 1,
                                                onClick = { callbacks.onCommandClick(item) },
                                                onDoubleClick = { callbacks.onCommandDoubleClick(item) }
                                            )
                                        }
                                    }
                                }
                                is TreeNode.CommandNode -> {
                                    item(key = child.id) {
                                        CommandLeafItem(
                                            parentGroupId = child.parentGroupId,
                                            item = child.item,
                                            depth = child.depth,
                                            onClick = { callbacks.onCommandClick(child.item) },
                                            onDoubleClick = { callbacks.onCommandDoubleClick(child.item) }
                                        )
                                    }
                                }
                                else -> Unit
                            }
                        }
                    }
                }
            }
            }
            // 滚动条
            VerticalScrollbar(
                //与 LazyColumn 共享 listState，用于跟踪滚动位置
                adapter = rememberScrollbarAdapter(listState),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(5.dp)
            )
        }
    }
}

/**
 * 命令树叶子节点：复用 [TreeItem]，但不需要分组节点的展开逻辑。
 */
@Composable
private fun CommandLeafItem(
    parentGroupId: String,
    item: CmdItem,
    depth: Int,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit
) {
    val node = TreeNode.CommandNode(parentGroupId = parentGroupId, item = item, depth = depth)
    TreeItem(
        node = node,
        isSelected = false,
        isExpanded = false,
        hasChildren = false,
        onClick = onClick,
        onDoubleClick = onDoubleClick,
        onToggleExpand = {},
        supportsDoubleClick = true
    )
}

@Composable
private fun SectionHeader(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(EasyAdbColors.PanelHeader)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            color = EasyAdbColors.TextPrimary
        )
    }
}

/**
 * 提供给宿主使用的选中状态容器；保留 [Stable] 标记方便 Compose 跳过重组。
 */
@Stable
class DeviceSelectionState {
    var selectedIp: String? by mutableStateOf(null)
        internal set
}
